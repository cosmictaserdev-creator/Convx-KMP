package com.music.innertube

import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.response.PlayerResponse
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.WEB
import kotlinx.coroutines.CancellationException

data class ResolvedAudioStream(
    val url: String,
    val mimeType: String,
    val durationMs: Long?,
    val bitrate: Int,
)

class InnertubeAudioStreamResolver(
    private val repository: InnertubeRepository,
) {
    suspend fun resolve(videoId: String, durationMs: Long? = null): Result<ResolvedAudioStream> {
        try {
            val newPipeStreams = PlatformStreamResolver.newPipePlayer(videoId).toMap()
            val clients = listOf(ANDROID_VR_1_43_32, IOS, WEB)
            for (client in clients) {
                val signatureTimestamp = if (client.useSignatureTimestamp) {
                    PlatformStreamResolver.signatureTimestamp(videoId).getOrNull()
                } else {
                    null
                }
                val player = repository.player(
                    videoId = videoId,
                    client = client,
                    signatureTimestamp = signatureTimestamp,
                ).getOrNull() ?: continue
                val stream = player.audioCandidates(newPipeStreams, videoId, durationMs).firstOrNull() ?: continue
                return Result.success(stream)
            }
            return Result.failure(IllegalStateException("No playable audio stream was returned for $videoId"))
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return Result.failure(error)
        }
    }

    private fun PlayerResponse.audioCandidates(
        newPipeStreams: Map<Int, String>,
        videoId: String,
        durationMs: Long?,
    ): List<ResolvedAudioStream> = streamingData?.let { data ->
        (data.adaptiveFormats + data.formats.orEmpty())
            .asSequence()
            .filter { it.isAudio }
            .mapNotNull { format ->
                val url = PlatformStreamResolver.streamUrl(format, videoId)
                    ?: newPipeStreams[format.itag]
                    ?: format.url
                    ?: return@mapNotNull null
                if (!url.startsWith("https://") && !url.startsWith("http://")) return@mapNotNull null
                ResolvedAudioStream(
                    url = url,
                    mimeType = format.mimeType,
                    durationMs = format.approxDurationMs?.toLongOrNull()?.takeIf { it > 0L } ?: durationMs,
                    bitrate = format.averageBitrate ?: format.bitrate,
                )
            }
            .sortedWith(
                compareByDescending<ResolvedAudioStream> { it.mimeType.startsWith("audio/mp4", ignoreCase = true) }
                    .thenByDescending { it.bitrate },
            )
            .toList()
    }.orEmpty()

}
