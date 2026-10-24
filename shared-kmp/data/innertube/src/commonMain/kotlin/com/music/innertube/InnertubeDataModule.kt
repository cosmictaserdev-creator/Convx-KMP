package com.music.innertube

import com.convx.music.playback.AudioStreamResolver
import com.convx.music.catalog.MusicCatalogRepository
import com.music.innertube.catalog.InnertubeMusicCatalogRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val innertubeDataModule = module {
    single<InnertubeRepository> { YouTubeRepository() }
    singleOf(::InnertubeAudioStreamResolver) { bind<AudioStreamResolver>() }
    singleOf(::InnertubeMusicCatalogRepository) { bind<MusicCatalogRepository>() }
}
