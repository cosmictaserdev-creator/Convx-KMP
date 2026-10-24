package com.convx.music.client.desktop

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.convx.music.account.SecureSessionStore
import com.convx.music.account.presentation.musicAccountPresentationModule
import com.convx.music.catalog.presentation.catalogPresentationModule
import com.convx.music.library.libraryCoreModule
import com.convx.music.playback.AudioStreamResolver
import com.music.innertube.innertubeDataModule
import kotlinx.coroutines.runBlocking
import org.koin.compose.KoinApplication
import org.koin.dsl.module

private val desktopHostModule = module {
    single<SecureSessionStore> { DesktopSecureSessionStore() }
    single<DesktopMusicController> { DesktopPlaybackController(get<AudioStreamResolver>()) }
}

fun main(args: Array<String>) {
    if (args.contains(SECURE_STORAGE_PROBE_ARGUMENT)) {
        runBlocking { verifyDesktopSecureSessionStoreRoundTrip() }
        println("Desktop secure session storage round trip passed")
        return
    }

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Convx",
            state = WindowState(size = DpSize(1360.dp, 860.dp)),
        ) {
            KoinApplication(application = {
                modules(
                    innertubeDataModule,
                    libraryCoreModule,
                    musicAccountPresentationModule,
                    catalogPresentationModule,
                    desktopHostModule,
                )
            }) {
                ConvxDesktopShell()
            }
        }
    }
}

private const val SECURE_STORAGE_PROBE_ARGUMENT = "--convx-secure-storage-probe"
