package com.convx.music.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomSchemaMigrationTest {
    @Test
    fun schema39To40PreservesSongAndDefaultsPodcastFlag() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val databaseName = "schema-39-to-40-junit-proof.db"
        val context = instrumentation.targetContext
        context.deleteDatabase(databaseName)

        try {
            val helper = MigrationTestHelper(instrumentation, InternalDatabase::class.java)
            helper.createDatabase(databaseName, 39).use { oldDatabase ->
                oldDatabase.execSQL(
                    """
                    INSERT INTO song (id, title, duration, liked, totalPlayTime)
                    VALUES ('junit-migration-proof', 'Preserved song', 210000, 1, 321)
                    """.trimIndent()
                )
            }

            helper.runMigrationsAndValidate(databaseName, 40, true, MIGRATION_39_40).use { migratedDatabase ->
                migratedDatabase.query(
                    "SELECT title, liked, totalPlayTime, isPodcastEpisode FROM song WHERE id = 'junit-migration-proof'"
                ).use { cursor ->
                    assertTrue("Song row disappeared during migration", cursor.moveToFirst())
                    assertEquals("Preserved song", cursor.getString(0))
                    assertEquals(1, cursor.getInt(1))
                    assertEquals(321L, cursor.getLong(2))
                    assertEquals(0, cursor.getInt(3))
                }
            }
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
