package com.convx.music.client.ios

import androidx.room.Room
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.window.Dialog
import com.convx.music.account.MusicAccountState
import com.convx.music.account.SecureSessionStore
import com.convx.music.library.libraryCoreModule
import com.convx.music.library.data.libraryDataModule
import com.convx.music.library.data.MusicLibraryDatabase
import com.convx.music.library.data.buildMusicLibraryDatabase
import com.convx.music.account.presentation.MusicAccountPresentationState
import com.convx.music.account.presentation.MusicAccountStateHolder
import com.convx.music.account.presentation.musicAccountPresentationModule
import com.convx.music.catalog.presentation.MusicBrowserScreen
import com.convx.music.catalog.presentation.catalogPresentationModule
import com.convx.music.playback.AudioStreamResolver
import com.convx.music.playback.PlaybackController
import com.convx.music.playback.PlaybackSettingsRepository
import com.convx.music.playback.data.PlaybackSettingsDatabase
import com.convx.music.playback.data.buildPlaybackSettingsDatabase
import com.convx.music.playback.data.playbackDataModule
import com.convx.music.playback.presentation.playbackPresentationModule
import com.convx.music.playback.presentation.PlaybackSleepTimerSheet
import com.music.innertube.innertubeDataModule
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.module
import platform.Foundation.NSFileManager
import platform.Foundation.NSHomeDirectory

private val iosHostModule = module {
    single<SecureSessionStore> { IosSecureSessionStore() }
    single<PlaybackController> {
        IosPlaybackController(
            streamResolver = get<AudioStreamResolver>(),
            playbackSettingsRepository = get<PlaybackSettingsRepository>(),
        )
    }
}

private fun iosDatabasePath(fileName: String): String {
    val directory = "${NSHomeDirectory()}/Library/Application Support/Convx"
    val fileManager = NSFileManager.defaultManager
    check(
        fileManager.fileExistsAtPath(directory) ||
            fileManager.createDirectoryAtPath(
                path = directory,
                withIntermediateDirectories = true,
                attributes = null,
                error = null,
            ),
    ) { "Unable to create the Convx application support directory." }
    return "$directory/$fileName"
}

private fun iosLibraryDatabasePath() = iosDatabasePath("client-library.db")

private fun iosPlaybackSettingsDatabasePath() = iosDatabasePath("client-playback-settings.db")

fun iosMusicController() = iosMusicController(runKeychainProbe = false)

fun iosMusicControllerWithKeychainProbe() = iosMusicController(runKeychainProbe = true)

private fun iosMusicController(runKeychainProbe: Boolean) = ComposeUIViewController {
    val libraryDatabase = remember {
        buildMusicLibraryDatabase(Room.databaseBuilder<MusicLibraryDatabase>(iosLibraryDatabasePath()))
    }
    val playbackSettingsDatabase = remember {
        buildPlaybackSettingsDatabase(
            Room.databaseBuilder<PlaybackSettingsDatabase>(iosPlaybackSettingsDatabasePath()),
        )
    }
    DisposableEffect(libraryDatabase, playbackSettingsDatabase) {
        onDispose {
            libraryDatabase.close()
            playbackSettingsDatabase.close()
        }
    }
    KoinApplication(application = {
        modules(
            libraryDataModule(libraryDatabase),
            playbackDataModule(playbackSettingsDatabase),
            playbackPresentationModule(),
            innertubeDataModule,
            libraryCoreModule,
            musicAccountPresentationModule,
            catalogPresentationModule,
            iosHostModule,
        )
    }) {
        val playbackController = koinInject<PlaybackController>()
        val playback by playbackController.playbackSnapshot.collectAsState()
        val sleepTimer = remember(playbackController) { requireNotNull(playbackController.sleepTimer) }
        val sleepTimerState by sleepTimer.state.collectAsState()
        val accountHolder = koinInject<MusicAccountStateHolder>()
        val account by accountHolder.state.collectAsState()
        var showSignIn by remember { mutableStateOf(false) }
        var showSleepTimer by remember { mutableStateOf(false) }
        var keychainProbeMessage by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(runKeychainProbe) {
            if (runKeychainProbe) {
                keychainProbeMessage = verifyIosSecureSessionStoreRoundTrip()
            }
        }
        DisposableEffect(playbackController, accountHolder) {
            onDispose {
                accountHolder.close()
                playbackController.release()
            }
        }

        Column(Modifier.fillMaxSize()) {
            keychainProbeMessage?.let { BasicText(it, Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            IosAccountHeader(
                state = account,
                onSignIn = { showSignIn = true },
                onSignOut = {
                    accountHolder.signOut()
                    clearIosWebCookies()
                },
                onCancelSignIn = accountHolder::cancelSignIn,
            )
            account.message?.let { BasicText(it, Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
            MusicBrowserScreen(
                playbackController = playbackController,
                crossfadeAvailable = true,
                modifier = Modifier.weight(1f),
                isSignedIn = account.account is MusicAccountState.SignedIn,
                isSleepTimerActive = sleepTimerState.isActive,
                onSleepTimerRequested = { showSleepTimer = true },
                onSignInRequested = { showSignIn = true },
            )
        }

        if (showSleepTimer) {
            PlaybackSleepTimerSheet(
                timer = sleepTimer,
                playback = playback,
                darkTheme = false,
                onDismissRequest = { showSleepTimer = false },
            )
        }

        if (showSignIn) {
            IosSignInDialog(
                onComplete = { cookie, visitorData, dataSyncId ->
                    accountHolder.completeWebSignIn(cookie, visitorData, dataSyncId)
                    showSignIn = false
                },
                onDismiss = {
                    accountHolder.cancelSignIn()
                    clearIosWebCookies()
                    showSignIn = false
                },
            )
        }

        val channels = (account.account as? MusicAccountState.ChoosingChannel)?.channels.orEmpty()
        if (channels.isNotEmpty()) {
            Dialog(onDismissRequest = accountHolder::cancelSignIn) {
                Column(
                    Modifier.fillMaxWidth().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    BasicText("Choose a YouTube channel")
                    channels.forEach { channel ->
                        BasicText(
                            channel.name,
                            Modifier.fillMaxWidth().clickable { accountHolder.selectChannel(channel.dataSyncId) }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IosAccountHeader(
    state: MusicAccountPresentationState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onCancelSignIn: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        BasicText(
            when (val account = state.account) {
                MusicAccountState.SignedOut -> "YouTube Music · Guest"
                is MusicAccountState.ChoosingChannel -> "Choose a YouTube channel"
                is MusicAccountState.SignedIn -> "YouTube Music · ${account.account.name}"
            },
        )
        when (state.account) {
            MusicAccountState.SignedOut -> BasicText(
                if (state.isRestoring || state.isWorking) "Please wait…" else "Sign in",
                Modifier.clickable(enabled = !state.isRestoring && !state.isWorking, onClick = onSignIn),
            )
            is MusicAccountState.SignedIn -> BasicText("Sign out", Modifier.clickable(onClick = onSignOut))
            is MusicAccountState.ChoosingChannel -> BasicText("Cancel", Modifier.clickable(onClick = onCancelSignIn))
        }
    }
}
