package com.convx.migration.roomproof

import kotlin.test.Test
import kotlin.test.assertEquals
import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.random.Random

class RoomPersistenceProofTest {
    @Test
    fun roomPersistsLibraryRowsAndQueriesThemThroughACommonFlow() = runTest {
        val database = Room.inMemoryDatabaseBuilder<RoomPersistenceProofDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()

        try {
            val repository = RoomLibraryProofRepository(database.librarySongDao())
            val song = LibrarySongRecord(id = "track-1", title = "Example", totalPlayTimeMillis = 12_000)
            repository.upsert(song)

            assertEquals(listOf(song), repository.observeSongs().first())
        } finally {
            database.close()
        }
    }

    @Test
    fun snapshotImportReplacesRowsAndRejectsInvalidDataWithoutLosingCurrentRows() = runTest {
        val database = Room.inMemoryDatabaseBuilder<RoomPersistenceProofDatabase>()
            .setDriver(BundledSQLiteDriver())
            .build()

        try {
            val repository = RoomLibraryProofRepository(database.librarySongDao())
            val current = LibrarySongRecord(id = "current", title = "Current")
            val imported = LibrarySongRecord(id = "imported", title = "Imported", totalPlayTimeMillis = 4_000)
            repository.upsert(current)
            val backup = LibrarySnapshot(version = 1, songs = listOf(imported))

            repository.importSnapshot(backup)
            assertEquals(listOf(imported), repository.observeSongs().first())

            val beforeInvalidImport = repository.exportSnapshot()
            val invalid = backup.copy(songs = listOf(imported, imported))
            var rejected = false
            try {
                repository.importSnapshot(invalid)
            } catch (_: IllegalArgumentException) {
                rejected = true
            }
            assertEquals(true, rejected)
            assertEquals(beforeInvalidImport, repository.exportSnapshot())
        } finally {
            database.close()
        }
    }

    @Test
    fun diskDatabaseMigratesAndRetainsChangesAfterReopen() = runTest {
        val path = roomProofDatabasePath("room-kmp-migration-${Random.nextLong()}.db")
        val versionOne = Room.databaseBuilder<RoomPersistenceProofDatabaseV1>(path)
            .setDriver(BundledSQLiteDriver())
            .build()

        try {
            versionOne.librarySongDao().upsert(
                LibrarySongRecordV1(id = "track-1", title = "Example", totalPlayTimeMillis = 12_000),
            )
        } finally {
            versionOne.close()
        }

        val migrated = Room.databaseBuilder<RoomPersistenceProofDatabase>(path)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(librarySongMigration1To2)
            .build()
        try {
            assertEquals(
                listOf(LibrarySongRecord(id = "track-1", title = "Example", totalPlayTimeMillis = 12_000)),
                migrated.librarySongDao().getLibrarySongs(),
            )
            migrated.librarySongDao().upsert(
                LibrarySongRecord(id = "track-2", title = "Second", totalPlayTimeMillis = 3_000, source = "remote"),
            )
        } finally {
            migrated.close()
        }

        val reopened = Room.databaseBuilder<RoomPersistenceProofDatabase>(path)
            .setDriver(BundledSQLiteDriver())
            .addMigrations(librarySongMigration1To2)
            .build()
        try {
            assertEquals(
                listOf(
                    LibrarySongRecord(id = "track-1", title = "Example", totalPlayTimeMillis = 12_000),
                    LibrarySongRecord(id = "track-2", title = "Second", totalPlayTimeMillis = 3_000, source = "remote"),
                ),
                reopened.librarySongDao().getLibrarySongs(),
            )
        } finally {
            reopened.close()
            deleteRoomProofDatabase(path)
        }
    }
}
