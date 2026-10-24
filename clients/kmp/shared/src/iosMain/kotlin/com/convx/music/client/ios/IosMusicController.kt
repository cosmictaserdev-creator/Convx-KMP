package com.convx.music.client.ios

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.runtime.DisposableEffect
import com.convx.music.client.shared.MusicBrowserScreen
import com.convx.music.client.shared.catalogPresentationModule
import com.convx.music.client.shared.innertubeDataModule
import com.convx.music.playback.PlaybackController
import com.music.innertube.InnertubeAudioStreamResolver
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.module

private val iosHostModule = module {
    single<PlaybackController> { IosPlaybackController(get<InnertubeAudioStreamResolver>()) }
}

fun iosMusicController() = ComposeUIViewController {
    KoinApplication(application = { modules(innertubeDataModule, catalogPresentationModule, iosHostModule) }) {
        val playbackController = koinInject<PlaybackController>()
        DisposableEffect(playbackController) {
            onDispose { playbackController.release() }
        }
        MusicBrowserScreen(playbackController = playbackController)
    }
}
