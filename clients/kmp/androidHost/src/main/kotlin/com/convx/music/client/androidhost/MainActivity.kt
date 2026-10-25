package com.convx.music.client.androidhost

import android.os.Bundle
import android.webkit.CookieManager
import androidx.room.Room
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.convx.music.account.MusicAccountState
import com.convx.music.account.SecureSessionStore
import com.convx.music.account.presentation.MusicAccountPresentationState
import com.convx.music.account.presentation.MusicAccountStateHolder
import com.convx.music.account.presentation.musicAccountPresentationModule
import com.convx.music.catalog.presentation.MusicBrowserScreen
import com.convx.music.catalog.presentation.catalogPresentationModule
import com.convx.music.library.libraryCoreModule
import com.convx.music.library.data.libraryDataModule
import com.convx.music.library.data.MusicLibraryDatabase
import com.convx.music.library.data.buildMusicLibraryDatabase
import com.convx.music.playback.AudioStreamResolver
import com.convx.music.playback.PlaybackSettingsRepository
import com.convx.music.playback.PlaybackCheckpointRepository
import com.convx.music.playback.data.PlaybackSettingsDatabase
import com.convx.music.playback.data.buildPlaybackSettingsDatabase
import com.convx.music.playback.data.playbackDataModule
import com.convx.music.playback.presentation.playbackPresentationModule
import com.music.innertube.innertubeDataModule
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.module

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appContext = applicationContext
        setContent {
            val libraryDatabase = remember(appContext) {
                val databaseFile = appContext.getDatabasePath("convx-client-library.db")
                val databaseDirectory = requireNotNull(databaseFile.parentFile)
                check(databaseDirectory.exists() || databaseDirectory.mkdirs()) {
                    "Unable to create the client library database directory."
                }
                buildMusicLibraryDatabase(
                    Room.databaseBuilder(appContext, MusicLibraryDatabase::class.java, databaseFile.absolutePath),
                )
            }
            val playbackSettingsDatabase = remember(appContext) {
                val databaseFile = appContext.getDatabasePath("convx-client-playback-settings.db")
                val databaseDirectory = requireNotNull(databaseFile.parentFile)
                check(databaseDirectory.exists() || databaseDirectory.mkdirs()) {
                    "Unable to create the client playback settings database directory."
                }
                buildPlaybackSettingsDatabase(
                    Room.databaseBuilder(appContext, PlaybackSettingsDatabase::class.java, databaseFile.absolutePath),
                )
            }
            val libraryStorageModule = remember(libraryDatabase) { libraryDataModule(libraryDatabase) }
            val playbackStorageModule = remember(playbackSettingsDatabase) {
                playbackDataModule(playbackSettingsDatabase)
            }
            val playbackPresentation = remember { playbackPresentationModule() }
            val sessionStorageModule = remember(appContext) {
                module { single<SecureSessionStore> { AndroidSecureSessionStore(appContext) } }
            }
            val webPoTokenProvider = remember(appContext) { AndroidWebPoTokenProvider(appContext) }
            KoinApplication(application = {
                modules(
                    libraryStorageModule,
                    playbackStorageModule,
                    playbackPresentation,
                    sessionStorageModule,
                    innertubeDataModule(webPoTokenProvider),
                    libraryCoreModule,
                    musicAccountPresentationModule,
                    catalogPresentationModule,
                )
            }) {
                DisposableEffect(libraryDatabase, playbackSettingsDatabase) {
                    onDispose {
                        libraryDatabase.close()
                        playbackSettingsDatabase.close()
                    }
                }
                val context = LocalContext.current
                val streamResolver: AudioStreamResolver = koinInject()
                val playbackSettingsRepository: PlaybackSettingsRepository = koinInject()
                val playbackCheckpointRepository: PlaybackCheckpointRepository = koinInject()
                val playbackController = remember(
                    context,
                    streamResolver,
                    playbackSettingsRepository,
                    playbackCheckpointRepository,
                ) {
                    AndroidPlaybackController(
                        context.applicationContext,
                        streamResolver,
                        playbackSettingsRepository,
                        playbackCheckpointRepository,
                    )
                }
                val accountHolder: MusicAccountStateHolder = koinInject()
                val account by accountHolder.state.collectAsState()
                var showSignIn by remember { mutableStateOf(false) }
                LaunchedEffect(account.account, account.isWorking, account.message) {
                    if (
                        account.account is MusicAccountState.SignedIn ||
                        account.account is MusicAccountState.ChoosingChannel ||
                        (!account.isWorking && account.message != null)
                    ) {
                        showSignIn = false
                    }
                }

                DisposableEffect(playbackController, accountHolder, webPoTokenProvider) {
                    onDispose {
                        accountHolder.close()
                        playbackController.release()
                        webPoTokenProvider.close()
                    }
                }

                Column(Modifier.fillMaxSize()) {
                    AccountHeader(
                        state = account,
                        onSignIn = { showSignIn = true },
                        onSignOut = {
                            accountHolder.signOut()
                            CookieManager.getInstance().removeAllCookies(null)
                            CookieManager.getInstance().flush()
                        },
                        onCancelSignIn = accountHolder::cancelSignIn,
                    )
                    account.message?.let { message ->
                        BasicText(message, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                    }
                    MusicBrowserScreen(
                        playbackController = playbackController,
                        crossfadeAvailable = true,
                        modifier = Modifier.weight(1f),
                        isSignedIn = account.account is MusicAccountState.SignedIn,
                        onSignInRequested = { showSignIn = true },
                    )
                }

                if (showSignIn) {
                    AndroidSignInDialog(
                        accountIsWorking = account.isWorking,
                        onComplete = { cookie, visitorData, dataSyncId ->
                            accountHolder.completeWebSignIn(cookie, visitorData, dataSyncId)
                        },
                        onDismiss = {
                            accountHolder.cancelSignIn()
                            CookieManager.getInstance().removeAllCookies(null)
                            showSignIn = false
                        },
                    )
                }

                val channels = (account.account as? MusicAccountState.ChoosingChannel)?.channels.orEmpty()
                if (channels.isNotEmpty()) {
                    Dialog(onDismissRequest = accountHolder::cancelSignIn) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            BasicText("Choose a YouTube channel")
                            channels.forEach { channel ->
                                BasicText(
                                    channel.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { accountHolder.selectChannel(channel.dataSyncId) }
                                        .padding(vertical = 8.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun AccountHeader(
    state: MusicAccountPresentationState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    onCancelSignIn: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
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
                modifier = Modifier.clickable(enabled = !state.isRestoring && !state.isWorking, onClick = onSignIn),
            )
            is MusicAccountState.SignedIn -> BasicText(
                "Sign out",
                modifier = Modifier.clickable(enabled = !state.isWorking, onClick = onSignOut),
            )
            is MusicAccountState.ChoosingChannel -> BasicText(
                "Cancel",
                modifier = Modifier.clickable(onClick = onCancelSignIn),
            )
        }
    }
}
