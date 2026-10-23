package com.convx.music.di

import com.convx.music.viewmodels.NewReleaseViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val homePresentationModule = module {
    viewModelOf(::NewReleaseViewModel)
}
