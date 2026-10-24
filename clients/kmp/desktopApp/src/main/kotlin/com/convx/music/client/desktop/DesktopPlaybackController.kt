package com.convx.music.client.desktop

import androidx.compose.ui.graphics.Color
import com.convx.music.catalog.CatalogEntry
import com.convx.music.playback.AudioStreamResolver
import com.convx.music.playback.PlaybackController
import com.convx.music.playback.PlaybackPhase
import com.convx.music.playback.PlaybackRepeatMode
import com.convx.music.playback.PlaybackRequestTrack
import com.convx.music.playback.PlaybackSnapshot
import com.convx.music.playback.PlaybackTrackSnapshot
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
import kotlin.random.Random

internal data class DesktopTrack(
    val request: PlaybackRequestTrack,
    val album: String,
    val durationMs: Long,
    val artStart: Color,
    val artEnd: Color,
    val artworkUrl: String?,
) {
    val snapshot = PlaybackTrackSnapshot(request.id, request.title, request.artists)
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
) : DesktopMusicController {
    private val lock = Any()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _playbackSnapshot = MutableStateFlow(PlaybackSnapshot.EMPTY)
    override val playbackSnapshot: StateFlow<PlaybackSnapshot> = _playbackSnapshot.asStateFlow()
    private val audio = DesktopStreamPlayer(
        onReady = ::onAudioReady,
        onProgress = ::onAudioProgress,
        onEnded = ::onAudioEnded,
        onError = ::onAudioError,
    )

    private var tracks: List<DesktopTrack> = emptyList()
    private var currentIndex = -1
    private var currentRequest: PlaybackRequestTrack? = null
    private var resolutionJob: Job? = null
    private var generation = 0L
    private var repeatMode = PlaybackRepeatMode.OFF
    private var shuffleEnabled = false
    private var isPlaying = false
    private var phase = PlaybackPhase.IDLE
    private var positionMs = 0L
    private var durationMs: Long? = null
    private var errorMessage: String? = null

    override fun setCatalogTracks(tracks: List<DesktopTrack>) {
        synchronized(lock) {
            val currentId = currentRequest?.id
            if (this.tracks.map { it.request.id } == tracks.map { it.request.id }) return
            this.tracks = tracks
            currentIndex = tracks.indexOfFirst { it.request.id == currentId }
            publishLocked()
        }
    }

    override fun togglePlayPause() {
        if (playbackSnapshot.value.isPlaying) pause() else play()
    }

    override fun play() {
        val track = synchronized(lock) { currentRequest } ?: return
        val snapshot = playbackSnapshot.value
        if (snapshot.phase == PlaybackPhase.BUFFERING) return
        if (snapshot.phase == PlaybackPhase.IDLE || snapshot.phase == PlaybackPhase.ENDED || snapshot.errorMessage != null) {
            playTrack(track)
        } else {
            audio.play()
            synchronized(lock) {
                isPlaying = true
                errorMessage = null
                publishLocked()
            }
        }
    }

    override fun pause() {
        audio.pause()
        synchronized(lock) {
            isPlaying = false
            publishLocked()
        }
    }

    override fun seekTo(positionMs: Long) {
        val duration = synchronized(lock) { durationMs } ?: return
        val position = positionMs.coerceIn(0L, duration)
        audio.seekTo(position)
        synchronized(lock) {
            this.positionMs = position
            publishLocked()
        }
    }

    override fun requestSkip(forward: Boolean) {
        val targetIndex = synchronized(lock) {
            if (tracks.isEmpty()) return
            if (shuffleEnabled && tracks.size > 1) {
                tracks.indices.filter { it != currentIndex }.random(Random)
            } else {
                val step = if (forward) 1 else -1
                val target = currentIndex + step
                when {
                    target in tracks.indices -> target
                    forward && repeatMode == PlaybackRepeatMode.ALL -> 0
                    !forward && repeatMode == PlaybackRepeatMode.ALL -> tracks.lastIndex
                    else -> return
                }
            }
        }
        val track = synchronized(lock) { tracks.getOrNull(targetIndex)?.request } ?: return
        playTrack(track)
    }

    override fun setRepeatMode(mode: PlaybackRepeatMode) {
        synchronized(lock) {
            repeatMode = mode
            publishLocked()
        }
    }

    override fun setShuffleEnabled(enabled: Boolean) {
        synchronized(lock) {
            shuffleEnabled = enabled
            publishLocked()
        }
    }

    override fun selectTrack(trackId: String) {
        val track = synchronized(lock) { tracks.firstOrNull { it.request.id == trackId }?.request } ?: return
        playTrack(track)
    }

    override fun playTrack(track: PlaybackRequestTrack) {
        audio.pause()
        audio.seekTo(0L)
        val requestGeneration = synchronized(lock) {
            resolutionJob?.cancel()
            generation += 1L
            currentRequest = track
            currentIndex = tracks.indexOfFirst { it.request.id == track.id }
            positionMs = 0L
            durationMs = track.durationMs
            isPlaying = false
            phase = PlaybackPhase.BUFFERING
            errorMessage = null
            publishLocked()
            generation
        }
        resolutionJob = scope.launch {
            try {
                val stream = streamResolver.resolve(track.id, track.durationMs)
                    .getOrElse { throw it }
                if (!isCurrent(requestGeneration)) return@launch
                audio.play(track.id, stream.url)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                synchronized(lock) {
                    if (generation != requestGeneration) return@synchronized
                    phase = PlaybackPhase.IDLE
                    isPlaying = false
                    errorMessage = error.message ?: "Couldn't start playback."
                    publishLocked()
                }
            }
        }
    }

    override fun close() {
        resolutionJob?.cancel()
        scope.cancel()
        audio.close()
    }

    override fun release() = close()

    private fun onAudioReady(trackId: String, durationMs: Long?) {
        synchronized(lock) {
            if (currentRequest?.id != trackId) return
            phase = PlaybackPhase.READY
            isPlaying = true
            this.durationMs = durationMs?.takeIf { it > 0L } ?: this.durationMs
            errorMessage = null
            publishLocked()
        }
    }

    private fun onAudioProgress(trackId: String, positionMs: Long, durationMs: Long?) {
        synchronized(lock) {
            if (currentRequest?.id != trackId) return
            this.positionMs = positionMs.coerceAtLeast(0L)
            this.durationMs = durationMs?.takeIf { it > 0L } ?: this.durationMs
            publishLocked()
        }
    }

    private fun onAudioEnded(trackId: String) {
        synchronized(lock) {
            if (currentRequest?.id != trackId) return
            if (repeatMode == PlaybackRepeatMode.ONE) {
                positionMs = 0L
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
            phase = PlaybackPhase.IDLE
            isPlaying = false
            errorMessage = message
            publishLocked()
        }
    }

    private fun isCurrent(requestGeneration: Long): Boolean = synchronized(lock) {
        generation == requestGeneration
    }

    private fun publishLocked() {
        val track = currentRequest
        _playbackSnapshot.value = if (track == null) {
            PlaybackSnapshot.EMPTY
        } else {
            PlaybackSnapshot(
                track = PlaybackTrackSnapshot(track.id, track.title, track.artists),
                phase = phase,
                playWhenReady = isPlaying,
                isPlaying = isPlaying,
                positionMs = positionMs,
                durationMs = durationMs,
                mediaItemIndex = currentIndex,
                mediaItemCount = tracks.size,
                repeatMode = repeatMode,
                shuffleEnabled = shuffleEnabled,
                canSkipPrevious = currentIndex > 0 || repeatMode == PlaybackRepeatMode.ALL,
                canSkipNext = currentIndex in tracks.indices &&
                    (currentIndex < tracks.lastIndex || repeatMode == PlaybackRepeatMode.ALL),
                errorMessage = errorMessage,
            )
        }
    }
}
