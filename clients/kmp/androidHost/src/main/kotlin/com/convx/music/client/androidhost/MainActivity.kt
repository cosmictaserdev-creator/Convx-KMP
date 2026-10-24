package com.convx.music.client.androidhost

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.convx.music.client.shared.MusicBrowserScreen
import com.convx.music.client.shared.catalogPresentationModule
import com.convx.music.client.shared.innertubeDataModule
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
