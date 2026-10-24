package com.convx.music.client.shared

import com.convx.music.catalog.CatalogEntry
import com.convx.music.catalog.CatalogError
import com.convx.music.catalog.CatalogHomePage
import com.convx.music.catalog.CatalogLibraryCollection
import com.convx.music.catalog.CatalogResult
import com.convx.music.catalog.CatalogSection
import com.convx.music.catalog.CatalogSearchFilter
import com.convx.music.catalog.MusicCatalogRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class CatalogScreenState(
    val sections: List<CatalogSection> = emptyList(),
    val isLoading: Boolean = false,
    val searchQuery: String = "",
    val message: String? = null,
    val continuationToken: String? = null,
    val pageTitle: String = "Listen Now",
    val canGoBack: Boolean = false,
)

class CatalogStateHolder(
    private val catalog: MusicCatalogRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val mutableState = MutableStateFlow(CatalogScreenState())
    private var request: Job? = null
    private var parentState: CatalogScreenState? = null

    val state: StateFlow<CatalogScreenState> = mutableState.asStateFlow()

    fun loadHome() = loadCatalogPage("Listen Now") { catalog.home() }

    fun loadExplore() = loadCatalogPage("Explore") { catalog.explore() }

    fun loadCharts() = loadCatalogPage("Charts") { catalog.charts() }

    private fun loadCatalogPage(
        title: String,
        load: suspend () -> CatalogResult<CatalogHomePage>,
    ) {
        request?.cancel()
        parentState = null
        request = scope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, message = null, pageTitle = title)
            when (val result = load()) {
                is CatalogResult.Success -> mutableState.value = CatalogScreenState(
                    sections = result.value.sections,
                    continuationToken = result.value.continuationToken,
                    pageTitle = title,
                )
                is CatalogResult.Failure -> mutableState.value = CatalogScreenState(
                    message = result.error.toMessage(),
                    pageTitle = title,
                )
            }
        }
    }

    fun search(query: String) {
        mutableState.value = mutableState.value.copy(searchQuery = query)
        request?.cancel()
        parentState = null
        if (query.isBlank()) {
            loadHome()
            return
        }
        request = scope.launch {
            delay(350)
            mutableState.value = mutableState.value.copy(isLoading = true, message = null)
            when (val result = catalog.search(query.trim(), CatalogSearchFilter.ALL)) {
                is CatalogResult.Success -> mutableState.value = mutableState.value.copy(
                    sections = result.value.sections,
                    isLoading = false,
                    continuationToken = result.value.continuationToken,
                )
                is CatalogResult.Failure -> mutableState.value = mutableState.value.copy(
                    sections = emptyList(),
                    isLoading = false,
                    message = result.error.toMessage(),
                )
            }
        }
    }

    fun loadLibrary(collection: CatalogLibraryCollection = CatalogLibraryCollection.RECENT_ACTIVITY) {
        request?.cancel()
        parentState = null
        request = scope.launch {
            mutableState.value = mutableState.value.copy(isLoading = true, message = null)
            when (val result = catalog.library(collection)) {
                is CatalogResult.Success -> {
                    val page = result.value
                    val section = CatalogSection(
                        id = "library-${collection.name.lowercase()}",
                        title = collection.title(),
                        entries = page.entries,
                        continuationToken = page.continuationToken,
                    )
                    mutableState.value = CatalogScreenState(
                        sections = listOf(section),
                        continuationToken = page.continuationToken,
                        pageTitle = collection.title(),
                    )
                }
                is CatalogResult.Failure -> mutableState.value = CatalogScreenState(
                    message = result.error.toMessage(),
                    pageTitle = collection.title(),
                )
            }
        }
    }

    fun openEntry(entry: CatalogEntry) {
        val id = when (entry) {
            is CatalogEntry.Track -> return
            is CatalogEntry.Album -> entry.id
            is CatalogEntry.Artist -> entry.id
            is CatalogEntry.Playlist -> entry.id
            is CatalogEntry.Browse -> entry.id
        }
        request?.cancel()
        val previous = mutableState.value
        request = scope.launch {
            mutableState.value = previous.copy(isLoading = true, message = null)
            val sectionsResult = when (entry) {
                is CatalogEntry.Album -> catalog.album(id).map { page ->
                    listOf(CatalogSection("album-${id.value}", page.album.title, page.tracks))
                }
                is CatalogEntry.Artist -> catalog.artist(id).map { page -> page.sections }
                is CatalogEntry.Playlist -> catalog.playlist(id).map { page ->
                    listOf(CatalogSection("playlist-${id.value}", page.playlist.title, page.tracks, page.continuationToken))
                }
                is CatalogEntry.Browse -> catalog.browse(id, entry.params).map { page -> page.sections }
                is CatalogEntry.Track -> return@launch
            }
            when (val result = sectionsResult) {
                is CatalogResult.Success -> {
                    parentState = previous
                    mutableState.value = CatalogScreenState(
                        sections = result.value,
                        pageTitle = entry.title,
                        canGoBack = true,
                    )
                }
                is CatalogResult.Failure -> mutableState.value = previous.copy(
                    isLoading = false,
                    message = result.error.toMessage(),
                )
            }
        }
    }

    fun goBack() {
        request?.cancel()
        parentState?.let { previous ->
            mutableState.value = previous.copy(isLoading = false)
            parentState = null
        }
    }

    fun close() {
        request?.cancel()
        scope.cancel()
    }
}

private fun CatalogLibraryCollection.title(): String = when (this) {
    CatalogLibraryCollection.RECENT_ACTIVITY -> "Recent activity"
    CatalogLibraryCollection.PLAYLISTS -> "Playlists"
    CatalogLibraryCollection.LIKED_TRACKS -> "Liked songs"
    CatalogLibraryCollection.LIKED_ALBUMS -> "Albums"
    CatalogLibraryCollection.ARTISTS -> "Artists"
    CatalogLibraryCollection.UPLOADED_TRACKS -> "Uploaded songs"
}

fun CatalogError.toMessage(): String = when (this) {
    CatalogError.Offline -> "You appear to be offline."
    CatalogError.Unauthorized -> "Sign in to load this content."
    is CatalogError.RateLimited -> "The music service is busy. Try again shortly."
    CatalogError.NotFound -> "This item is no longer available."
    CatalogError.Unavailable -> "The music service is temporarily unavailable."
    CatalogError.InvalidResponse -> "The music service returned an unsupported response."
    CatalogError.Unknown -> "Couldn't load music right now."
}

fun CatalogScreenState.visibleEntries(): List<CatalogEntry> = sections.flatMap(CatalogSection::entries)

private inline fun <T, R> CatalogResult<T>.map(transform: (T) -> R): CatalogResult<R> = when (this) {
    is CatalogResult.Success -> CatalogResult.Success(transform(value))
    is CatalogResult.Failure -> this
}
