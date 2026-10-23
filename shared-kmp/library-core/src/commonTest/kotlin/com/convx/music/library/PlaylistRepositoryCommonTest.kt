package com.convx.music.library

import kotlinx.coroutines.flow.Flow
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

    private class FakePlaylistRepository : PlaylistRepository {
        val rows = flowOf(emptyList<PlaylistSummary>())
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
}
