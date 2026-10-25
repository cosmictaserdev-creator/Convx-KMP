package com.convx.music.client.desktop

import javafx.application.Platform
import javafx.animation.Interpolator
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import com.convx.music.playback.CrossfadeEnvelope
import com.convx.music.playback.EqualizerFilterType
import com.convx.music.playback.PlaybackSettings
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

internal class DesktopStreamPlayer(
    private val onReady: (trackId: String, durationMs: Long?) -> Unit,
    private val onProgress: (trackId: String, positionMs: Long, durationMs: Long?) -> Unit,
    private val onStalled: (trackId: String) -> Unit,
    private val onPlaying: (trackId: String) -> Unit,
    private val onEnded: (trackId: String) -> Unit,
    private val onError: (trackId: String, message: String) -> Unit,
) {
    private var player: MediaPlayer? = null
    private var activeTrackId: String? = null
    private var activeMediaToken: Any? = null
    private var preparedPlayer: MediaPlayer? = null
    private var preparedTrackId: String? = null
    private var preparedMediaToken: Any? = null
    private var fadingOutPlayer: MediaPlayer? = null
    private var activeAudioFile: Path? = null
    private var preparedAudioFile: Path? = null
    private var fadingOutAudioFile: Path? = null
    private var fadeTimeline: Timeline? = null
    @Volatile
    private var playbackSettings = PlaybackSettings()

    fun updatePlaybackSettings(settings: PlaybackSettings) {
        playbackSettings = settings.normalized()
        JavaFxRuntime.run {
            listOfNotNull(player, preparedPlayer, fadingOutPlayer).distinct().forEach(::applyPlaybackSettings)
        }
    }

    fun play(
        trackId: String,
        sourceUri: String,
        ownedAudioFile: Path? = null,
        initialPositionMs: Long = 0L,
        shouldStartPlayback: () -> Boolean,
    ) {
        JavaFxRuntime.run {
            disposePlayer()
            activeTrackId = trackId
            val mediaToken = Any()
            activeMediaToken = mediaToken
            activeAudioFile = ownedAudioFile
            try {
                val media = Media(sourceUri)
                val errorReported = AtomicBoolean(false)
                media.setOnError {
                    if (errorReported.compareAndSet(false, true)) {
                        reportActiveError(trackId, mediaToken, media.error?.message ?: "The decoded audio could not be loaded.")
                    }
                }
                val nextPlayer = MediaPlayer(media)
                fun reportPlayerError(message: String) {
                    if (isActivePlayer(trackId, nextPlayer) && errorReported.compareAndSet(false, true)) {
                        reportActiveError(trackId, mediaToken, message)
                    }
                }
                player = nextPlayer
                applyPlaybackSettings(nextPlayer)
                nextPlayer.volume = 1.0
                nextPlayer.setOnReady {
                    if (!isActivePlayer(trackId, nextPlayer) || errorReported.get()) return@setOnReady
                    val durationMs = nextPlayer.totalDuration.toMillis().toLong().takeIf { it > 0L }
                    if (initialPositionMs > 0L) nextPlayer.seek(Duration.millis(initialPositionMs.toDouble()))
                    onReady(trackId, durationMs)
                    if (shouldStartPlayback()) nextPlayer.play() else nextPlayer.pause()
                }
                nextPlayer.setOnEndOfMedia {
                    if (isActivePlayer(trackId, nextPlayer)) onEnded(trackId)
                }
                nextPlayer.setOnStalled {
                    if (isActivePlayer(trackId, nextPlayer)) onStalled(trackId)
                }
                nextPlayer.setOnPlaying {
                    if (isActivePlayer(trackId, nextPlayer)) onPlaying(trackId)
                }
                nextPlayer.setOnError {
                    reportPlayerError(nextPlayer.error?.message ?: "The audio stream could not be played.")
                }
                nextPlayer.currentTimeProperty().addListener { _, _, time ->
                    if (isActivePlayer(trackId, nextPlayer)) {
                        val durationMs = nextPlayer.totalDuration.toMillis().toLong().takeIf { it > 0L }
                        onProgress(trackId, time.toMillis().toLong(), durationMs)
                    }
                }
            } catch (error: Exception) {
                disposePlayer()
                onError(trackId, error.message ?: "The audio stream could not be opened.")
            }
        }
    }

    /** Loads the next item silently so stream resolution and media preparation happen before handoff. */
    fun prepare(
        trackId: String,
        sourceUri: String,
        ownedAudioFile: Path? = null,
        initialPositionMs: Long = 0L,
        playbackRate: Double = 1.0,
    ) {
        JavaFxRuntime.run {
            if (trackId == activeTrackId || trackId == preparedTrackId) {
                ownedAudioFile?.let(::deleteAudioFile)
                return@run
            }
            disposePreparedPlayer()
            try {
                preparedTrackId = trackId
                val mediaToken = Any()
                preparedMediaToken = mediaToken
                preparedAudioFile = ownedAudioFile
                val media = Media(sourceUri)
                val errorReported = AtomicBoolean(false)
                media.setOnError {
                    if (preparedTrackId == trackId && preparedMediaToken === mediaToken && errorReported.compareAndSet(false, true)) {
                        disposePreparedPlayer()
                    }
                }
                val nextPlayer = MediaPlayer(media)
                if (errorReported.get()) {
                    nextPlayer.dispose()
                    return@run
                }
                preparedPlayer = nextPlayer
                applyPlaybackSettings(nextPlayer)
                nextPlayer.volume = 0.0
                fun reportError(message: String) {
                    when {
                        isActivePlayer(trackId, nextPlayer) && errorReported.compareAndSet(false, true) ->
                            reportActiveError(trackId, mediaToken, message)
                        preparedPlayer === nextPlayer && errorReported.compareAndSet(false, true) ->
                            disposePreparedPlayer()
                    }
                }
                nextPlayer.setOnEndOfMedia {
                    if (isActivePlayer(trackId, nextPlayer)) onEnded(trackId)
                }
                nextPlayer.setOnReady {
                    if (preparedPlayer === nextPlayer && preparedTrackId == trackId) {
                        if (initialPositionMs > 0L) {
                            nextPlayer.seek(Duration.millis(initialPositionMs.toDouble()))
                        }
                        nextPlayer.rate = playbackRate.coerceIn(0.9, 1.1)
                    }
                }
                nextPlayer.setOnError {
                    reportError(nextPlayer.error?.message ?: "The audio stream could not be played.")
                }
                nextPlayer.currentTimeProperty().addListener { _, _, time ->
                    if (isActivePlayer(trackId, nextPlayer)) {
                        val durationMs = nextPlayer.totalDuration.toMillis().toLong().takeIf { it > 0L }
                        onProgress(trackId, time.toMillis().toLong(), durationMs)
                    }
                }
            } catch (_: Exception) {
                disposePreparedPlayer()
            }
        }
    }

    /** Starts a prepared deck and moves both decks through an equal-power fade. */
    fun crossfadeToPrepared(
        outgoingTrackId: String,
        incomingTrackId: String,
        durationMs: Long,
        onHandoff: () -> Boolean,
    ) {
        JavaFxRuntime.run {
            val outgoing = player ?: return@run
            val incoming = preparedPlayer ?: return@run
            if (activeTrackId != outgoingTrackId || preparedTrackId != incomingTrackId) return@run
            if (incoming.status != MediaPlayer.Status.READY || fadeTimeline != null) return@run
            if (!onHandoff()) return@run

            preparedPlayer = null
            preparedTrackId = null
            fadingOutPlayer = outgoing
            fadingOutAudioFile = activeAudioFile
            activeAudioFile = preparedAudioFile
            activeMediaToken = preparedMediaToken
            preparedAudioFile = null
            preparedMediaToken = null
            player = incoming
            activeTrackId = incomingTrackId
            incoming.volume = 0.0
            incoming.play()

            val fadeDuration = durationMs.coerceAtLeast(1L)
            val frameInterval = 50L
            val frameCount = (fadeDuration / frameInterval).toInt().coerceAtLeast(1)
            val frames = (0..frameCount).map { frame ->
                val progress = frame.toFloat() / frameCount
                val elapsed = if (frame == frameCount) fadeDuration else frame * frameInterval
                KeyFrame(
                    Duration.millis(elapsed.toDouble()),
                    KeyValue(outgoing.volumeProperty(), CrossfadeEnvelope.outgoingGain(progress).toDouble(), Interpolator.LINEAR),
                    KeyValue(incoming.volumeProperty(), CrossfadeEnvelope.incomingGain(progress).toDouble(), Interpolator.LINEAR),
                )
            }
            Timeline(*frames.toTypedArray()).also { timeline ->
                fadeTimeline = timeline
                timeline.setOnFinished {
                    if (fadeTimeline === timeline) {
                        fadeTimeline = null
                        if (fadingOutPlayer === outgoing) fadingOutPlayer = null
                        incoming.rate = 1.0
                        dispose(outgoing)
                        fadingOutAudioFile?.let(::deleteAudioFile)
                        fadingOutAudioFile = null
                    }
                }
                timeline.play()
            }
        }
    }

    fun clearPrepared() = JavaFxRuntime.run { disposePreparedPlayer() }

    fun play() = JavaFxRuntime.run {
        player?.play()
        fadingOutPlayer?.play()
        fadeTimeline?.play()
    }

    fun pause() = JavaFxRuntime.run {
        player?.pause()
        fadingOutPlayer?.pause()
        fadeTimeline?.pause()
    }

    fun seekTo(positionMs: Long) = JavaFxRuntime.run {
        finishCrossfadeOnActiveDeck()
        player?.seek(Duration.millis(positionMs.toDouble()))
    }

    fun replay() = JavaFxRuntime.run {
        finishCrossfadeOnActiveDeck()
        player?.seek(Duration.ZERO)
        player?.play()
    }

    fun close(onClosed: () -> Unit = {}) {
        JavaFxRuntime.run {
            activeTrackId = null
            disposePlayer()
            onClosed()
            Platform.exit()
        }
    }

    private fun isActivePlayer(trackId: String, candidate: MediaPlayer): Boolean =
        activeTrackId == trackId && player === candidate

    private fun applyPlaybackSettings(target: MediaPlayer) {
        val settings = playbackSettings
        val equalizer = target.audioEqualizer
        equalizer.isEnabled = settings.equalizerEnabled && !settings.bitPerfectEnabled
        if (!equalizer.isEnabled) return

        // JavaFX exposes fixed graphic bands; map the shared parametric profile onto those centers.
        val profile = settings.equalizerProfile
        equalizer.bands.forEach { engineBand ->
            val profileGain = profile.preamp + profile.bands.asSequence()
                .filter { it.enabled }
                .sumOf { band -> graphicEqualizerGain(engineBand.centerFrequency, band.frequency, band.gain, band.q, band.filterType) }
            engineBand.gain = profileGain.coerceIn(-24.0, 12.0)
        }
    }

    private fun graphicEqualizerGain(
        centerFrequency: Double,
        frequency: Double,
        gain: Double,
        q: Double,
        filterType: EqualizerFilterType,
    ): Double = when (filterType) {
        EqualizerFilterType.PK -> {
            val octaveOffset = ln(centerFrequency / frequency) / LN_TWO
            val octaveWidth = max(0.35, 1.0 / q)
            gain * exp(-0.5 * (octaveOffset / octaveWidth) * (octaveOffset / octaveWidth))
        }
        EqualizerFilterType.LSC -> if (centerFrequency <= frequency) gain else 0.0
        EqualizerFilterType.HSC -> if (centerFrequency >= frequency) gain else 0.0
        EqualizerFilterType.LPQ -> if (centerFrequency > frequency) -24.0 else 0.0
        EqualizerFilterType.HPQ -> if (centerFrequency < frequency) -24.0 else 0.0
    }

    private fun disposePlayer() {
        fadeTimeline?.stop()
        fadeTimeline = null
        val previousPlayers = listOfNotNull(player, preparedPlayer, fadingOutPlayer).distinct()
        val previousFiles = listOfNotNull(activeAudioFile, preparedAudioFile, fadingOutAudioFile).distinct()
        player = null
        preparedPlayer = null
        preparedTrackId = null
        fadingOutPlayer = null
        activeMediaToken = null
        preparedMediaToken = null
        activeAudioFile = null
        preparedAudioFile = null
        fadingOutAudioFile = null
        activeTrackId = null
        previousPlayers.forEach(::dispose)
        previousFiles.forEach(::deleteAudioFile)
    }

    private fun disposePreparedPlayer() {
        val prepared = preparedPlayer
        val audioFile = preparedAudioFile
        preparedPlayer = null
        preparedTrackId = null
        preparedMediaToken = null
        preparedAudioFile = null
        prepared?.let(::dispose)
        audioFile?.let(::deleteAudioFile)
    }

    private fun finishCrossfadeOnActiveDeck() {
        fadeTimeline?.stop()
        fadeTimeline = null
        val outgoing = fadingOutPlayer
        val outgoingAudioFile = fadingOutAudioFile
        fadingOutPlayer = null
        fadingOutAudioFile = null
        player?.volume = 1.0
        outgoing?.let(::dispose)
        outgoingAudioFile?.let(::deleteAudioFile)
    }

    private fun dispose(target: MediaPlayer) {
        runCatching { target.stop() }
        runCatching { target.dispose() }
    }

    private fun reportActiveError(trackId: String, mediaToken: Any, message: String) {
        if (activeTrackId != trackId || activeMediaToken !== mediaToken) return
        disposePlayer()
        onError(trackId, message)
    }

    private fun deleteAudioFile(file: Path) {
        runCatching { java.nio.file.Files.deleteIfExists(file) }
    }

    private companion object {
        const val LN_TWO = 0.6931471805599453
    }
}

internal object JavaFxRuntime {
    private val initialized = AtomicBoolean(false)

    fun run(action: () -> Unit) {
        if (initialized.compareAndSet(false, true)) {
            try {
                Platform.startup {
                    Platform.setImplicitExit(false)
                    action()
                }
            } catch (_: IllegalStateException) {
                Platform.runLater(action)
            }
        } else {
            Platform.runLater(action)
        }
    }
}
