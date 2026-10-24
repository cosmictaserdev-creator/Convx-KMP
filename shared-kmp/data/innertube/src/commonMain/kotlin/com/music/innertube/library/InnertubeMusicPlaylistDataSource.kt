package com.music.innertube.library

import com.convx.music.library.MusicLibraryMutationError
import com.convx.music.library.MusicLibraryMutationResult
import com.convx.music.library.MusicPlaylistRemoteDataSource
import com.music.innertube.InnertubeRepository
import kotlinx.coroutines.CancellationException

class InnertubeMusicPlaylistDataSource(
    private val innertube: InnertubeRepository,
) : MusicPlaylistRemoteDataSource {
    override suspend fun addSongToPlaylist(
        playlistId: String,
        songId: String,
    ): MusicLibraryMutationResult {
        if (playlistId.isBlank() || songId.isBlank()) {
            return MusicLibraryMutationResult.Failure(MusicLibraryMutationError.REJECTED)
        }

        return try {
            innertube.addToPlaylist(playlistId, songId).fold(
                onSuccess = { MusicLibraryMutationResult.Success },
                onFailure = { error -> error.toMusicLibraryMutationFailure() },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            error.toMusicLibraryMutationFailure()
        }
    }
}
