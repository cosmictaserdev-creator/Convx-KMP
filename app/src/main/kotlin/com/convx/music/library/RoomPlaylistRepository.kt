package com.convx.music.library

import com.convx.music.constants.PlaylistSortType
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.ZoneId

class RoomPlaylistRepository(
    private val database: MusicDatabase,
) : PlaylistRepository, PlaylistMenuDataSource {
    override suspend fun loadPlaylist(playlistId: String): Playlist? = withContext(Dispatchers.IO) {
        database.playlist(playlistId).firstOrNull()
    }

    override fun observePlaylists(order: PlaylistOrder, descending: Boolean): Flow<List<PlaylistSummary>> =
        database.playlists(order.toRoomSortType(), descending).map { playlists ->
            playlists.map { playlist ->
                PlaylistSummary(
                    id = playlist.id,
                    name = playlist.playlist.name,
                    artworkUrls = playlist.thumbnails,
                    songCount = playlist.songCount,
                    remoteSongCount = playlist.playlist.remoteSongCount,
                    isEditable = playlist.playlist.isEditable,
                    isBookmarked = playlist.playlist.bookmarkedAt != null,
                    isLocal = playlist.playlist.isLocal,
                    browseId = playlist.playlist.browseId,
                    sourceThumbnailUrl = playlist.playlist.thumbnailUrl,
                    pinnedAtEpochMillis = playlist.playlist.pinnedAt
                        ?.atZone(ZoneId.systemDefault())
                        ?.toInstant()
                        ?.toEpochMilli(),
                )
            }
        }

    override fun observePlaylist(playlistId: String): Flow<PlaylistDetail?> =
        database.playlist(playlistId).map { playlist ->
            playlist?.let {
                PlaylistDetail(
                    id = it.id,
                    name = it.playlist.name,
                    artworkUrls = it.thumbnails,
                    songCount = it.songCount,
                    remoteSongCount = it.playlist.remoteSongCount,
                    browseId = it.playlist.browseId,
                    sourceThumbnailUrl = it.playlist.thumbnailUrl,
                    isEditable = it.playlist.isEditable,
                    isBookmarked = it.playlist.bookmarkedAt != null,
                    isLocal = it.playlist.isLocal,
                    pinnedAtEpochMillis = it.playlist.pinnedAt
                        ?.atZone(ZoneId.systemDefault())
                        ?.toInstant()
                        ?.toEpochMilli(),
                    description = it.playlist.description,
                    playEndpointParams = it.playlist.playEndpointParams,
                    shuffleEndpointParams = it.playlist.shuffleEndpointParams,
                    radioEndpointParams = it.playlist.radioEndpointParams,
                )
            }
        }

    override suspend fun duplicateSongIds(playlistId: String, songIds: List<String>): List<String> =
        withContext(Dispatchers.IO) {
            if (songIds.isEmpty()) emptyList() else database.playlistDuplicates(playlistId, songIds).distinct()
        }

    override suspend fun addSongs(playlistId: String, songIds: List<String>): Boolean =
        withContext(Dispatchers.IO) {
            val playlist = database.playlist(playlistId).firstOrNull() ?: return@withContext false
            if (songIds.isNotEmpty()) database.addSongToPlaylist(playlist, songIds)
            true
        }

    override suspend fun togglePin(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        val playlist = database.playlist(playlistId).firstOrNull() ?: return@withContext false
        database.query { update(playlist.playlist.togglePin()) }
        true
    }

    override suspend fun updatePlaylist(
        playlistId: String,
        name: String,
        description: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        val playlist = database.playlist(playlistId).firstOrNull() ?: return@withContext false
        database.withTransaction {
            update(
                playlist.playlist.copy(
                    name = name,
                    description = description,
                    lastUpdateTime = LocalDateTime.now(),
                ),
            )
        }
        true
    }

    override suspend fun deletePlaylist(playlistId: String): Boolean = withContext(Dispatchers.IO) {
        val playlist = database.playlist(playlistId).firstOrNull() ?: return@withContext false
        database.withTransaction { delete(playlist.playlist) }
        true
    }

    override suspend fun clearPlaylistSongs(playlistId: String) = withContext(Dispatchers.IO) {
        database.withTransaction { clearPlaylist(playlistId) }
    }

    override suspend fun normalizePlaylistSongPositions(playlistId: String) = withContext(Dispatchers.IO) {
        val songs = database.playlistSongMaps(playlistId, 0)
            .sortedWith(compareBy({ it.position }, { it.id }))
        database.withTransaction {
            songs.forEachIndexed { index, song ->
                if (song.position != index) update(song.copy(position = index))
            }
        }
    }

    override suspend fun movePlaylistSong(
        playlistId: String,
        fromPosition: Int,
        toPosition: Int,
    ): List<PlaylistOrderEntry> = withContext(Dispatchers.IO) {
        database.withTransaction { move(playlistId, fromPosition, toPosition) }
        database.playlistSongMaps(playlistId, 0).map { entry ->
            PlaylistOrderEntry(songId = entry.songId, setVideoId = entry.setVideoId)
        }
    }

    override suspend fun removePlaylistSong(
        playlistId: String,
        entry: PlaylistEntryReference,
    ): PlaylistSongRemoval = withContext(Dispatchers.IO) {
        val mapId = entry.id.toIntOrNull()
            ?: return@withContext PlaylistSongRemoval(removed = false, setVideoId = null)
        val map = database.playlistSongMaps(entry.songId)
            .firstOrNull { it.id == mapId && it.playlistId == playlistId }
            ?: return@withContext PlaylistSongRemoval(removed = false, setVideoId = null)
        val setVideoId = database.getSetVideoId(entry.songId)?.setVideoId
        database.withTransaction {
            move(playlistId, map.position, Int.MAX_VALUE)
            delete(map.copy(position = Int.MAX_VALUE))
        }
        PlaylistSongRemoval(removed = true, setVideoId = setVideoId)
    }

    private fun PlaylistOrder.toRoomSortType(): PlaylistSortType = when (this) {
        PlaylistOrder.CREATED -> PlaylistSortType.CREATE_DATE
        PlaylistOrder.NAME -> PlaylistSortType.NAME
        PlaylistOrder.SONG_COUNT -> PlaylistSortType.SONG_COUNT
        PlaylistOrder.LAST_UPDATED -> PlaylistSortType.LAST_UPDATED
    }
}

interface PlaylistMenuDataSource {
    suspend fun loadPlaylist(playlistId: String): Playlist?
}
