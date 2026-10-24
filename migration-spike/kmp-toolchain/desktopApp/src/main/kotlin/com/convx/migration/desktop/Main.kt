package com.convx.migration.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.convx.migration.shared.ProofPlatform
import com.convx.migration.shared.sharedKoinModule
import org.koin.compose.KoinApplication
import org.koin.dsl.module

private val desktopHostModule = module {
    single { ProofPlatform("Desktop") }
    single<DesktopMusicController> { DesktopPreviewPlaybackController() }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Convx",
        state = WindowState(size = DpSize(1360.dp, 860.dp)),
    ) {
        KoinApplication(application = { modules(sharedKoinModule, desktopHostModule) }) {
            ConvxDesktopShell()
        }
    }
}
