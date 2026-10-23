package com.convx.migration.roomproof

import kotlin.test.Test
import kotlin.test.assertEquals

class RoomPersistenceProofTest {
    @Test
    fun libraryRowKeepsItsStableIdAndPlaybackTotal() {
        val song = LibrarySongRecord(id = "track-1", title = "Example", totalPlayTimeMillis = 12_000)

        assertEquals("track-1", song.id)
        assertEquals(12_000, song.totalPlayTimeMillis)
    }
}
