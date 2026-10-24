package com.convx.music.client.shared

import com.convx.music.catalog.MusicCatalogRepository
import com.music.innertube.InnertubeAudioStreamResolver
import com.music.innertube.InnertubeRepository
import com.music.innertube.YouTubeRepository
import com.music.innertube.catalog.InnertubeMusicCatalogRepository
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val innertubeDataModule = module {
    single<InnertubeRepository> { YouTubeRepository() }
    singleOf(::InnertubeAudioStreamResolver)
    singleOf(::InnertubeMusicCatalogRepository) { bind<MusicCatalogRepository>() }
}
