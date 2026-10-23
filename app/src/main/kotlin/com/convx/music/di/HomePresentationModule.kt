package com.convx.music.di

import com.convx.music.viewmodels.NewReleaseViewModel
import com.convx.music.viewmodels.HomeViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homePresentationModule = module {
    viewModelOf(::NewReleaseViewModel)
    viewModel {
        val dependencies: HomeViewModelDependenciesEntryPoint = get()
        HomeViewModel(
            context = get(),
            database = get(),
            historyDataSource = dependencies.historyDataSource(),
            syncUtils = dependencies.syncUtils(),
            wrappedManager = dependencies.wrappedManager(),
            wrappedAudioService = dependencies.wrappedAudioService(),
        )
    }
}
