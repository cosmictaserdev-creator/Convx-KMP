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
}
