package com.music.innertube

import com.convx.music.playback.AudioStreamResolver
import com.convx.music.playback.PlaybackAudioStream
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.response.PlayerResponse
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.WEB
import kotlinx.coroutines.CancellationException

class InnertubeAudioStreamResolver(
    private val repository: InnertubeRepository,
) : AudioStreamResolver {
    override suspend fun resolve(trackId: String, durationMs: Long?): Result<PlaybackAudioStream> {
        try {
            val unresolvedPlayers = mutableListOf<PlayerResponse>()
            val clients = listOf(ANDROID_VR_1_43_32, IOS, WEB)
            for (client in clients) {
                val signatureTimestamp = if (client.useSignatureTimestamp) {
                    val timestampResult = PlatformStreamResolver.signatureTimestamp(trackId)
                    val timestampError = timestampResult.exceptionOrNull()
                    if (timestampError is CancellationException) throw timestampError
                    timestampResult.getOrNull()
                } else {
                    null
                }
                val playerResult = repository.player(
                    videoId = trackId,
                    client = client,
                    signatureTimestamp = signatureTimestamp,
                )
                if (playerResult.isFailure) {
                    val error = playerResult.exceptionOrNull()
                    if (error is CancellationException) throw error
                    continue
                }
                val player = playerResult.getOrThrow()
                val stream = player.audioCandidates(trackId, durationMs).firstOrNull()
                if (stream != null) return Result.success(stream)
                if (player.hasAudioFormats()) unresolvedPlayers += player
            }

            if (unresolvedPlayers.isNotEmpty()) {
                val fallbackStreams = PlatformStreamResolver.newPipePlayer(trackId).toMap()
                val fallbackStream = unresolvedPlayers.firstNotNullOfOrNull { player ->
                    player.audioCandidates(
                        newPipeStreams = fallbackStreams,
                        videoId = trackId,
                        durationMs = durationMs,
                        resolveWithPlatform = false,
                    ).firstOrNull()
                }
                if (fallbackStream != null) return Result.success(fallbackStream)
            }
            return Result.failure(IllegalStateException("No playable audio stream was returned for $trackId"))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return Result.failure(error)
        }
    }

    private fun PlayerResponse.audioCandidates(
        videoId: String,
        durationMs: Long?,
    ): List<PlaybackAudioStream> = audioCandidates(
        newPipeStreams = emptyMap(),
        videoId = videoId,
        durationMs = durationMs,
        resolveWithPlatform = true,
    )

    private fun PlayerResponse.audioCandidates(
        newPipeStreams: Map<Int, String>,
        videoId: String,
        durationMs: Long?,
        resolveWithPlatform: Boolean,
    ): List<PlaybackAudioStream> = streamingData?.let { data ->
        (data.adaptiveFormats + data.formats.orEmpty())
            .asSequence()
            .filter { it.isAudio }
            .mapNotNull { format ->
                val url = (if (resolveWithPlatform) PlatformStreamResolver.streamUrl(format, videoId) else null)
                    ?: newPipeStreams[format.itag]
                    ?: format.url
                    ?: return@mapNotNull null
                if (!url.startsWith("https://") && !url.startsWith("http://")) return@mapNotNull null
                PlaybackAudioStream(
                    url = url,
                    mimeType = format.mimeType,
                    durationMs = format.approxDurationMs?.toLongOrNull()?.takeIf { it > 0L } ?: durationMs,
                    bitrate = format.averageBitrate ?: format.bitrate,
                )
            }
            .sortedWith(
                compareByDescending<PlaybackAudioStream> { it.mimeType.startsWith("audio/mp4", ignoreCase = true) }
                    .thenByDescending { it.bitrate },
            )
            .toList()
    }.orEmpty()

    private fun PlayerResponse.hasAudioFormats(): Boolean = streamingData?.let { data ->
        (data.adaptiveFormats + data.formats.orEmpty()).any { it.isAudio }
    } == true

}
