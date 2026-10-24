package com.convx.music.client.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import java.awt.EventQueue

@Composable
internal fun DesktopSignInDialog(
    onComplete: (cookie: String, visitorData: String, dataSyncId: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    val status = remember { mutableStateOf("Opening the sign-in window…") }
    val currentOnComplete = rememberUpdatedState(onComplete)
    val currentOnDismiss = rememberUpdatedState(onDismiss)
    val controller = remember {
        DesktopSignInController(
            onStatusChanged = { message -> EventQueue.invokeLater { status.value = message } },
            onComplete = { cookie, visitorData, dataSyncId ->
                EventQueue.invokeLater { currentOnComplete.value(cookie, visitorData, dataSyncId) }
            },
            onDismiss = { EventQueue.invokeLater { currentOnDismiss.value() } },
        )
    }

    DisposableEffect(controller) {
        controller.start()
        onDispose(controller::close)
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.58f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .background(Color(0xF219191D), RoundedCornerShape(20.dp))
                .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(20.dp))
                .padding(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            BasicText("Sign in to YouTube Music", style = TextStyle(color = Color.White))
            BasicText(
                "Complete sign-in in the separate browser window. Your session is transferred to Convx over a local connection and stored using the system secure store.",
                style = TextStyle(color = Color.White.copy(alpha = 0.74f)),
            )
            BasicText(status.value, style = TextStyle(color = Color.White.copy(alpha = 0.9f)))
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                BasicText(
                    "Open sign-in window",
                    modifier = Modifier.clickable(role = Role.Button, onClick = controller::openWindow),
                    style = TextStyle(color = Color(0xFFB5C8FF)),
                )
                BasicText(
                    "Cancel",
                    modifier = Modifier.clickable(role = Role.Button) {
                        controller.close()
                        currentOnDismiss.value()
                    },
                    style = TextStyle(color = Color.White),
                )
            }
        }
    }
}
