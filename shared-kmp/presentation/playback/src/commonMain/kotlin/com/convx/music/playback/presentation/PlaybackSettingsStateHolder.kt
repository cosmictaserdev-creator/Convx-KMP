package com.convx.music.playback.presentation

import com.convx.music.playback.PlaybackSettings
import com.convx.music.playback.PlaybackSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.dsl.module

class PlaybackSettingsStateHolder(
    private val repository: PlaybackSettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(PlaybackSettings())
    private val mutableErrorMessage = MutableStateFlow<String?>(null)

    val state: StateFlow<PlaybackSettings> = mutableState.asStateFlow()
    val errorMessage: StateFlow<String?> = mutableErrorMessage.asStateFlow()

    init {
        scope.launch {
            repository.observeSettings().collect { mutableState.value = it }
        }
    }

    fun setCrossfadeEnabled(enabled: Boolean) {
        scope.launch {
            try {
                repository.updateSettings { current ->
                    current.copy(crossfadeEnabled = enabled, autoMixEnabled = false)
                }
                mutableErrorMessage.value = null
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                mutableErrorMessage.value = "Couldn't save playback settings."
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}

fun playbackPresentationModule(): Module = module {
    single { PlaybackSettingsStateHolder(get()) }
}
