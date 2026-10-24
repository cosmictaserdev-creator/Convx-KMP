package com.convx.music.client.desktop

import androidx.room.Room
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.convx.music.account.SecureSessionStore
import com.convx.music.account.presentation.musicAccountPresentationModule
import com.convx.music.catalog.presentation.catalogPresentationModule
import com.convx.music.library.libraryCoreModule
import com.convx.music.library.data.libraryDataModule
import com.convx.music.library.data.MusicLibraryDatabase
import com.convx.music.library.data.buildMusicLibraryDatabase
import com.convx.music.playback.AudioStreamResolver
import com.convx.music.playback.data.PlaybackSettingsDatabase
import com.convx.music.playback.data.buildPlaybackSettingsDatabase
import com.convx.music.playback.data.playbackDataModule
import com.convx.music.playback.presentation.playbackPresentationModule
import com.convx.music.playback.PlaybackSettingsRepository
import com.music.innertube.innertubeDataModule
import kotlinx.coroutines.runBlocking
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import java.io.File

private val desktopHostModule = module {
    single<SecureSessionStore> { DesktopSecureSessionStore() }
    single<DesktopMusicController> {
        DesktopPlaybackController(
            streamResolver = get<AudioStreamResolver>(),
            playbackSettingsRepository = get<PlaybackSettingsRepository>(),
        )
    }
}

fun main(args: Array<String>) {
    if (args.contains(SECURE_STORAGE_PROBE_ARGUMENT)) {
        runBlocking { verifyDesktopSecureSessionStoreRoundTrip() }
        println("Desktop secure session storage round trip passed")
        return
    }

    val libraryDatabase = buildMusicLibraryDatabase(
        Room.databaseBuilder<MusicLibraryDatabase>(desktopLibraryDatabasePath()),
    )
    val playbackSettingsDatabase = buildPlaybackSettingsDatabase(
        Room.databaseBuilder<PlaybackSettingsDatabase>(desktopPlaybackSettingsDatabasePath()),
    )

    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Convx",
            state = WindowState(size = DpSize(1360.dp, 860.dp)),
        ) {
            KoinApplication(application = {
                modules(
                    libraryDataModule(libraryDatabase),
                    playbackDataModule(playbackSettingsDatabase),
                    playbackPresentationModule(),
                    innertubeDataModule,
                    libraryCoreModule,
                    musicAccountPresentationModule,
                    catalogPresentationModule,
                    desktopHostModule,
                )
            }) {
                ConvxDesktopShell()
                DisposableEffect(libraryDatabase, playbackSettingsDatabase) {
                    onDispose {
                        libraryDatabase.close()
                        playbackSettingsDatabase.close()
                    }
                }
            }
        }
    }
}

private fun desktopLibraryDatabasePath(): String {
    val userHome = System.getProperty("user.home")
    val operatingSystem = System.getProperty("os.name").lowercase()
    val directory = when {
        operatingSystem.contains("win") -> File(
            System.getenv("APPDATA")?.takeIf(String::isNotBlank)
                ?: File(userHome, "AppData/Roaming").absolutePath,
            "Convx",
        )
        operatingSystem.contains("mac") -> File(userHome, "Library/Application Support/Convx")
        else -> {
            val xdgDataHome = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() && File(it).isAbsolute }
            File(xdgDataHome ?: File(userHome, ".local/share").absolutePath, "convx")
        }
    }
    check(directory.isDirectory || directory.mkdirs()) {
        "Unable to create the Convx application data directory."
    }
    return File(directory, "client-library.db").absolutePath
}

private fun desktopPlaybackSettingsDatabasePath(): String =
    File(desktopLibraryDatabasePath()).resolveSibling("client-playback-settings.db").absolutePath

private const val SECURE_STORAGE_PROBE_ARGUMENT = "--convx-secure-storage-probe"
