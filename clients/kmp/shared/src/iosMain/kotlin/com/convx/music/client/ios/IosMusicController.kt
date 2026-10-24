package com.convx.music.client.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.convx.music.client.shared.MusicBrowserScreen
import com.convx.music.client.shared.clientDataModule
import com.convx.music.playback.PlaybackController
import com.music.innertube.InnertubeAudioStreamResolver
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.module

private val iosHostModule = module {
    single<PlaybackController> { IosPlaybackController(get<InnertubeAudioStreamResolver>()) }
}

fun iosMusicController() = ComposeUIViewController {
    KoinApplication(application = { modules(clientDataModule, iosHostModule) }) {
        MusicBrowserScreen(playbackController = koinInject())
    }
}
