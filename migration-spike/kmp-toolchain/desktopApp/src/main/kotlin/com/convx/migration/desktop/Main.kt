package com.convx.migration.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.convx.migration.shared.ProofScreen
import com.convx.migration.shared.ProofPlatform
import com.convx.migration.shared.sharedKoinModule
import org.koin.compose.KoinApplication
import org.koin.dsl.module

private val desktopHostModule = module {
    single { ProofPlatform("Desktop") }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Convx KMP proof",
    ) {
        KoinApplication(application = { modules(sharedKoinModule, desktopHostModule) }) {
            ProofScreen()
        }
    }
}
