package com.convx.music.catalog.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.window.Dialog
import com.convx.music.catalog.CatalogEntry
import com.convx.music.catalog.CatalogLibraryCollection
import com.convx.music.catalog.YOUTUBE_MUSIC_PROVIDER
import com.convx.music.playback.PlaybackController
import com.convx.music.playback.PlaybackQueueController
import com.convx.music.playback.PlaybackQueueSnapshot
import com.convx.music.playback.PlaybackRequestTrack
import com.convx.music.ui.ios.component.IosText
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.ios.foundation.ProvideIosTheme
import com.convx.music.ui.ios.foundation.iosTypography
import com.convx.music.ui.ios.foundation.lightIosColors
import kotlinx.coroutines.flow.collect
import org.koin.compose.koinInject

@Composable
fun MusicBrowserScreen(
    playbackController: PlaybackController? = null,
    modifier: Modifier = Modifier,
    isSignedIn: Boolean = false,
    onSignInRequested: () -> Unit = {},
) {
    val catalog: CatalogStateHolder = koinInject()
    val libraryActions: CatalogLibraryActionStateHolder = koinInject()
    val playlistActions: CatalogPlaylistActionStateHolder = koinInject()
    val state by catalog.state.collectAsState()
    val libraryActionState by libraryActions.state.collectAsState()
    val playlistActionState by playlistActions.state.collectAsState()
    val playback = playbackController?.playbackSnapshot?.collectAsState()?.value
    val queueSnapshot = playbackController?.queueSnapshot?.collectAsState()?.value ?: PlaybackQueueSnapshot.EMPTY
    val listState = rememberLazyListState()
    var showQueue by remember { mutableStateOf(false) }
    val queue = remember(state.sections) {
        state.sections.flatMap { section ->
            section.entries.filterIsInstance<CatalogEntry.Track>()
                .filter { it.isPlayable }
                .map { track ->
                    PlaybackRequestTrack(
                        providerId = track.id.providerId,
                        id = track.id.value,
                        title = track.title,
                        artists = track.artists.map { it.name },
                        durationMs = track.durationMillis,
                        artworkUrl = track.artworkUrls.firstOrNull(),
                    )
                }
        }
    }
    DisposableEffect(catalog, libraryActions, playlistActions) {
        onDispose {
            catalog.close()
            libraryActions.close()
            playlistActions.close()
        }
    }
    LaunchedEffect(catalog) { catalog.loadHome() }
    LaunchedEffect(listState, state.continuationToken, state.isLoading, state.isLoadingMore, state.continuationError) {
        if (state.continuationToken == null || state.isLoading || state.isLoadingMore || state.continuationError != null) {
            return@LaunchedEffect
        }
        snapshotFlow {
            val layout = listState.layoutInfo
            layout.visibleItemsInfo.lastOrNull()?.index to layout.totalItemsCount
        }.collect { (lastVisibleIndex, itemCount) ->
            if (itemCount > 0 && lastVisibleIndex != null && lastVisibleIndex >= itemCount - 5) {
                catalog.loadMore()
            }
        }
    }

    ProvideIosTheme(colors = lightIosColors(), typography = iosTypography()) {
        Column(
            modifier = modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IosText(state.pageTitle, style = IosTheme.typography.title, color = IosTheme.colors.label)
                if (state.canGoBack) {
                    IosText("Back", color = IosTheme.colors.secondaryLabel, modifier = Modifier.clickable(onClick = catalog::goBack))
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BrowserDestination("Home", state.pageTitle == "Listen Now" && !state.canGoBack) { catalog.loadHome() }
                BrowserDestination("Library", state.pageTitle == "Recent activity" && !state.canGoBack) {
                    catalog.loadLibrary(CatalogLibraryCollection.RECENT_ACTIVITY)
                }
                BrowserDestination("Playlists", state.pageTitle == "Playlists" && !state.canGoBack) {
                    catalog.loadLibrary(CatalogLibraryCollection.PLAYLISTS)
                }
                BrowserDestination("Songs", state.pageTitle == "Liked songs" && !state.canGoBack) {
                    catalog.loadLibrary(CatalogLibraryCollection.LIKED_TRACKS)
                }
            }
            if (!state.canGoBack) {
                BasicTextField(
                    value = state.searchQuery,
                    onValueChange = catalog::search,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    decorationBox = { field ->
                        if (state.searchQuery.isEmpty()) {
                            IosText("Search songs, artists, albums", color = IosTheme.colors.secondaryLabel)
                        }
                        field()
                    },
                )
            }
            playback?.let { current ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        IosText(current.track?.title ?: "Choose a song", color = IosTheme.colors.label)
                        IosText(
                            current.errorMessage ?: current.track?.artists?.joinToString().orEmpty(),
                            color = IosTheme.colors.secondaryLabel,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        IosText("Previous", color = IosTheme.colors.label, modifier = Modifier.clickable {
                            playbackController?.requestSkip(forward = false)
                        })
                        IosText(
                            if (current.isPlaying) "Pause" else "Play",
                            color = IosTheme.colors.label,
                            modifier = Modifier.clickable {
                                if (current.isPlaying) playbackController?.pause() else playbackController?.play()
                            },
                        )
                        IosText("Next", color = IosTheme.colors.label, modifier = Modifier.clickable {
                            playbackController?.requestSkip(forward = true)
                        })
                        if (playbackController != null) {
                            IosText("Queue", color = IosTheme.colors.label, modifier = Modifier.clickable {
                                showQueue = true
                            })
                        }
                    }
                }
            }
            when {
                state.isLoading -> IosText("Loading music…", color = IosTheme.colors.secondaryLabel)
                state.message != null -> IosText(state.message.orEmpty(), color = IosTheme.colors.secondaryLabel)
                state.sections.isEmpty() -> IosText("No music to show yet.", color = IosTheme.colors.secondaryLabel)
            }
            libraryActionState.message?.let { message ->
                IosText(message, color = IosTheme.colors.secondaryLabel)
            }
            playlistActionState.message?.let { message ->
                IosText(message, color = IosTheme.colors.secondaryLabel)
            }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                state.sections.forEach { section ->
                    item(key = section.id) {
                        IosText(section.title, style = IosTheme.typography.title2, color = IosTheme.colors.label)
                    }
                    items(section.entries, key = { "${section.id}:${it.id.value}" }) { entry ->
                        CatalogEntryRow(
                            entry = entry,
                            isSignedIn = isSignedIn,
                            isSaved = (entry as? CatalogEntry.Track)?.let(libraryActions::isSaved) ?: false,
                            isLibraryActionPending = (entry as? CatalogEntry.Track)?.let(libraryActions::isPending) ?: false,
                            onSignInRequested = onSignInRequested,
                            onLibraryAction = { track, isSaved -> libraryActions.setSaved(track, isSaved) },
                            onAddToPlaylist = { track ->
                                if (isSignedIn) playlistActions.openPicker(track) else onSignInRequested()
                            },
                        ) {
                            if (entry is CatalogEntry.Track) {
                                if (entry.isPlayable) {
                                    val queueIndex = queue.indexOfFirst { it.id == entry.id.value }
                                    if (queueIndex >= 0) playbackController?.playQueue(queue, queueIndex)
                                }
                            } else {
                                catalog.openEntry(entry)
                            }
                        }
                    }
                }
                when {
                    state.isLoadingMore -> item(key = "catalog-loading-more") {
                        IosText("Loading more...", color = IosTheme.colors.secondaryLabel)
                    }
                    state.continuationError != null -> item(key = "catalog-load-more-error") {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            IosText(state.continuationError.orEmpty(), color = IosTheme.colors.secondaryLabel)
                            IosText("Retry", color = IosTheme.colors.label, modifier = Modifier.clickable {
                                catalog.loadMore()
                            })
                        }
                    }
                }
            }
        }
        if (showQueue && playbackController != null) {
            PlaybackQueueDialog(
                queue = queueSnapshot,
                controller = playbackController,
                onDismiss = { showQueue = false },
            )
        }
        if (playlistActionState.isPickerVisible) {
            PlaylistPickerDialog(
                state = playlistActionState,
                onDismiss = playlistActions::dismissPicker,
                onSelectPlaylist = playlistActions::addToPlaylist,
                onLoadMore = playlistActions::loadMorePlaylists,
                onRetry = playlistActions::retryPlaylists,
            )
        }
    }
}

