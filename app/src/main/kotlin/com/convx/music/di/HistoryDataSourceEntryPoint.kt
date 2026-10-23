package com.convx.music.di

import com.convx.music.history.HistoryLocalDataSource
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Shares the existing singleton with Koin while HomeViewModel remains in Hilt. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface HistoryDataSourceEntryPoint {
    fun historyDataSource(): HistoryLocalDataSource
}
