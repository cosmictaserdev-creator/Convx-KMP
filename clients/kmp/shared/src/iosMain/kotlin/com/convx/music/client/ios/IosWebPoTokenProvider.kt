@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.convx.music.client.ios

import com.convx.music.client.shared.generated.resources.Res
import com.music.innertube.InnertubeWebPoTokenProvider
import com.music.innertube.InnertubeWebPoTokens
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.jetbrains.compose.resources.ExperimentalResourceApi
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSData
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSMutableURLRequest
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSURL
import platform.Foundation.NSURLSession
import platform.Foundation.NSURLSessionDataTask
import platform.Foundation.NSUTF8StringEncoding
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKScriptMessage
import platform.WebKit.WKScriptMessageHandlerProtocol
import platform.WebKit.WKUserContentController
import platform.WebKit.WKWebView
import platform.WebKit.WKWebViewConfiguration
import platform.WebKit.WKWebsiteDataStore
import platform.darwin.NSObject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/** WKWebView implementation of the BotGuard token contract used by the shared stream resolver. */
internal class IosWebPoTokenProvider : InnertubeWebPoTokenProvider {
    private val mutex = Mutex()
    private var tokenSession: IosBotGuardSession? = null

    override suspend fun generate(videoId: String, visitorData: String): InnertubeWebPoTokens? {
        if (videoId.isBlank() || visitorData.isBlank()) return null

        return mutex.withLock {
            try {
                var activeSession = tokenSession
                if (activeSession == null || activeSession.visitorData != visitorData || activeSession.isExpired) {
                    activeSession?.close()
                    activeSession = IosBotGuardSession(visitorData).also {
                        tokenSession = it
                        it.initialize()
                    }
                }

                val streamingToken = activeSession.streamingToken
                    ?: activeSession.generateToken(visitorData).also { activeSession.streamingToken = it }
                val playerToken = activeSession.generateToken(videoId)
                InnertubeWebPoTokens(playerRequest = playerToken, streamingData = streamingToken)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                println("iOS BotGuard token generation failed: ${error.message}")
                tokenSession?.close()
                tokenSession = null
                null
            }
        }
    }

    fun close() {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate).launch {
            mutex.withLock {
                tokenSession?.close()
                tokenSession = null
            }
        }
    }
}

