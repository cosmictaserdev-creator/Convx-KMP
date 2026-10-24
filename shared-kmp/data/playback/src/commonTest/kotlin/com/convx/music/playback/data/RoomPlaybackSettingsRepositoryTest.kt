package com.convx.music.playback.data

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.convx.music.playback.DjDelayType
import com.convx.music.playback.DjReverbType
import com.convx.music.playback.EqualizerBand
import com.convx.music.playback.EqualizerProfile
import com.convx.music.playback.PlaybackSettings
import com.convx.music.playback.PlaybackTransitionMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomPlaybackSettingsRepositoryTest {
    @Test
    fun defaultSettingsCanBeUpdatedAndObserved() = runTest {
        val database = inMemoryDatabase()
        try {
            val repository = RoomPlaybackSettingsRepository(database.playbackSettingsDao())
            assertEquals(PlaybackSettings(), repository.observeSettings().first())

            val updated = repository.updateSettings {
                it.copy(
                    crossfadeEnabled = true,
                    autoMixEnabled = true,
                    crossfadeDurationMs = 7_500L,
                    creativeTransitionsEnabled = true,
                    reverbType = DjReverbType.PLATE,
                    delayType = DjDelayType.PING_PONG,
                    equalizerEnabled = true,
                    equalizerProfile = EqualizerProfile(
                        preamp = -2.5,
                        bands = listOf(EqualizerBand(frequency = 1_000.0, gain = 3.0)),
                    ),
                )
            }

            assertEquals(PlaybackTransitionMode.AUTO_MIX, updated.transitionMode)
            assertEquals(updated, repository.getSettings())
            assertEquals(updated, repository.observeSettings().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun corruptStoredSettingsRecoverToDefaultsOnNextUpdate() = runTest {
        val database = inMemoryDatabase()
        try {
            val repository = RoomPlaybackSettingsRepository(database.playbackSettingsDao())
            database.playbackSettingsDao().upsert(PlaybackSettingsRecord(settingsJson = "{"))
            assertEquals(PlaybackSettings(), repository.getSettings())

            val recovered = repository.updateSettings { it.copy(crossfadeEnabled = true) }
            assertEquals(PlaybackTransitionMode.CROSSFADE, recovered.transitionMode)
            assertEquals(recovered, repository.getSettings())
        } finally {
            database.close()
        }
    }

    private fun inMemoryDatabase(): PlaybackSettingsDatabase =
        Room.inMemoryDatabaseBuilder<PlaybackSettingsDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()
}
