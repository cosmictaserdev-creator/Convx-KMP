package com.convx.music.client.androidhost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.convx.music.catalog.presentation.MusicBrowserScreen
import com.convx.music.catalog.presentation.catalogPresentationModule
import com.music.innertube.innertubeDataModule
import org.koin.compose.KoinApplication

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            KoinApplication(application = { modules(innertubeDataModule, catalogPresentationModule) }) {
                MusicBrowserScreen()
            }
        }
    }
}
