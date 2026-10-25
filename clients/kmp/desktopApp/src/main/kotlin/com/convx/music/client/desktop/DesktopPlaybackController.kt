package com.convx.music.client.desktop

import androidx.compose.ui.graphics.Color
import com.convx.music.catalog.CatalogEntry
import com.convx.music.playback.AudioStreamResolver
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
import com.convx.music.playback.moveQueueIndices
import com.convx.music.playback.planPlaybackTransitionWindow
import com.convx.music.playback.shouldPauseOnToggle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.nio.file.Path

internal data class DesktopTrack(
    val catalogTrack: CatalogEntry.Track,
    val request: PlaybackRequestTrack,
    val album: String,
    val durationMs: Long,
    val isInLibrary: Boolean,
    val artStart: Color,
    val artEnd: Color,
    val artworkUrl: String?,
) {
    val snapshot = PlaybackTrackSnapshot(
        id = request.id,
        title = request.title,
        artists = request.artists,
        providerId = request.providerId,
        artworkUrl = request.artworkUrl,
    )
}

internal fun CatalogEntry.Track.toDesktopTrack(index: Int): DesktopTrack {
    val palette = listOf(
        Color(0xFF745B9C) to Color(0xFFE49E9B),
        Color(0xFF245E77) to Color(0xFF8BC0B7),
        Color(0xFF885E66) to Color(0xFFF0B889),
        Color(0xFF466C57) to Color(0xFFCFD98B),
        Color(0xFF394574) to Color(0xFFAC8DD0),
    )
    val colors = palette[index.mod(palette.size)]
    val duration = durationMillis
    return DesktopTrack(
        catalogTrack = this,
        request = PlaybackRequestTrack(
            providerId = id.providerId,
            id = id.value,
            title = title,
            artists = artists.map { it.name },
            durationMs = duration,
            artworkUrl = artworkUrls.firstOrNull(),
        ),
        album = album?.title.orEmpty(),
        durationMs = duration ?: 0L,
        isInLibrary = isInLibrary ?: false,
        artStart = colors.first,
        artEnd = colors.second,
        artworkUrl = artworkUrls.firstOrNull(),
    )
}

internal interface DesktopMusicController : PlaybackController {
    fun selectTrack(trackId: String)
    fun setCatalogTracks(tracks: List<DesktopTrack>)
    fun close()
}

