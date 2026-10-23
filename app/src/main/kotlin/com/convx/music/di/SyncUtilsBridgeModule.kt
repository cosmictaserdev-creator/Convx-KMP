package com.convx.music.di

import android.content.Context
import com.convx.music.utils.SyncUtils
import dagger.hilt.android.EntryPointAccessors
import org.koin.dsl.module

val syncUtilsBridgeModule = module {
    single<SyncUtils> {
        val context: Context = get()
        EntryPointAccessors.fromApplication(context, SyncUtilsEntryPoint::class.java).syncUtils()
    }
}
