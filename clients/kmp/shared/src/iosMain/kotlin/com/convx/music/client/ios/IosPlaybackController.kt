@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.convx.music.client.ios

import com.convx.music.playback.PlaybackController
import com.convx.music.playback.PlaybackPhase
import com.convx.music.playback.PlaybackRepeatMode
import com.convx.music.playback.PlaybackRequestTrack
import com.convx.music.playback.PlaybackSnapshot
import com.convx.music.playback.PlaybackTrackSnapshot
import com.music.innertube.InnertubeAudioStreamResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.CoreMedia.CMTimeGetSeconds
import platform.CoreMedia.CMTimeMakeWithSeconds
import platform.Foundation.NSURL

internal class IosPlaybackController(
    private val streamResolver: InnertubeAudioStreamResolver,
) : PlaybackController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player = AVPlayer()
    private val _playbackSnapshot = MutableStateFlow(PlaybackSnapshot.EMPTY)
    override val playbackSnapshot: StateFlow<PlaybackSnapshot> = _playbackSnapshot.asStateFlow()

    private var queue: List<PlaybackRequestTrack> = emptyList()
    private var currentIndex = -1
    private var currentRequest: PlaybackRequestTrack? = null
    private var resolveJob: Job? = null
    private var generation = 0L
    private var repeatMode = PlaybackRepeatMode.OFF
    private var shuffleEnabled = false
    private var phase = PlaybackPhase.IDLE
    private var isPlaying = false
    private var positionMs = 0L
    private var durationMs: Long? = null
    private var errorMessage: String? = null
    private var timeObserver: Any? = null

    init {
        timeObserver = player.addPeriodicTimeObserverForInterval(
            interval = CMTimeMakeWithSeconds(0.5, 600),
            queue = null,
        ) { time ->
            val track = currentRequest
            val item = player.currentItem
            if (item?.status == AVPlayerItemStatusFailed) {
                fail(item.error?.localizedDescription ?: "The audio stream could not be played.")
            } else if (track != null) {
                val position = (CMTimeGetSeconds(time) * 1_000.0).toLong().coerceAtLeast(0L)
                val durationSeconds = item?.let { CMTimeGetSeconds(it.duration) }
                val itemDuration = durationSeconds?.takeIf { it.isFinite() && it > 0.0 }
                    ?.times(1_000.0)
                    ?.toLong()
                positionMs = position
                durationMs = itemDuration ?: durationMs
                if (item?.status == AVPlayerItemStatusReadyToPlay && phase == PlaybackPhase.BUFFERING) {
                    phase = PlaybackPhase.READY
                }
                publish()
                if (durationMs != null && positionMs >= durationMs!! && isPlaying) {
                    if (repeatMode == PlaybackRepeatMode.ONE) {
                        seekTo(0L)
                        play()
                    } else {
                        phase = PlaybackPhase.ENDED
                        isPlaying = false
                        publish()
                        requestSkip(forward = true)
                    }
                }
            }
        }
    }

    override fun updateQueue(tracks: List<PlaybackRequestTrack>) {
        val currentId = currentRequest?.id
        if (queue.map { it.id } == tracks.map { it.id }) return
        queue = tracks
        currentIndex = queue.indexOfFirst { it.id == currentId }
        publish()
    }

    override fun playTrack(track: PlaybackRequestTrack) {
        resolveJob?.cancel()
        generation += 1L
        val requestGeneration = generation
        currentRequest = track
        currentIndex = queue.indexOfFirst { it.id == track.id }
        positionMs = 0L
        durationMs = track.durationMs
        phase = PlaybackPhase.BUFFERING
        isPlaying = false
        errorMessage = null
        player.pause()
        publish()

        resolveJob = scope.launch {
            try {
                val stream = streamResolver.resolve(track.id, track.durationMs).getOrElse { throw it }
                if (generation != requestGeneration) return@launch
                val url = NSURL.URLWithString(stream.url)
                if (url == null) {
                    fail("The audio stream returned an invalid URL.")
                    return@launch
                }
                player.replaceCurrentItemWithPlayerItem(AVPlayerItem(uRL = url))
                player.play()
                isPlaying = true
                phase = PlaybackPhase.BUFFERING
                publish()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                if (generation == requestGeneration) {
                    fail(error.message ?: "Couldn't start playback.")
                }
            }
        }
    }

    override fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    override fun play() {
        val current = currentRequest ?: return
        if (phase == PlaybackPhase.IDLE || errorMessage != null) {
            playTrack(current)
        } else {
            player.play()
            isPlaying = true
            errorMessage = null
            publish()
        }
    }

    override fun pause() {
        player.pause()
        isPlaying = false
        publish()
    }

    override fun seekTo(positionMs: Long) {
        val position = positionMs.coerceAtLeast(0L)
        player.seekToTime(CMTimeMakeWithSeconds(position / 1_000.0, 600))
        this.positionMs = durationMs?.let(position::coerceAtMost) ?: position
        publish()
    }

    override fun requestSkip(forward: Boolean) {
        if (queue.isEmpty()) return
        val nextIndex = when {
            shuffleEnabled && queue.size > 1 -> queue.indices.filter { it != currentIndex }.random()
            else -> {
                val candidate = currentIndex + if (forward) 1 else -1
                when {
                    candidate in queue.indices -> candidate
                    repeatMode == PlaybackRepeatMode.ALL && forward -> 0
                    repeatMode == PlaybackRepeatMode.ALL && !forward -> queue.lastIndex
                    else -> return
                }
            }
        }
        queue.getOrNull(nextIndex)?.let(::playTrack)
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
        scope.cancel()
        timeObserver?.let(player::removeTimeObserver)
        timeObserver = null
        player.pause()
        player.replaceCurrentItemWithPlayerItem(null)
    }

    private fun fail(message: String) {
        player.pause()
        isPlaying = false
        phase = PlaybackPhase.IDLE
        errorMessage = message
        publish()
    }

    private fun publish() {
        val track = currentRequest
        _playbackSnapshot.value = if (track == null) PlaybackSnapshot.EMPTY else PlaybackSnapshot(
            track = PlaybackTrackSnapshot(track.id, track.title, track.artists),
            phase = phase,
            playWhenReady = isPlaying,
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
