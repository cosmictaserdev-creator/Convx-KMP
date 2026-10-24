package com.convx.music.client.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.convx.music.account.presentation.musicAccountPresentationModule
import com.convx.music.catalog.presentation.catalogPresentationModule
import com.convx.music.playback.AudioStreamResolver
import com.convx.music.account.SecureSessionStore
import com.music.innertube.innertubeDataModule
import org.koin.compose.KoinApplication
import org.koin.dsl.module

private val desktopHostModule = module {
    single<SecureSessionStore> { DesktopSecureSessionStore() }
    single<DesktopMusicController> { DesktopPlaybackController(get<AudioStreamResolver>()) }
}

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        title = "Convx",
        state = WindowState(size = DpSize(1360.dp, 860.dp)),
    ) {
        KoinApplication(application = {
            modules(
                innertubeDataModule,
                musicAccountPresentationModule,
                catalogPresentationModule,
                desktopHostModule,
            )
        }) {
            ConvxDesktopShell()
        }
    }
}
