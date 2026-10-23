package com.convx.migration.shared

import androidx.compose.ui.window.ComposeUIViewController
import org.koin.compose.KoinApplication
import org.koin.dsl.module

private val iosHostModule = module {
    single { ProofPlatform("iOS") }
}

fun iosProofController() = ComposeUIViewController {
    KoinApplication(application = { modules(sharedKoinModule, iosHostModule) }) {
        ProofScreen()
    }
}
