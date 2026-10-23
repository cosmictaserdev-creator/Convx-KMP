package com.convx.migration.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.convx.music.ui.ios.foundation.iosTypography
import com.convx.music.ui.ios.foundation.lightIosColors
import org.koin.compose.koinInject

@Composable
fun ProofScreen() {
    val factory: ProofStateFactory = koinInject()
    val state = remember(factory) { factory.create() }
    val colors = lightIosColors()
    val typography = iosTypography()
    Column {
        BasicText(state.title, style = typography.title.copy(color = colors.label))
        BasicText("Platform: ${state.platform}", style = typography.body.copy(color = colors.secondaryLabel))
        BasicText("Lyrics sync: ${state.lyricsSyncLevel.label}", style = typography.body.copy(color = colors.secondaryLabel))
        BasicText("Parsed lyric lines: ${state.parsedLyricsLineCount}", style = typography.body.copy(color = colors.secondaryLabel))
    }
}
