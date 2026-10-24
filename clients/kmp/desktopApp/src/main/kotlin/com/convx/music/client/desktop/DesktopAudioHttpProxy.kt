package com.convx.music.client.desktop

import com.music.innertube.models.YouTubeClient
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.EOFException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.UUID

/** Adapts YouTube's bounded range requests to the media engine's full-resource HTTP reads. */
internal class DesktopAudioHttpProxy : AutoCloseable {
    private val streams = object : LinkedHashMap<String, String>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean =
            size > MAX_CACHED_STREAMS
    }

    private var server: HttpServer? = null
    private var executor: ExecutorService? = null

    @Synchronized
    fun createMediaSource(streamUrl: String, mimeType: String): String {
        val httpServer = server ?: startServer().also { server = it }
        val token = UUID.randomUUID().toString()
        synchronized(streams) { streams[token] = streamUrl }
        val extension = when {
            mimeType.startsWith("audio/mp4", ignoreCase = true) -> "m4a"
            mimeType.startsWith("audio/webm", ignoreCase = true) -> "webm"
            mimeType.startsWith("video/mp4", ignoreCase = true) -> "mp4"
            else -> "audio"
        }
        return "http://127.0.0.1:${httpServer.address.port}/stream/$token.$extension"
    }

    @Synchronized
    override fun close() {
        server?.stop(0)
        server = null
        executor?.shutdownNow()
        executor = null
        synchronized(streams) { streams.clear() }
    }

    private fun startServer(): HttpServer {
        val httpServer = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        val requestExecutor = Executors.newCachedThreadPool { task ->
            Thread(task, "convx-desktop-audio-proxy").apply { isDaemon = true }
        }
        executor = requestExecutor
        httpServer.executor = requestExecutor
        httpServer.createContext("/stream/") { exchange -> handleRequest(exchange) }
        httpServer.start()
        return httpServer
    }

    private fun handleRequest(exchange: HttpExchange) {
        if (exchange.requestMethod != "GET" && exchange.requestMethod != "HEAD") {
            exchange.responseHeaders.set("Allow", "GET, HEAD")
            exchange.sendResponseHeaders(405, -1)
            exchange.close()
            return
        }

        val token = exchange.requestURI.path.substringAfterLast('/').substringBeforeLast('.')
        val sourceUrl = synchronized(streams) { streams[token] }
        if (sourceUrl == null) {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
            return
        }

        var headersSent = false
        try {
            if (exchange.requestMethod == "HEAD") {
                writeHeadResponse(exchange, sourceUrl) { headersSent = true }
            } else {
                val range = exchange.requestHeaders.getFirst("Range")
                if (range == null) {
                    streamWholeResource(exchange, sourceUrl) { headersSent = true }
                } else {
                    forwardRange(exchange, sourceUrl, range) { headersSent = true }
                }
            }
        } catch (_: Exception) {
            if (!headersSent) {
                runCatching { exchange.sendResponseHeaders(502, -1) }
            }
        } finally {
            exchange.close()
        }
    }

    private fun writeHeadResponse(exchange: HttpExchange, sourceUrl: String, onHeadersSent: () -> Unit) {
        val connection = openRange(sourceUrl, "bytes=0-$METADATA_RANGE_END")
        try {
            val status = connection.responseCode
            val contentRange = connection.getHeaderField("Content-Range")?.let(::parseContentRange)
            copyMetadata(connection, exchange)
            if (status >= 400) {
                exchange.sendResponseHeaders(status, -1)
                onHeadersSent()
                return
            }
            val totalLength = contentRange?.totalLength
                ?: connection.getHeaderFieldLong("Content-Length", -1L)
            if (totalLength >= 0L) exchange.responseHeaders.set("Content-Length", totalLength.toString())
            exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, -1)
            onHeadersSent()
        } finally {
            connection.disconnect()
        }
    }

    private fun forwardRange(exchange: HttpExchange, sourceUrl: String, requestedRange: String, onHeadersSent: () -> Unit) {
        val range = parseRequestedRange(requestedRange)
        if (range == null) {
            exchange.sendResponseHeaders(416, -1)
            onHeadersSent()
            return
        }
        if (range.end == null) {
            forwardOpenEndedRange(exchange, sourceUrl, range.start, onHeadersSent)
            return
        }
        val connection = openRange(sourceUrl, range.toHeaderValue())
        try {
            val status = connection.responseCode
            val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
            copyMetadata(connection, exchange)
            connection.getHeaderField("Content-Range")?.let { exchange.responseHeaders.set("Content-Range", it) }
            exchange.requestHeaders.getFirst("If-Range")?.let { connection.setRequestProperty("If-Range", it) }
            exchange.sendResponseHeaders(status, responseLength(status, contentLength, connection.getHeaderField("Content-Range")))
            onHeadersSent()
            val source = responseBody(connection, status) ?: return
            runCatching { source.use { input -> exchange.responseBody.use { output -> input.copyTo(output) } } }
        } finally {
            connection.disconnect()
        }
    }

    private fun forwardOpenEndedRange(
        exchange: HttpExchange,
        sourceUrl: String,
        start: Long,
        onHeadersSent: () -> Unit,
    ) {
        var offset = start
        var totalLength: Long? = null
        var firstConnection: HttpURLConnection? = null
        try {
            while (totalLength == null || offset < totalLength) {
                val end = (offset + CHUNK_SIZE_BYTES - 1L).let { requestedEnd ->
                    totalLength?.let { minOf(requestedEnd, it - 1L) } ?: requestedEnd
                }
                val connection = firstConnection?.also { firstConnection = null }
                    ?: openRange(sourceUrl, "bytes=$offset-$end")
                try {
                    val status = connection.responseCode
                    if (status >= 400) {
                        if (offset > start) throw IllegalStateException("Audio stream range request failed.")
                        copyMetadata(connection, exchange)
                        exchange.sendResponseHeaders(status, responseLength(status, connection.getHeaderFieldLong("Content-Length", -1L)))
                        onHeadersSent()
                        responseBody(connection, status)?.use { input -> exchange.responseBody.use { output -> input.copyTo(output) } }
                        return
                    }

                    if (status == HttpURLConnection.HTTP_OK && offset == start) {
                        copyMetadata(connection, exchange)
                        exchange.responseHeaders.remove("Accept-Ranges")
                        val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
                        if (contentLength >= 0L) exchange.responseHeaders.set("Content-Length", contentLength.toString())
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, contentLength.coerceAtLeast(0L))
                        onHeadersSent()
                        connection.inputStream.use { input ->
                            exchange.responseBody.use { output -> input.copyTo(output) }
                        }
                        return
                    }

                    val contentRange = connection.getHeaderField("Content-Range")?.let(::parseContentRange)
                        ?: throw IllegalStateException("Audio server omitted its content range.")
                    if (status != HttpURLConnection.HTTP_PARTIAL || contentRange.start != offset) {
                        throw IllegalStateException("Audio server returned an unsupported range response.")
                    }
                    if (totalLength == null) {
                        totalLength = contentRange.totalLength
                        if (totalLength <= start) throw IllegalStateException("Audio server omitted the stream length.")
                        copyMetadata(connection, exchange)
                        exchange.responseHeaders.set("Content-Range", "bytes $start-${totalLength - 1L}/$totalLength")
                        exchange.responseHeaders.set("Content-Length", (totalLength - start).toString())
                        exchange.responseHeaders.set("Accept-Ranges", "bytes")
                        exchange.sendResponseHeaders(HttpURLConnection.HTTP_PARTIAL, totalLength - start)
                        onHeadersSent()
                    } else if (contentRange.totalLength != totalLength) {
                        throw IllegalStateException("Audio stream length changed while reading.")
                    }

                    val expectedBytes = (contentRange.end - contentRange.start + 1L)
                        .coerceAtMost(totalLength!! - offset)
                    if (expectedBytes <= 0L) throw EOFException("Audio server returned an empty range.")
                    connection.inputStream.use { input -> copyExactly(input, exchange.responseBody, expectedBytes) }
                    offset += expectedBytes
                } finally {
                    if (connection !== firstConnection) connection.disconnect()
                }
            }
            exchange.responseBody.flush()
        } finally {
            firstConnection?.disconnect()
        }
    }

    /** Fetches bounded upstream ranges because Googlevideo rejects unbounded GETs from desktop media stacks. */
    private fun streamWholeResource(exchange: HttpExchange, sourceUrl: String, onHeadersSent: () -> Unit) {
        var offset = 0L
        var totalLength: Long? = null
        while (totalLength == null || offset < totalLength) {
            val requestedEnd = (offset + CHUNK_SIZE_BYTES - 1L).let { end ->
                totalLength?.let { minOf(end, it - 1L) } ?: end
            }
            val connection = openRange(sourceUrl, "bytes=$offset-$requestedEnd")
            try {
                val status = connection.responseCode
                val rawContentRange = connection.getHeaderField("Content-Range")
                val contentRange = rawContentRange?.let(::parseContentRange)
                if (status >= 400) {
                    if (offset > 0L) throw IllegalStateException("Audio stream range request failed.")
                    copyMetadata(connection, exchange)
                    exchange.sendResponseHeaders(status, responseLength(status, connection.getHeaderFieldLong("Content-Length", -1L)))
                    onHeadersSent()
                    responseBody(connection, status)?.use { input -> exchange.responseBody.use { output -> input.copyTo(output) } }
                    return
                }

                if (status == HttpURLConnection.HTTP_OK && offset == 0L) {
                    copyMetadata(connection, exchange)
                    exchange.responseHeaders.remove("Accept-Ranges")
                    val contentLength = connection.getHeaderFieldLong("Content-Length", -1L)
                    if (contentLength >= 0L) exchange.responseHeaders.set("Content-Length", contentLength.toString())
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, contentLength.coerceAtLeast(0L))
                    onHeadersSent()
                    connection.inputStream.use { input ->
                        exchange.responseBody.use { output -> input.copyTo(output) }
                    }
                    return
                }

                if (status != HttpURLConnection.HTTP_PARTIAL || contentRange == null || contentRange.start != offset) {
                    throw IllegalStateException("Audio server returned an unsupported range response.")
                }
                if (totalLength == null) {
                    totalLength = contentRange.totalLength
                    if (totalLength <= 0L) throw IllegalStateException("Audio server omitted the stream length.")
                    copyMetadata(connection, exchange)
                    exchange.responseHeaders.set("Content-Length", totalLength.toString())
                    exchange.responseHeaders.set("Accept-Ranges", "bytes")
                    exchange.sendResponseHeaders(HttpURLConnection.HTTP_OK, totalLength)
                    onHeadersSent()
                } else if (contentRange.totalLength != totalLength) {
                    throw IllegalStateException("Audio stream length changed while reading.")
                }

                val expectedBytes = (contentRange.end - contentRange.start + 1L)
                    .coerceAtMost(totalLength - offset)
                if (expectedBytes <= 0L) throw EOFException("Audio server returned an empty range.")
                val source = connection.inputStream
                source.use { input -> copyExactly(input, exchange.responseBody, expectedBytes) }
                offset += expectedBytes
                if (contentRange.end + 1L < offset) throw EOFException("Audio server returned an incomplete range.")
            } finally {
                connection.disconnect()
            }
        }
        exchange.responseBody.flush()
    }

    private fun openRange(sourceUrl: String, range: String): HttpURLConnection =
        (URI.create(sourceUrl).toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECTION_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", YouTubeClient.USER_AGENT_WEB)
            setRequestProperty("Referer", MUSIC_REFERER)
            setRequestProperty("Origin", MUSIC_ORIGIN)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Encoding", "identity")
            setRequestProperty("Range", range)
        }

    private fun copyMetadata(connection: HttpURLConnection, exchange: HttpExchange) {
        listOf("Content-Type", "Accept-Ranges", "Cache-Control", "ETag", "Last-Modified").forEach { name ->
            connection.getHeaderField(name)?.let { exchange.responseHeaders.set(name, it) }
        }
    }

    private fun responseBody(connection: HttpURLConnection, status: Int): InputStream? = when {
        status == HttpURLConnection.HTTP_NO_CONTENT || status == HttpURLConnection.HTTP_NOT_MODIFIED -> null
        status >= 400 -> connection.errorStream
        else -> connection.inputStream
    }

    private fun responseLength(status: Int, contentLength: Long, contentRangeHeader: String? = null): Long = when {
        status == HttpURLConnection.HTTP_NO_CONTENT || status == HttpURLConnection.HTTP_NOT_MODIFIED -> -1L
        contentLength >= 0L -> contentLength
        contentRangeHeader != null -> contentRangeHeader.let(::parseContentRange)?.let { it.end - it.start + 1L } ?: 0L
        else -> 0L
    }

    private fun parseRequestedRange(value: String): RequestedRange? {
        val match = REQUESTED_RANGE_PATTERN.matchEntire(value.trim()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        if (start < 0L) return null
        val requestedEnd = match.groupValues[2].takeIf(String::isNotEmpty)?.toLongOrNull()
        if (requestedEnd != null && requestedEnd < start) return null
        val end = requestedEnd?.let { minOf(it, start + CHUNK_SIZE_BYTES - 1L) }
        return RequestedRange(start, end)
    }

    private data class RequestedRange(val start: Long, val end: Long?) {
        fun toHeaderValue(): String = "bytes=$start-${end ?: ""}"
    }

    private fun copyExactly(input: InputStream, output: java.io.OutputStream, expectedBytes: Long) {
        val buffer = ByteArray(COPY_BUFFER_BYTES)
        var remaining = expectedBytes
        while (remaining > 0L) {
            val count = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (count < 0) throw EOFException("Audio stream ended before its advertised range.")
            output.write(buffer, 0, count)
            remaining -= count
        }
        output.flush()
    }

    private fun parseContentRange(value: String): ContentRange? {
        val match = CONTENT_RANGE_PATTERN.matchEntire(value.trim()) ?: return null
        return ContentRange(
            start = match.groupValues[1].toLongOrNull() ?: return null,
            end = match.groupValues[2].toLongOrNull() ?: return null,
            totalLength = match.groupValues[3].toLongOrNull() ?: return null,
        )
    }

    private data class ContentRange(val start: Long, val end: Long, val totalLength: Long)

    private companion object {
        const val MAX_CACHED_STREAMS = 64
        const val CHUNK_SIZE_BYTES = 512 * 1024
        const val COPY_BUFFER_BYTES = 64 * 1024
        const val METADATA_RANGE_END = 1_023
        const val CONNECTION_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 60_000
        const val MUSIC_REFERER = "https://music.youtube.com/"
        const val MUSIC_ORIGIN = "https://music.youtube.com"
        val CONTENT_RANGE_PATTERN = Regex("bytes (\\d+)-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
        val REQUESTED_RANGE_PATTERN = Regex("bytes=(\\d+)-(\\d*)", RegexOption.IGNORE_CASE)
    }
}
