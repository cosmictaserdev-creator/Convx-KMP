package com.convx.migration.shared

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.convx.music.ui.ios.component.IosText
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.ios.foundation.ProvideIosTheme
import com.convx.music.ui.ios.foundation.iosTypography
import com.convx.music.ui.ios.foundation.lightIosColors
import org.koin.compose.koinInject

@Composable
fun ProofScreen() {
    val factory: ProofStateFactory = koinInject()
    val state = remember(factory) { factory.create() }
    ProvideIosTheme(colors = lightIosColors(), typography = iosTypography()) {
        Column {
            IosText(state.title, style = IosTheme.typography.title, color = IosTheme.colors.label)
            IosText("Platform: ${state.platform}", color = IosTheme.colors.secondaryLabel)
            IosText("Lyrics sync: ${state.lyricsSyncLevel.label}", color = IosTheme.colors.secondaryLabel)
            IosText("Parsed lyric lines: ${state.parsedLyricsLineCount}", color = IosTheme.colors.secondaryLabel)
        }
    }
}
