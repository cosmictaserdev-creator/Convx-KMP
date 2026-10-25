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
import com.convx.music.playback.PlaybackCheckpointRepository
import com.convx.music.playback.PlaybackTrackAnalysisRepository
import com.music.innertube.innertubeDataModule
import kotlinx.coroutines.runBlocking
import org.koin.compose.KoinApplication
import org.koin.dsl.module
import java.io.File
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE

private val desktopHostModule = module {
    single<SecureSessionStore> { DesktopSecureSessionStore() }
    single<DesktopMusicController> {
        DesktopPlaybackController(
            streamResolver = get<AudioStreamResolver>(),
            playbackSettingsRepository = get<PlaybackSettingsRepository>(),
            playbackCheckpointRepository = get<PlaybackCheckpointRepository>(),
            trackAnalysisRepository = get<PlaybackTrackAnalysisRepository>(),
        )
    }
}

fun main(args: Array<String>) {
    if (args.contains(SECURE_STORAGE_PROBE_ARGUMENT)) {
        runBlocking { verifyDesktopSecureSessionStoreRoundTrip() }
        println("Desktop secure session storage round trip passed")
        return
    }

    if (!DesktopInstanceGuard.acquire()) {
        println("Convx is already running for this user.")
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
                    innertubeDataModule(DesktopWebPoTokenProvider()),
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

/** Prevents repeated Gradle or installer launches from creating competing playback sessions. */
private object DesktopInstanceGuard {
    private var channel: FileChannel? = null
    private var lock: FileLock? = null

    @Synchronized
    fun acquire(): Boolean {
        if (lock?.isValid == true) return true

        val lockFile = File(desktopLibraryDatabasePath()).resolveSibling("client-instance.lock").toPath()
        val openedChannel = FileChannel.open(lockFile, CREATE, WRITE)
        val acquiredLock = try {
            openedChannel.tryLock()
        } catch (_: OverlappingFileLockException) {
            null
        }
        if (acquiredLock == null) {
            openedChannel.close()
            return false
        }

        channel = openedChannel
        lock = acquiredLock
        Runtime.getRuntime().addShutdownHook(Thread({ release() }, "convx-instance-lock-release"))
        return true
    }

    @Synchronized
    private fun release() {
        runCatching { lock?.release() }
        lock = null
        runCatching { channel?.close() }
        channel = null
    }
}

private const val SECURE_STORAGE_PROBE_ARGUMENT = "--convx-secure-storage-probe"
