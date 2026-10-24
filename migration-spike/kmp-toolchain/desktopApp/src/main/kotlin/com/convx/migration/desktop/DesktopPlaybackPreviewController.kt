package com.convx.migration.desktop

import androidx.compose.ui.graphics.Color
import com.convx.music.playback.PlaybackController
import com.convx.music.playback.PlaybackPhase
import com.convx.music.playback.PlaybackRepeatMode
import com.convx.music.playback.PlaybackSnapshot
import com.convx.music.playback.PlaybackTrackSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal data class DesktopDemoTrack(
    val snapshot: PlaybackTrackSnapshot,
    val album: String,
    val durationMs: Long,
    val artStart: Color,
    val artEnd: Color,
)

internal val desktopDemoTracks = listOf(
    DesktopDemoTrack(
        PlaybackTrackSnapshot("satellite-hearts", "Satellite Hearts", listOf("Mira Sol")),
        "Soft Focus",
        226_000L,
        Color(0xFF745B9C),
        Color(0xFFE49E9B),
    ),
    DesktopDemoTrack(
        PlaybackTrackSnapshot("blue-hour", "Blue Hour", listOf("Northbound")),
        "Long Way Home",
        201_000L,
        Color(0xFF245E77),
        Color(0xFF8BC0B7),
    ),
    DesktopDemoTrack(
        PlaybackTrackSnapshot("glass-gardens", "Glass Gardens", listOf("Luma")),
        "Rooms of Light",
        247_000L,
        Color(0xFF885E66),
        Color(0xFFF0B889),
    ),
    DesktopDemoTrack(
        PlaybackTrackSnapshot("sunroom", "Sunroom", listOf("The Everydays")),
        "Small Hours",
        193_000L,
        Color(0xFF466C57),
        Color(0xFFCFD98B),
    ),
    DesktopDemoTrack(
        PlaybackTrackSnapshot("after-the-rain", "After the Rain", listOf("Nocturne")),
        "Night Lines",
        232_000L,
        Color(0xFF394574),
        Color(0xFFAC8DD0),
    ),
)

/** Preview-only adapter. The production desktop audio engine will replace this binding. */
internal interface DesktopMusicController : PlaybackController {
    val favoriteTrackIds: StateFlow<Set<String>>
    fun selectTrack(trackId: String)
    fun toggleFavorite(trackId: String)
}

internal class DesktopPreviewPlaybackController : DesktopMusicController {
    private var currentIndex = 0
    private var repeatMode = PlaybackRepeatMode.OFF
    private var shuffleEnabled = false
    private var isPlaying = true
    private var positionMs = 86_000L
    private val _favoriteTrackIds = MutableStateFlow<Set<String>>(emptySet())
    override val favoriteTrackIds: StateFlow<Set<String>> = _favoriteTrackIds.asStateFlow()

    private val _playbackSnapshot = MutableStateFlow(snapshot())
    override val playbackSnapshot: StateFlow<PlaybackSnapshot> = _playbackSnapshot.asStateFlow()

    override fun togglePlayPause() {
        isPlaying = !isPlaying
        publish()
    }

    override fun play() {
        isPlaying = true
        publish()
    }

    override fun pause() {
        isPlaying = false
        publish()
    }

    override fun seekTo(positionMs: Long) {
        this.positionMs = positionMs.coerceIn(0L, currentTrack().durationMs)
        publish()
    }

    override fun requestSkip(forward: Boolean) {
        val nextIndex = currentIndex + if (forward) 1 else -1
        if (nextIndex !in desktopDemoTracks.indices) return
        currentIndex = nextIndex
        positionMs = 0L
        isPlaying = true
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

    override fun selectTrack(trackId: String) {
        val index = desktopDemoTracks.indexOfFirst { it.snapshot.id == trackId }
        if (index < 0) return
        currentIndex = index
        positionMs = 0L
        isPlaying = true
        publish()
    }

    override fun toggleFavorite(trackId: String) {
        _favoriteTrackIds.value = if (trackId in _favoriteTrackIds.value) {
            _favoriteTrackIds.value - trackId
        } else {
            _favoriteTrackIds.value + trackId
        }
    }

    private fun currentTrack() = desktopDemoTracks[currentIndex]

    private fun snapshot() = PlaybackSnapshot(
        track = currentTrack().snapshot,
        phase = PlaybackPhase.READY,
        playWhenReady = isPlaying,
        isPlaying = isPlaying,
        positionMs = positionMs,
        durationMs = currentTrack().durationMs,
        mediaItemIndex = currentIndex,
        mediaItemCount = desktopDemoTracks.size,
        repeatMode = repeatMode,
        shuffleEnabled = shuffleEnabled,
        canSkipPrevious = currentIndex > 0,
        canSkipNext = currentIndex < desktopDemoTracks.lastIndex,
    )

    private fun publish() {
        _playbackSnapshot.value = snapshot()
    }
}
