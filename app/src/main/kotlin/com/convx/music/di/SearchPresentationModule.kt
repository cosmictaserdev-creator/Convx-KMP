package com.convx.music.di

import com.convx.music.ui.screens.search.AppleSearchViewModel
import com.convx.music.ui.screens.search.suggestions.SuggestionsViewModel
import com.convx.music.viewmodels.MoodAndGenresViewModel
import com.convx.music.viewmodels.OnlineSearchViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val searchPresentationModule = module {
    viewModelOf(::AppleSearchViewModel)
    viewModelOf(::SuggestionsViewModel)
    viewModelOf(::MoodAndGenresViewModel)
    viewModelOf(::OnlineSearchViewModel)
}
