@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.convx.music.client.ios

import com.convx.music.playback.PlaybackController
import com.convx.music.playback.PlaybackQueueController
import com.convx.music.playback.PlaybackQueueEntry
import com.convx.music.playback.PlaybackQueueSnapshot
import com.convx.music.playback.moveQueueIndices
import com.convx.music.playback.AudioStreamResolver
import com.convx.music.playback.PlaybackPhase
import com.convx.music.playback.PlaybackRepeatMode
import com.convx.music.playback.PlaybackRequestTrack
import com.convx.music.playback.PlaybackSnapshot
import com.convx.music.playback.PlaybackTrackSnapshot
import com.convx.music.client.ios.avplayercompat.convx_av_player_current_time_seconds
import com.convx.music.client.ios.avplayercompat.convx_av_player_duration_seconds
import com.convx.music.client.ios.avplayercompat.convx_av_player_item_status
import com.convx.music.client.ios.avplayercompat.convx_av_player_pause
import com.convx.music.client.ios.avplayercompat.convx_av_player_play
import com.convx.music.client.ios.avplayercompat.convx_av_player_replace_item
import com.convx.music.client.ios.avplayercompat.convx_av_player_seek_to_seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import platform.AVFoundation.AVPlayer
import platform.AVFoundation.AVPlayerItem
import platform.AVFoundation.AVPlayerItemStatusFailed
import platform.AVFoundation.AVPlayerItemStatusReadyToPlay
import platform.Foundation.NSURL

internal class IosPlaybackController(
    private val streamResolver: AudioStreamResolver,
) : PlaybackController, PlaybackQueueController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val player = AVPlayer()
    private val _playbackSnapshot = MutableStateFlow(PlaybackSnapshot.EMPTY)
    override val playbackSnapshot: StateFlow<PlaybackSnapshot> = _playbackSnapshot.asStateFlow()
    private val mutableQueueSnapshot = MutableStateFlow(PlaybackQueueSnapshot.EMPTY)
    override val queueSnapshot: StateFlow<PlaybackQueueSnapshot> = mutableQueueSnapshot.asStateFlow()

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
    private var playbackMonitor: Job? = null

    override fun updateQueue(tracks: List<PlaybackRequestTrack>) {
        if (queue == tracks) return
        val current = currentRequest
        queue = tracks.toList()
        currentIndex = current?.let { currentTrack -> queue.indexOfFirst { it.sameTrack(currentTrack) } } ?: -1
        publishQueueSnapshot()
        publish()
    }

    override fun playTrack(track: PlaybackRequestTrack) {
        playTrackAt(track, queue.indexOfFirst { it.sameTrack(track) })
    }

    override fun playQueueItem(index: Int) {
        val track = queue.getOrNull(index) ?: return
        playTrackAt(track, index)
    }

    private fun playTrackAt(track: PlaybackRequestTrack, queueIndex: Int) {
        resolveJob?.cancel()
        generation += 1L
        val requestGeneration = generation
        currentRequest = track
        currentIndex = queueIndex.takeIf { queue.getOrNull(it)?.sameTrack(track) == true }
            ?: queue.indexOfFirst { it.sameTrack(track) }
        positionMs = 0L
        durationMs = track.durationMs
        phase = PlaybackPhase.BUFFERING
        isPlaying = false
        errorMessage = null
        convx_av_player_pause(player)
        publishQueueSnapshot()
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
                convx_av_player_replace_item(player, AVPlayerItem(uRL = url))
                convx_av_player_play(player)
                isPlaying = true
                phase = PlaybackPhase.BUFFERING
                publish()
                monitorPlayback(requestGeneration)
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
            convx_av_player_play(player)
            isPlaying = true
            errorMessage = null
            publish()
        }
    }

    override fun pause() {
        convx_av_player_pause(player)
        isPlaying = false
        publish()
    }

    override fun seekTo(positionMs: Long) {
        val position = positionMs.coerceAtLeast(0L)
        convx_av_player_seek_to_seconds(player, position / 1_000.0)
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
        if (!removedCurrent) {
            if (index < currentIndex) currentIndex -= 1
            publishQueueSnapshot()
            publish()
            return
        }

        if (queue.isEmpty()) {
            resolveJob?.cancel()
            generation += 1L
            convx_av_player_pause(player)
            convx_av_player_replace_item(player, null)
            currentRequest = null
            currentIndex = -1
            phase = PlaybackPhase.IDLE
            isPlaying = false
            positionMs = 0L
            durationMs = null
            errorMessage = null
            publishQueueSnapshot()
            publish()
        } else {
            playQueueItem(index.coerceAtMost(queue.lastIndex))
        }
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
        playbackMonitor?.cancel()
        playbackMonitor = null
        scope.cancel()
        convx_av_player_pause(player)
        convx_av_player_replace_item(player, null)
    }

    private fun fail(message: String) {
        convx_av_player_pause(player)
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

    private fun monitorPlayback(requestGeneration: Long) {
        playbackMonitor?.cancel()
        playbackMonitor = scope.launch {
            while (isActive && generation == requestGeneration) {
                delay(500)
                if (generation != requestGeneration) break

                val itemStatus = convx_av_player_item_status(player)
                if (itemStatus == AVPlayerItemStatusFailed) {
                    fail("The audio stream could not be played.")
                    break
                }

                val positionSeconds = convx_av_player_current_time_seconds(player)
                if (positionSeconds.isFinite() && positionSeconds >= 0.0) {
                    positionMs = (positionSeconds * 1_000.0).toLong()
                }
                val durationSeconds = convx_av_player_duration_seconds(player)
                if (durationSeconds.isFinite() && durationSeconds > 0.0) {
                    durationMs = (durationSeconds * 1_000.0).toLong()
                }
                if (itemStatus == AVPlayerItemStatusReadyToPlay && phase == PlaybackPhase.BUFFERING) {
                    phase = PlaybackPhase.READY
                }
                publish()

                val totalDuration = durationMs
                if (totalDuration != null && positionMs >= totalDuration && isPlaying) {
                    if (repeatMode == PlaybackRepeatMode.ONE) {
                        seekTo(0L)
                        convx_av_player_play(player)
                    } else {
                        phase = PlaybackPhase.ENDED
                        isPlaying = false
                        publish()
                        requestSkip(forward = true)
                        break
                    }
                }
            }
        }
    }
}
