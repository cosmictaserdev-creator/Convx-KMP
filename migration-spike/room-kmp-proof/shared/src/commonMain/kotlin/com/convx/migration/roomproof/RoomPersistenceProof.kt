package com.convx.migration.roomproof

import androidx.room.ConstructedBy
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.SQLiteConnection
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "library_song")
data class LibrarySongRecord(
    @PrimaryKey val id: String,
    val title: String,
    val totalPlayTimeMillis: Long = 0,
    @ColumnInfo(defaultValue = "'local'") val source: String = "local",
)

@Dao
interface LibrarySongDao {
    @Query("SELECT * FROM library_song ORDER BY title COLLATE NOCASE")
    fun observeLibrarySongs(): Flow<List<LibrarySongRecord>>

    @Query("SELECT * FROM library_song ORDER BY title COLLATE NOCASE")
    suspend fun getLibrarySongs(): List<LibrarySongRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: LibrarySongRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(songs: List<LibrarySongRecord>)

    @Query("DELETE FROM library_song")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(songs: List<LibrarySongRecord>) {
        clear()
        upsertAll(songs)
    }
}

data class LibrarySnapshot(
    val version: Int,
    val songs: List<LibrarySongRecord>,
)

interface LibraryProofRepository {
    fun observeSongs(): Flow<List<LibrarySongRecord>>
    suspend fun upsert(song: LibrarySongRecord)
    suspend fun exportSnapshot(): LibrarySnapshot
    suspend fun importSnapshot(snapshot: LibrarySnapshot)
}

class RoomLibraryProofRepository(
    private val dao: LibrarySongDao,
) : LibraryProofRepository {
    override fun observeSongs(): Flow<List<LibrarySongRecord>> = dao.observeLibrarySongs()

    override suspend fun upsert(song: LibrarySongRecord) {
        dao.upsert(song)
    }

    override suspend fun exportSnapshot(): LibrarySnapshot =
        LibrarySnapshot(version = LIBRARY_SNAPSHOT_VERSION, songs = dao.getLibrarySongs())

    override suspend fun importSnapshot(snapshot: LibrarySnapshot) {
        require(snapshot.version == LIBRARY_SNAPSHOT_VERSION) { "Unsupported library snapshot version" }
        require(snapshot.songs.map(LibrarySongRecord::id).distinct().size == snapshot.songs.size) {
            "Library snapshot contains duplicate song IDs"
        }
        require(snapshot.songs.all { it.id.isNotBlank() && it.title.isNotBlank() && it.totalPlayTimeMillis >= 0 }) {
            "Library snapshot contains an invalid song"
        }
        dao.replaceAll(snapshot.songs)
    }

    private companion object {
        const val LIBRARY_SNAPSHOT_VERSION = 1
    }
}

@Database(
    entities = [LibrarySongRecord::class],
    version = 2,
    exportSchema = true,
)
@ConstructedBy(RoomPersistenceProofDatabaseConstructor::class)
abstract class RoomPersistenceProofDatabase : RoomDatabase() {
    abstract fun librarySongDao(): LibrarySongDao
}

@Entity(tableName = "library_song")
data class LibrarySongRecordV1(
    @PrimaryKey val id: String,
    val title: String,
    val totalPlayTimeMillis: Long = 0,
)

@Dao
interface LibrarySongV1Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(song: LibrarySongRecordV1)
}

@Database(
    entities = [LibrarySongRecordV1::class],
    version = 1,
    exportSchema = true,
)
@ConstructedBy(RoomPersistenceProofDatabaseV1Constructor::class)
abstract class RoomPersistenceProofDatabaseV1 : RoomDatabase() {
    abstract fun librarySongDao(): LibrarySongV1Dao
}

val librarySongMigration1To2 = object : Migration(1, 2) {
    override fun migrate(connection: SQLiteConnection) {
        connection.execSQL("ALTER TABLE library_song ADD COLUMN source TEXT NOT NULL DEFAULT 'local'")
    }
}

expect fun roomProofDatabasePath(name: String): String
expect fun deleteRoomProofDatabase(path: String)

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object RoomPersistenceProofDatabaseConstructor : RoomDatabaseConstructor<RoomPersistenceProofDatabase> {
    override fun initialize(): RoomPersistenceProofDatabase
}

@Suppress("NO_ACTUAL_FOR_EXPECT")
expect object RoomPersistenceProofDatabaseV1Constructor : RoomDatabaseConstructor<RoomPersistenceProofDatabaseV1> {
    override fun initialize(): RoomPersistenceProofDatabaseV1
}
