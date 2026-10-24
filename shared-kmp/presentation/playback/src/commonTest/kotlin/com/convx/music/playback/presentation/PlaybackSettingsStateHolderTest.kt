package com.convx.music.playback.presentation

import com.convx.music.playback.PlaybackSettings
import com.convx.music.playback.PlaybackSettingsRepository
import com.convx.music.playback.PlaybackTransitionMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlaybackSettingsStateHolderTest {
    @Test
    fun enablingCrossfadeClearsAutoMixAndPublishesPersistedSettings() = runBlocking {
        val repository = FakePlaybackSettingsRepository(
            PlaybackSettings(crossfadeEnabled = true, autoMixEnabled = true),
        )
        val holder = PlaybackSettingsStateHolder(repository)

        try {
            holder.setCrossfadeEnabled(true)
            val updated = withTimeout(5_000L) {
                holder.state.first { it.transitionMode == PlaybackTransitionMode.CROSSFADE }
            }

            assertTrue(updated.crossfadeEnabled)
            assertFalse(updated.autoMixEnabled)
            assertEquals(PlaybackTransitionMode.CROSSFADE, updated.transitionMode)
        } finally {
            holder.close()
        }
    }

    @Test
    fun failedSettingsWriteIsReportedWithoutCancellingTheStateHolder() = runBlocking {
        val repository = FakePlaybackSettingsRepository(PlaybackSettings(), failUpdates = true)
        val holder = PlaybackSettingsStateHolder(repository)

        try {
            holder.setCrossfadeEnabled(true)
            val error = withTimeout(5_000L) { holder.errorMessage.first { it != null } }

            assertEquals("Couldn't save playback settings.", error)
            assertFalse(holder.state.value.crossfadeEnabled)
        } finally {
            holder.close()
        }
    }

    private class FakePlaybackSettingsRepository(
        initial: PlaybackSettings,
        private val failUpdates: Boolean = false,
    ) : PlaybackSettingsRepository {
        private val settings = MutableStateFlow(initial)

        override fun observeSettings(): Flow<PlaybackSettings> = settings

        override suspend fun getSettings(): PlaybackSettings = settings.value

        override suspend fun updateSettings(
            transform: (PlaybackSettings) -> PlaybackSettings,
        ): PlaybackSettings {
            check(!failUpdates) { "Simulated settings persistence failure." }
            val updated = transform(settings.value).normalized()
            settings.value = updated
            return updated
        }
    }
}
