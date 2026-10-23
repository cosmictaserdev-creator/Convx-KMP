package com.convx.migration.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import org.koin.compose.koinInject

@Composable
fun ProofScreen() {
    val factory: ProofStateFactory = koinInject()
    val state = remember(factory) { factory.create() }
    Column {
        BasicText(state.title)
        BasicText("Platform: ${state.platform}")
        BasicText("Lyrics sync: ${state.lyricsSyncLevel.label}")
        BasicText("Parsed lyric lines: ${state.parsedLyricsLineCount}")
    }
}
