package com.music.innertube

import com.music.innertube.models.response.PlayerResponse
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.cio.CIO

internal actual fun createInnertubeHttpClientEngine(settings: InnertubeNetworkSettings): HttpClientEngine = CIO.create()

internal actual object PlatformStreamResolver {
    actual fun signatureTimestamp(videoId: String): Result<Int> = NewPipeExtractor.getSignatureTimestamp(videoId)
    actual fun streamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? =
        NewPipeExtractor.getStreamUrl(format, videoId)
    actual fun newPipePlayer(videoId: String): List<Pair<Int, String>> = NewPipeExtractor.newPipePlayer(videoId)
}
