package com.convx.music.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

enum class PlaylistOrder {
    CREATED,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
}

/** Platform-neutral playlist fields used by library rows, navigation, and grouping. */
interface PlaylistCardData {
    val id: String
    val name: String
    val artworkUrls: List<String>
    val songCount: Int
    val remoteSongCount: Int?
    val browseId: String?
    val sourceThumbnailUrl: String?
    val isEditable: Boolean
    val isBookmarked: Boolean
    val isLocal: Boolean
    val pinnedAtEpochMillis: Long?
}

/** A client-independent playlist row for library navigation and presentation. */
data class PlaylistSummary(
    override val id: String,
    override val name: String,
    override val artworkUrls: List<String>,
    override val songCount: Int,
    override val isEditable: Boolean,
    override val isBookmarked: Boolean,
    override val isLocal: Boolean,
    override val remoteSongCount: Int? = null,
    override val browseId: String? = null,
    override val sourceThumbnailUrl: String? = null,
    override val pinnedAtEpochMillis: Long? = null,
) : PlaylistCardData

/** Playlist access. Storage implementations map their own rows to these models. */
interface PlaylistRepository {
    fun observePlaylists(order: PlaylistOrder, descending: Boolean): Flow<List<PlaylistSummary>>
    suspend fun duplicateSongIds(playlistId: String, songIds: List<String>): List<String>
    suspend fun addSongs(playlistId: String, songIds: List<String>): Boolean
    suspend fun togglePin(playlistId: String): Boolean
}

class ObservePlaylists(
    private val repository: PlaylistRepository,
) {
    operator fun invoke(order: PlaylistOrder, descending: Boolean): Flow<List<PlaylistSummary>> =
        repository.observePlaylists(order, descending)
}

class ObserveLibraryPlaylists(
    private val repository: PlaylistRepository,
) {
    operator fun invoke(
        order: PlaylistOrder,
        descending: Boolean,
        hideYoutubeShorts: Boolean,
        localOnly: Boolean,
    ): Flow<List<PlaylistSummary>> =
        repository.observePlaylists(order, descending).map { playlists ->
            playlists
                .distinctBy(PlaylistSummary::id)
                .filter { !localOnly || it.browseId == null }
                .filterNot { hideYoutubeShorts && it.browseId?.startsWith("SS") == true }
        }
}

class PlaylistCommands(
    private val repository: PlaylistRepository,
) {
    suspend fun duplicateSongIds(playlistId: String, songIds: List<String>): List<String> =
        repository.duplicateSongIds(playlistId, songIds)

    suspend fun addSongs(playlistId: String, songIds: List<String>): Boolean =
        repository.addSongs(playlistId, songIds)

    suspend fun togglePin(playlistId: String): Boolean =
        repository.togglePin(playlistId)
}

/** Common Koin bindings consumed by Android, iOS, and desktop composition roots. */
val libraryCoreModule = module {
    factoryOf(::ObservePlaylists)
    factoryOf(::ObserveLibraryPlaylists)
    factoryOf(::PlaylistCommands)
}
