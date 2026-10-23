package com.convx.migration.shared

import androidx.compose.ui.window.ComposeUIViewController

fun iosProofController() = ComposeUIViewController {
    ProofScreen(proofState("iOS"))
}
