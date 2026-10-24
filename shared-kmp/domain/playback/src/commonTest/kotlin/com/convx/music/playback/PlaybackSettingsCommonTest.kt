package com.convx.music.playback

import kotlin.test.Test
import kotlin.test.assertEquals

class PlaybackSettingsCommonTest {
    @Test
    fun transitionModeRespectsCrossfadeAndBitPerfectSettings() {
        assertEquals(PlaybackTransitionMode.OFF, PlaybackSettings().transitionMode)
        assertEquals(
            PlaybackTransitionMode.CROSSFADE,
            PlaybackSettings(crossfadeEnabled = true).transitionMode,
        )
        assertEquals(
            PlaybackTransitionMode.AUTO_MIX,
            PlaybackSettings(crossfadeEnabled = true, autoMixEnabled = true).transitionMode,
        )
        assertEquals(
            PlaybackTransitionMode.OFF,
            PlaybackSettings(crossfadeEnabled = true, autoMixEnabled = true, bitPerfectEnabled = true).transitionMode,
        )
    }

    @Test
    fun normalizedSettingsClampFadeAndDiscardInvalidEqualizerBands() {
        val settings = PlaybackSettings(
            crossfadeDurationMs = Long.MAX_VALUE,
            equalizerProfile = EqualizerProfile(
                preamp = Double.NaN,
                bands = listOf(
                    EqualizerBand(frequency = 1_000.0, gain = 3.0),
                    EqualizerBand(frequency = Double.NaN, gain = 2.0),
                    EqualizerBand(frequency = 2_000.0, gain = 40.0),
                ),
            ),
        ).normalized()

        assertEquals(PlaybackSettings.MAX_CROSSFADE_DURATION_MS, settings.crossfadeDurationMs)
        assertEquals(0.0, settings.equalizerProfile.preamp)
        assertEquals(listOf(1_000.0), settings.equalizerProfile.bands.map { it.frequency })
    }
}
