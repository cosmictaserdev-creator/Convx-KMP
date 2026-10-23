package com.convx.music.di

import com.convx.music.history.HistoryLocalDataSource
import com.convx.music.ui.screens.wrapped.WrappedAudioService
import com.convx.music.ui.screens.wrapped.WrappedManager
import com.convx.music.utils.SyncUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Reuses the existing lazy Android graph services while the main HomeViewModel moves to Koin. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HomeViewModelDependenciesEntryPoint {
    fun historyDataSource(): dagger.Lazy<HistoryLocalDataSource>
    fun syncUtils(): dagger.Lazy<SyncUtils>
    fun wrappedManager(): WrappedManager
    fun wrappedAudioService(): WrappedAudioService
}
