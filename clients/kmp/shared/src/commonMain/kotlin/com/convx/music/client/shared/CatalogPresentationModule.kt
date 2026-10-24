package com.convx.music.client.shared

import org.koin.dsl.module

val catalogPresentationModule = module {
    factory { CatalogStateHolder(get()) }
}
