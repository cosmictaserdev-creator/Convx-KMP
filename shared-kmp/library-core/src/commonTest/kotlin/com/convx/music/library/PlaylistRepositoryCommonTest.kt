package com.convx.music.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.koin.dsl.module
import org.koin.dsl.koinApplication

class PlaylistRepositoryCommonTest {
    @Test
    fun sharedKoinModuleResolvesPlaylistUseCaseAgainstPlatformRepository() = runTest {
        val repository = FakePlaylistRepository()
        val app = koinApplication {
            modules(
                libraryCoreModule,
                module { single<PlaylistRepository> { repository } },
            )
        }

        try {
            val useCase = app.koin.get<ObservePlaylists>()
            val result = useCase(PlaylistOrder.NAME, descending = false)
            val commands = app.koin.get<PlaylistCommands>()
            val duplicateIds = commands.duplicateSongIds("playlist-1", listOf("song-1", "song-2"))
            val added = commands.addSongs("playlist-1", listOf("song-3"))

            assertSame(repository.rows, result)
            assertEquals(PlaylistOrder.NAME, repository.lastOrder)
            assertEquals(false, repository.lastDescending)
            assertEquals(listOf("song-1"), duplicateIds)
            assertEquals("playlist-1", repository.checkedPlaylistId)
            assertEquals(listOf("song-1", "song-2"), repository.checkedSongIds)
            assertEquals(true, added)
            assertEquals("playlist-1", repository.addedPlaylistId)
            assertEquals(listOf("song-3"), repository.addedSongIds)
        } finally {
            app.close()
        }
    }

    @Test
    fun libraryObservationFiltersShortsAndHonorsLocalOnly() = runTest {
        val repository = FakePlaylistRepository(
            listOf(
                playlistSummary("remote", browseId = "VLPL123"),
                playlistSummary("shorts", browseId = "SS123"),
                playlistSummary("local"),
            ),
        )
        val app = koinApplication {
            modules(
                libraryCoreModule,
                module { single<PlaylistRepository> { repository } },
            )
        }

        try {
            val observeLibraryPlaylists = app.koin.get<ObserveLibraryPlaylists>()
            val visible = observeLibraryPlaylists(
                order = PlaylistOrder.NAME,
                descending = false,
                hideYoutubeShorts = true,
                localOnly = false,
            ).first()
            val localOnly = observeLibraryPlaylists(
                order = PlaylistOrder.NAME,
                descending = false,
                hideYoutubeShorts = true,
                localOnly = true,
            ).first()

            assertEquals(listOf("remote", "local"), visible.map(PlaylistSummary::id))
            assertEquals(listOf("local"), localOnly.map(PlaylistSummary::id))
            assertEquals(PlaylistOrder.NAME, repository.lastOrder)
            assertEquals(false, repository.lastDescending)
        } finally {
            app.close()
        }
    }

    private class FakePlaylistRepository(
        playlists: List<PlaylistSummary> = emptyList(),
    ) : PlaylistRepository {
        val rows = flowOf(playlists)
        var lastOrder: PlaylistOrder? = null
        var lastDescending: Boolean? = null
        var checkedPlaylistId: String? = null
        var checkedSongIds: List<String>? = null
        var addedPlaylistId: String? = null
        var addedSongIds: List<String>? = null

        override fun observePlaylists(order: PlaylistOrder, descending: Boolean): Flow<List<PlaylistSummary>> {
            lastOrder = order
            lastDescending = descending
            return rows
        }

        override suspend fun duplicateSongIds(playlistId: String, songIds: List<String>): List<String> {
            checkedPlaylistId = playlistId
            checkedSongIds = songIds
            return listOf("song-1")
        }

        override suspend fun addSongs(playlistId: String, songIds: List<String>): Boolean {
            addedPlaylistId = playlistId
            addedSongIds = songIds
            return true
        }
    }

    private fun playlistSummary(id: String, browseId: String? = null) = PlaylistSummary(
        id = id,
        name = id,
        artworkUrls = emptyList(),
        songCount = 0,
        isEditable = true,
        isBookmarked = false,
        isLocal = browseId == null,
        browseId = browseId,
    )
}