@Composable
private fun PlaybackQueueDialog(
    queue: PlaybackQueueSnapshot,
    controller: PlaybackQueueController,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(IosTheme.colors.secondarySystemBackground)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IosText("Up Next", style = IosTheme.typography.title2, color = IosTheme.colors.label)
                IosText("Done", color = IosTheme.colors.accent, modifier = Modifier.clickable(onClick = onDismiss))
            }
            if (queue.entries.isEmpty()) {
                IosText("Your queue is empty.", color = IosTheme.colors.secondaryLabel)
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(queue.entries, key = { index, entry -> "${entry.id}:$index" }) { index, entry ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (index == queue.currentIndex) IosTheme.colors.tertiarySystemFill else IosTheme.colors.secondarySystemBackground)
                                .clickable { controller.playQueueItem(index) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            IosText(
                                entry.title,
                                color = if (index == queue.currentIndex) IosTheme.colors.accent else IosTheme.colors.label,
                            )
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                IosText(entry.artists.joinToString(), color = IosTheme.colors.secondaryLabel)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    IosText(
                                        "↑",
                                        color = IosTheme.colors.label,
                                        modifier = Modifier.clickable(enabled = index > 0) {
                                            controller.moveQueueItem(index, index - 1)
                                        },
                                    )
                                    IosText(
                                        "↓",
                                        color = IosTheme.colors.label,
                                        modifier = Modifier.clickable(enabled = index < queue.entries.lastIndex) {
                                            controller.moveQueueItem(index, index + 1)
                                        },
                                    )
                                    IosText(
                                        "Remove",
                                        color = IosTheme.colors.secondaryLabel,
                                        modifier = Modifier.clickable { controller.removeQueueItem(index) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaylistPickerDialog(
    state: CatalogPlaylistActionState,
    onDismiss: () -> Unit,
    onSelectPlaylist: (CatalogEntry.Playlist) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(IosTheme.colors.secondarySystemBackground)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IosText("Add to playlist", style = IosTheme.typography.title2, color = IosTheme.colors.label)
                IosText("Cancel", color = IosTheme.colors.accent, modifier = Modifier.clickable(onClick = onDismiss))
            }
            state.selectedTrack?.let { track ->
                IosText(track.title, color = IosTheme.colors.secondaryLabel)
            }
            when {
                state.isLoadingPlaylists -> IosText("Loading playlists…", color = IosTheme.colors.secondaryLabel)
                state.playlistError != null && state.playlists.isEmpty() -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IosText(state.playlistError, color = IosTheme.colors.secondaryLabel)
                        IosText(
                            "Retry",
                            color = IosTheme.colors.accent,
                            modifier = Modifier.clickable(onClick = onRetry),
                        )
                    }
                }
                state.playlists.isEmpty() -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        IosText(
                            state.playlistError ?: "No editable playlists on this page.",
                            color = IosTheme.colors.secondaryLabel,
                        )
                        when {
                            state.continuationToken != null -> IosText(
                                "Load more playlists",
                                color = IosTheme.colors.accent,
                                modifier = Modifier.clickable(onClick = onLoadMore),
                            )
                            state.playlistError != null -> IosText(
                                "Retry",
                                color = IosTheme.colors.accent,
                                modifier = Modifier.clickable(onClick = onRetry),
                            )
                        }
                    }
                }
                else -> {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(state.playlists, key = { it.id.value }) { playlist ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = state.pendingPlaylistIds.isEmpty()) { onSelectPlaylist(playlist) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            IosText(playlist.title, color = IosTheme.colors.label)
                            if (playlist.id in state.pendingPlaylistIds) {
                                IosText("Adding…", color = IosTheme.colors.secondaryLabel)
                            }
                        }
                    }
                    if (state.continuationToken != null || state.isLoadingMorePlaylists) {
                        item(key = "playlist-picker-load-more") {
                            IosText(
                                if (state.isLoadingMorePlaylists) "Loading more…" else "Load more playlists",
                                color = IosTheme.colors.accent,
                                modifier = Modifier.clickable(
                                    enabled = !state.isLoadingMorePlaylists,
                                    onClick = onLoadMore,
                                ).padding(12.dp),
                            )
                        }
                    }
                }
                state.playlistError?.let { error ->
                    IosText(error, color = IosTheme.colors.secondaryLabel)
                }
                }
            }
            state.message?.let { message ->
                IosText(message, color = IosTheme.colors.secondaryLabel)
            }
        }
    }
}