private class IosBotGuardSession(val visitorData: String) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val initialized = CompletableDeferred<Unit>()
    private val tokenRequests = mutableMapOf<Int, CompletableDeferred<String>>()
    private var nextRequestId = 0
    private var webView: WKWebView? = null
    private var navigationDelegate: IosPoTokenNavigationDelegate? = null
    private var messageHandler: IosPoTokenMessageHandler? = null
    private var expiresAtMillis = Long.MAX_VALUE
    var streamingToken: String? = null

    val isExpired: Boolean
        get() = currentTimeMillis() >= expiresAtMillis

    @OptIn(ExperimentalResourceApi::class)
    suspend fun initialize() {
        val html = Res.readBytes("files/po_token.html").decodeToString()
        withContext(Dispatchers.Main.immediate) {
            val contentController = WKUserContentController()
            val handler = IosPoTokenMessageHandler(this)
            val delegate = IosPoTokenNavigationDelegate(this)
            val configuration = WKWebViewConfiguration().apply {
                userContentController = contentController
                websiteDataStore = WKWebsiteDataStore.nonPersistentDataStore()
            }
            val view = WKWebView(
                frame = CGRectMake(0.0, 0.0, 1.0, 1.0),
                configuration = configuration,
            )
            contentController.addScriptMessageHandler(handler, name = BRIDGE_NAME)
            view.navigationDelegate = delegate
            webView = view
            navigationDelegate = delegate
            messageHandler = handler
            val baseUrl = NSURL.URLWithString(YOUTUBE_ORIGIN) ?: error("Invalid YouTube base URL.")
            view.loadHTMLString(htmlString = html, baseURL = baseUrl)
        }
        withTimeout(INITIALIZATION_TIMEOUT_MS) { initialized.await() }
    }

    suspend fun generateToken(identifier: String): String {
        withTimeout(INITIALIZATION_TIMEOUT_MS) { initialized.await() }
        val requestId = ++nextRequestId
        val result = CompletableDeferred<String>()
        val bytes = identifier.encodeToByteArray().joinToString(",") { (it.toInt() and 0xff).toString() }

        withContext(Dispatchers.Main.immediate) {
            tokenRequests[requestId] = result
            evaluateJavascript(
                """try {
                    obtainPoToken(new Uint8Array([$bytes])).then(function(token) {
                        window.webkit.messageHandlers.$BRIDGE_NAME.postMessage({
                            type: 'token', requestId: $requestId, bytes: Array.from(token).join(',')
                        });
                    }).catch(function(error) {
                        window.webkit.messageHandlers.$BRIDGE_NAME.postMessage({
                            type: 'tokenError', requestId: $requestId, error: String(error)
                        });
                    });
                } catch (error) {
                    window.webkit.messageHandlers.$BRIDGE_NAME.postMessage({
                        type: 'tokenError', requestId: $requestId, error: String(error)
                    });
                }""",
            )
        }

        try {
            return withTimeout(TOKEN_TIMEOUT_MS) { result.await() }
        } finally {
            withContext(Dispatchers.Main.immediate) { tokenRequests.remove(requestId) }
        }
    }

    suspend fun startBotGuard() {
        scope.launch {
            runCatching {
                val challenge = requestBotGuard(CREATE_URL, "[ \"$REQUEST_KEY\" ]")
                val payload = parseChallengeData(challenge)
                evaluateJavascript(
                    """try {
                        window.data = $payload;
                        runBotGuard(window.data).then(function(result) {
                            window.webPoSignalOutput = result.webPoSignalOutput;
                            window.webkit.messageHandlers.$BRIDGE_NAME.postMessage({
                                type: 'botguard', response: result.botguardResponse
                            });
                        }, function(error) {
                            window.webkit.messageHandlers.$BRIDGE_NAME.postMessage({type: 'initError', error: String(error)});
                        });
                    } catch (error) {
                        window.webkit.messageHandlers.$BRIDGE_NAME.postMessage({type: 'initError', error: String(error)});
                    }""",
                )
            }.onFailure { failInitialization("Could not load BotGuard challenge: ${it.message}") }
        }
    }

    fun onBotGuardResult(botguardResponse: String) {
        scope.launch {
            runCatching {
                val response = requestBotGuard(
                    GENERATE_IT_URL,
                    "[ \"$REQUEST_KEY\", ${JsonPrimitive(botguardResponse)} ]",
                )
                val (integrityToken, lifetimeSeconds) = parseIntegrityTokenData(response)
                expiresAtMillis = currentTimeMillis() +
                    (lifetimeSeconds - TOKEN_EXPIRY_SAFETY_SECONDS)
                        .coerceAtLeast(MIN_TOKEN_LIFETIME_SECONDS) * 1_000L
                evaluateJavascript(
                    """try {
                        window.integrityToken = $integrityToken;
                        createPoTokenMinter(window.webPoSignalOutput, window.integrityToken).then(function() {
                            window.webkit.messageHandlers.$BRIDGE_NAME.postMessage({type: 'ready'});
                        }).catch(function(error) {
                            window.webkit.messageHandlers.$BRIDGE_NAME.postMessage({type: 'initError', error: String(error)});
                        });
                    } catch (error) {
                        window.webkit.messageHandlers.$BRIDGE_NAME.postMessage({type: 'initError', error: String(error)});
                    }""",
                )
            }.onFailure { failInitialization("Could not create the BotGuard token minter: ${it.message}") }
        }
    }

    fun onToken(requestId: Int, bytes: String) {
        val value = runCatching {
            val raw = bytes.split(',').map { item ->
                item.trim().toInt().also { require(it in 0..255) }.toByte()
            }.toByteArray()
            raw.toNSData().base64EncodedStringWithOptions(0uL).replace('+', '-').replace('/', '_')
        }
        value.fold(
            onSuccess = { tokenRequests[requestId]?.complete(it) },
            onFailure = { tokenRequests[requestId]?.completeExceptionally(it) },
        )
    }

    fun onTokenError(requestId: Int, message: String) {
        tokenRequests[requestId]?.completeExceptionally(IllegalStateException(message.take(MAX_ERROR_LENGTH)))
    }

    fun onReady() {
        initialized.complete(Unit)
    }

    fun onPageLoaded() {
        scope.launch { startBotGuard() }
    }

    fun onInitializationError(message: String) {
        scope.launch { failInitialization(message) }
    }

    suspend fun failInitialization(message: String) {
        val error = IllegalStateException(message.take(MAX_ERROR_LENGTH))
        initialized.completeExceptionally(error)
        withContext(Dispatchers.Main.immediate) {
            tokenRequests.values.forEach { it.completeExceptionally(error) }
        }
    }

    suspend fun evaluateJavascript(script: String) {
        withContext(Dispatchers.Main.immediate) {
            val view = checkNotNull(webView) { "BotGuard web view has been closed." }
            view.evaluateJavaScript(script, completionHandler = { _, error ->
                if (error != null) {
                    scope.launch { failInitialization("BotGuard JavaScript failed: ${error.localizedDescription}") }
                }
            })
        }
    }

    suspend fun close() {
        scope.cancel()
        withContext(Dispatchers.Main.immediate) {
            val cancellation = CancellationException("BotGuard session closed.")
            initialized.completeExceptionally(cancellation)
            tokenRequests.values.forEach { it.completeExceptionally(cancellation) }
            tokenRequests.clear()
            webView?.let { view ->
                view.stopLoading()
                view.navigationDelegate = null
                view.configuration.userContentController.removeScriptMessageHandlerForName(BRIDGE_NAME)
                view.loadHTMLString(htmlString = "", baseURL = null)
            }
            webView = null
            navigationDelegate = null
            messageHandler = null
        }
    }

    private suspend fun requestBotGuard(url: String, body: String): String =
        suspendCancellableCoroutine { continuation ->
            val nativeUrl = NSURL.URLWithString(url)
                ?: return@suspendCancellableCoroutine continuation.resumeWithException(
                    IllegalArgumentException("Invalid BotGuard endpoint."),
                )
            val request = NSMutableURLRequest.requestWithURL(nativeUrl).apply {
                HTTPMethod = "POST"
                setValue(USER_AGENT, forHTTPHeaderField = "User-Agent")
                setValue("application/json", forHTTPHeaderField = "Accept")
                setValue("application/json+protobuf", forHTTPHeaderField = "Content-Type")
                setValue(GOOGLE_API_KEY, forHTTPHeaderField = "x-goog-api-key")
                setValue("grpc-web-javascript/0.1", forHTTPHeaderField = "x-user-agent")
                HTTPBody = body.encodeToByteArray().toNSData()
            }
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
                if (continuation.isCancelled) return@dataTaskWithRequest
                if (error != null) {
                    continuation.resumeWithException(IllegalStateException(error.localizedDescription))
                    return@dataTaskWithRequest
                }
                val status = (response as? NSHTTPURLResponse)?.statusCode
                val responseBody = data?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }.orEmpty()
                if (status != 200L) {
                    continuation.resumeWithException(IllegalStateException("BotGuard request returned HTTP $status."))
                } else {
                    continuation.resume(responseBody)
                }
            }
            continuation.invokeOnCancellation { task.cancel() }
            task.resume()
        }

    private fun parseChallengeData(raw: String): String {
        val scrambled = Json.parseToJsonElement(raw).jsonArray
        val challenge = if (scrambled.size > 1 && (scrambled[1] as? JsonPrimitive)?.isString == true) {
            Json.parseToJsonElement(descramble(scrambled[1].jsonPrimitive.content)).jsonArray
        } else {
            scrambled[0].jsonArray
        }
        val safeScript = (challenge[1] as? JsonArray)
            ?.firstOrNull { (it as? JsonPrimitive)?.isString == true } ?: JsonNull
        val trustedResourceUrl = (challenge[2] as? JsonArray)
            ?.firstOrNull { (it as? JsonPrimitive)?.isString == true } ?: JsonNull
        return Json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "messageId" to challenge[0],
                    "interpreterJavascript" to JsonObject(
                        mapOf(
                            "privateDoNotAccessOrElseSafeScriptWrappedValue" to safeScript,
                            "privateDoNotAccessOrElseTrustedResourceUrlWrappedValue" to trustedResourceUrl,
                        ),
                    ),
                    "interpreterHash" to challenge[3],
                    "program" to challenge[4],
                    "globalName" to challenge[5],
                    "clientExperimentsStateBlob" to challenge[7],
                ),
            ),
        )
    }

    private fun parseIntegrityTokenData(raw: String): Pair<String, Long> {
        val fields = Json.parseToJsonElement(raw).jsonArray
        val bytes = decodeBase64(fields[0].jsonPrimitive.content)
        val token = "new Uint8Array([" + bytes.joinToString(",") { it.toUByte().toString() } + "])"
        return token to fields[1].jsonPrimitive.long
    }

    private fun descramble(value: String): String =
        decodeBase64(value).map { (it + 97).toByte() }.toByteArray().decodeToString()

    private fun decodeBase64(value: String): ByteArray {
        val normalized = value.replace('-', '+').replace('_', '/').replace('.', '=')
        val data = NSData.create(base64EncodedString = normalized, options = 0uL)
            ?: error("Could not decode BotGuard challenge data.")
        return data.toByteArray()
    }

    private companion object {
        const val BRIDGE_NAME = "convxPoToken"
        const val YOUTUBE_ORIGIN = "https://www.youtube.com"
        const val INITIALIZATION_TIMEOUT_MS = 60_000L
        const val TOKEN_TIMEOUT_MS = 30_000L
        const val TOKEN_EXPIRY_SAFETY_SECONDS = 10 * 60L
        const val MIN_TOKEN_LIFETIME_SECONDS = 60L
        const val MAX_ERROR_LENGTH = 500
        const val REQUEST_KEY = "O43z0dpjhgX20SCx4KAo"
        const val GOOGLE_API_KEY = "AIzaSyDyT5W0Jh49F30Pqqtyfdf7pDLFKLJoAnw"
        const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.3"
        const val CREATE_URL = "https://www.youtube.com/api/jnn/v1/Create"
        const val GENERATE_IT_URL = "https://www.youtube.com/api/jnn/v1/GenerateIT"
    }
}

