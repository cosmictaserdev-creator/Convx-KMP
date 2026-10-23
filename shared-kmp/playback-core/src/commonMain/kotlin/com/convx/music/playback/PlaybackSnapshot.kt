package com.convx.music.playback

/** Portable identity for the track currently exposed by a playback adapter. */
data class PlaybackTrackSnapshot(
    val id: String,
    val title: String,
    val artists: List<String>,
)

enum class PlaybackPhase {
    IDLE,
    BUFFERING,
    READY,
    ENDED,
}

enum class PlaybackRepeatMode {
    OFF,
    ONE,
    ALL,
}

/** Read-only playback state without Media3, Android, or database types. */
data class PlaybackSnapshot(
    val track: PlaybackTrackSnapshot?,
    val phase: PlaybackPhase,
    val playWhenReady: Boolean,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long?,
    val mediaItemIndex: Int,
    val mediaItemCount: Int,
    val repeatMode: PlaybackRepeatMode,
    val shuffleEnabled: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
) {
    companion object {
        val EMPTY = PlaybackSnapshot(
            track = null,
            phase = PlaybackPhase.IDLE,
            playWhenReady = false,
            isPlaying = false,
            positionMs = 0L,
            durationMs = null,
            mediaItemIndex = -1,
            mediaItemCount = 0,
            repeatMode = PlaybackRepeatMode.OFF,
            shuffleEnabled = false,
            canSkipPrevious = false,
            canSkipNext = false,
        )
    }
}
