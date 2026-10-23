package com.convx.music.library

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import org.koin.dsl.module
import org.koin.dsl.koinApplication

class PlaylistRepositoryCommonTest {
    @Test
    fun sharedKoinModuleResolvesPlaylistUseCaseAgainstPlatformRepository() {
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

            assertSame(repository.rows, result)
            assertEquals(PlaylistOrder.NAME, repository.lastOrder)
            assertEquals(false, repository.lastDescending)
        } finally {
            app.close()
        }
    }

    private class FakePlaylistRepository : PlaylistRepository {
        val rows = flowOf(emptyList<PlaylistSummary>())
        var lastOrder: PlaylistOrder? = null
        var lastDescending: Boolean? = null

        override fun observePlaylists(order: PlaylistOrder, descending: Boolean): Flow<List<PlaylistSummary>> {
            lastOrder = order
            lastDescending = descending
            return rows
        }
    }
}
