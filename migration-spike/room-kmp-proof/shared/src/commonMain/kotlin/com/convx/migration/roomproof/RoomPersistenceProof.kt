package com.convx.migration.roomproof

import androidx.room.ConstructedBy
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "library_song")
data class LibrarySongRecord(
    @PrimaryKey val id: String,
    val title: String,
    val totalPlayTimeMillis: Long = 0,
)

@Dao
interface LibrarySongDao {
    @Query("SELECT * FROM library_song ORDER BY title COLLATE NOCASE")
    fun observeLibrarySongs(): Flow<List<LibrarySongRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: LibrarySongRecord)
}

@Database(
    entities = [LibrarySongRecord::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(RoomPersistenceProofDatabaseConstructor::class)
abstract class RoomPersistenceProofDatabase : RoomDatabase() {
    abstract fun librarySongDao(): LibrarySongDao
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object RoomPersistenceProofDatabaseConstructor : RoomDatabaseConstructor<RoomPersistenceProofDatabase> {
    override fun initialize(): RoomPersistenceProofDatabase
}