internal class DesktopPlaybackController(
    private val streamResolver: AudioStreamResolver,
    private val playbackSettingsRepository: PlaybackSettingsRepository,
    private val audioProxy: DesktopAudioHttpProxy = DesktopAudioHttpProxy(),
    private val audioTranscoder: DesktopAudioTranscoder = DesktopAudioTranscoder(),
) : DesktopMusicController {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _playbackSnapshot = MutableStateFlow(PlaybackSnapshot.EMPTY)
    override val playbackSnapshot: StateFlow<PlaybackSnapshot> = _playbackSnapshot.asStateFlow()
    override val sleepTimer = PlaybackSleepTimer(scope, System::currentTimeMillis, ::pause)
    private val mutableQueueSnapshot = MutableStateFlow(PlaybackQueueSnapshot.EMPTY)
    override val queueSnapshot: StateFlow<PlaybackQueueSnapshot> = mutableQueueSnapshot.asStateFlow()
    private val audio = DesktopStreamPlayer(
        onReady = ::onAudioReady,
        onProgress = ::onAudioProgress,
        onStalled = ::onAudioStalled,
        onPlaying = ::onAudioPlaying,
        onEnded = ::onAudioEnded,
        onError = ::onAudioError,
    )

    private var catalogTracks: List<DesktopTrack> = emptyList()
    private var queue: List<PlaybackRequestTrack> = emptyList()
    private val queueNavigator = PlaybackQueueNavigator()
    private var currentIndex = -1
    private var currentRequest: PlaybackRequestTrack? = null
    private var resolutionJob: Job? = null
    private var startupTimeoutJob: Job? = null
    private var stallTimeoutJob: Job? = null
    private var crossfadePreloadJob: Job? = null
    private var crossfadePreloadKey: String? = null
    private var generation = 0L
    private var playbackSettings = PlaybackSettings()
    private var repeatMode = PlaybackRepeatMode.OFF
    private var shuffleEnabled = false
    private var playWhenReady = false
    private var isPlaying = false
    private var phase = PlaybackPhase.IDLE
    private var positionMs = 0L
    private var lastPublishedProgressMs = 0L
    private var durationMs: Long? = null
    private var errorMessage: String? = null

    init {
        scope.launch {
            playbackSettingsRepository.observeSettings().collect { settings ->
                synchronized(lock) {
                    playbackSettings = settings.normalized()
                }
            }
        }
    }

    override fun setCatalogTracks(tracks: List<DesktopTrack>) {
        synchronized(lock) {
            catalogTracks = tracks.toList()
        }
    }

    override fun updateQueue(tracks: List<PlaybackRequestTrack>) {
        synchronized(lock) {
            if (queue == tracks) return
            val currentTrack = currentRequest
            queue = tracks.toList()
            currentIndex = currentTrack?.let { current -> queue.indexOfFirst { it.sameTrack(current) } } ?: -1
            queueNavigator.replaceQueue(queue.size, currentIndex, shuffleEnabled)
            publishQueueLocked()
            publishLocked()
        }
    }

    override fun appendQueue(tracks: List<PlaybackRequestTrack>) {
        synchronized(lock) {
            val existing = queue.mapTo(mutableSetOf()) { it.providerId to it.id }
            val additions = tracks.filter { (it.providerId to it.id) !in existing }
            if (additions.isEmpty()) return
            val previousCount = queue.size
            queue = queue + additions
            queueNavigator.append(previousCount, additions.size, shuffleEnabled)
            publishQueueLocked()
            publishLocked()
        }
    }

    override fun togglePlayPause() {
        if (playbackSnapshot.value.shouldPauseOnToggle()) pause() else play()
    }

    override fun play() {
        val track = synchronized(lock) { currentRequest } ?: return
        val snapshot = playbackSnapshot.value
        if (snapshot.phase == PlaybackPhase.IDLE || snapshot.phase == PlaybackPhase.ENDED || snapshot.errorMessage != null) {
            playTrack(track)
        } else {
            synchronized(lock) {
                playWhenReady = true
                isPlaying = phase != PlaybackPhase.BUFFERING
                errorMessage = null
                publishLocked()
            }
            audio.play()
        }
    }

    override fun pause() {
        synchronized(lock) {
            playWhenReady = false
            isPlaying = false
            publishLocked()
        }
        audio.pause()
    }

    override fun seekTo(positionMs: Long) {
        val duration = synchronized(lock) { durationMs } ?: return
        val position = positionMs.coerceIn(0L, duration)
        audio.seekTo(position)
        synchronized(lock) {
            this.positionMs = position
            lastPublishedProgressMs = position
            publishLocked()
        }
    }

    override fun requestSkip(forward: Boolean) {
        val targetIndex = synchronized(lock) {
            queueNavigator.nextIndex(
                currentIndex = currentIndex,
                itemCount = queue.size,
                forward = forward,
                repeatMode = repeatMode,
                shuffleEnabled = shuffleEnabled,
            ) ?: return
        }
        playQueueItem(targetIndex)
    }

    override fun setRepeatMode(mode: PlaybackRepeatMode) {
        synchronized(lock) {
            repeatMode = mode
            publishLocked()
        }
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        synchronized(lock) {
            if (shuffleEnabled == enabled) return
            shuffleEnabled = enabled
            queueNavigator.setShuffleEnabled(enabled, queue.size, currentIndex)
            publishLocked()
        }
    }

    override fun selectTrack(trackId: String) {
        val context = synchronized(lock) {
            val startIndex = catalogTracks.indexOfFirst { it.request.id == trackId }
            if (startIndex < 0) return
            catalogTracks.map { it.request } to startIndex
        }
        playQueue(context.first, context.second)
    }

    override fun playTrack(track: PlaybackRequestTrack) {
        val targetIndex = synchronized(lock) { queue.indexOfFirst { it.sameTrack(track) } }
        startTrack(track, targetIndex)
    }

    override fun playQueueItem(index: Int) {
        val track = synchronized(lock) { queue.getOrNull(index) } ?: return
        startTrack(track, index)
    }

    override fun playQueue(tracks: List<PlaybackRequestTrack>, startIndex: Int) {
        updateQueue(tracks)
        synchronized(lock) {
            queueNavigator.replaceQueue(queue.size, startIndex, shuffleEnabled)
        }
        playQueueItem(startIndex)
    }

    private fun startTrack(track: PlaybackRequestTrack, requestedIndex: Int) {
        audio.pause()
        audio.seekTo(0L)
        val requestGeneration = synchronized(lock) {
            val previousIndex = currentIndex
            resolutionJob?.cancel()
            startupTimeoutJob?.cancel()
            stallTimeoutJob?.cancel()
            cancelCrossfadePreloadLocked()
            generation += 1L
            currentRequest = track
            currentIndex = requestedIndex.takeIf { queue.getOrNull(it)?.sameTrack(track) == true }
                ?: queue.indexOfFirst { it.sameTrack(track) }
            queueNavigator.trackSelected(currentIndex, previousIndex, queue.size, shuffleEnabled)
            positionMs = 0L
            lastPublishedProgressMs = 0L
            durationMs = track.durationMs
            playWhenReady = true
            isPlaying = false
            phase = PlaybackPhase.BUFFERING
            errorMessage = null
            publishQueueLocked()
            publishLocked()
            generation
        }
        resolutionJob = scope.launch {
            var decodedAudio: Path? = null
            try {
                scheduleStartupTimeout(track.id, requestGeneration, AUDIO_DECODE_STARTUP_TIMEOUT_MS)
                val stream = streamResolver.resolve(track)
                    .getOrElse { throw it }
                if (!isCurrent(requestGeneration)) return@launch
                val sourceUrl = audioProxy.createMediaSource(stream.url, stream.mimeType)
                val audioFile = audioTranscoder.transcode(sourceUrl)
                decodedAudio = audioFile
                if (!isCurrent(requestGeneration)) {
                    audioTranscoder.delete(audioFile)
                    decodedAudio = null
                    return@launch
                }
                audio.play(
                    track.id,
                    audioFile.toUri().toString(),
                    ownedAudioFile = audioFile,
                    shouldStartPlayback = {
                        synchronized(lock) {
                            generation == requestGeneration && currentRequest?.id == track.id && playWhenReady
                        }
                    },
                )
                decodedAudio = null
            } catch (cancellation: CancellationException) {
                decodedAudio?.let(audioTranscoder::delete)
                throw cancellation
            } catch (error: LinkageError) {
                decodedAudio?.let(audioTranscoder::delete)
                synchronized(lock) {
                    if (generation != requestGeneration) return@synchronized
                    phase = PlaybackPhase.IDLE
                    playWhenReady = false
                    isPlaying = false
                    errorMessage = "The desktop audio decoder isn't available for this platform."
                    publishLocked()
                }
            } catch (error: Exception) {
                decodedAudio?.let(audioTranscoder::delete)
                synchronized(lock) {
                    if (generation != requestGeneration) return@synchronized
                    phase = PlaybackPhase.IDLE
                    playWhenReady = false
                    isPlaying = false
                    errorMessage = error.message ?: "Couldn't start playback."
                    publishLocked()
                }
            }
        }
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        synchronized(lock) {
            val order = moveQueueIndices(queue.indices.toList(), fromIndex, toIndex)
            if (order == queue.indices.toList()) return
            val oldCurrentIndex = currentIndex
            queue = order.map(queue::get)
            currentIndex = order.indexOf(oldCurrentIndex).takeIf { oldCurrentIndex >= 0 } ?: -1
            queueNavigator.move(fromIndex, toIndex, queue.size, shuffleEnabled)
            publishQueueLocked()
            publishLocked()
        }
    }

    override fun removeQueueItem(index: Int) {
        var nextQueueIndex: Int? = null
        var clearCurrent = false
        synchronized(lock) {
            if (index !in queue.indices) return
            val removedCurrent = index == currentIndex
            queue = queue.toMutableList().apply { removeAt(index) }
            queueNavigator.remove(index, queue.size, shuffleEnabled)
            when {
                removedCurrent && queue.isEmpty() -> {
                    resolutionJob?.cancel()
                    generation += 1L
                    currentRequest = null
                    currentIndex = -1
                    positionMs = 0L
                    durationMs = null
                    playWhenReady = false
                    phase = PlaybackPhase.IDLE
                    isPlaying = false
                    errorMessage = null
                    clearCurrent = true
                    publishQueueLocked()
                    publishLocked()
                }
                removedCurrent -> nextQueueIndex = index.coerceAtMost(queue.lastIndex)
                else -> {
                    if (index < currentIndex) currentIndex -= 1
                    publishQueueLocked()
                    publishLocked()
                }
            }
        }
        if (clearCurrent) {
            audio.pause()
            audio.seekTo(0L)
        } else {
            nextQueueIndex?.let(::playQueueItem)
        }
    }

    override fun removeQueueItemsAfterCurrent() {
        synchronized(lock) {
            val retainedCount = (currentIndex + 1).coerceAtLeast(0)
            if (retainedCount >= queue.size) return
            queue = queue.take(retainedCount)
            queueNavigator.truncate(retainedCount, shuffleEnabled)
            publishQueueLocked()
            publishLocked()
        }
    }

    override fun close() {
        synchronized(lock) { playWhenReady = false }
        resolutionJob?.cancel()
        startupTimeoutJob?.cancel()
        stallTimeoutJob?.cancel()
        crossfadePreloadJob?.cancel()
        sleepTimer.release()
        scope.cancel()
        audio.close(onClosed = audioTranscoder::close)
        audioProxy.close()
    }

    override fun release() = close()

    private fun onAudioReady(trackId: String, durationMs: Long?) {
        synchronized(lock) {
            if (currentRequest?.id != trackId || phase != PlaybackPhase.BUFFERING) return
            startupTimeoutJob?.cancel()
            startupTimeoutJob = null
            stallTimeoutJob?.cancel()
            stallTimeoutJob = null
            phase = PlaybackPhase.READY
            isPlaying = playWhenReady
            this.durationMs = durationMs?.takeIf { it > 0L } ?: this.durationMs
            errorMessage = null
            publishLocked()
        }
    }

    private fun onAudioProgress(trackId: String, positionMs: Long, durationMs: Long?) {
        val target = synchronized(lock) {
            if (currentRequest?.id != trackId) return
            val nextPositionMs = positionMs.coerceAtLeast(0L)
            this.positionMs = nextPositionMs
            val previousDurationMs = this.durationMs
            this.durationMs = durationMs?.takeIf { it > 0L } ?: this.durationMs
            if (
                nextPositionMs < lastPublishedProgressMs ||
                nextPositionMs - lastPublishedProgressMs >= UI_PROGRESS_INTERVAL_MS ||
                previousDurationMs != this.durationMs
            ) {
                lastPublishedProgressMs = nextPositionMs
                publishLocked()
            }
            crossfadeTargetLocked(this.positionMs)
        }
        if (target == null) {
            clearCrossfadePreload()
        } else {
            prepareOrStartCrossfade(target, positionMs.coerceAtLeast(0L))
        }
    }

    private fun onAudioStalled(trackId: String) {
        val requestGeneration = synchronized(lock) {
            if (currentRequest?.id != trackId || phase != PlaybackPhase.READY || !playWhenReady) return
            phase = PlaybackPhase.BUFFERING
            isPlaying = false
            publishLocked()
            generation
        }
        stallTimeoutJob?.cancel()
        stallTimeoutJob = scope.launch {
            delay(STREAM_STALL_TIMEOUT_MS)
            val stillStalled = synchronized(lock) {
                generation == requestGeneration &&
                    currentRequest?.id == trackId &&
                    phase == PlaybackPhase.BUFFERING &&
                    playWhenReady
            }
            if (stillStalled) onAudioError(trackId, "The audio stream stalled.")
        }
    }

    private fun onAudioPlaying(trackId: String) {
        synchronized(lock) {
            if (currentRequest?.id != trackId) return
            stallTimeoutJob?.cancel()
            stallTimeoutJob = null
            if (phase == PlaybackPhase.BUFFERING) {
                phase = PlaybackPhase.READY
                isPlaying = playWhenReady
                startupTimeoutJob?.cancel()
                startupTimeoutJob = null
                publishLocked()
            } else if (isPlaying != playWhenReady) {
                isPlaying = playWhenReady
                publishLocked()
            }
        }
    }

    private fun crossfadeTargetLocked(positionMs: Long): CrossfadeTarget? {
        if (repeatMode == PlaybackRepeatMode.ONE || sleepTimer.state.value.pauseWhenTrackEnds) return null
        val outgoing = currentRequest ?: return null
        val outgoingDurationMs = durationMs?.takeIf { it > 0L } ?: return null
        val incomingIndex = queueNavigator.nextIndex(
            currentIndex = currentIndex,
            itemCount = queue.size,
            forward = true,
            repeatMode = repeatMode,
            shuffleEnabled = shuffleEnabled,
        ) ?: return null
        if (incomingIndex == currentIndex) return null
        val incoming = queue.getOrNull(incomingIndex) ?: return null
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

    private fun prepareOrStartCrossfade(target: CrossfadeTarget, positionMs: Long) {
        val shouldPrepare = synchronized(lock) {
            if (!isCrossfadeTargetCurrentLocked(target) || crossfadePreloadKey == target.key) {
                false
            } else {
                cancelCrossfadePreloadLocked()
                crossfadePreloadKey = target.key
                true
            }
        }
        if (shouldPrepare) {
            crossfadePreloadJob = scope.launch {
                try {
                    val stream = streamResolver.resolve(target.incoming)
                        .getOrElse { throw it }
                    val sourceUrl = audioProxy.createMediaSource(stream.url, stream.mimeType)
                    val shouldInstall = synchronized(lock) { isCrossfadeTargetCurrentLocked(target) }
                    if (shouldInstall) {
                        audio.prepare(target.incoming.id, sourceUrl)
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: LinkageError) {
                    // The next track's normal start path will use the compatibility decoder.
                } catch (_: Exception) {
                    // Keep the current deck playing; normal end-of-track handling will retry the next item.
                }
            }
        }

        val canStart = synchronized(lock) {
            isPlaying && isCrossfadeTargetCurrentLocked(target)
        }
        if (positionMs >= target.startAtMs && canStart) {
            audio.crossfadeToPrepared(
                outgoingTrackId = target.outgoing.id,
                incomingTrackId = target.incoming.id,
                durationMs = target.durationMs,
                onHandoff = { onAudioHandoff(target) },
            )
        }
    }

    private fun onAudioHandoff(target: CrossfadeTarget): Boolean = synchronized(lock) {
            if (!isPlaying || !isCrossfadeTargetCurrentLocked(target)) return@synchronized false
            val previousIndex = currentIndex
            currentRequest = target.incoming
            currentIndex = target.incomingIndex
            queueNavigator.trackSelected(currentIndex, previousIndex, queue.size, shuffleEnabled)
            crossfadePreloadJob = null
            crossfadePreloadKey = null
            generation += 1L
            positionMs = 0L
            lastPublishedProgressMs = 0L
            durationMs = target.incoming.durationMs
            isPlaying = true
            phase = PlaybackPhase.READY
            errorMessage = null
            publishQueueLocked()
            publishLocked()
            true
    }

    private fun isCrossfadeTargetCurrentLocked(target: CrossfadeTarget): Boolean =
        generation == target.generation &&
            currentRequest?.sameTrack(target.outgoing) == true &&
            queue.getOrNull(target.incomingIndex)?.sameTrack(target.incoming) == true

    private fun cancelCrossfadePreloadLocked() {
        crossfadePreloadJob?.cancel()
        crossfadePreloadJob = null
        crossfadePreloadKey = null
    }

    private fun clearCrossfadePreload() {
        val shouldClear = synchronized(lock) {
            if (crossfadePreloadKey == null) {
                false
            } else {
                cancelCrossfadePreloadLocked()
                true
            }
        }
        if (shouldClear) audio.clearPrepared()
    }

    private fun onAudioEnded(trackId: String) {
        synchronized(lock) {
            if (currentRequest?.id != trackId) return
            playWhenReady = false
            if (sleepTimer.onTrackEnded()) {
                phase = PlaybackPhase.ENDED
                isPlaying = false
                publishLocked()
                return
            }
            if (repeatMode == PlaybackRepeatMode.ONE) {
                positionMs = 0L
                playWhenReady = true
                isPlaying = true
                phase = PlaybackPhase.READY
                publishLocked()
                audio.replay()
                return
            }
            phase = PlaybackPhase.ENDED
            isPlaying = false
            publishLocked()
        }
        requestSkip(forward = true)
    }

    private fun onAudioError(trackId: String, message: String) {
        synchronized(lock) {
            if (currentRequest?.id != trackId) return
            startupTimeoutJob?.cancel()
            startupTimeoutJob = null
            stallTimeoutJob?.cancel()
            stallTimeoutJob = null
            phase = PlaybackPhase.IDLE
            playWhenReady = false
            isPlaying = false
            errorMessage = message
            publishLocked()
        }
    }

    private fun isCurrent(requestGeneration: Long): Boolean = synchronized(lock) {
        generation == requestGeneration
    }

    private fun scheduleStartupTimeout(
        trackId: String,
        requestGeneration: Long,
        timeoutMs: Long,
    ) {
        startupTimeoutJob?.cancel()
        startupTimeoutJob = scope.launch {
            delay(timeoutMs)
            val didTimeOut = synchronized(lock) {
                if (generation != requestGeneration || currentRequest?.id != trackId || phase != PlaybackPhase.BUFFERING) {
                    false
                } else {
                    generation += 1L
                    resolutionJob?.cancel()
                    phase = PlaybackPhase.IDLE
                    playWhenReady = false
                    isPlaying = false
                    errorMessage = "Playback timed out while preparing the audio stream."
                    startupTimeoutJob = null
                    publishLocked()
                    true
                }
            }
            if (didTimeOut) {
                audio.pause()
            }
        }
    }

    private data class CrossfadeTarget(
        val generation: Long,
        val outgoing: PlaybackRequestTrack,
        val incoming: PlaybackRequestTrack,
        val incomingIndex: Int,
        val durationMs: Long,
        val startAtMs: Long,
        val key: String,
    )

    private fun publishLocked() {
        val track = currentRequest
        _playbackSnapshot.value = if (track == null) {
            PlaybackSnapshot.EMPTY
        } else {
            PlaybackSnapshot(
                track = PlaybackTrackSnapshot(
                    id = track.id,
                    title = track.title,
                    artists = track.artists,
                    providerId = track.providerId,
                    artworkUrl = track.artworkUrl,
                ),
                phase = phase,
                playWhenReady = playWhenReady,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
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
    }

    private fun publishQueueLocked() {
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
        const val CROSSFADE_PRELOAD_LEAD_MS = 8_000L
        const val AUDIO_DECODE_STARTUP_TIMEOUT_MS = 180_000L
        const val STREAM_STALL_TIMEOUT_MS = 15_000L
        const val UI_PROGRESS_INTERVAL_MS = 250L
    }
}
