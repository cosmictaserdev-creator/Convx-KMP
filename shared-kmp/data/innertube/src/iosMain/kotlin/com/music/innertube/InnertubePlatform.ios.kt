package com.music.innertube

import com.music.innertube.models.response.PlayerResponse
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.darwin.Darwin

internal actual fun createInnertubeHttpClientEngine(settings: InnertubeNetworkSettings): HttpClientEngine = Darwin.create()

internal actual object PlatformStreamResolver {
    actual fun signatureTimestamp(videoId: String): Result<Int> =
        Result.failure(UnsupportedOperationException("iOS stream signature decoding is not connected yet"))
    actual fun streamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? = format.url
    actual fun newPipePlayer(videoId: String): List<Pair<Int, String>> = emptyList()
}
