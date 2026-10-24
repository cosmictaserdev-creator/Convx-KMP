package com.convx.music.playback

import kotlinx.coroutines.flow.StateFlow

/** Portable read and intent surface implemented by each platform playback adapter. */
interface PlaybackController {
    val playbackSnapshot: StateFlow<PlaybackSnapshot>

    fun togglePlayPause()

    fun play()

    fun pause()

    fun seekTo(positionMs: Long)

    fun requestSkip(forward: Boolean)

    fun setRepeatMode(mode: PlaybackRepeatMode)

    fun setShuffleEnabled(enabled: Boolean)

    /** Optional queue handoff for platform clients that own their playback queue. */
    fun updateQueue(tracks: List<PlaybackRequestTrack>) = Unit

    /** Replaces the current queue with a playback context and starts its selected item. */
    fun playQueue(tracks: List<PlaybackRequestTrack>, startIndex: Int) {
        updateQueue(tracks)
        val queueController = this as? PlaybackQueueController
        if (queueController != null) {
            queueController.playQueueItem(startIndex)
        } else {
            tracks.getOrNull(startIndex)?.let(::playTrack)
        }
    }

    /** Implemented by platform clients that can resolve and play provider tracks. */
    fun playTrack(track: PlaybackRequestTrack) = Unit

    /** Release any platform media resources when the owning client closes. */
    fun release() = Unit

    fun cycleRepeatMode() {
        setRepeatMode(playbackSnapshot.value.repeatMode.next())
    }

    fun toggleShuffle() {
        setShuffleEnabled(!playbackSnapshot.value.shuffleEnabled)
    }
}
