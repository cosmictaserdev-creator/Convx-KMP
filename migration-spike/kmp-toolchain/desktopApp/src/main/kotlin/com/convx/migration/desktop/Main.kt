package com.convx.migration.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.convx.migration.shared.ProofScreen
import com.convx.migration.shared.proofState

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Convx KMP proof",
    ) {
        ProofScreen(proofState("Desktop"))
    }
}
