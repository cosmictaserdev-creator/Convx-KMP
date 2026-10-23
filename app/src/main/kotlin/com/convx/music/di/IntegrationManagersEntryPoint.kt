package com.convx.music.di

import com.convx.music.jam.JamManager
import com.convx.music.listentogether.ListenTogetherManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Exposes existing app-scoped integration managers while their ViewModels move to Koin. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface IntegrationManagersEntryPoint {
    fun jamManager(): JamManager
    fun listenTogetherManager(): ListenTogetherManager
}
