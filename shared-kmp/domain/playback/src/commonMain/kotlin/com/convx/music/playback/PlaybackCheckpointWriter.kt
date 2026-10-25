package com.convx.music.playback

import kotlinx.atomicfu.atomic
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Coalesces frequent player updates into ordered, cancellation-aware checkpoint writes. */
class PlaybackCheckpointWriter(
    scope: CoroutineScope,
    private val repository: PlaybackCheckpointRepository,
    private val positionWriteIntervalMs: Long = POSITION_WRITE_INTERVAL_MS,
) {
    init {
        require(positionWriteIntervalMs > 0L)
    }

    private val pending = Channel<PlaybackCheckpoint>(Channel.CONFLATED)
    private val lastRequested = atomic<PlaybackCheckpoint?>(null)

    init {
        scope.launch {
            while (isActive) {
                val checkpoint = pending.receiveCatching().getOrNull() ?: break
                try {
                    repository.saveCheckpoint(checkpoint)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    lastRequested.compareAndSet(checkpoint, null)
                }
            }
        }
    }

    fun save(checkpoint: PlaybackCheckpoint, force: Boolean = false) {
        val next = checkpoint.normalized()
        while (true) {
            val previous = lastRequested.value
            if (!force && previous != null && !needsWrite(previous, next)) return
            if (lastRequested.compareAndSet(previous, next)) {
                pending.trySend(next)
                return
            }
        }
    }

    private fun needsWrite(previous: PlaybackCheckpoint, next: PlaybackCheckpoint): Boolean {
        if (
            previous.queue != next.queue ||
            previous.currentIndex != next.currentIndex ||
            previous.repeatMode != next.repeatMode ||
            previous.shuffleEnabled != next.shuffleEnabled
        ) {
            return true
        }
        return if (next.positionMs >= previous.positionMs) {
            next.positionMs - previous.positionMs >= positionWriteIntervalMs
        } else {
            previous.positionMs - next.positionMs >= positionWriteIntervalMs
        }
    }

    private companion object {
        const val POSITION_WRITE_INTERVAL_MS = 5_000L
    }
}