private class IosPoTokenNavigationDelegate(
    private val session: IosBotGuardSession,
) : NSObject(), WKNavigationDelegateProtocol {
    private var didStart = false

    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        if (didStart) return
        didStart = true
        session.onPageLoaded()
    }
}

private class IosPoTokenMessageHandler(
    private val session: IosBotGuardSession,
) : NSObject(), WKScriptMessageHandlerProtocol {
    override fun userContentController(userContentController: WKUserContentController, didReceiveScriptMessage: WKScriptMessage) {
        val body = didReceiveScriptMessage.body as? platform.Foundation.NSDictionary ?: return
        fun string(key: String): String? = body.objectForKey(key) as? String
        fun integer(key: String): Int? = (body.objectForKey(key) as? NSNumber)?.intValue

        when (string("type")) {
            "botguard" -> string("response")?.let(session::onBotGuardResult)
            "ready" -> session.onReady()
            "token" -> {
                val requestId = integer("requestId")
                val bytes = string("bytes")
                if (requestId != null && bytes != null) session.onToken(requestId, bytes)
            }
            "tokenError" -> {
                val requestId = integer("requestId")
                if (requestId != null) session.onTokenError(requestId, string("error").orEmpty())
            }
            "initError" -> session.onInitializationError(
                string("error") ?: "BotGuard reported an initialization error.",
            )
        }
    }
}

private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
    NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
}

private fun NSData.toByteArray(): ByteArray {
    if (length == 0uL) return ByteArray(0)
    val pointer = bytes?.reinterpret<ByteVar>() ?: error("BotGuard returned empty binary data.")
    return pointer.readBytes(length.toInt())
}

private fun currentTimeMillis(): Long = (platform.Foundation.NSDate().timeIntervalSince1970 * 1_000.0).toLong()
