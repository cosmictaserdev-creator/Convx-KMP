package com.convx.music.di

import android.content.Context
import com.convx.music.lyrics.LyricsHelper
import com.convx.music.lyrics.LyricsProviderRepository
import com.convx.music.utils.NetworkConnectivityObserver
import dagger.hilt.android.EntryPointAccessors
import org.koin.dsl.module

val lyricsBridgeModule = module {
    single<LyricsHelperEntryPoint> {
        val context: Context = get()
        EntryPointAccessors.fromApplication(context, LyricsHelperEntryPoint::class.java)
    }
    single<LyricsHelper> { get<LyricsHelperEntryPoint>().lyricsHelper() }
    single<LyricsProviderRepository> { get<LyricsHelperEntryPoint>().lyricsProviderRepository() }
    single<NetworkConnectivityObserver> { get<LyricsHelperEntryPoint>().networkConnectivityObserver() }
}
