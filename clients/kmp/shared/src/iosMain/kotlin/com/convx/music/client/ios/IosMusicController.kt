package com.convx.music.client.ios

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import com.convx.music.account.presentation.MusicAccountPresentationState
import com.convx.music.account.presentation.MusicAccountStateHolder
import com.convx.music.account.presentation.musicAccountPresentationModule
import com.convx.music.catalog.presentation.MusicBrowserScreen
import com.convx.music.catalog.presentation.catalogPresentationModule
import com.convx.music.playback.AudioStreamResolver
import com.convx.music.playback.PlaybackController
import com.music.innertube.innertubeDataModule
import org.koin.compose.KoinApplication
import org.koin.compose.koinInject
import org.koin.dsl.module

private val iosHostModule = module {
    single<SecureSessionStore> { IosSecureSessionStore() }
    single<PlaybackController> { IosPlaybackController(get<AudioStreamResolver>()) }
}

fun iosMusicController() = ComposeUIViewController {
    KoinApplication(application = {
        modules(innertubeDataModule, musicAccountPresentationModule, catalogPresentationModule, iosHostModule)
    }) {
        val playbackController = koinInject<PlaybackController>()
        val accountHolder = koinInject<MusicAccountStateHolder>()
        val account by accountHolder.state.collectAsState()
        var showSignIn by remember { mutableStateOf(false) }
        DisposableEffect(playbackController, accountHolder) {
            onDispose {
                accountHolder.close()
                playbackController.release()
            }
        }

        Column(Modifier.fillMaxSize()) {
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
            MusicBrowserScreen(playbackController, Modifier.weight(1f))
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
