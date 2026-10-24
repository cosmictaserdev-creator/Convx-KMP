package com.convx.music.playback

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

@Serializable
enum class DjReverbType {
    AUTO,
    ROOM,
    PLATE,
    HALL,
}

@Serializable
enum class DjDelayType {
    AUTO,
    STEREO,
    PING_PONG,
    TAPE,
    FILTERED,
    SLAPBACK,
}

enum class PlaybackTransitionMode {
    OFF,
    CROSSFADE,
    AUTO_MIX,
}

@Serializable
data class PlaybackSettings(
    val crossfadeEnabled: Boolean = false,
    val crossfadeDurationMs: Long = DEFAULT_CROSSFADE_DURATION_MS,
    val crossfadeGapless: Boolean = false,
    val autoMixEnabled: Boolean = false,
    val creativeTransitionsEnabled: Boolean = false,
    val reverbType: DjReverbType = DjReverbType.AUTO,
    val delayType: DjDelayType = DjDelayType.AUTO,
    val bitPerfectEnabled: Boolean = false,
    val equalizerEnabled: Boolean = false,
    val equalizerProfile: EqualizerProfile = EqualizerProfile(preamp = 0.0, bands = emptyList()),
) {
    val transitionMode: PlaybackTransitionMode
        get() = when {
            bitPerfectEnabled || !crossfadeEnabled -> PlaybackTransitionMode.OFF
            autoMixEnabled -> PlaybackTransitionMode.AUTO_MIX
            else -> PlaybackTransitionMode.CROSSFADE
        }

    fun normalized(): PlaybackSettings = copy(
        crossfadeDurationMs = crossfadeDurationMs.coerceIn(MIN_CROSSFADE_DURATION_MS, MAX_CROSSFADE_DURATION_MS),
        equalizerProfile = equalizerProfile.normalized(),
    )

    companion object {
        const val DEFAULT_CROSSFADE_DURATION_MS = 5_000L
        const val MIN_CROSSFADE_DURATION_MS = 1_000L
        const val MAX_CROSSFADE_DURATION_MS = 12_000L
    }
}

interface PlaybackSettingsRepository {
    fun observeSettings(): Flow<PlaybackSettings>

    suspend fun getSettings(): PlaybackSettings

    suspend fun updateSettings(transform: (PlaybackSettings) -> PlaybackSettings): PlaybackSettings
}

private fun EqualizerProfile.normalized(): EqualizerProfile = copy(
    preamp = preamp.takeIf(Double::isFinite)?.coerceIn(-50.0, 50.0) ?: 0.0,
    bands = bands.asSequence()
        .filter { band ->
            band.frequency.isFinite() && band.frequency in 1.0..100_000.0 &&
                band.gain.isFinite() && band.gain in -30.0..30.0 &&
                band.q.isFinite() && band.q > 0.0 && band.q <= 20.0
        }
        .take(EqualizerProfile.MAX_BANDS)
        .toList(),
)
