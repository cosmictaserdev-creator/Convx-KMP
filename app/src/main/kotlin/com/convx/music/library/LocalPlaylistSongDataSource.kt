package com.convx.music.library

import com.convx.music.db.entities.PlaylistSong
import kotlinx.coroutines.flow.Flow

/** Android row source for playlist screens that need platform Song objects for playback. */
interface LocalPlaylistSongDataSource {
    fun observeSongs(playlistId: String): Flow<List<PlaylistSong>>

    suspend fun loadSongs(playlistId: String): List<PlaylistSong>
}
