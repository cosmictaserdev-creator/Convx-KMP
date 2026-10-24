package com.convx.music.client.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import javafx.application.Platform
import javafx.concurrent.Worker
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.URI
import java.util.concurrent.atomic.AtomicBoolean

@Composable
internal fun DesktopSignInDialog(
    onComplete: (cookie: String, visitorData: String, dataSyncId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val status = remember { mutableStateOf("Complete sign-in in the window below.") }
    val signInPanel = remember {
        DesktopSignInPanel(
            onStatusChanged = { status.value = it },
            onComplete = onComplete,
        )
    }
    DisposableEffect(signInPanel) {
        onDispose(signInPanel::close)
    }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(Color(0xF219191D), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("Sign in to YouTube Music", style = TextStyle(color = Color.White))
                BasicText("Cancel", style = TextStyle(color = Color.White), modifier = Modifier.clickable(onClick = onDismiss))
            }
            SwingPanel(
                modifier = Modifier.fillMaxWidth().height(640.dp),
                factory = signInPanel::createView,
            )
            BasicText(status.value, style = TextStyle(color = Color.White.copy(alpha = 0.72f)))
        }
    }
}

internal fun clearDesktopWebCookies() {
    DesktopWebCookieHandler.clear()
}

private class DesktopSignInPanel(
    private val onStatusChanged: (String) -> Unit,
    private val onComplete: (cookie: String, visitorData: String, dataSyncId: String?) -> Unit,
) {
    private val completed = AtomicBoolean(false)
    @Volatile private var engine: javafx.scene.web.WebEngine? = null
    @Volatile private var closed = false

    fun createView(): JFXPanel = JFXPanel().also { panel ->
        DesktopWebCookieHandler.install()
        Platform.runLater {
            if (closed) return@runLater
            val webView = WebView()
            val webEngine = webView.engine
            engine = webEngine
            webEngine.loadWorker.stateProperty().addListener { _, _, state ->
                if (state != Worker.State.SUCCEEDED || closed || completed.get()) return@addListener
                if (!webEngine.location.startsWith(MUSIC_URL)) return@addListener

                val visitorData = webEngine.executeScript(READ_VISITOR_DATA).toString().takeUnless { it == "null" }.orEmpty()
                val dataSyncId = webEngine.executeScript(READ_DATA_SYNC_ID).toString().takeUnless { it == "null" }.orEmpty()
                val cookie = DesktopWebCookieHandler.youtubeCookieHeader()
                if (visitorData.isBlank() || !cookie.hasYouTubeAccountSession()) {
                    onStatusChanged("YouTube did not provide a usable session yet. Finish sign-in and wait for Music to load.")
                    return@addListener
                }
                if (completed.compareAndSet(false, true)) {
                    onStatusChanged("Signed in. Saving the account securely…")
                    onComplete(cookie, visitorData, dataSyncId.takeIf(String::isNotBlank))
                }
            }
            panel.scene = Scene(webView)
            webEngine.load(LOGIN_URL)
        }
    }

    fun close() {
        closed = true
        Platform.runLater {
            engine?.load(null)
            engine = null
            DesktopWebCookieHandler.clear()
        }
    }
}

private object DesktopWebCookieHandler {
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private var previousHandler: CookieHandler? = null
    private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        previousHandler = CookieHandler.getDefault()
        CookieHandler.setDefault(cookieManager)
        installed = true
    }

    @Synchronized
    fun youtubeCookieHeader(): String = sequenceOf(MUSIC_URI, GOOGLE_URI)
        .flatMap { uri -> cookieManager.cookieStore.get(uri).asSequence() }
        .distinctBy(HttpCookie::getName)
        .joinToString("; ") { cookie -> "${cookie.name}=${cookie.value}" }

    @Synchronized
    fun clear() {
        cookieManager.cookieStore.removeAll()
        if (installed) {
            CookieHandler.setDefault(previousHandler)
            previousHandler = null
            installed = false
        }
    }
}

private fun String.hasYouTubeAccountSession(): Boolean = split(';').any { part ->
    part.trim().substringBefore('=') in AUTHENTICATED_COOKIE_NAMES
}

private const val LOGIN_URL = "https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com"
private const val MUSIC_URL = "https://music.youtube.com"
private val MUSIC_URI = URI(MUSIC_URL)
private val GOOGLE_URI = URI("https://accounts.google.com")
private const val READ_VISITOR_DATA = "(window.yt&&window.yt.config_&&window.yt.config_.VISITOR_DATA)||''"
private const val READ_DATA_SYNC_ID = "(window.yt&&window.yt.config_&&window.yt.config_.DATASYNC_ID)||''"
private val AUTHENTICATED_COOKIE_NAMES = setOf(
    "SID", "HSID", "SSID", "APISID", "SAPISID", "__Secure-1PSID", "__Secure-3PSID",
    "__Secure-1PAPISID", "__Secure-3PAPISID",
)
