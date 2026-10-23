package com.convx.music.di

import android.content.Context
import dagger.hilt.android.EntryPointAccessors
import org.koin.dsl.module

val homeViewModelDependenciesBridgeModule = module {
    single<HomeViewModelDependenciesEntryPoint> {
        val context: Context = get()
        EntryPointAccessors.fromApplication(context, HomeViewModelDependenciesEntryPoint::class.java)
    }
}
