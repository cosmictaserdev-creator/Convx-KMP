package com.convx.music.di

import com.convx.music.viewmodels.NewReleaseViewModel
import com.convx.music.viewmodels.HomeViewModel
import com.convx.music.history.HistoryLocalDataSource
import com.convx.music.utils.SyncUtils
import com.convx.music.ui.screens.wrapped.WrappedAudioService
import com.convx.music.ui.screens.wrapped.WrappedManager
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homePresentationModule = module {
    viewModelOf(::NewReleaseViewModel)
    viewModel {
        val koin = getKoin()
        HomeViewModel(
            context = get(),
            database = get(),
            historyDataSource = ValueProvider { koin.get<HistoryLocalDataSource>() },
            syncUtils = ValueProvider { koin.get<SyncUtils>() },
            wrappedManager = get<WrappedManager>(),
            wrappedAudioService = get<WrappedAudioService>(),
        )
    }
}
