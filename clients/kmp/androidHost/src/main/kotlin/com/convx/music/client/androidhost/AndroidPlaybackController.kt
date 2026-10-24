package com.convx.music.client.androidhost

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.convx.music.playback.AudioStreamResolver
import com.convx.music.playback.PlaybackController
import com.convx.music.playback.PlaybackQueueEntry
import com.convx.music.playback.PlaybackQueueSnapshot
import com.convx.music.playback.PlaybackPhase
import com.convx.music.playback.PlaybackRepeatMode
import com.convx.music.playback.PlaybackRequestTrack
import com.convx.music.playback.PlaybackSnapshot
import com.convx.music.playback.PlaybackTrackSnapshot
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
import kotlin.random.Random

/** Android media adapter for the KMP host; all catalog and stream lookup stays behind domain ports. */
internal class AndroidPlaybackController(
    context: Context,
    private val streamResolver: AudioStreamResolver,
) : PlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val player = ExoPlayer.Builder(context.applicationContext).build().apply {
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true,
        )
        setHandleAudioBecomingNoisy(true)
    }
    private val mutableSnapshot = MutableStateFlow(PlaybackSnapshot.EMPTY)
    override val playbackSnapshot: StateFlow<PlaybackSnapshot> = mutableSnapshot.asStateFlow()
    private val mutableQueueSnapshot = MutableStateFlow(PlaybackQueueSnapshot.EMPTY)
    override val queueSnapshot: StateFlow<PlaybackQueueSnapshot> = mutableQueueSnapshot.asStateFlow()

    private var queue: List<PlaybackRequestTrack> = emptyList()
    private var currentTrack: PlaybackRequestTrack? = null
    private var currentIndex = -1
    private var resolveJob: Job? = null
    private var progressJob: Job? = null
    private var generation = 0L
    private var repeatMode = PlaybackRepeatMode.OFF
    private var shuffleEnabled = false
    private var phase = PlaybackPhase.IDLE
    private var errorMessage: String? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> phase = PlaybackPhase.BUFFERING
                    Player.STATE_READY -> phase = PlaybackPhase.READY
                    Player.STATE_ENDED -> {
                        if (repeatMode == PlaybackRepeatMode.ONE) {
                            player.seekTo(0L)
                            player.play()
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
                if (isPlaying && phase == PlaybackPhase.BUFFERING) phase = PlaybackPhase.READY
                publish()
            }

            override fun onPlayerError(error: PlaybackException) {
                fail(error.localizedMessage ?: "The audio stream could not be played.")
            }
        })
        progressJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_INTERVAL_MS)
                publish()
            }
        }
    }

    override fun updateQueue(tracks: List<PlaybackRequestTrack>) {
        if (queue == tracks) return
        val current = currentTrack
        queue = tracks
        currentIndex = current?.let { track -> queue.indexOfFirst { it.sameTrack(track) } } ?: -1
        publishQueueSnapshot()
        publish()
    }

    override fun playTrack(track: PlaybackRequestTrack) {
        playTrackAt(track, queue.indexOfFirst { it.sameTrack(track) })
    }

    override fun playQueueItem(index: Int) {
        queue.getOrNull(index)?.let { track -> playTrackAt(track, index) }
    }

    private fun playTrackAt(track: PlaybackRequestTrack, requestedIndex: Int) {
        if (track.providerId != YOUTUBE_MUSIC_PROVIDER) {
            fail("This music provider isn't supported by the Android host yet.")
            return
        }
        resolveJob?.cancel()
        generation += 1
        val requestGeneration = generation
        currentTrack = track
        currentIndex = requestedIndex.takeIf { queue.getOrNull(it)?.sameTrack(track) == true }
            ?: queue.indexOfFirst { it.sameTrack(track) }
        phase = PlaybackPhase.BUFFERING
        errorMessage = null
        player.pause()
        player.clearMediaItems()
        publishQueueSnapshot()
        publish()

        resolveJob = scope.launch {
            try {
                val stream = withContext(Dispatchers.IO) {
                    streamResolver.resolve(track.id, track.durationMs).getOrElse { throw it }
                }
                if (generation != requestGeneration) return@launch
                player.setMediaItem(MediaItem.fromUri(stream.url))
                player.prepare()
                player.play()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (generation == requestGeneration) fail(error.message ?: "Couldn't start playback.")
            }
        }
    }

    override fun togglePlayPause() {
        if (player.isPlaying) pause() else play()
    }

    override fun play() {
        val track = currentTrack ?: return
        if (phase == PlaybackPhase.IDLE || phase == PlaybackPhase.ENDED || errorMessage != null) {
            playTrack(track)
        } else {
            errorMessage = null
            player.play()
            publish()
        }
    }

    override fun pause() {
        player.pause()
        publish()
    }

    override fun seekTo(positionMs: Long) {
        val duration = durationMs() ?: return
        player.seekTo(positionMs.coerceIn(0L, duration))
        publish()
    }

    override fun requestSkip(forward: Boolean) {
        if (queue.isEmpty()) return
        val nextIndex = when {
            shuffleEnabled && queue.size > 1 -> queue.indices.filter { it != currentIndex }.random(Random)
            else -> {
                val candidate = currentIndex + if (forward) 1 else -1
                when {
                    candidate in queue.indices -> candidate
                    forward && repeatMode == PlaybackRepeatMode.ALL -> 0
                    !forward && repeatMode == PlaybackRepeatMode.ALL -> queue.lastIndex
                    else -> return
                }
            }
        }
        playQueueItem(nextIndex)
    }

    override fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val order = moveQueueIndices(queue.indices.toList(), fromIndex, toIndex)
        if (order == queue.indices.toList()) return
        val oldCurrentIndex = currentIndex
        queue = order.map(queue::get)
        currentIndex = order.indexOf(oldCurrentIndex).takeIf { oldCurrentIndex >= 0 } ?: -1
        publishQueueSnapshot()
        publish()
    }

    override fun removeQueueItem(index: Int) {
        if (index !in queue.indices) return
        val removedCurrent = index == currentIndex
        queue = queue.toMutableList().apply { removeAt(index) }
        if (removedCurrent) {
            if (queue.isEmpty()) {
                resolveJob?.cancel()
                generation += 1L
                player.pause()
                player.clearMediaItems()
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
        publishQueueSnapshot()
        publish()
    }

    override fun setRepeatMode(mode: PlaybackRepeatMode) {
        repeatMode = mode
        publish()
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        shuffleEnabled = enabled
        publish()
    }

    override fun release() {
        resolveJob?.cancel()
        progressJob?.cancel()
        scope.cancel()
        player.release()
    }

    private fun fail(message: String) {
        player.pause()
        phase = PlaybackPhase.IDLE
        errorMessage = message
        publish()
    }

    private fun durationMs(): Long? = player.duration.takeIf { it > 0L && it != C.TIME_UNSET }
        ?: currentTrack?.durationMs?.takeIf { it > 0L }

    private fun publish() {
        val track = currentTrack ?: run {
            mutableSnapshot.value = PlaybackSnapshot.EMPTY
            return
        }
        val position = player.currentPosition.coerceAtLeast(0L)
        mutableSnapshot.value = PlaybackSnapshot(
            track = PlaybackTrackSnapshot(track.id, track.title, track.artists),
            phase = phase,
            playWhenReady = player.playWhenReady,
            isPlaying = player.isPlaying,
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
                PlaybackQueueEntry(track.id, track.title, track.artists, track.artworkUrl)
            },
            currentIndex = currentIndex,
        )
    }

    private fun PlaybackRequestTrack.sameTrack(other: PlaybackRequestTrack): Boolean =
        providerId == other.providerId && id == other.id

    private companion object {
        const val YOUTUBE_MUSIC_PROVIDER = "youtube_music"
        const val PROGRESS_INTERVAL_MS = 500L
    }
}
