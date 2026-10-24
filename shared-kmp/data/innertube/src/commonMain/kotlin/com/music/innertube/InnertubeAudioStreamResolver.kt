package com.music.innertube

import com.convx.music.playback.AudioStreamResolver
import com.convx.music.playback.PlaybackAudioStream
import com.music.innertube.models.YouTubeClient
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_43_32
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_61_48
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_1_65_10
import com.music.innertube.models.YouTubeClient.Companion.ANDROID_VR_NO_AUTH
import com.music.innertube.models.YouTubeClient.Companion.IPADOS
import com.music.innertube.models.YouTubeClient.Companion.IOS
import com.music.innertube.models.YouTubeClient.Companion.MOBILE
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5
import com.music.innertube.models.YouTubeClient.Companion.TVHTML5_SIMPLY_EMBEDDED_PLAYER
import com.music.innertube.models.YouTubeClient.Companion.VISIONOS
import com.music.innertube.models.YouTubeClient.Companion.WEB
import com.music.innertube.models.YouTubeClient.Companion.WEB_CREATOR
import com.music.innertube.models.YouTubeClient.Companion.WEB_REMIX
import com.music.innertube.models.response.PlayerResponse
import kotlinx.coroutines.CancellationException

/** Resolves only stream URLs that can serve a later byte range, avoiding truncated playback URLs. */
class InnertubeAudioStreamResolver(
    private val repository: InnertubeRepository,
    private val webPoTokenProvider: InnertubeWebPoTokenProvider = NoOpInnertubeWebPoTokenProvider,
) : AudioStreamResolver {
    override suspend fun resolve(trackId: String, durationMs: Long?): Result<PlaybackAudioStream> {
        try {
            val clients = listOf(
                ANDROID_VR_1_43_32,
                VISIONOS,
                IOS,
                ANDROID_VR_1_61_48,
                ANDROID_VR_1_65_10,
                WEB_REMIX,
                TVHTML5_SIMPLY_EMBEDDED_PLAYER,
                TVHTML5,
                ANDROID_CREATOR,
                IPADOS,
                ANDROID_VR_NO_AUTH,
                MOBILE,
                WEB,
                WEB_CREATOR,
            )
            val unresolvedPlayers = mutableListOf<UnresolvedPlayer>()
            var webPoTokenAttempted = false
            var webPoTokens: InnertubeWebPoTokens? = null

            for (client in clients) {
                if (client.loginRequired && repository.cookie.isNullOrBlank()) continue
                if (client.useWebPoTokens && !webPoTokenAttempted) {
                    webPoTokenAttempted = true
                    val visitorData = repository.visitorData?.takeIf(String::isNotBlank)
                        ?: repository.refreshVisitorData().fold(
                            onSuccess = { it },
                            onFailure = { error ->
                                if (error is CancellationException) throw error
                                null
                            },
                        )
                    if (visitorData != null) {
                        webPoTokens = try {
                            webPoTokenProvider.generate(trackId, visitorData)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (_: Exception) {
                            null
                        }
                    }
                }
                if (client.useWebPoTokens && webPoTokens == null) continue
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
                    poToken = if (client.useWebPoTokens) webPoTokens?.playerRequest else null,
                )
                if (playerResult.isFailure) {
                    val error = playerResult.exceptionOrNull()
                    if (error is CancellationException) throw error
                    continue
                }

                val player = playerResult.getOrThrow()
                if (player.playabilityStatus.status != "OK") continue
                val streamingDataPoToken = if (client.useWebPoTokens) webPoTokens?.streamingData else null
                val candidates = player.audioCandidates(trackId, durationMs, streamingDataPoToken = streamingDataPoToken)
                if (candidates.isEmpty()) {
                    if (player.hasAudioFormats()) unresolvedPlayers += UnresolvedPlayer(player, streamingDataPoToken)
                    continue
                }

                for (candidate in candidates) {
                    if (PlatformStreamResolver.supportsStreamRange(candidate.stream.url, candidate.contentLength)) {
                        return Result.success(candidate.stream)
                    }
                }
                unresolvedPlayers += UnresolvedPlayer(player, streamingDataPoToken)
            }

            if (unresolvedPlayers.isNotEmpty()) {
                val newPipeStreams = PlatformStreamResolver.newPipePlayer(trackId).toMap()
                for ((player, streamingDataPoToken) in unresolvedPlayers) {
                    for (candidate in player.audioCandidates(trackId, durationMs, newPipeStreams, streamingDataPoToken)) {
                        if (PlatformStreamResolver.supportsStreamRange(candidate.stream.url, candidate.contentLength)) {
                            return Result.success(candidate.stream)
                        }
                    }
                }
            }

            return Result.failure(
                IllegalStateException("YouTube did not provide a complete, range-readable audio stream for $trackId"),
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            return Result.failure(error)
        }
    }

    private fun PlayerResponse.audioCandidates(
        videoId: String,
        durationMs: Long?,
        newPipeStreams: Map<Int, String> = emptyMap(),
        streamingDataPoToken: String? = null,
    ): List<AudioCandidate> = streamingData?.let { data ->
        (data.adaptiveFormats + data.formats.orEmpty())
            .asSequence()
            .filter { it.isAudio }
            .flatMap { format ->
                val urls = buildList {
                    format.url?.let(::add)
                    PlatformStreamResolver.streamUrl(format, videoId)?.let(::add)
                    newPipeStreams[format.itag]?.let(::add)
                }.distinct().map { url ->
                    if (streamingDataPoToken == null) url else url.withStreamingDataPoToken(streamingDataPoToken)
                }
                urls.asSequence()
                    .filter { it.startsWith("https://") || it.startsWith("http://") }
                    .map { url ->
                        AudioCandidate(
                            stream = PlaybackAudioStream(
                                url = url,
                                mimeType = format.mimeType,
                                durationMs = format.approxDurationMs?.toLongOrNull()?.takeIf { it > 0L } ?: durationMs,
                                bitrate = format.averageBitrate ?: format.bitrate,
                            ),
                            contentLength = format.contentLength,
                        )
                    }
            }
            .sortedWith(
                compareByDescending<AudioCandidate> { it.stream.mimeType.startsWith("audio/mp4", ignoreCase = true) }
                    .thenByDescending { it.stream.bitrate },
            )
            .toList()
    }.orEmpty()

    private fun PlayerResponse.hasAudioFormats(): Boolean = streamingData?.let { data ->
        (data.adaptiveFormats + data.formats.orEmpty()).any { it.isAudio }
    } == true

    private data class AudioCandidate(
        val stream: PlaybackAudioStream,
        val contentLength: Long?,
    )

    private data class UnresolvedPlayer(
        val player: PlayerResponse,
        val streamingDataPoToken: String?,
    )

    private fun String.withStreamingDataPoToken(token: String): String =
        if (contains("pot=")) this else "$this${if ('?' in this) '&' else '?'}pot=$token"
}
