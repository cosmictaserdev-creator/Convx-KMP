package com.convx.music.client.androidhost

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.convx.music.playback.AudioStreamResolver
import com.convx.music.playback.CrossfadeEnvelope
import com.convx.music.playback.PlaybackController
import com.convx.music.playback.PlaybackQueueEntry
import com.convx.music.playback.PlaybackQueueNavigator
import com.convx.music.playback.PlaybackQueueSnapshot
import com.convx.music.playback.PlaybackPhase
import com.convx.music.playback.PlaybackRepeatMode
import com.convx.music.playback.PlaybackRequestTrack
import com.convx.music.playback.PlaybackSettings
import com.convx.music.playback.PlaybackSettingsRepository
import com.convx.music.playback.PlaybackSleepTimer
import com.convx.music.playback.PlaybackSnapshot
import com.convx.music.playback.PlaybackTrackSnapshot
import com.convx.music.playback.planPlaybackTransitionWindow
import com.convx.music.playback.moveQueueIndices
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect

/** Android media adapter for the KMP host; all catalog and stream lookup stays behind domain ports. */
internal class AndroidPlaybackController(
    context: Context,
    private val streamResolver: AudioStreamResolver,
    private val playbackSettingsRepository: PlaybackSettingsRepository,
) : PlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val primaryPlayer = createPlayer(context)
    private val secondaryPlayer = createPlayer(context)
    private var activePlayer = primaryPlayer
    private var standbyPlayer = secondaryPlayer
    private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot.EMPTY)
    override val playbackSnapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()
    override val sleepTimer = PlaybackSleepTimer(scope, System::currentTimeMillis, ::pause)
    private val mutableQueueSnapshot = MutableStateFlow(PlaybackQueueSnapshot.EMPTY)
    override val queueSnapshot: StateFlow<PlaybackQueueSnapshot> = mutableQueueSnapshot.asStateFlow()

    private var queue: List<PlaybackRequestTrack> = emptyList()
    private val queueNavigator = PlaybackQueueNavigator()
    private var currentTrack: PlaybackRequestTrack? = null
    private var currentIndex = -1
    private var resolveJob: Job? = null
    private var progressJob: Job? = null
    private var crossfadePreloadJob: Job? = null
    private var crossfadeJob: Job? = null
    private var crossfadePreloadKey: String? = null
    private var generation = 0L
    private var playbackSettings = PlaybackSettings()
    private var repeatMode = PlaybackRepeatMode.OFF
    private var shuffleEnabled = false
    private var phase = PlaybackPhase.IDLE
    private var errorMessage: String? = null

    init {
        addPlayerListener(primaryPlayer)
        addPlayerListener(secondaryPlayer)
        scope.launch {
            playbackSettingsRepository.observeSettings().collect { settings ->
                playbackSettings = settings.normalized()
            }
        }
        progressJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                updateCrossfade()
                publish()
            }
        }
    }

    private fun createPlayer(context: Context): ExoPlayer =
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            setHandleAudioBecomingNoisy(true)
        }

    private fun addPlayerListener(source: ExoPlayer) {
        source.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (source !== activePlayer) return
                when (playbackState) {
                    Player.STATE_BUFFERING -> phase = PlaybackPhase.BUFFERING
                    Player.STATE_READY -> phase = PlaybackPhase.READY
                    Player.STATE_ENDED -> {
                        if (sleepTimer.onTrackEnded()) {
                            phase = PlaybackPhase.ENDED
                            publish()
                            return
                        }
                        if (repeatMode == PlaybackRepeatMode.ONE) {
                            source.seekTo(0L)
                            source.play()
                            return
                        }
                        phase = PlaybackPhase.ENDED
                    }
                    Player.STATE_IDLE -> if (currentTrack == null) phase = PlaybackPhase.IDLE
                }
                publish()
                if (playbackState == Player.STATE_ENDED) requestSkip(forward = true)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (source !== activePlayer) return
                if (isPlaying && phase == PlaybackPhase.BUFFERING) phase = PlaybackPhase.READY
                publish()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (source === activePlayer) {
                    fail(error.localizedMessage ?: "The audio stream could not be played.")
                } else {
                    source.pause()
                    source.clearMediaItems()
                }
            }
        })
    }

    override fun updateQueue(tracks: List<PlaybackRequestTrack>) {
        if (queue == tracks) return
        val current = currentTrack
        queue = tracks
        currentIndex = current?.let { track -> queue.indexOfFirst { it.sameTrack(track) } } ?: -1
        queueNavigator.replaceQueue(queue.size, currentIndex, shuffleEnabled)
        publishQueueSnapshot()
        publish()
    }

    override fun appendQueue(tracks: List<PlaybackRequestTrack>) {
        val existing = queue.mapTo(mutableSetOf()) { it.providerId to it.id }
        val additions = tracks.filter { (it.providerId to it.id) !in existing }
        if (additions.isEmpty()) return
        val previousCount = queue.size
        queue = queue + additions
        queueNavigator.append(previousCount, additions.size, shuffleEnabled)
        publishQueueSnapshot()
        publish()
    }

    override fun playTrack(track: PlaybackRequestTrack) {
        playTrackAt(track, queue.indexOfFirst { it.sameTrack(track) })
    }

    override fun playQueueItem(index: Int) {
        queue.getOrNull(index)?.let { track -> playTrackAt(track, index) }
    }

    override fun playQueue(tracks: List<PlaybackRequestTrack>, startIndex: Int) {
        updateQueue(tracks)
        queueNavigator.replaceQueue(queue.size, startIndex, shuffleEnabled)
        playQueueItem(startIndex)
    }

    private fun playTrackAt(track: PlaybackRequestTrack, requestedIndex: Int) {
        if (track.providerId != YOUTUBE_MUSIC_PROVIDER) {
            fail("This music provider isn't supported by the Android host yet.")
            return
        }
        resolveJob?.cancel()
        generation += 1
        val requestGeneration = generation
        val previousIndex = currentIndex
        currentTrack = track
        currentIndex = requestedIndex.takeIf { queue.getOrNull(it)?.sameTrack(track) == true }
            ?: queue.indexOfFirst { it.sameTrack(track) }
        queueNavigator.trackSelected(currentIndex, previousIndex, queue.size, shuffleEnabled)
        phase = PlaybackPhase.BUFFERING
        errorMessage = null
        stopCrossfade(clearPrepared = true)
        activePlayer.pause()
        activePlayer.clearMediaItems()
        standbyPlayer.pause()
        standbyPlayer.clearMediaItems()
        publishQueueSnapshot()
        publish()

        resolveJob = scope.launch {
            try {
                val stream = withContext(Dispatchers.IO) {
                    streamResolver.resolve(track).getOrElse { throw it }
                }
                if (generation != requestGeneration) return@launch
                activePlayer.setMediaItem(MediaItem.fromUri(stream.url))
                activePlayer.prepare()
                activePlayer.play()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (generation == requestGeneration) fail(error.message ?: "Couldn't start playback.")
            }
        }
    }

    override fun togglePlayPause() {
        if (activePlayer.isPlaying) pause() else play()
    }

    override fun play() {
        val track = currentTrack ?: return
        if (phase == PlaybackPhase.IDLE || phase == PlaybackPhase.ENDED || errorMessage != null) {
            playTrack(track)
        } else {
            errorMessage = null
            activePlayer.play()
            if (crossfadeJob != null) standbyPlayer.play()
            publish()
        }
    }

    override fun pause() {
        activePlayer.pause()
        if (crossfadeJob != null) standbyPlayer.pause()
        publish()
    }

    override fun seekTo(positionMs: Long) {
        val duration = durationMs() ?: return
        stopCrossfade(clearPrepared = true)
        activePlayer.seekTo(positionMs.coerceIn(0L, duration))
        publish()
    }

    override fun requestSkip(forward: Boolean) {
        val nextIndex = queueNavigator.nextIndex(
            currentIndex = currentIndex,
            itemCount = queue.size,
            forward = forward,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
        ) ?: return
        playQueueItem(nextIndex)
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val order = moveQueueIndices(queue.indices.toList(), fromIndex, toIndex)
        if (order == queue.indices.toList()) return
        val oldCurrentIndex = currentIndex
        queue = order.map(queue::get)
        currentIndex = order.indexOf(oldCurrentIndex).takeIf { oldCurrentIndex >= 0 } ?: -1
        queueNavigator.move(fromIndex, toIndex, queue.size, shuffleEnabled)
        publishQueueSnapshot()
        publish()
    }

    override fun removeQueueItem(index: Int) {
        if (index !in queue.indices) return
        val removedCurrent = index == currentIndex
        queue = queue.toMutableList().apply { removeAt(index) }
        queueNavigator.remove(index, queue.size, shuffleEnabled)
        if (removedCurrent) {
            if (queue.isEmpty()) {
                resolveJob?.cancel()
                generation += 1L
                stopCrossfade(clearPrepared = true)
                activePlayer.pause()
                activePlayer.clearMediaItems()
                standbyPlayer.pause()
                standbyPlayer.clearMediaItems()
                currentTrack = null
                currentIndex = -1
                phase = PlaybackPhase.IDLE
                errorMessage = null
                publishQueueSnapshot()
                publish()
            } else {
                playQueueItem(index.coerceAtMost(queue.lastIndex))
            }
            return
        }
        if (index < currentIndex) currentIndex -= 1
        publishQueueSnapshot()
        publish()
    }

    override fun removeQueueItemsAfterCurrent() {
        val retainedCount = (currentIndex + 1).coerceAtLeast(0)
        if (retainedCount >= queue.size) return
        queue = queue.take(retainedCount)
        queueNavigator.truncate(retainedCount, shuffleEnabled)
        publishQueueSnapshot()
        publish()
    }

    override fun setRepeatMode(mode: PlaybackRepeatMode) {
        repeatMode = mode
        publish()
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        if (shuffleEnabled == enabled) return
        shuffleEnabled = enabled
        queueNavigator.setShuffleEnabled(enabled, queue.size, currentIndex)
        publish()
    }

    private fun updateCrossfade() {
        if (crossfadeJob != null) return
        val target = createCrossfadeTarget()
        if (target == null) {
            clearCrossfadePreload()
            return
        }

        if (crossfadePreloadKey != target.key) {
            crossfadePreloadJob?.cancel()
            crossfadePreloadKey = target.key
            val targetPlayer = standbyPlayer
            targetPlayer.pause()
            targetPlayer.clearMediaItems()
            targetPlayer.volume = 0f
            crossfadePreloadJob = scope.launch {
                try {
                    val stream = withContext(Dispatchers.IO) {
                        streamResolver.resolve(target.incoming).getOrElse { throw it }
                    }
                    if (!isCrossfadeTargetCurrent(target) || standbyPlayer !== targetPlayer) return@launch
                    targetPlayer.setMediaItem(MediaItem.fromUri(stream.url))
                    targetPlayer.prepare()
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    if (standbyPlayer === targetPlayer) targetPlayer.clearMediaItems()
                }
            }
        }

        if (activePlayer.isPlaying && activePlayer.currentPosition >= target.startAtMs &&
            standbyPlayer.playbackState == Player.STATE_READY
        ) {
            startCrossfade(target)
        }
    }

    private fun createCrossfadeTarget(): CrossfadeTarget? {
        if (repeatMode == PlaybackRepeatMode.ONE || sleepTimer.state.value.pauseWhenTrackEnds) return null
        val outgoing = currentTrack ?: return null
        val outgoingDurationMs = durationMs() ?: return null
        val incomingIndex = queueNavigator.nextIndex(
            currentIndex = currentIndex,
            itemCount = queue.size,
            forward = true,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
        ) ?: return null
        if (incomingIndex == currentIndex) return null
        val incoming = queue.getOrNull(incomingIndex) ?: return null
        val positionMs = activePlayer.currentPosition.coerceAtLeast(0L)
        val window = planPlaybackTransitionWindow(
            settings = playbackSettings,
            outgoing = outgoing,
            incoming = incoming,
            currentPositionMs = positionMs,
            outgoingDurationMs = outgoingDurationMs,
        ) ?: return null
        val prepareAtMs = (window.startAtMs - CROSSFADE_PRELOAD_LEAD_MS).coerceAtLeast(0L)
        if (positionMs < prepareAtMs) return null
        return CrossfadeTarget(
            generation = generation,
            outgoing = outgoing,
            incoming = incoming,
            incomingIndex = incomingIndex,
            durationMs = window.durationMs,
            startAtMs = window.startAtMs,
            key = "$generation:$incomingIndex:${incoming.providerId}:${incoming.id}",
        )
    }

    private fun startCrossfade(target: CrossfadeTarget) {
        if (crossfadeJob != null || !activePlayer.isPlaying || !isCrossfadeTargetCurrent(target)) return
        val outgoing = activePlayer
        val incoming = standbyPlayer
        if (incoming.playbackState != Player.STATE_READY) return
        activePlayer = incoming
        standbyPlayer = outgoing
        if (!onCrossfadeHandoff(target)) {
            activePlayer = outgoing
            standbyPlayer = incoming
            return
        }
        incoming.volume = 0f
        incoming.play()
        crossfadeJob = scope.launch {
            var elapsedMs = 0L
            while (elapsedMs < target.durationMs && activePlayer === incoming && isActive) {
                val progress = CrossfadeEnvelope.progress(elapsedMs, target.durationMs)
                incoming.volume = CrossfadeEnvelope.incomingGain(progress)
                outgoing.volume = CrossfadeEnvelope.outgoingGain(progress)
                if (incoming.isPlaying) elapsedMs = (elapsedMs + CROSSFADE_FRAME_MS).coerceAtMost(target.durationMs)
                delay(CROSSFADE_FRAME_MS)
            }
            if (activePlayer === incoming) {
                incoming.volume = 1f
                outgoing.volume = 1f
                outgoing.pause()
                outgoing.clearMediaItems()
                crossfadeJob = null
                crossfadePreloadKey = null
            }
        }
    }

    private fun onCrossfadeHandoff(target: CrossfadeTarget): Boolean {
        if (!isCrossfadeTargetCurrent(target)) return false
        val previousIndex = currentIndex
        currentTrack = target.incoming
        currentIndex = target.incomingIndex
        queueNavigator.trackSelected(currentIndex, previousIndex, queue.size, shuffleEnabled)
        crossfadePreloadJob?.cancel()
        crossfadePreloadJob = null
        crossfadePreloadKey = null
        generation += 1L
        phase = PlaybackPhase.READY
        errorMessage = null
        publishQueueSnapshot()
        publish()
        return true
    }

    private fun isCrossfadeTargetCurrent(target: CrossfadeTarget): Boolean =
        generation == target.generation &&
            currentTrack?.sameTrack(target.outgoing) == true &&
            queue.getOrNull(target.incomingIndex)?.sameTrack(target.incoming) == true

    private fun clearCrossfadePreload() {
        if (crossfadePreloadKey == null) return
        crossfadePreloadJob?.cancel()
        crossfadePreloadJob = null
        crossfadePreloadKey = null
        standbyPlayer.pause()
        standbyPlayer.clearMediaItems()
    }

    private fun stopCrossfade(clearPrepared: Boolean) {
        crossfadeJob?.cancel()
        crossfadeJob = null
        crossfadePreloadJob?.cancel()
        crossfadePreloadJob = null
        crossfadePreloadKey = null
        activePlayer.volume = 1f
        standbyPlayer.volume = 1f
        if (clearPrepared) {
            standbyPlayer.pause()
            standbyPlayer.clearMediaItems()
        }
    }

    override fun release() {
        resolveJob?.cancel()
        progressJob?.cancel()
        crossfadePreloadJob?.cancel()
        crossfadeJob?.cancel()
        sleepTimer.release()
        scope.cancel()
        primaryPlayer.release()
        secondaryPlayer.release()
    }

    private fun fail(message: String) {
        stopCrossfade(clearPrepared = true)
        activePlayer.pause()
        phase = PlaybackPhase.IDLE
        errorMessage = message
        publish()
    }

    private fun durationMs(): Long? = activePlayer.duration.takeIf { it > 0L && it != C.TIME_UNSET }
        ?: currentTrack?.durationMs?.takeIf { it > 0L }

    private data class CrossfadeTarget(
        val generation: Long,
        val outgoing: PlaybackRequestTrack,
        val incoming: PlaybackRequestTrack,
        val incomingIndex: Int,
        val durationMs: Long,
        val startAtMs: Long,
        val key: String,
    )

    private fun publish() {
        val track = currentTrack ?: run {
            mutableSnapshot.value = PlaybackSnapshot.EMPTY
            return
        }
        val position = activePlayer.currentPosition.coerceAtLeast(0L)
        mutableSnapshot.value = PlaybackSnapshot(
            track = PlaybackTrackSnapshot(
                id = track.id,
                title = track.title,
                artists = track.artists,
                providerId = track.providerId,
                artworkUrl = track.artworkUrl,
            ),
            phase = phase,
            playWhenReady = activePlayer.playWhenReady,
            isPlaying = activePlayer.isPlaying,
            positionMs = position,
            durationMs = durationMs(),
            mediaItemIndex = currentIndex,
            mediaItemCount = queue.size,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
            canSkipPrevious = currentIndex > 0 || repeatMode == PlaybackRepeatMode.ALL,
            canSkipNext = currentIndex in queue.indices &&
                (currentIndex < queue.lastIndex || repeatMode == PlaybackRepeatMode.ALL),
            errorMessage = errorMessage,
        )
    }

    private fun publishQueueSnapshot() {
        mutableQueueSnapshot.value = PlaybackQueueSnapshot(
            entries = queue.map { track ->
                PlaybackQueueEntry(
                    id = track.id,
                    title = track.title,
                    artists = track.artists,
                    artworkUrl = track.artworkUrl,
                    providerId = track.providerId,
                    durationMs = track.durationMs,
                    origin = track.queueOrigin,
                )
            },
            currentIndex = currentIndex,
        )
    }

    private fun PlaybackRequestTrack.sameTrack(other: PlaybackRequestTrack): Boolean =
        providerId == other.providerId && id == other.id

    private companion object {
        const val YOUTUBE_MUSIC_PROVIDER = "youtube_music"
        const val PROGRESS_INTERVAL_MS = 500L
        const val CROSSFADE_FRAME_MS = 50L
        const val CROSSFADE_PRELOAD_LEAD_MS = 8_000L
    }
}
