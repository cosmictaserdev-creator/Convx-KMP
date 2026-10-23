package com.convx.migration.roomproof

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest

class RoomPersistenceProofTest {
    @Test
    fun roomPersistsLibraryRowsAndQueriesThemThroughACommonFlow() = runTest {
        val database = Room.inMemoryDatabaseBuilder<RoomPersistenceProofDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()

        try {
            val song = LibrarySongRecord(id = "track-1", title = "Example", totalPlayTimeMillis = 12_000)
            database.librarySongDao().upsert(song)

            assertEquals(listOf(song), database.librarySongDao().observeLibrarySongs().first())
        } finally {
            database.close()
        }
    }
}
