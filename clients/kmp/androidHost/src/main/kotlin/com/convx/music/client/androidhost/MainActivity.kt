package com.convx.music.client.androidhost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.convx.music.catalog.presentation.MusicBrowserScreen
import com.convx.music.catalog.presentation.catalogPresentationModule
import com.convx.music.playback.AudioStreamResolver
import com.music.innertube.innertubeDataModule
import org.koin.compose.koinInject
import org.koin.compose.KoinApplication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KoinApplication(application = { modules(innertubeDataModule, catalogPresentationModule) }) {
                val context = LocalContext.current
                val streamResolver: AudioStreamResolver = koinInject()
                val playbackController = remember(context, streamResolver) {
                    AndroidPlaybackController(context.applicationContext, streamResolver)
                }
                DisposableEffect(playbackController) {
                    onDispose(playbackController::release)
                }
                MusicBrowserScreen(playbackController)
            }
        }
    }
}
