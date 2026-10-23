package com.convx.migration.shared

import com.convx.music.lyrics.LyricsSyncLevel
import com.convx.music.lyrics.bitchord.LrcParser

data class ProofState(
    val title: String,
    val platform: String,
    val lyricsSyncLevel: LyricsSyncLevel,
    val parsedLyricsLineCount: Int,
)

fun proofState(platform: String): ProofState {
    val parsedLyrics = LrcParser.parse("[00:01.00]A portable lyric line")
    return ProofState(
        title = "Convx shared screen",
        platform = platform,
        lyricsSyncLevel = LyricsSyncLevel.detect("[00:01.00]A line"),
        parsedLyricsLineCount = parsedLyrics.size,
    )
}
