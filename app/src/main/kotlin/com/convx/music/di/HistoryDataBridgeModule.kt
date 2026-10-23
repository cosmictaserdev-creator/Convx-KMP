package com.convx.music.di

import android.content.Context
import com.convx.music.history.HistoryLocalDataSource
import dagger.hilt.android.EntryPointAccessors
import org.koin.dsl.module

val historyDataBridgeModule = module {
    single<HistoryLocalDataSource> {
        val context: Context = get()
        EntryPointAccessors.fromApplication(context, HistoryDataSourceEntryPoint::class.java).historyDataSource()
    }
}
