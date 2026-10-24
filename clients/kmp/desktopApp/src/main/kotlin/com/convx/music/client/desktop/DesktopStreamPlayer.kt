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
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopStreamPlayer(
    private val onReady: (trackId: String, durationMs: Long?) -> Unit,
    private val onProgress: (trackId: String, positionMs: Long, durationMs: Long?) -> Unit,
    private val onEnded: (trackId: String) -> Unit,
    private val onError: (trackId: String, message: String) -> Unit,
) {
    private var player: MediaPlayer? = null
    private var activeTrackId: String? = null
    private var preparedPlayer: MediaPlayer? = null
    private var preparedTrackId: String? = null
    private var fadingOutPlayer: MediaPlayer? = null
    private var fadeTimeline: Timeline? = null

    fun play(trackId: String, url: String) {
        JavaFxRuntime.run {
            disposePlayer()
            activeTrackId = trackId
            try {
                val media = Media(url)
                val nextPlayer = MediaPlayer(media)
                player = nextPlayer
                nextPlayer.volume = 1.0
                nextPlayer.setOnReady {
                    if (!isActivePlayer(trackId, nextPlayer)) return@setOnReady
                    val durationMs = nextPlayer.totalDuration.toMillis().toLong().takeIf { it > 0L }
                    onReady(trackId, durationMs)
                    nextPlayer.play()
                }
                nextPlayer.setOnEndOfMedia {
                    if (isActivePlayer(trackId, nextPlayer)) onEnded(trackId)
                }
                nextPlayer.setOnError {
                    if (isActivePlayer(trackId, nextPlayer)) {
                        onError(trackId, nextPlayer.error?.message ?: "The audio stream could not be played.")
                    }
                }
                nextPlayer.currentTimeProperty().addListener { _, _, time ->
                    if (isActivePlayer(trackId, nextPlayer)) {
                        val durationMs = nextPlayer.totalDuration.toMillis().toLong().takeIf { it > 0L }
                        onProgress(trackId, time.toMillis().toLong(), durationMs)
                    }
                }
            } catch (error: Exception) {
                onError(trackId, error.message ?: "The audio stream could not be opened.")
            }
        }
    }

    /** Loads the next item silently so stream resolution and media preparation happen before handoff. */
    fun prepare(trackId: String, url: String) {
        JavaFxRuntime.run {
            if (trackId == activeTrackId || trackId == preparedTrackId) return@run
            disposePreparedPlayer()
            try {
                val nextPlayer = MediaPlayer(Media(url))
                preparedPlayer = nextPlayer
                preparedTrackId = trackId
                nextPlayer.volume = 0.0
                nextPlayer.setOnEndOfMedia {
                    if (isActivePlayer(trackId, nextPlayer)) onEnded(trackId)
                }
                nextPlayer.setOnError {
                    when {
                        isActivePlayer(trackId, nextPlayer) -> {
                            onError(trackId, nextPlayer.error?.message ?: "The audio stream could not be played.")
                        }
                        preparedPlayer === nextPlayer -> disposePreparedPlayer()
                    }
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
                        dispose(outgoing)
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

    fun close() {
        JavaFxRuntime.run {
            activeTrackId = null
            disposePlayer()
            Platform.exit()
        }
    }

    private fun isActivePlayer(trackId: String, candidate: MediaPlayer): Boolean =
        activeTrackId == trackId && player === candidate

    private fun disposePlayer() {
        fadeTimeline?.stop()
        fadeTimeline = null
        val previousPlayers = listOfNotNull(player, preparedPlayer, fadingOutPlayer).distinct()
        player = null
        preparedPlayer = null
        preparedTrackId = null
        fadingOutPlayer = null
        previousPlayers.forEach(::dispose)
    }

    private fun disposePreparedPlayer() {
        val prepared = preparedPlayer
        preparedPlayer = null
        preparedTrackId = null
        prepared?.let(::dispose)
    }

    private fun finishCrossfadeOnActiveDeck() {
        fadeTimeline?.stop()
        fadeTimeline = null
        val outgoing = fadingOutPlayer
        fadingOutPlayer = null
        player?.volume = 1.0
        outgoing?.let(::dispose)
    }

    private fun dispose(target: MediaPlayer) {
        target.stop()
        target.dispose()
    }
}

private object JavaFxRuntime {
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
