package com.convx.migration.shared

import com.convx.music.lyrics.LyricsSyncLevel
import com.convx.music.lyrics.LyricsUpgradePolicy
import com.convx.music.lyrics.bitchord.LrcParser
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

data class ProofState(
    val title: String,
    val platform: String,
    val lyricsSyncLevel: LyricsSyncLevel,
    val parsedLyricsLineCount: Int,
)

data class ProofPlatform(val name: String)

class ProofStateFactory(
    private val platform: ProofPlatform,
    private val lyricsUpgradePolicy: LyricsUpgradePolicy,
) {
    fun create(): ProofState {
        val lyrics = "[00:01.00]A portable lyric line"
        val best = lyricsUpgradePolicy.consider("proof", priority = 0, lyrics = lyrics)
        return ProofState(
            title = "Convx shared screen",
            platform = platform.name,
            lyricsSyncLevel = best?.sync ?: LyricsSyncLevel.PLAIN,
            parsedLyricsLineCount = LrcParser.parse(lyrics).size,
        )
    }
}

val sharedKoinModule = module {
    factoryOf(::LyricsUpgradePolicy)
    factoryOf(::ProofStateFactory)
}
