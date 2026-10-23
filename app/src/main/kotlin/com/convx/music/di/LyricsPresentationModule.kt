package com.convx.music.di

import com.convx.music.viewmodels.LyricsAppearanceViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val lyricsPresentationModule = module {
    viewModelOf(::LyricsAppearanceViewModel)
}
