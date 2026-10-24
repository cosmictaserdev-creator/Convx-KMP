package com.convx.music.client.shared

import com.convx.music.catalog.MusicCatalogRepository
import com.music.innertube.InnertubeAudioStreamResolver
import com.music.innertube.InnertubeRepository
import com.music.innertube.YouTube
import com.music.innertube.catalog.InnertubeMusicCatalogRepository
import org.koin.dsl.module

val clientDataModule = module {
    single<InnertubeRepository> { YouTube }
    single { InnertubeAudioStreamResolver(get()) }
    single<MusicCatalogRepository> { InnertubeMusicCatalogRepository(get()) }
    single { CatalogStateHolder(get()) }
}
