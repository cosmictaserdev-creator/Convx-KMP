package com.convx.music.di

import com.convx.music.utils.SyncUtils
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Shares the existing Hilt singleton with Koin during the Android graph transition. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface SyncUtilsEntryPoint {
    fun syncUtils(): SyncUtils
}
