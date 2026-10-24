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

class SetMusicLibraryTrackSaved(
    private val remote: MusicLibraryRemoteDataSource,
) {
    suspend operator fun invoke(songId: String, isSaved: Boolean): MusicLibraryMutationResult =
        remote.setSongSaved(songId, isSaved)
}
