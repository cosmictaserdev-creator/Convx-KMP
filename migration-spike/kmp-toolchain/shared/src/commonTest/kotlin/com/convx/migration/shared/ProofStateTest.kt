package com.convx.migration.shared

import com.convx.music.lyrics.LyricsSyncLevel
import kotlin.test.Test
import kotlin.test.assertEquals

class ProofStateTest {
    @Test
    fun includesRequestedPlatformInState() {
        val state = proofState("iOS")

        assertEquals("Convx shared screen", state.title)
        assertEquals("iOS", state.platform)
        assertEquals(LyricsSyncLevel.LINE, state.lyricsSyncLevel)
        assertEquals(1, state.parsedLyricsLineCount)
    }
}
