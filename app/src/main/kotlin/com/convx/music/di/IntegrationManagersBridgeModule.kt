package com.convx.music.di

import android.content.Context
import com.convx.music.jam.JamManager
import com.convx.music.listentogether.ListenTogetherManager
import dagger.hilt.android.EntryPointAccessors
import org.koin.dsl.module

val integrationManagersBridgeModule = module {
    single<IntegrationManagersEntryPoint> {
        val context: Context = get()
        EntryPointAccessors.fromApplication(context, IntegrationManagersEntryPoint::class.java)
    }
    single<JamManager> { get<IntegrationManagersEntryPoint>().jamManager() }
    single<ListenTogetherManager> { get<IntegrationManagersEntryPoint>().listenTogetherManager() }
}