@Composable
private fun BrowserDestination(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    IosText(
        text = title,
        color = if (selected) IosTheme.colors.label else IosTheme.colors.secondaryLabel,
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun CatalogEntryRow(
    entry: CatalogEntry,
    isSignedIn: Boolean,
    isSaved: Boolean,
    isLibraryActionPending: Boolean,
    onSignInRequested: () -> Unit,
    onLibraryAction: (CatalogEntry.Track, Boolean) -> Unit,
    onAddToPlaylist: (CatalogEntry.Track) -> Unit,
    onClick: (CatalogEntry) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(IosTheme.colors.tertiarySystemFill)
                .clickable { onClick(entry) },
        ) {
            RemoteArtwork(entry.artworkUrls.firstOrNull(), Modifier.fillMaxSize())
        }
        Column(
            modifier = Modifier.weight(1f).clickable { onClick(entry) },
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            IosText(entry.title, color = IosTheme.colors.label)
            val detail = when (entry) {
                is CatalogEntry.Track -> entry.artists.joinToString { it.name }
                is CatalogEntry.Album -> entry.artists.joinToString { it.name }
                is CatalogEntry.Artist -> entry.subtitle.orEmpty()
                is CatalogEntry.Playlist -> entry.description.orEmpty()
                is CatalogEntry.Browse -> ""
            }
            if (detail.isNotBlank()) IosText(detail, color = IosTheme.colors.secondaryLabel)
        }
        (entry as? CatalogEntry.Track)
            ?.takeIf { it.id.providerId == YOUTUBE_MUSIC_PROVIDER }
            ?.let { track ->
            IosText(
                text = when {
                    isLibraryActionPending -> "Updating…"
                    !isSignedIn -> "Sign in"
                    isSaved -> "Remove"
                    else -> "Save"
                },
                color = IosTheme.colors.accent,
                modifier = Modifier.clickable(enabled = !isLibraryActionPending) {
                    if (isSignedIn) onLibraryAction(track, !isSaved) else onSignInRequested()
                },
            )
            IosText(
                text = "Add",
                color = IosTheme.colors.accent,
                modifier = Modifier.clickable {
                    if (isSignedIn) onAddToPlaylist(track) else onSignInRequested()
                },
            )
            }
    }
}
