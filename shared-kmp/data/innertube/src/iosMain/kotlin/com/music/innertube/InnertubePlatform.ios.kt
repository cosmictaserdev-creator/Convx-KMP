package com.music.innertube

import com.music.innertube.models.response.PlayerResponse
import com.music.innertube.models.YouTubeClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CancellationException

internal actual fun createInnertubeHttpClientEngine(settings: InnertubeNetworkSettings): HttpClientEngine = Darwin.create()

internal actual object PlatformStreamResolver {
    actual fun signatureTimestamp(videoId: String): Result<Int> =
        Result.failure(UnsupportedOperationException("iOS stream signature decoding is not connected yet"))
    actual fun streamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? = format.url
    actual fun newPipePlayer(videoId: String): List<Pair<Int, String>> = emptyList()

    actual suspend fun supportsStreamRange(url: String, contentLength: Long?): Boolean {
        val offset = contentLength?.let { minOf(RANGE_PROBE_OFFSET, (it - RANGE_PROBE_LENGTH).coerceAtLeast(0L)) }
            ?: RANGE_PROBE_OFFSET
        val client = HttpClient(Darwin.create())
        return try {
            val response = client.get(url) {
                header("User-Agent", YouTubeClient.USER_AGENT_WEB)
                header("Referer", "https://music.youtube.com/")
                header("Origin", "https://music.youtube.com")
                header("Accept", "*/*")
                header("Accept-Encoding", "identity")
                header("Range", "bytes=$offset-${offset + RANGE_PROBE_LENGTH - 1L}")
            }
            response.status == HttpStatusCode.PartialContent &&
                response.headers["Content-Range"]?.startsWith("bytes $offset-") == true
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            false
        } finally {
            client.close()
        }
    }

    private const val RANGE_PROBE_OFFSET = 1_572_864L
    private const val RANGE_PROBE_LENGTH = 1_024L
}
