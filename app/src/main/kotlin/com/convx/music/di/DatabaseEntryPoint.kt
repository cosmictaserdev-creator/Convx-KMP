package com.convx.music.di

import com.convx.music.db.MusicDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Exposes the existing Room database while Android features transition to Koin. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
    fun musicDatabase(): MusicDatabase
}
