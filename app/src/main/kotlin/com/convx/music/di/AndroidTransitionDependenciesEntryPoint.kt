package com.convx.music.di

import androidx.media3.datasource.cache.SimpleCache
import com.convx.music.eq.EqualizerService
import com.convx.music.eq.data.EQProfileRepository
import com.convx.storage.data.NetworkMusicRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Reuses app-scoped Android services while their consumers move from Hilt to Koin. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AndroidTransitionDependenciesEntryPoint {
    fun networkMusicRepository(): NetworkMusicRepository
    fun equalizerService(): EqualizerService
    fun eqProfileRepository(): EQProfileRepository

    @PlayerCache
    fun playerCache(): SimpleCache

    @DownloadCache
    fun downloadCache(): SimpleCache
}
