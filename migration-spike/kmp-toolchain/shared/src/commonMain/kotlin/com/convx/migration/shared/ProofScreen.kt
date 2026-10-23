package com.convx.migration.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable

@Composable
fun ProofScreen(state: ProofState) {
    Column {
        BasicText(state.title)
        BasicText("Platform: ${state.platform}")
        BasicText("Lyrics sync: ${state.lyricsSyncLevel.label}")
        BasicText("Parsed lyric lines: ${state.parsedLyricsLineCount}")
    }
}
