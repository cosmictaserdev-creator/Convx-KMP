package com.convx.music.di

import android.content.Context
import androidx.media3.datasource.cache.SimpleCache
import com.convx.music.eq.EqualizerService
import com.convx.music.eq.data.EQProfileRepository
import com.convx.storage.data.NetworkMusicRepository
import dagger.hilt.android.EntryPointAccessors
import org.koin.core.qualifier.named
import org.koin.dsl.module

val androidTransitionBridgeModule = module {
    single<AndroidTransitionDependenciesEntryPoint> {
        val context: Context = get()
        EntryPointAccessors.fromApplication(context, AndroidTransitionDependenciesEntryPoint::class.java)
    }
    single<NetworkMusicRepository> { get<AndroidTransitionDependenciesEntryPoint>().networkMusicRepository() }
    single<EqualizerService> { get<AndroidTransitionDependenciesEntryPoint>().equalizerService() }
    single<EQProfileRepository> { get<AndroidTransitionDependenciesEntryPoint>().eqProfileRepository() }
    single<SimpleCache>(named("playerCache")) { get<AndroidTransitionDependenciesEntryPoint>().playerCache() }
    single<SimpleCache>(named("downloadCache")) { get<AndroidTransitionDependenciesEntryPoint>().downloadCache() }
}
