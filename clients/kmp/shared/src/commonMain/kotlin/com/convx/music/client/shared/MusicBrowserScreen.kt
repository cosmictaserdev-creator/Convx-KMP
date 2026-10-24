package com.convx.music.client.shared

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.lazy.rememberLazyListState
import com.convx.music.catalog.CatalogEntry
import com.convx.music.playback.PlaybackController
import com.convx.music.playback.PlaybackRequestTrack
import com.convx.music.ui.ios.component.IosText
import com.convx.music.ui.ios.foundation.IosTheme
import com.convx.music.ui.ios.foundation.ProvideIosTheme
import com.convx.music.ui.ios.foundation.iosTypography
import com.convx.music.ui.ios.foundation.lightIosColors
import kotlinx.coroutines.flow.collect
import org.koin.compose.koinInject

@Composable
fun MusicBrowserScreen(playbackController: PlaybackController? = null) {
    val catalog: CatalogStateHolder = koinInject()
    val state by catalog.state.collectAsState()
    val playback = playbackController?.playbackSnapshot?.collectAsState()?.value
    val listState = rememberLazyListState()
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
    LaunchedEffect(playbackController, queue) { playbackController?.updateQueue(queue) }
    DisposableEffect(catalog) {
        onDispose(catalog::close)
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
            modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IosText(state.pageTitle, style = IosTheme.typography.title, color = IosTheme.colors.label)
                if (state.canGoBack) {
                    IosText("Back", color = IosTheme.colors.secondaryLabel, modifier = Modifier.clickable(onClick = catalog::goBack))
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
                    }
                }
            }
            when {
                state.isLoading -> IosText("Loading music…", color = IosTheme.colors.secondaryLabel)
                state.message != null -> IosText(state.message.orEmpty(), color = IosTheme.colors.secondaryLabel)
                state.sections.isEmpty() -> IosText("No music to show yet.", color = IosTheme.colors.secondaryLabel)
            }
            LazyColumn(state = listState, verticalArrangement = Arrangement.spacedBy(18.dp)) {
                state.sections.forEach { section ->
                    item(key = section.id) {
                        IosText(section.title, style = IosTheme.typography.title2, color = IosTheme.colors.label)
                    }
                    items(section.entries, key = { "${section.id}:${it.id.value}" }) { entry ->
                        CatalogEntryRow(entry) {
                            if (entry is CatalogEntry.Track) {
                                if (entry.isPlayable) {
                                    playbackController?.playTrack(
                                        PlaybackRequestTrack(
                                            providerId = entry.id.providerId,
                                            id = entry.id.value,
                                            title = entry.title,
                                            artists = entry.artists.map { it.name },
                                            durationMs = entry.durationMillis,
                                            artworkUrl = entry.artworkUrls.firstOrNull(),
                                        ),
                                    )
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
    }
}

@Composable
private fun CatalogEntryRow(entry: CatalogEntry, onClick: (CatalogEntry) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick(entry) }.padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(IosTheme.colors.tertiarySystemFill),
        ) {
            RemoteArtwork(entry.artworkUrls.firstOrNull(), Modifier.fillMaxSize())
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
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
    }
}
