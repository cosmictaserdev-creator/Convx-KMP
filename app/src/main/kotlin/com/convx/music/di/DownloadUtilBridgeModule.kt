package com.convx.music.di

import android.content.Context
import com.convx.music.playback.DownloadUtil
import dagger.hilt.android.EntryPointAccessors
import org.koin.dsl.module

val downloadUtilBridgeModule = module {
    single<DownloadUtil> {
        val context: Context = get()
        EntryPointAccessors.fromApplication(context, DownloadUtilEntryPoint::class.java).downloadUtil()
    }
}
