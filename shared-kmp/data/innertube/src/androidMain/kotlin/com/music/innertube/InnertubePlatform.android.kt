package com.music.innertube

import com.music.innertube.models.IpVersion
import com.music.innertube.models.response.PlayerResponse
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.Protocol
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.Proxy
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

internal actual fun createInnertubeHttpClientEngine(settings: InnertubeNetworkSettings): HttpClientEngine =
    OkHttp.create {
        config {
            connectionPool(ConnectionPool(10, 5, TimeUnit.MINUTES))
            connectTimeout(30, TimeUnit.SECONDS)
            readTimeout(60, TimeUnit.SECONDS)
            writeTimeout(60, TimeUnit.SECONDS)
            protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            retryOnConnectionFailure(false)
            dns(object : Dns {
                override fun lookup(hostname: String): List<InetAddress> {
                    val addresses = Dns.SYSTEM.lookup(hostname)
                    return when (settings.ipVersion) {
                        IpVersion.IPV4 -> addresses.filterIsInstance<Inet4Address>().ifEmpty { addresses }
                        IpVersion.IPV6 -> addresses.filterIsInstance<Inet6Address>().ifEmpty { addresses }
                        IpVersion.AUTO -> addresses
                    }
                }
            })
            settings.proxy?.let { proxy ->
                proxy(proxy.toJavaProxy())
            }
            settings.proxyAuth?.let { authorization ->
                proxyAuthenticator { _, response ->
                    response.request.newBuilder()
                        .header("Proxy-Authorization", authorization)
                        .build()
                }
            }
        }
    }

internal actual object PlatformStreamResolver {
    actual fun signatureTimestamp(videoId: String): Result<Int> = NewPipeExtractor.getSignatureTimestamp(videoId)
    actual fun streamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? =
        NewPipeExtractor.getStreamUrl(format, videoId)
    actual fun newPipePlayer(videoId: String): List<Pair<Int, String>> = NewPipeExtractor.newPipePlayer(videoId)

    actual suspend fun supportsStreamRange(url: String, contentLength: Long?): Boolean = withContext(Dispatchers.IO) {
        val offset = contentLength?.let { minOf(RANGE_PROBE_OFFSET, (it - RANGE_PROBE_LENGTH).coerceAtLeast(0L)) }
            ?: RANGE_PROBE_OFFSET
        runCatching {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.connectTimeout = RANGE_PROBE_TIMEOUT_MS
            connection.readTimeout = RANGE_PROBE_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", com.music.innertube.models.YouTubeClient.USER_AGENT_WEB)
            connection.setRequestProperty("Referer", "https://music.youtube.com/")
            connection.setRequestProperty("Origin", "https://music.youtube.com")
            connection.setRequestProperty("Accept", "*/*")
            connection.setRequestProperty("Accept-Encoding", "identity")
            connection.setRequestProperty("Range", "bytes=$offset-${offset + RANGE_PROBE_LENGTH - 1L}")
            try {
                connection.responseCode == HttpURLConnection.HTTP_PARTIAL &&
                    connection.getHeaderField("Content-Range")?.startsWith("bytes $offset-") == true
            } finally {
                connection.disconnect()
            }
        }.getOrDefault(false)
    }

    private const val RANGE_PROBE_OFFSET = 1_572_864L
    private const val RANGE_PROBE_LENGTH = 1_024L
    private const val RANGE_PROBE_TIMEOUT_MS = 5_000
}
