package com.music.innertube

import com.convx.music.playback.AudioStreamResolver
import com.convx.music.account.MusicAccountRepository
import com.convx.music.catalog.MusicCatalogRepository
import com.convx.music.library.MusicLibraryRemoteDataSource
import com.music.innertube.account.InnertubeMusicAccountRepository
import com.music.innertube.catalog.InnertubeMusicCatalogRepository
import com.music.innertube.library.InnertubeMusicLibraryDataSource
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val innertubeDataModule = module {
    single<InnertubeRepository> { YouTubeRepository() }
    singleOf(::InnertubeAudioStreamResolver) { bind<AudioStreamResolver>() }
    singleOf(::InnertubeMusicCatalogRepository) { bind<MusicCatalogRepository>() }
    singleOf(::InnertubeMusicLibraryDataSource) { bind<MusicLibraryRemoteDataSource>() }
    singleOf(::InnertubeMusicAccountRepository) { bind<MusicAccountRepository>() }
}
