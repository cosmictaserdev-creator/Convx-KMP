package com.convx.music.library

import kotlinx.coroutines.flow.Flow

enum class PlaylistOrder {
    CREATED,
    NAME,
    SONG_COUNT,
    LAST_UPDATED,
}

/** A client-independent playlist row for library navigation and presentation. */
data class PlaylistSummary(
    val id: String,
    val name: String,
    val artworkUrls: List<String>,
    val songCount: Int,
    val isEditable: Boolean,
    val isBookmarked: Boolean,
    val isLocal: Boolean,
)

/** Read-only playlist access. Storage implementations map their own rows to this model. */
interface PlaylistRepository {
    fun observePlaylists(order: PlaylistOrder, descending: Boolean): Flow<List<PlaylistSummary>>
}
