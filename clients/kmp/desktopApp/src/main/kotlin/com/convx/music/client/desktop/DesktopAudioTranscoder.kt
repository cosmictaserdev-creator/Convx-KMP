package com.convx.music.client.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.runInterruptible
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegFrameRecorder
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.atomic.AtomicReference

/** Decodes YouTube's fragmented AAC streams to temporary PCM files JavaFX can play reliably. */
internal class DesktopAudioTranscoder : AutoCloseable {
    private val directory = Files.createTempDirectory("convx-desktop-audio-")
    private val lifecycleLock = Any()
    @Volatile private var closed = false
    private var activeTranscodes = 0

    suspend fun transcode(sourceUrl: String): Path {
        synchronized(lifecycleLock) {
            check(!closed) { "The desktop audio decoder has been closed." }
            activeTranscodes += 1
        }
        try {
            val completedFile = AtomicReference<Path?>()
            try {
                val file = runInterruptible(Dispatchers.IO) {
                    check(!closed) { "The desktop audio decoder has been closed." }
                    val destination = Files.createTempFile(directory, "track-", ".wav")
                    val grabber = FFmpegFrameGrabber(sourceUrl)
                    val recorder = FFmpegFrameRecorder(destination.toFile(), 0)
                    var completed = false
                    try {
                        grabber.setOption("rw_timeout", READ_TIMEOUT_MICROS.toString())
                        grabber.start()
                        check(grabber.sampleRate > 0 && grabber.audioChannels > 0) {
                            "The audio stream has no supported audio track."
                        }
                        recorder.format = "wav"
                        recorder.audioCodecName = "pcm_s16le"
                        recorder.sampleRate = grabber.sampleRate
                        recorder.audioChannels = grabber.audioChannels
                        recorder.start()

                        while (true) {
                            if (Thread.currentThread().isInterrupted) throw InterruptedException("Audio decoding was cancelled.")
                            val frame = grabber.grabSamples() ?: break
                            recorder.record(frame)
                        }
                        recorder.stop()
                        check(Files.size(destination) > WAV_HEADER_BYTES) { "The audio stream decoded to an empty file." }
                        completed = true
                        completedFile.set(destination)
                        destination
                    } finally {
                        runCatching { recorder.stop() }
                        runCatching { recorder.release() }
                        runCatching { grabber.stop() }
                        runCatching { grabber.release() }
                        if (!completed) Files.deleteIfExists(destination)
                    }
                }
                currentCoroutineContext().ensureActive()
                return file
            } catch (error: Throwable) {
                completedFile.getAndSet(null)?.let(::delete)
                throw error
            }
        } finally {
            synchronized(lifecycleLock) {
                activeTranscodes -= 1
                if (closed && activeTranscodes == 0) deleteDirectoryContents()
            }
        }
    }

    fun delete(file: Path) {
        if (file.parent == directory) runCatching { Files.deleteIfExists(file) }
    }

    override fun close() {
        synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            if (activeTranscodes == 0) deleteDirectoryContents()
        }
    }

    private fun deleteDirectoryContents() {
        runCatching {
            Files.list(directory).use { files ->
                files.forEach { file -> runCatching { Files.deleteIfExists(file) } }
            }
            Files.deleteIfExists(directory)
        }
    }

    private companion object {
        const val READ_TIMEOUT_MICROS = 30_000_000
        const val WAV_HEADER_BYTES = 44L
    }
}
