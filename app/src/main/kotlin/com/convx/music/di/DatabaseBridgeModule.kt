package com.convx.music.di

import android.content.Context
import com.convx.music.db.MusicDatabase
import dagger.hilt.android.EntryPointAccessors
import org.koin.dsl.module

val databaseBridgeModule = module {
    single<MusicDatabase> {
        val context: Context = get()
        EntryPointAccessors.fromApplication(context, DatabaseEntryPoint::class.java).musicDatabase()
    }
}
