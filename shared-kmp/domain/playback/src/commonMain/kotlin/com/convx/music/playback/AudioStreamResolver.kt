package com.convx.music.playback

/** Provider-independent playable audio source returned by a playback resolver. */
data class PlaybackAudioStream(
    val url: String,
    val mimeType: String,
    val durationMs: Long?,
    val bitrate: Int,
)

/** Resolves a provider track into a stream that the platform audio engine can play. */
fun interface AudioStreamResolver {
    suspend fun resolve(trackId: String, durationMs: Long? = null): Result<PlaybackAudioStream>
}
