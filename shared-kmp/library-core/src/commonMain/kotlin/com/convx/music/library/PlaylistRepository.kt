package com.convx.music.library

import kotlinx.coroutines.flow.Flow
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

enum class PlaylistOrder {
    CREATED,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
}

/** Minimum platform-neutral data required to render a playlist row or card. */
interface PlaylistCardData {
    val id: String
    val name: String
    val artworkUrls: List<String>
    val songCount: Int
    val remoteSongCount: Int?
}

/** A client-independent playlist row for library navigation and presentation. */
data class PlaylistSummary(
    override val id: String,
    override val name: String,
    override val artworkUrls: List<String>,
    override val songCount: Int,
    val isEditable: Boolean,
    val isBookmarked: Boolean,
    val isLocal: Boolean,
    override val remoteSongCount: Int? = null,
) : PlaylistCardData

/** Playlist access. Storage implementations map their own rows to these models. */
interface PlaylistRepository {
    fun observePlaylists(order: PlaylistOrder, descending: Boolean): Flow<List<PlaylistSummary>>
    suspend fun duplicateSongIds(playlistId: String, songIds: List<String>): List<String>
    suspend fun addSongs(playlistId: String, songIds: List<String>): Boolean
}

class ObservePlaylists(
    private val repository: PlaylistRepository,
) {
    operator fun invoke(order: PlaylistOrder, descending: Boolean): Flow<List<PlaylistSummary>> =
        repository.observePlaylists(order, descending)
}

class PlaylistCommands(
    private val repository: PlaylistRepository,
) {
    suspend fun duplicateSongIds(playlistId: String, songIds: List<String>): List<String> =
        repository.duplicateSongIds(playlistId, songIds)

    suspend fun addSongs(playlistId: String, songIds: List<String>): Boolean =
        repository.addSongs(playlistId, songIds)
}

/** Common Koin bindings consumed by Android, iOS, and desktop composition roots. */
val libraryCoreModule = module {
    factoryOf(::ObservePlaylists)
    factoryOf(::PlaylistCommands)
}
