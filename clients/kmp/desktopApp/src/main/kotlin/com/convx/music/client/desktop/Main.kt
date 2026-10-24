package com.convx.music.client.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.convx.music.client.shared.clientDataModule
import com.music.innertube.InnertubeAudioStreamResolver
import org.koin.compose.KoinApplication
import org.koin.dsl.module

private val desktopHostModule = module {
    single<DesktopMusicController> { DesktopPlaybackController(get<InnertubeAudioStreamResolver>()) }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Convx",
        state = WindowState(size = DpSize(1360.dp, 860.dp)),
    ) {
        KoinApplication(application = { modules(clientDataModule, desktopHostModule) }) {
            ConvxDesktopShell()
        }
    }
}
