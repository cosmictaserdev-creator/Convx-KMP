package com.convx.migration.shared

import com.convx.music.lyrics.LyricsSyncLevel
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals

class ProofStateTest {
    @Test
    fun includesRequestedPlatformInState() {
        val application = koinApplication {
            modules(
                sharedKoinModule,
                module { single { ProofPlatform("iOS") } },
            )
        }
        val state = try {
            application.koin.get<ProofStateFactory>().create()
        } finally {
            application.close()
        }

        assertEquals("Convx shared screen", state.title)
        assertEquals("iOS", state.platform)
        assertEquals(LyricsSyncLevel.LINE, state.lyricsSyncLevel)
        assertEquals(1, state.parsedLyricsLineCount)
    }
}
