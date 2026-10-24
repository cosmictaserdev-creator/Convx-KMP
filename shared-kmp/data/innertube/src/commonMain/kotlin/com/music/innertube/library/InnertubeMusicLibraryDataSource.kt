package com.music.innertube.library

import com.convx.music.library.MusicLibraryMutationError
import com.convx.music.library.MusicLibraryMutationResult
import com.convx.music.library.MusicLibraryRemoteDataSource
import com.music.innertube.InnertubeRepository
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.ResponseException
import kotlinx.coroutines.CancellationException
import kotlinx.io.IOException

class InnertubeMusicLibraryDataSource(
    private val innertube: InnertubeRepository,
) : MusicLibraryRemoteDataSource {
    override suspend fun setSongSaved(songId: String, isSaved: Boolean): MusicLibraryMutationResult {
        if (songId.isBlank()) return MusicLibraryMutationResult.Failure(MusicLibraryMutationError.REJECTED)

        return try {
            val result = innertube.toggleSongLibrary(songId, addToLibrary = isSaved)
            result.fold(
                onSuccess = { processed ->
                    if (processed) MusicLibraryMutationResult.Success
                    else MusicLibraryMutationResult.Failure(MusicLibraryMutationError.REJECTED)
                },
                onFailure = { error -> error.toMutationFailure() },
            )
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Exception) {
            error.toMutationFailure()
        }
    }
}

private fun Throwable.toMutationFailure(): MusicLibraryMutationResult.Failure {
    if (this is CancellationException) throw this
    val mutationError = when (this) {
        is ResponseException -> when (response.status.value) {
            401, 403 -> MusicLibraryMutationError.UNAUTHORIZED
            else -> MusicLibraryMutationError.UNAVAILABLE
        }
        is HttpRequestTimeoutException, is IOException -> MusicLibraryMutationError.OFFLINE
        else -> MusicLibraryMutationError.UNAVAILABLE
    }
    return MusicLibraryMutationResult.Failure(mutationError)
}
