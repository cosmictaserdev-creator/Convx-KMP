package com.convx.music.di

import com.convx.music.playback.DownloadUtil
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** Exposes the existing download utility singleton while library screens move to Koin. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface DownloadUtilEntryPoint {
    fun downloadUtil(): DownloadUtil
}
