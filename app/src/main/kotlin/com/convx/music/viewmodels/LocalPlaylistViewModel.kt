/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.viewmodels

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.convx.music.constants.HideVideoSongsKey
import com.convx.music.constants.DataSaverEnabledKey
import com.convx.music.constants.PendingPlaylistDeletesKey
import com.convx.music.constants.PlaylistSongSortDescendingKey
import com.convx.music.constants.PlaylistSongSortType
import com.convx.music.constants.PlaylistSongSortTypeKey
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.PlaylistSong
import com.convx.music.library.PlaylistCommands
import com.convx.music.library.ObservePlaylist
import com.convx.music.library.PlaylistEntryReference
import com.convx.music.library.PlaylistTrackOrder
import com.convx.music.library.PlaylistTrackSortData
import com.convx.music.library.orderPlaylistTracks
import com.convx.music.extensions.toEnum
import com.convx.music.utils.SyncUtils
import com.convx.music.utils.dataStore
import com.music.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.Collator
import java.util.Locale

class LocalPlaylistViewModel(
    private val context: Context,
    database: MusicDatabase,
    private val playlistCommands: PlaylistCommands,
    private val observePlaylist: ObservePlaylist,
    private val syncUtils: SyncUtils,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    val playlistId = requireNotNull(savedStateHandle.get<String>("playlistId")) { "Missing playlistId" }
    val thumbArg = savedStateHandle.get<String>("thumb")?.takeIf { it.isNotEmpty() }
    val playlistDetail = observePlaylist(playlistId)
        .stateIn(viewModelScope, SharingStarted.Lazily, null)
    val playlistSongs: StateFlow<List<PlaylistSong>> =
        combine(
            database.playlistSongs(playlistId),
            context.dataStore.data
                .map {
                    Triple(
                        it[PlaylistSongSortTypeKey].toEnum(PlaylistSongSortType.CUSTOM),
                        it[PlaylistSongSortDescendingKey] ?: true,
                        (it[HideVideoSongsKey] ?: false) || (it[DataSaverEnabledKey] ?: false)
                    )
                }.distinctUntilChanged(),
        ) { songs, (sortType, sortDescending, hideVideoSongs) ->
            val collator = Collator.getInstance(Locale.getDefault()).apply {
                strength = Collator.PRIMARY
            }
            val order = when (sortType) {
                PlaylistSongSortType.CUSTOM -> PlaylistTrackOrder.CUSTOM
                PlaylistSongSortType.CREATE_DATE -> PlaylistTrackOrder.CREATED
                PlaylistSongSortType.NAME -> PlaylistTrackOrder.NAME
                PlaylistSongSortType.ARTIST -> PlaylistTrackOrder.ARTIST
                PlaylistSongSortType.PLAY_TIME -> PlaylistTrackOrder.PLAY_TIME
            }
            val orderedEntryIds = orderPlaylistTracks(
                tracks = songs.map { playlistSong ->
                    PlaylistTrackSortData(
                        entryId = playlistSong.map.id.toString(),
                        creationOrder = playlistSong.map.id.toLong(),
                        title = playlistSong.song.song.title,
                        artistNames = playlistSong.song.artists.map { it.name },
                        albumTitle = playlistSong.song.album?.title,
                        totalPlayTimeMillis = playlistSong.song.song.totalPlayTime,
                        isVideo = playlistSong.song.song.isVideo,
                    )
                },
                order = order,
                descending = sortDescending,
                hideVideos = hideVideoSongs,
                compareText = { first, second -> collator.compare(first, second) },
            ).map(PlaylistTrackSortData::entryId)
            val songsByEntryId = songs.associateBy { it.map.id.toString() }
            orderedEntryIds.mapNotNull(songsByEntryId::get)
        }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    init {
        refresh()

        viewModelScope.launch(Dispatchers.IO) {
            playlistCommands.normalizePlaylistSongPositions(playlistId)
        }
    }

    fun updatePlaylist(name: String, description: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            if (playlistCommands.updatePlaylist(playlistId, name, description)) {
                observePlaylist(playlistId).first()?.browseId?.let { YouTube.renamePlaylist(it, name) }
            }
        }
    }

    fun deletePlaylist() {
        viewModelScope.launch(Dispatchers.IO) {
            val browseId = observePlaylist(playlistId).first()?.browseId
            if (!playlistCommands.deletePlaylist(playlistId) || browseId == null) return@launch
            // Record remote deletion before the request so a concurrent saved-playlist
            // sync cannot restore the row while the request is in flight.
            context.dataStore.edit {
                it[PendingPlaylistDeletesKey] = (it[PendingPlaylistDeletesKey] ?: emptySet()) + browseId
            }
            YouTube.deletePlaylist(browseId)
        }
    }

    fun clearPlaylistSongs() {
        viewModelScope.launch(Dispatchers.IO) {
            playlistCommands.clearPlaylistSongs(playlistId)
        }
    }

    fun movePlaylistSong(fromPosition: Int, toPosition: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val order = playlistCommands.movePlaylistSong(playlistId, fromPosition, toPosition)
            val browseId = observePlaylist(playlistId).first()?.browseId ?: return@launch
            val successorIndex = if (fromPosition > toPosition) toPosition else toPosition + 1
            val successorSetVideoId = order.getOrNull(successorIndex)?.setVideoId
            order.getOrNull(fromPosition)?.setVideoId?.let { setVideoId ->
                YouTube.moveSongPlaylist(browseId, setVideoId, successorSetVideoId)
            }
        }
    }

    fun removePlaylistSong(entryId: Int, songId: String, position: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val removal = playlistCommands.removePlaylistSong(
                playlistId,
                PlaylistEntryReference(
                    id = entryId.toString(),
                    songId = songId,
                    position = position,
                ),
            )
            if (!removal.removed) return@launch
            val browseId = observePlaylist(playlistId).first()?.browseId ?: return@launch
            removal.setVideoId?.let { setVideoId ->
                YouTube.removeFromPlaylist(browseId, songId, setVideoId)
            }
        }
    }

    /**
     * Re-syncs this playlist against YouTube. Bound to the pull-to-refresh gesture.
     * No-op for a purely local playlist, which has no browseId to sync against.
     */
    fun refresh() {
        viewModelScope.launch {
            observePlaylist(playlistId).first()?.browseId?.let { browseId ->
                syncUtils.syncPlaylist(browseId, playlistId)
            }
        }
    }
}
