package com.convx.music.library

enum class MusicLibraryMutationError {
    UNAUTHORIZED,
    OFFLINE,
    REJECTED,
    UNAVAILABLE,
}

sealed interface MusicLibraryMutationResult {
    data object Success : MusicLibraryMutationResult
    data class Failure(val error: MusicLibraryMutationError) : MusicLibraryMutationResult
}

/** Writes one track's saved state to the signed-in music provider. */
interface MusicLibraryRemoteDataSource {
    suspend fun setSongSaved(songId: String, isSaved: Boolean): MusicLibraryMutationResult
}

/** Adds a track to one editable playlist on the signed-in music provider. */
interface MusicPlaylistRemoteDataSource {
    suspend fun addSongToPlaylist(playlistId: String, songId: String): MusicLibraryMutationResult
}

class SetMusicLibraryTrackSaved(
    private val remote: MusicLibraryRemoteDataSource,
) {
    suspend operator fun invoke(songId: String, isSaved: Boolean): MusicLibraryMutationResult =
        remote.setSongSaved(songId, isSaved)
}

class AddSongToMusicPlaylist(
    private val remote: MusicPlaylistRemoteDataSource,
) {
    suspend operator fun invoke(playlistId: String, songId: String): MusicLibraryMutationResult =
        remote.addSongToPlaylist(playlistId, songId)
}
