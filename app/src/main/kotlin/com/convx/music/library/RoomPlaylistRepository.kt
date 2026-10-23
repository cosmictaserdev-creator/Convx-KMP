package com.convx.music.library

import com.convx.music.constants.PlaylistSortType
import com.convx.music.db.MusicDatabase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class RoomPlaylistRepository(
    private val database: MusicDatabase,
) : PlaylistRepository {
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
                )
            }
        }

    private fun PlaylistOrder.toRoomSortType(): PlaylistSortType = when (this) {
        PlaylistOrder.CREATED -> PlaylistSortType.CREATE_DATE
        PlaylistOrder.NAME -> PlaylistSortType.NAME
        PlaylistOrder.SONG_COUNT -> PlaylistSortType.SONG_COUNT
        PlaylistOrder.LAST_UPDATED -> PlaylistSortType.LAST_UPDATED
    }
}
