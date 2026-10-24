package com.convx.music.client.desktop

import javafx.application.Platform
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.util.Duration
import java.util.concurrent.atomic.AtomicBoolean

internal class DesktopStreamPlayer(
    private val onReady: (trackId: String, durationMs: Long?) -> Unit,
    private val onProgress: (trackId: String, positionMs: Long, durationMs: Long?) -> Unit,
    private val onEnded: (trackId: String) -> Unit,
    private val onError: (trackId: String, message: String) -> Unit,
) {
    private var player: MediaPlayer? = null
    private var activeTrackId: String? = null

    fun play(trackId: String, url: String) {
        JavaFxRuntime.run {
            disposePlayer()
            activeTrackId = trackId
            try {
                val media = Media(url)
                val nextPlayer = MediaPlayer(media)
                player = nextPlayer
                nextPlayer.setOnReady {
                    if (activeTrackId != trackId) return@setOnReady
                    val durationMs = nextPlayer.totalDuration.toMillis().toLong().takeIf { it > 0L }
                    onReady(trackId, durationMs)
                    nextPlayer.play()
                }
                nextPlayer.setOnEndOfMedia {
                    if (activeTrackId == trackId) onEnded(trackId)
                }
                nextPlayer.setOnError {
                    if (activeTrackId == trackId) {
                        onError(trackId, nextPlayer.error?.message ?: "The audio stream could not be played.")
                    }
                }
                nextPlayer.currentTimeProperty().addListener { _, _, time ->
                    if (activeTrackId == trackId) {
                        val durationMs = nextPlayer.totalDuration.toMillis().toLong().takeIf { it > 0L }
                        onProgress(trackId, time.toMillis().toLong(), durationMs)
                    }
                }
            } catch (error: Exception) {
                onError(trackId, error.message ?: "The audio stream could not be opened.")
            }
        }
    }

    fun play() = JavaFxRuntime.run { player?.play() }

    fun pause() = JavaFxRuntime.run { player?.pause() }

    fun seekTo(positionMs: Long) = JavaFxRuntime.run {
        player?.seek(Duration.millis(positionMs.toDouble()))
    }

    fun replay() = JavaFxRuntime.run {
        player?.seek(Duration.ZERO)
        player?.play()
    }

    fun close() {
        JavaFxRuntime.run {
            disposePlayer()
            Platform.exit()
        }
    }

    private fun disposePlayer() {
        player?.stop()
        player?.dispose()
        player = null
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
