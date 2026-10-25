package com.convx.music.client.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.BasicText as ComposeText
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.playback.PlaybackRepeatMode
import com.convx.music.playback.PlaybackPhase
import com.convx.music.playback.PlaybackSnapshot
import com.convx.music.playback.PlaybackTrackSnapshot
import com.convx.music.playback.PlaybackQueueController
import com.convx.music.playback.PlaybackQueueSnapshot
import com.convx.music.account.MusicAccountState
import com.convx.music.account.presentation.MusicAccountPresentationState
import com.convx.music.account.presentation.MusicAccountStateHolder
import com.convx.music.designsystem.ConvxDesignTokens
import com.convx.music.catalog.CatalogEntry
import com.convx.music.catalog.CatalogId
import com.convx.music.catalog.CatalogLibraryCollection
import com.convx.music.catalog.YOUTUBE_MUSIC_PROVIDER
import com.convx.music.catalog.presentation.CatalogLibraryActionState
import com.convx.music.catalog.presentation.CatalogLibraryActionStateHolder
import com.convx.music.catalog.presentation.CatalogPlaylistActionState
import com.convx.music.catalog.presentation.CatalogPlaylistActionStateHolder
import com.convx.music.catalog.presentation.CatalogScreenState
import com.convx.music.catalog.presentation.CatalogStateHolder
import com.convx.music.catalog.presentation.RemoteArtwork
import com.convx.music.catalog.presentation.CatalogSharedTransitionPage
import com.convx.music.catalog.presentation.sharedCatalogArtwork
import com.convx.music.catalog.presentation.sharedCatalogTitle
import com.convx.music.playback.presentation.PlaybackFullPlayer
import com.convx.music.playback.presentation.PlaybackSharedElements
import com.convx.music.playback.presentation.PlaybackSettingsStateHolder
import com.convx.music.playback.presentation.PlaybackSleepTimerSheet
import com.composables.sfsymbols.SfRenderingMode
import com.composables.sfsymbols.SfSymbol
import com.composables.sfsymbols.SfSymbolIcon
import com.composables.sfsymbols.SfWeight
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import org.koin.compose.koinInject

private val DesktopBackground = ConvxDesignTokens.Colors.Background
private val DesktopMuted = ConvxDesignTokens.Colors.SecondaryLabel
private val DesktopAccent = ConvxDesignTokens.Colors.AccentRed
private val DesktopSidebarWidth = 260.dp
private val SidePanelShape = RoundedCornerShape(ConvxDesignTokens.Metrics.SidebarCorner)
private val SideRowShape = RoundedCornerShape(ConvxDesignTokens.Metrics.SidebarRowHeight / 2f)
private val PlayerShape = RoundedCornerShape(ConvxDesignTokens.Metrics.MiniPlayerHeight / 2f)

private data class DesktopNowPlaying(
    val trackId: String? = null,
    val isPlaying: Boolean = false,
)

private data class DesktopCompactPlayback(
    val track: PlaybackTrackSnapshot? = null,
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val playWhenReady: Boolean = false,
    val isPlaying: Boolean = false,
    val mediaItemCount: Int = 0,
    val repeatMode: PlaybackRepeatMode = PlaybackRepeatMode.OFF,
    val shuffleEnabled: Boolean = false,
    val canSkipPrevious: Boolean = false,
    val canSkipNext: Boolean = false,
    val errorMessage: String? = null,
)

private fun PlaybackSnapshot.toDesktopCompactPlayback() = DesktopCompactPlayback(
    track = track,
    phase = phase,
    playWhenReady = playWhenReady,
    isPlaying = isPlaying,
    mediaItemCount = mediaItemCount,
    repeatMode = repeatMode,
    shuffleEnabled = shuffleEnabled,
    canSkipPrevious = canSkipPrevious,
    canSkipNext = canSkipNext,
    errorMessage = errorMessage,
)

private enum class DesktopDestination(val title: String, val icon: DesktopGlyphKind) {
    HOME("Home", DesktopGlyphKind.HOUSE),
    SEARCH("Search", DesktopGlyphKind.SEARCH),
    EXPLORE("Browse", DesktopGlyphKind.GRID),
    SONGS("Songs", DesktopGlyphKind.MUSIC_NOTE),
    PLAYLISTS("Playlists", DesktopGlyphKind.PLAYLIST),
    CHARTS("Charts", DesktopGlyphKind.CHARTS),
    LIBRARY("History", DesktopGlyphKind.HISTORY),
}

@Composable
fun ConvxDesktopShell() {
    val playbackController = koinInject<DesktopMusicController>()
    val playbackSettingsStateHolder = koinInject<PlaybackSettingsStateHolder>()
    val nowPlaying by remember(playbackController) {
        playbackController.playbackSnapshot
            .map { snapshot -> DesktopNowPlaying(snapshot.track?.id, snapshot.isPlaying) }
            .distinctUntilChanged()
    }.collectAsState(DesktopNowPlaying())
    val catalog: CatalogStateHolder = koinInject()
    val catalogState by catalog.state.collectAsState()
    val libraryActions: CatalogLibraryActionStateHolder = koinInject()
    val libraryActionState by libraryActions.state.collectAsState()
    val playlistActions: CatalogPlaylistActionStateHolder = koinInject()
    val playlistActionState by playlistActions.state.collectAsState()
    val accountHolder: MusicAccountStateHolder = koinInject()
    val account by accountHolder.state.collectAsState()
    val isSignedIn = account.account is MusicAccountState.SignedIn
    var destination by remember { mutableStateOf(DesktopDestination.HOME) }
    var searchQuery by remember { mutableStateOf("") }
    var showSignIn by remember { mutableStateOf(false) }
    val tracks = remember(catalogState.sections, libraryActionState.savedOverrides) {
        catalogState.sections
            .flatMap { section -> section.entries.filterIsInstance<CatalogEntry.Track>() }
            .distinctBy { it.id }
            .mapIndexed { index, track ->
                track.copy(isInLibrary = libraryActionState.savedOverrides[track.id] ?: track.isInLibrary)
                    .toDesktopTrack(index)
            }
    }
    val playlists = remember(catalogState.sections) {
        catalogState.sections
            .flatMap { section -> section.entries.filterIsInstance<CatalogEntry.Playlist>() }
            .distinctBy { it.id }
    }

    LaunchedEffect(destination, searchQuery, isSignedIn) {
        when (destination) {
            DesktopDestination.SEARCH -> catalog.search(searchQuery)
            DesktopDestination.EXPLORE -> catalog.loadExplore()
            DesktopDestination.SONGS -> if (isSignedIn) catalog.loadLibrary(CatalogLibraryCollection.LIKED_TRACKS)
            DesktopDestination.PLAYLISTS -> if (isSignedIn) catalog.loadLibrary(CatalogLibraryCollection.PLAYLISTS)
            DesktopDestination.CHARTS -> catalog.loadCharts()
            DesktopDestination.LIBRARY -> if (isSignedIn) catalog.loadLibrary(CatalogLibraryCollection.RECENT_ACTIVITY)
            DesktopDestination.HOME -> catalog.loadHome()
        }
    }
    LaunchedEffect(account.account) {
        if (account.account is MusicAccountState.SignedIn || account.account is MusicAccountState.ChoosingChannel) {
            showSignIn = false
        }
    }
    SideEffect { playbackController.setCatalogTracks(tracks) }
    DisposableEffect(playbackController, catalog, accountHolder, libraryActions, playlistActions, playbackSettingsStateHolder) {
        onDispose {
            playbackController.close()
            catalog.close()
            accountHolder.close()
            libraryActions.close()
            playlistActions.close()
            playbackSettingsStateHolder.close()
        }
    }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(DesktopBackground),
    ) {
        val sidebarInset = ConvxDesignTokens.Metrics.SidebarInset
        val sidebarWidth = DesktopSidebarWidth
        val contentStart = sidebarInset + sidebarWidth + ConvxDesignTokens.Metrics.SidebarContentGap
        val contentWidth = (maxWidth - contentStart - 30.dp).coerceAtLeast(0.dp)
        val playerWidth = minOf(
            (contentWidth * ConvxDesignTokens.Metrics.MiniPlayerWidthFraction).coerceAtLeast(480.dp),
            contentWidth,
        )
        val playerStart = contentStart + (contentWidth - playerWidth) / 2f

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            DesktopBackdrop()
            CatalogSharedTransitionPage(
                state = catalogState,
                modifier = Modifier.fillMaxSize(),
                enableSharedElements = false,
            ) { pageState ->
                DesktopPage(
                    destination = destination,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    catalogState = pageState,
                    libraryActionState = libraryActionState,
                    isSignedIn = isSignedIn,
                    tracks = tracks,
                    playbackTrackId = nowPlaying.trackId,
                    playbackIsPlaying = nowPlaying.isPlaying,
                    onTrackSelected = playbackController::selectTrack,
                    onEntrySelected = catalog::openEntry,
                    onBack = catalog::goBack,
                    onSignInRequested = { showSignIn = true },
                    onSetTrackSaved = libraryActions::setSaved,
                    onAddTrackToPlaylist = { track ->
                        if (account.account is MusicAccountState.SignedIn) playlistActions.openPicker(track)
                        else showSignIn = true
                    },
                    contentInset = contentStart - ConvxDesignTokens.Metrics.ContentGutter,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = ConvxDesignTokens.Metrics.ContentGutter, end = 30.dp, top = 38.dp),
                )
            }
        }

        DesktopSidebar(
            selected = destination,
            onDestinationSelected = { destination = it },
            tracks = tracks,
            playlists = playlists,
            accountState = account,
            onTrackSelected = playbackController::selectTrack,
            onEntrySelected = catalog::openEntry,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = sidebarInset, top = 30.dp, bottom = 30.dp)
                .width(sidebarWidth)
                .fillMaxHeight(),
        )

        DesktopPlaybackOverlay(
            playbackController = playbackController,
            settingsStateHolder = playbackSettingsStateHolder,
            account = account,
            onSignInRequested = { showSignIn = true },
            onSignOut = accountHolder::signOut,
            playerStart = playerStart,
            playerWidth = playerWidth,
            modifier = Modifier.fillMaxSize(),
        )

        playlistActionState.message?.let { message ->
            Text(
                text = message,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                color = DesktopMuted,
                fontSize = 12.sp,
            )
        }

        if (playlistActionState.isPickerVisible) {
            DesktopPlaylistPickerDialog(
                state = playlistActionState,
                onDismiss = playlistActions::dismissPicker,
                onSelectPlaylist = playlistActions::addToPlaylist,
                onCreatePlaylist = playlistActions::createPlaylistAndAdd,
                onLoadMore = playlistActions::loadMorePlaylists,
                onRetry = playlistActions::retryPlaylists,
            )
        }

        account.message?.let { message ->
            Text(
                text = message,
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 82.dp, end = 30.dp),
                color = DesktopMuted,
                fontSize = 12.sp,
            )
        }

        if (showSignIn) {
            DesktopSignInDialog(
                accountIsWorking = account.isWorking,
                accountMessage = account.message,
                onComplete = { cookie, visitorData, dataSyncId ->
                    accountHolder.completeWebSignIn(cookie, visitorData, dataSyncId)
                },
                onRetryRequested = accountHolder::clearMessage,
                onDismiss = {
                    accountHolder.cancelSignIn()
                    showSignIn = false
                },
            )
        }

        val channels = (account.account as? MusicAccountState.ChoosingChannel)?.channels.orEmpty()
        if (channels.isNotEmpty()) {
            DesktopChannelDialog(
                channels = channels,
                onSelect = accountHolder::selectChannel,
                onDismiss = accountHolder::cancelSignIn,
            )
        }
    }
}

@Composable
private fun DesktopPlaybackOverlay(
    playbackController: DesktopMusicController,
    settingsStateHolder: PlaybackSettingsStateHolder,
    account: MusicAccountPresentationState,
    onSignInRequested: () -> Unit,
    onSignOut: () -> Unit,
    playerStart: Dp,
    playerWidth: Dp,
    modifier: Modifier = Modifier,
) {
    val compactPlayback by remember(playbackController) {
        playbackController.playbackSnapshot
            .map(PlaybackSnapshot::toDesktopCompactPlayback)
            .distinctUntilChanged()
    }.collectAsState(DesktopCompactPlayback())
    val settings by settingsStateHolder.state.collectAsState()
    val settingsError by settingsStateHolder.errorMessage.collectAsState()
    val sleepTimer = remember(playbackController) { requireNotNull(playbackController.sleepTimer) }
    val sleepTimerState by sleepTimer.state.collectAsState()
    val queue by playbackController.queueSnapshot.collectAsState()
    var showQueue by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var showFullPlayer by remember { mutableStateOf(false) }

    Box(modifier) {
        if (compactPlayback.track != null) {
            if (showFullPlayer) {
                DesktopFullPlayerOverlay(
                    controller = playbackController,
                    sleepTimerActive = sleepTimerState.isActive,
                    crossfadeEnabled = settings.transitionMode == com.convx.music.playback.PlaybackTransitionMode.CROSSFADE,
                    autoMixEnabled = settings.transitionMode == com.convx.music.playback.PlaybackTransitionMode.AUTO_MIX,
                    crossfadeAvailable = true,
                    crossfadeLocked = settings.bitPerfectEnabled,
                    playbackSettingsError = settingsError,
                    sharedElements = PlaybackSharedElements(),
                    onToggleCrossfade = {
                        settingsStateHolder.setCrossfadeEnabled(
                            settings.transitionMode != com.convx.music.playback.PlaybackTransitionMode.CROSSFADE,
                        )
                    },
                    onToggleAutoMix = {
                        settingsStateHolder.setAutoMixEnabled(
                            settings.transitionMode != com.convx.music.playback.PlaybackTransitionMode.AUTO_MIX,
                        )
                    },
                    onCollapse = { showFullPlayer = false },
                    onOpenQueue = { showQueue = true },
                    onOpenSleepTimer = { showSleepTimer = true },
                )
            } else {
                DesktopMiniPlayer(
                    playback = compactPlayback,
                    controller = playbackController,
                    crossfadeEnabled = settings.transitionMode != com.convx.music.playback.PlaybackTransitionMode.OFF,
                    onToggleCrossfade = {
                        settingsStateHolder.setCrossfadeEnabled(
                            settings.transitionMode == com.convx.music.playback.PlaybackTransitionMode.OFF,
                        )
                    },
                    onExpand = { showFullPlayer = true },
                    sharedElements = PlaybackSharedElements(),
                    onQueueClick = { showQueue = true },
                    sleepTimerActive = sleepTimerState.isActive,
                    onSleepTimerClick = { showSleepTimer = true },
                    modifier = Modifier.align(Alignment.BottomStart)
                        .padding(start = playerStart, bottom = 22.dp)
                        .width(playerWidth)
                        .height(ConvxDesignTokens.Metrics.MiniPlayerHeight),
                )
            }
        }

        if (showQueue) {
            DesktopQueuePanel(
                queue = queue,
                controller = playbackController,
                onDismiss = { showQueue = false },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp),
            )
        }

        if (showSleepTimer) {
            DesktopSleepTimerOverlay(
                controller = playbackController,
                timer = sleepTimer,
                onDismissRequest = { showSleepTimer = false },
            )
        }

        if (!showFullPlayer) DesktopAccountControl(
            state = account,
            onSignIn = onSignInRequested,
            onSignOut = onSignOut,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = 30.dp, end = 30.dp),
        )
    }
}

@Composable
private fun DesktopFullPlayerOverlay(
    controller: DesktopMusicController,
    sleepTimerActive: Boolean,
    crossfadeEnabled: Boolean,
    autoMixEnabled: Boolean,
    crossfadeAvailable: Boolean,
    crossfadeLocked: Boolean,
    playbackSettingsError: String?,
    sharedElements: PlaybackSharedElements,
    onToggleCrossfade: () -> Unit,
    onToggleAutoMix: () -> Unit,
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
) {
    val playback by controller.playbackSnapshot.collectAsState()
    PlaybackFullPlayer(
        playback = playback,
        controller = controller,
        sharedElements = sharedElements,
        darkTheme = true,
        sleepTimerActive = sleepTimerActive,
        crossfadeEnabled = crossfadeEnabled,
        autoMixEnabled = autoMixEnabled,
        crossfadeAvailable = crossfadeAvailable,
        crossfadeLocked = crossfadeLocked,
        playbackSettingsError = playbackSettingsError,
        liquidGlassScrubber = false,
        onToggleCrossfade = onToggleCrossfade,
        onToggleAutoMix = onToggleAutoMix,
        onCollapse = onCollapse,
        onOpenQueue = onOpenQueue,
        onOpenSleepTimer = onOpenSleepTimer,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun DesktopSleepTimerOverlay(
    controller: DesktopMusicController,
    timer: com.convx.music.playback.PlaybackSleepTimer,
    onDismissRequest: () -> Unit,
) {
    val playback by controller.playbackSnapshot.collectAsState()
    PlaybackSleepTimerSheet(
        timer = timer,
        playback = playback,
        darkTheme = true,
        onDismissRequest = onDismissRequest,
    )
}

@Composable
private fun DesktopAccountControl(
    state: MusicAccountPresentationState,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = SideRowShape
    Row(
        modifier = modifier
            .shadow(8.dp, shape)
            .clip(shape)
            .desktopGlass(Color(0xB826262C), shape)
            .border(1.dp, Color.White.copy(alpha = 0.13f), shape)
            .clickable(enabled = !state.isRestoring && !state.isWorking) {
                when (state.account) {
                    MusicAccountState.SignedOut -> onSignIn()
                    is MusicAccountState.SignedIn -> onSignOut()
                    is MusicAccountState.ChoosingChannel -> Unit
                }
            }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val label = when (val account = state.account) {
            MusicAccountState.SignedOut -> if (state.isRestoring || state.isWorking) "Restoring account…" else "Sign in"
            is MusicAccountState.ChoosingChannel -> "Choose channel"
            is MusicAccountState.SignedIn -> account.account.name
        }
        Text(label, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (state.account is MusicAccountState.SignedIn) {
            Text("Sign out", color = Color.White.copy(alpha = 0.65f), fontSize = 12.sp)
        }
    }
}

@Composable
private fun DesktopChannelDialog(
    channels: List<com.convx.music.account.MusicAccount>,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF21C1C20))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Choose a YouTube channel", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            channels.forEach { channel ->
                Text(
                    text = channel.name,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelect(channel.dataSyncId) }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
private fun DesktopPlaylistPickerDialog(
    state: CatalogPlaylistActionState,
    onDismiss: () -> Unit,
    onSelectPlaylist: (CatalogEntry.Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
) {
    var newPlaylistName by remember(state.selectedTrack?.id) { mutableStateOf("") }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color(0xF21C1C20))
                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(22.dp))
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Add to playlist", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("Cancel", color = DesktopMuted, fontSize = 13.sp, modifier = Modifier.clickable(onClick = onDismiss))
            }
            state.selectedTrack?.let { track ->
                Text(track.title, color = DesktopMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            BasicTextField(
                value = newPlaylistName,
                onValueChange = { newPlaylistName = it },
                singleLine = true,
                enabled = !state.isCreatingPlaylist,
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.White.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { field ->
                    if (newPlaylistName.isEmpty()) {
                        Text("New playlist name", color = DesktopMuted, fontSize = 13.sp)
                    }
                    field()
                },
            )
            Text(
                text = if (state.isCreatingPlaylist) "Creating and adding…" else "Create and add",
                color = DesktopAccent,
                fontSize = 13.sp,
                modifier = Modifier.clickable(
                    enabled = !state.isCreatingPlaylist,
                    onClick = { onCreatePlaylist(newPlaylistName) },
                ),
            )
            when {
                state.isLoadingPlaylists -> Text("Loading playlists…", color = DesktopMuted, fontSize = 13.sp)
                state.playlists.isEmpty() -> {
                    Text(state.playlistError ?: "No editable playlists on this page.", color = DesktopMuted, fontSize = 13.sp)
                    when {
                        state.continuationToken != null -> Text(
                            "Load more playlists",
                            color = DesktopAccent,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable(onClick = onLoadMore),
                        )
                        state.playlistError != null -> Text(
                            "Retry",
                            color = DesktopAccent,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable(onClick = onRetry),
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 440.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        itemsIndexed(state.playlists, key = { _, playlist -> playlist.id.value }) { _, playlist ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = state.pendingPlaylistIds.isEmpty() && !state.isCreatingPlaylist) {
                                        onSelectPlaylist(playlist)
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(playlist.title, color = Color.White, fontSize = 13.sp)
                                if (playlist.id in state.pendingPlaylistIds) {
                                    Text("Adding…", color = DesktopMuted, fontSize = 12.sp)
                                }
                            }
                        }
                        if (state.continuationToken != null || state.isLoadingMorePlaylists) {
                            item(key = "playlist-picker-load-more") {
                                Text(
                                    if (state.isLoadingMorePlaylists) "Loading more…" else "Load more playlists",
                                    color = DesktopAccent,
                                    fontSize = 13.sp,
                                    modifier = Modifier.clickable(
                                        enabled = !state.isLoadingMorePlaylists,
                                        onClick = onLoadMore,
                                    ).padding(12.dp),
                                )
                            }
                        }
                    }
                    state.playlistError?.let { error ->
                        Text(error, color = DesktopMuted, fontSize = 12.sp)
                    }
                }
            }
            state.message?.let { message -> Text(message, color = DesktopMuted, fontSize = 12.sp) }
        }
    }
}

@Composable
private fun DesktopBackdrop() {
    Box(Modifier.fillMaxSize().background(DesktopBackground))
    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(
                colors = listOf(Color(0xFF493E49).copy(alpha = 0.22f), Color.Transparent),
                center = Offset(430f, 280f),
                radius = 680f,
            ),
        ),
    )
}

private fun Modifier.desktopGlass(
    tint: Color,
    shape: RoundedCornerShape,
): Modifier = this
    .clip(shape)
    .background(
        Brush.verticalGradient(
            colors = listOf(Color.White.copy(alpha = 0.09f), tint, tint.copy(alpha = tint.alpha * 0.92f)),
        ),
        shape,
    )

@Composable
private fun DesktopSidebar(
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    tracks: List<DesktopTrack>,
    playlists: List<CatalogEntry.Playlist>,
    accountState: MusicAccountPresentationState,
    onTrackSelected: (String) -> Unit,
    onEntrySelected: (CatalogEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(elevation = 16.dp, shape = SidePanelShape)
            .clip(SidePanelShape)
            .desktopGlass(Color(0xA61E1E23), SidePanelShape)
            .border(1.dp, Color.White.copy(alpha = 0.11f), SidePanelShape)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(34.dp).clip(CircleShape).background(DesktopAccent),
            ) {
                DesktopGlyph(DesktopGlyphKind.MUSIC_NOTE, color = Color.White, size = 17.dp)
            }
            Text("Convx Music", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }

        listOf(
            DesktopDestination.HOME,
            DesktopDestination.SEARCH,
            DesktopDestination.EXPLORE,
            DesktopDestination.CHARTS,
        ).forEach { destination ->
            SidebarItem(
                destination = destination,
                selected = selected == destination,
                onClick = { onDestinationSelected(destination) },
            )
        }

        SidebarSectionTitle("Your Library")
        listOf(
            DesktopDestination.SONGS,
            DesktopDestination.PLAYLISTS,
            DesktopDestination.LIBRARY,
        ).forEach { destination ->
            SidebarItem(
                destination = destination,
                selected = selected == destination,
                onClick = { onDestinationSelected(destination) },
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
        ) {
            SidebarSectionTitle("Recently Added")
            tracks.take(4).forEachIndexed { index, track ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(SideRowShape)
                        .clickable { onTrackSelected(track.snapshot.id) },
                ) {
                    TrackCover(track, Modifier.size(34.dp), index)
                    Text(track.snapshot.title, color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            SidebarSectionTitle("Playlists")
            playlists.take(4).forEachIndexed { index, playlist ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(SideRowShape)
                        .clickable { onEntrySelected(playlist) },
                ) {
                    PlaylistArtwork(tracks.getOrNull(index), index)
                    Text(playlist.title, color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
            modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 12.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(35.dp).clip(CircleShape).background(Color(0xFF35343A)),
            ) {
                DesktopGlyph(DesktopGlyphKind.MUSIC_NOTE, color = Color.White, size = 16.dp)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Convx Music", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    when (val account = accountState.account) {
                        MusicAccountState.SignedOut -> "Guest mode"
                        is MusicAccountState.SignedIn -> account.account.name
                        is MusicAccountState.ChoosingChannel -> "Choose channel"
                    },
                    color = DesktopMuted,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun SidebarSectionTitle(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = 0.58f),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 2.dp, top = 24.dp, bottom = 7.dp),
    )
}

@Composable
private fun SidebarItem(
    destination: DesktopDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp),
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(SideRowShape)
                    .background(Color(0xFF383238), SideRowShape),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(13.dp),
            modifier = Modifier
                .fillMaxSize()
                .clip(SideRowShape)
                .clickable(onClick = onClick)
                .padding(horizontal = 13.dp),
        ) {
            DesktopGlyph(
                kind = destination.icon,
                color = if (selected) DesktopAccent else Color.White.copy(alpha = 0.7f),
                size = ConvxDesignTokens.Metrics.SidebarIconSize,
            )
            Text(
                destination.title,
                color = if (selected) Color.White else Color.White.copy(alpha = 0.8f),
                fontSize = ConvxDesignTokens.Type.SidebarLabel,
                lineHeight = ConvxDesignTokens.Type.SidebarLabelLineHeight,
                letterSpacing = ConvxDesignTokens.Type.SidebarLabelTracking,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
    }
}

@Composable
private fun DesktopPage(
    destination: DesktopDestination,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    catalogState: CatalogScreenState,
    libraryActionState: CatalogLibraryActionState,
    isSignedIn: Boolean,
    tracks: List<DesktopTrack>,
    playbackTrackId: String?,
    playbackIsPlaying: Boolean,
    onTrackSelected: (String) -> Unit,
    onEntrySelected: (CatalogEntry) -> Unit,
    onBack: () -> Unit,
    onSignInRequested: () -> Unit,
    onSetTrackSaved: (CatalogId, Boolean, Boolean) -> Unit,
    onAddTrackToPlaylist: (CatalogEntry.Track) -> Unit,
    contentInset: Dp,
    modifier: Modifier = Modifier,
) {
    Column(
        // Keep the scroll viewport full width behind the glass rail. Its content
        // starts beside the rail, while horizontal shelves can bleed back under it.
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = contentInset),
        verticalArrangement = Arrangement.spacedBy(ConvxDesignTokens.Metrics.SectionGap + ConvxDesignTokens.Metrics.TextGap * 3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (catalogState.canGoBack) catalogState.pageTitle else destination.title,
                    color = Color.White,
                    fontSize = ConvxDesignTokens.Type.ScreenTitle,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.7).sp,
                )
            }
            if (catalogState.canGoBack) {
                Text(
                    "‹ ${destination.title}",
                    color = DesktopMuted,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(onClick = onBack),
                )
            } else if (destination == DesktopDestination.SEARCH) {
                SearchField(value = searchQuery, onValueChange = onSearchQueryChange, modifier = Modifier.width(340.dp))
            }
        }

        if (catalogState.isLoading) {
            Text("Loading your music…", color = DesktopMuted, fontSize = 13.sp)
        }
        catalogState.currentEntry?.takeIf { it.artworkUrls.isNotEmpty() }?.let { entry ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                RemoteArtwork(
                    entry.artworkUrls.firstOrNull(),
                    Modifier.sharedCatalogArtwork(entry.id)
                        .size(128.dp)
                        .clip(RoundedCornerShape(18.dp)),
                )
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        entry.title,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.sharedCatalogTitle(entry.id),
                    )
                    val subtitle = when (entry) {
                        is CatalogEntry.Album -> entry.artists.joinToString { it.name }
                        is CatalogEntry.Artist -> entry.subtitle.orEmpty()
                        is CatalogEntry.Playlist -> entry.description.orEmpty()
                        is CatalogEntry.Browse -> ""
                        is CatalogEntry.Track -> entry.artists.joinToString { it.name }
                    }
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, color = DesktopMuted, fontSize = 12.sp)
                    }
                }
            }
        }
        val requiresAccount = !isSignedIn && (
            destination == DesktopDestination.SONGS ||
                destination == DesktopDestination.LIBRARY ||
                (destination == DesktopDestination.PLAYLISTS && !catalogState.canGoBack)
            )
        catalogState.message?.takeUnless { requiresAccount }?.let { message ->
            Text(message, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
        }
        libraryActionState.message?.let { message ->
            Text(message, color = Color.White.copy(alpha = 0.78f), fontSize = 13.sp)
        }

        when (destination) {
            DesktopDestination.SEARCH -> {
                SectionTitle("Search results", if (searchQuery.isBlank()) "" else searchQuery.uppercase())
                if (catalogState.sections.isEmpty() && !catalogState.isLoading && catalogState.message == null) {
                    Text("No matching songs found.", color = DesktopMuted, fontSize = 13.sp)
                } else {
                    catalogState.sections.forEach { section ->
                        SectionTitle(section.title, "${section.entries.size} ITEMS")
                        val sectionTracks = section.entries.filterIsInstance<CatalogEntry.Track>()
                            .mapNotNull { entry -> tracks.firstOrNull { it.snapshot.id == entry.id.value } }
                        if (sectionTracks.isNotEmpty()) {
                            TrackList(sectionTracks, playbackTrackId, playbackIsPlaying, onTrackSelected, libraryActionState, isSignedIn, onSignInRequested, onSetTrackSaved, onAddTrackToPlaylist)
                        }
                        CatalogEntryRows(section.entries.filterNot { it is CatalogEntry.Track }, onEntrySelected)
                    }
                }
            }

            DesktopDestination.HOME, DesktopDestination.EXPLORE, DesktopDestination.CHARTS -> {
                catalogState.sections.forEach { section ->
                    if (section.entries.isNotEmpty()) {
                        SectionTitle(section.title, "")
                        CatalogBrowseRail(
                            entries = section.entries,
                            tracks = tracks,
                            contentInset = contentInset,
                            libraryActionState = libraryActionState,
                            isSignedIn = isSignedIn,
                            onTrackSelected = onTrackSelected,
                            onEntrySelected = onEntrySelected,
                            onSignInRequested = onSignInRequested,
                            onSetTrackSaved = onSetTrackSaved,
                            onAddTrackToPlaylist = onAddTrackToPlaylist,
                        )
                    }
                }
            }

            DesktopDestination.SONGS -> {
                if (isSignedIn) {
                    SectionTitle("Songs", "${tracks.size} SONGS")
                    TrackList(tracks, playbackTrackId, playbackIsPlaying, onTrackSelected, libraryActionState, isSignedIn, onSignInRequested, onSetTrackSaved, onAddTrackToPlaylist)
                } else {
                    DesktopAccountRequiredMessage("Sign in to view your saved songs.")
                }
            }

            DesktopDestination.PLAYLISTS -> {
                if (catalogState.canGoBack) {
                    catalogState.sections.forEach { section ->
                        val sectionTracks = section.entries.filterIsInstance<CatalogEntry.Track>()
                            .mapNotNull { entry -> tracks.firstOrNull { it.snapshot.id == entry.id.value } }
                        SectionTitle(section.title, "${section.entries.size} ITEMS")
                        if (sectionTracks.isNotEmpty()) {
                            TrackList(sectionTracks, playbackTrackId, playbackIsPlaying, onTrackSelected, libraryActionState, isSignedIn, onSignInRequested, onSetTrackSaved, onAddTrackToPlaylist)
                        }
                        CatalogEntryRows(section.entries.filterNot { it is CatalogEntry.Track }, onEntrySelected)
                    }
                } else if (!isSignedIn) {
                    DesktopAccountRequiredMessage("Sign in to view your playlists.")
                } else {
                    val playlistEntries = catalogState.sections.flatMap { it.entries }
                        .filterIsInstance<CatalogEntry.Playlist>()
                    SectionTitle("Playlists", "${playlistEntries.size} PLAYLISTS")
                    CatalogEntryRows(playlistEntries, onEntrySelected)
                }
            }

            DesktopDestination.LIBRARY -> {
                if (isSignedIn) {
                    catalogState.sections.forEach { section ->
                        val sectionTracks = section.entries.filterIsInstance<CatalogEntry.Track>()
                            .mapNotNull { entry -> tracks.firstOrNull { it.snapshot.id == entry.id.value } }
                        SectionTitle(section.title, "${section.entries.size} ITEMS")
                        if (sectionTracks.isNotEmpty()) {
                            TrackList(sectionTracks, playbackTrackId, playbackIsPlaying, onTrackSelected, libraryActionState, isSignedIn, onSignInRequested, onSetTrackSaved, onAddTrackToPlaylist)
                        }
                        CatalogEntryRows(section.entries.filterNot { it is CatalogEntry.Track }, onEntrySelected)
                    }
                } else {
                    DesktopAccountRequiredMessage("Sign in to view your library.")
                }
            }
        }

        // The floating glass player overlays the page, while scroll padding keeps the final row reachable.
        Spacer(Modifier.height(ConvxDesignTokens.Metrics.MiniPlayerHeight + 36.dp))
    }
}

@Composable
private fun DesktopAccountRequiredMessage(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 34.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(message, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text("Choose Sign in in the upper right to continue.", color = DesktopMuted, fontSize = 13.sp)
    }
}

@Composable
private fun CatalogEntryRows(entries: List<CatalogEntry>, onEntrySelected: (CatalogEntry) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        entries.forEach { entry ->
            val subtitle = when (entry) {
                is CatalogEntry.Track -> entry.artists.joinToString { it.name }
                is CatalogEntry.Album -> entry.artists.joinToString { it.name }
                is CatalogEntry.Artist -> entry.subtitle.orEmpty()
                is CatalogEntry.Playlist -> entry.description.orEmpty()
                is CatalogEntry.Browse -> "Open ${entry.title}"
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().height(52.dp).clickable { onEntrySelected(entry) }.padding(horizontal = 10.dp),
            ) {
                if (entry.artworkUrls.isNotEmpty()) {
                    RemoteArtwork(
                        entry.artworkUrls.firstOrNull(),
                        Modifier.sharedCatalogArtwork(entry.id)
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        entry.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.sharedCatalogTitle(entry.id),
                    )
                    if (subtitle.isNotBlank()) Text(subtitle, color = DesktopMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun CatalogBrowseRail(
    entries: List<CatalogEntry>,
    tracks: List<DesktopTrack>,
    contentInset: Dp,
    libraryActionState: CatalogLibraryActionState,
    isSignedIn: Boolean,
    onTrackSelected: (String) -> Unit,
    onEntrySelected: (CatalogEntry) -> Unit,
    onSignInRequested: () -> Unit,
    onSetTrackSaved: (CatalogId, Boolean, Boolean) -> Unit,
    onAddTrackToPlaylist: (CatalogEntry.Track) -> Unit,
) {
    Row(
        modifier = Modifier
            .bleedStartIntoSidebar(contentInset)
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.width((contentInset - 72.dp).coerceAtLeast(12.dp)))
        entries.forEach { entry ->
            val track = (entry as? CatalogEntry.Track)?.let { catalogTrack ->
                tracks.firstOrNull {
                    it.request.providerId == catalogTrack.id.providerId && it.request.id == catalogTrack.id.value
                }
            }
            val subtitle = when (entry) {
                is CatalogEntry.Track -> entry.artists.joinToString { it.name }
                is CatalogEntry.Album -> entry.artists.joinToString { it.name }
                is CatalogEntry.Artist -> entry.subtitle.orEmpty()
                is CatalogEntry.Playlist -> entry.description.orEmpty()
                is CatalogEntry.Browse -> "Explore ${entry.title}"
            }
            Column(
                modifier = Modifier.width(184.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(184.dp)
                        .clip(RoundedCornerShape(ConvxDesignTokens.Metrics.ArtworkCorner))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF3A3540), Color(0xFF22272D)),
                                start = Offset(0f, 0f),
                                end = Offset(520f, 520f),
                            ),
                        )
                        .clickable {
                            if (entry is CatalogEntry.Track) onTrackSelected(entry.id.value)
                            else onEntrySelected(entry)
                        },
                ) {
                    val artwork = track?.artworkUrl ?: entry.artworkUrls.firstOrNull()
                    if (artwork != null) {
                        RemoteArtwork(artwork, Modifier.fillMaxSize())
                    } else {
                        DesktopGlyph(
                            kind = when (entry) {
                                is CatalogEntry.Track -> DesktopGlyphKind.MUSIC_NOTE
                                is CatalogEntry.Album -> DesktopGlyphKind.ALBUM
                                is CatalogEntry.Artist -> DesktopGlyphKind.ARTIST
                                is CatalogEntry.Playlist -> DesktopGlyphKind.PLAYLIST
                                is CatalogEntry.Browse -> DesktopGlyphKind.GRID
                            },
                            color = Color.White.copy(alpha = 0.7f),
                            size = 32.dp,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Column(
                        modifier = Modifier.weight(1f).clickable {
                            if (entry is CatalogEntry.Track) onTrackSelected(entry.id.value)
                            else onEntrySelected(entry)
                        },
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            entry.title,
                            color = Color.White,
                            fontSize = ConvxDesignTokens.Type.ItemTitle,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (subtitle.isNotBlank()) {
                            Text(
                                subtitle,
                                color = DesktopMuted,
                                fontSize = ConvxDesignTokens.Type.Caption,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (track != null) {
                        DesktopTrackLibraryAction(
                            track = track,
                            state = libraryActionState,
                            isSignedIn = isSignedIn,
                            onSignInRequested = onSignInRequested,
                            onSetTrackSaved = onSetTrackSaved,
                            onAddTrackToPlaylist = onAddTrackToPlaylist,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Modifier.bleedStartIntoSidebar(inset: Dp): Modifier = layout { measurable, constraints ->
    val extraWidth = inset.roundToPx().coerceAtLeast(0)
    val expandedConstraints = if (constraints.hasBoundedWidth) {
        constraints.copy(
            minWidth = constraints.minWidth + extraWidth,
            maxWidth = constraints.maxWidth + extraWidth,
        )
    } else {
        constraints
    }
    val placeable = measurable.measure(expandedConstraints)
    layout((placeable.width - extraWidth).coerceAtLeast(0), placeable.height) {
        placeable.placeRelative(-extraWidth, 0)
    }
}

@Composable
private fun TrackList(
    tracks: List<DesktopTrack>,
    playbackTrackId: String?,
    playbackIsPlaying: Boolean,
    onTrackSelected: (String) -> Unit,
    libraryActionState: CatalogLibraryActionState,
    isSignedIn: Boolean,
    onSignInRequested: () -> Unit,
    onSetTrackSaved: (CatalogId, Boolean, Boolean) -> Unit,
    onAddTrackToPlaylist: (CatalogEntry.Track) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        tracks.forEachIndexed { index, track ->
            val selected = playbackTrackId == track.snapshot.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(ConvxDesignTokens.Metrics.ArtworkCorner))
                    .background(if (selected) Color.White.copy(alpha = 0.055f) else Color.Transparent)
                    .padding(horizontal = 10.dp),
            ) {
                TrackCover(track, Modifier.size(42.dp).clickable { onTrackSelected(track.snapshot.id) }, index)
                Column(
                    modifier = Modifier.weight(1f).clickable { onTrackSelected(track.snapshot.id) },
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(track.snapshot.title, color = if (selected) Color.White else Color.White.copy(alpha = 0.94f), fontSize = ConvxDesignTokens.Type.ItemTitle, fontWeight = FontWeight.SemiBold)
                    Text("${track.snapshot.artists.joinToString()}  |  ${track.album}", color = DesktopMuted, fontSize = ConvxDesignTokens.Type.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (selected && playbackIsPlaying) DesktopGlyph(DesktopGlyphKind.PAUSE, color = DesktopAccent, size = 15.dp)
                Text(formatTime(track.durationMs), color = DesktopMuted, fontSize = 11.sp, modifier = Modifier.width(42.dp))
                DesktopTrackLibraryAction(
                    track = track,
                    state = libraryActionState,
                    isSignedIn = isSignedIn,
                    onSignInRequested = onSignInRequested,
                    onSetTrackSaved = onSetTrackSaved,
                    onAddTrackToPlaylist = onAddTrackToPlaylist,
                )
            }
        }
    }
}

@Composable
private fun DesktopTrackLibraryAction(
    track: DesktopTrack,
    state: CatalogLibraryActionState,
    isSignedIn: Boolean,
    onSignInRequested: () -> Unit,
    onSetTrackSaved: (CatalogId, Boolean, Boolean) -> Unit,
    onAddTrackToPlaylist: (CatalogEntry.Track) -> Unit,
) {
    if (track.request.providerId != YOUTUBE_MUSIC_PROVIDER) return
    val id = CatalogId(track.request.providerId, track.request.id)
    val isPending = id in state.pendingTrackIds
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        DesktopIconButton(
            kind = if (isSignedIn && track.isInLibrary) DesktopGlyphKind.SAVED else DesktopGlyphKind.SAVE,
            description = when {
                isPending -> "Updating saved status"
                !isSignedIn -> "Sign in to save ${track.snapshot.title}"
                track.isInLibrary -> "Remove ${track.snapshot.title} from library"
                else -> "Save ${track.snapshot.title} to library"
            },
            controlSize = 30.dp,
            enabled = !isPending,
            selected = isSignedIn && track.isInLibrary,
            onClick = {
                if (isSignedIn) onSetTrackSaved(id, track.isInLibrary, !track.isInLibrary)
                else onSignInRequested()
            },
        )
        DesktopIconButton(
            kind = DesktopGlyphKind.ADD_TO_PLAYLIST,
            description = if (isSignedIn) "Add ${track.snapshot.title} to a playlist" else "Sign in to add ${track.snapshot.title} to a playlist",
            controlSize = 30.dp,
            enabled = !isPending,
            onClick = {
                if (isSignedIn) onAddTrackToPlaylist(track.catalogTrack) else onSignInRequested()
            },
        )
    }
}

@Composable
private fun TrackCover(
    track: DesktopTrack,
    modifier: Modifier = Modifier,
    index: Int,
    editorial: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(if (editorial) RoundedCornerShape(topStart = ConvxDesignTokens.Metrics.LargeCardCorner, bottomStart = ConvxDesignTokens.Metrics.LargeCardCorner) else RoundedCornerShape(ConvxDesignTokens.Metrics.ArtworkCorner))
            .background(Brush.linearGradient(listOf(track.artStart, track.artEnd))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension
            val pale = Color.White.copy(alpha = 0.18f)
            val soft = Color.White.copy(alpha = 0.11f)
            when (index % 5) {
                0 -> {
                    drawCircle(pale, unit * 0.4f, Offset(size.width * 0.68f, size.height * 0.34f))
                    drawCircle(soft, unit * 0.32f, Offset(size.width * 0.32f, size.height * 0.72f))
                    drawCircle(Color.White.copy(alpha = 0.12f), unit * 0.18f, Offset(size.width * 0.72f, size.height * 0.36f), style = Stroke(unit * 0.025f))
                }
                1 -> {
                    drawCircle(Color(0xFFFFE3BD).copy(alpha = 0.72f), unit * 0.23f, Offset(size.width * 0.69f, size.height * 0.3f))
                    repeat(4) { row ->
                        drawLine(soft, Offset(0f, size.height * (0.59f + row * 0.09f)), Offset(size.width, size.height * (0.46f + row * 0.09f)), unit * 0.035f)
                    }
                }
                2 -> {
                    drawCircle(pale, unit * 0.38f, Offset(size.width * 0.5f, size.height * 0.52f), style = Stroke(unit * 0.06f))
                    drawCircle(soft, unit * 0.23f, Offset(size.width * 0.5f, size.height * 0.52f))
                }
                3 -> {
                    drawRect(Color.Black.copy(alpha = 0.12f), Offset(0f, size.height * 0.63f), Size(size.width, size.height * 0.37f))
                    drawCircle(Color(0xFFFFD6A7).copy(alpha = 0.68f), unit * 0.22f, Offset(size.width * 0.52f, size.height * 0.37f))
                    drawArc(Color.White.copy(alpha = 0.2f), 190f, 160f, false, Offset(size.width * 0.1f, size.height * 0.55f), Size(size.width * 0.8f, size.height * 0.48f), style = Stroke(unit * 0.045f))
                }
                else -> {
                    val path = Path().apply {
                        moveTo(0f, size.height * 0.72f)
                        cubicTo(size.width * 0.24f, size.height * 0.44f, size.width * 0.48f, size.height * 0.95f, size.width, size.height * 0.45f)
                        lineTo(size.width, size.height)
                        lineTo(0f, size.height)
                        close()
                    }
                    drawPath(path, Color.White.copy(alpha = 0.13f))
                    drawCircle(Color.White.copy(alpha = 0.2f), unit * 0.13f, Offset(size.width * 0.72f, size.height * 0.29f))
                }
            }
        }
        RemoteArtwork(track.artworkUrl, Modifier.fillMaxSize())
        if (editorial) {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.horizontalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.12f))),
                ),
            )
        }
    }
}

@Composable
private fun PlaylistArtwork(track: DesktopTrack?, index: Int) {
    Box(
        Modifier.size(34.dp)
            .clip(RoundedCornerShape(ConvxDesignTokens.Metrics.ArtworkCorner))
            .background(Brush.linearGradient(listOf(Color(0xFF574A60), Color(0xFF303947)))),
    ) {
        track?.let { TrackCover(it, Modifier.fillMaxSize(), index) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(title, color = Color.White, fontSize = ConvxDesignTokens.Type.SectionHeader, fontWeight = FontWeight.Bold, letterSpacing = (-0.35).sp)
        if (detail.isNotEmpty()) {
            Text(detail, color = DesktopMuted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.7.sp)
        }
    }
}

@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
            .padding(horizontal = 13.dp, vertical = 11.dp),
    ) {
        DesktopGlyph(DesktopGlyphKind.SEARCH, color = DesktopMuted, size = 16.dp)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) Text("Search songs, artists, albums", color = DesktopMuted, fontSize = 12.sp)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun DesktopMiniPlayer(
    playback: DesktopCompactPlayback,
    controller: DesktopMusicController,
    crossfadeEnabled: Boolean,
    onToggleCrossfade: () -> Unit,
    onExpand: () -> Unit,
    sharedElements: PlaybackSharedElements,
    onQueueClick: () -> Unit,
    sleepTimerActive: Boolean,
    onSleepTimerClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playback.track
    val waitingForAudio = playback.phase == PlaybackPhase.BUFFERING && playback.playWhenReady
    val shouldShowPause = playback.isPlaying || waitingForAudio
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .clip(PlayerShape)
            .desktopGlass(Color(0xB826262C), PlayerShape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), PlayerShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Row(
            modifier = sharedElements.controls,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DesktopIconButton(
                kind = DesktopGlyphKind.SHUFFLE,
                description = if (playback.shuffleEnabled) "Turn shuffle off" else "Turn shuffle on",
                selected = playback.shuffleEnabled,
                clipContent = false,
                onClick = { controller.setShuffleEnabled(!playback.shuffleEnabled) },
            )
            DesktopIconButton(
                kind = DesktopGlyphKind.PREVIOUS,
                description = "Previous track",
                enabled = track != null && playback.canSkipPrevious,
                clipContent = false,
                onClick = { controller.requestSkip(forward = false) },
            )
            DesktopIconButton(
                kind = if (shouldShowPause) DesktopGlyphKind.PAUSE else DesktopGlyphKind.PLAY,
                description = if (shouldShowPause) "Pause" else "Play",
                modifier = sharedElements.playPause,
                prominent = true,
                enabled = track != null,
                clipContent = false,
                onClick = controller::togglePlayPause,
            )
            DesktopIconButton(
                kind = DesktopGlyphKind.NEXT,
                description = "Next track",
                enabled = track != null && playback.canSkipNext,
                clipContent = false,
                onClick = { controller.requestSkip(forward = true) },
            )
            DesktopIconButton(
                kind = DesktopGlyphKind.REPEAT,
                description = "Change repeat mode",
                selected = playback.repeatMode != PlaybackRepeatMode.OFF,
                clipContent = false,
                onClick = controller::cycleRepeatMode,
            )
            DesktopIconButton(
                kind = DesktopGlyphKind.CROSSFADE,
                description = if (crossfadeEnabled) "Turn transitions off" else "Turn transitions on",
                selected = crossfadeEnabled,
                clipContent = false,
                onClick = onToggleCrossfade,
            )
        }

        if (track != null) {
            Box(
                Modifier.padding(start = 5.dp)
                    .then(sharedElements.artwork)
                    .size(ConvxDesignTokens.Metrics.MiniPlayerArtworkSize)
                    .background(Color.White.copy(alpha = 0.08f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(ConvxDesignTokens.Metrics.ArtworkCorner))
                    .clickable(onClick = onExpand),
            ) {
                RemoteArtwork(track.artworkUrl, Modifier.fillMaxSize())
            }
        }
        Column(
            modifier = Modifier.weight(1f)
                .padding(start = 3.dp)
                .then(sharedElements.title)
                .clickable(role = Role.Button, onClick = onExpand),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(track?.title ?: "Choose a song", color = Color.White, fontSize = ConvxDesignTokens.Type.MiniPlayerTitle, lineHeight = ConvxDesignTokens.Type.MiniPlayerTitleLineHeight, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                playback.errorMessage
                    ?: if (waitingForAudio) "Preparing audio…" else track?.artists?.joinToString().orEmpty(),
                modifier = sharedElements.artist,
                color = DesktopMuted,
                fontSize = ConvxDesignTokens.Type.MiniPlayerArtist,
                lineHeight = ConvxDesignTokens.Type.MiniPlayerArtistLineHeight,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DesktopIconButton(
            kind = DesktopGlyphKind.LYRICS,
            description = "Lyrics",
            enabled = false,
            clipContent = false,
            onClick = { },
        )
        DesktopIconButton(
            kind = DesktopGlyphKind.TIMER,
            description = if (sleepTimerActive) "Sleep timer active" else "Set sleep timer",
            selected = sleepTimerActive,
            clipContent = false,
            onClick = onSleepTimerClick,
        )
        DesktopIconButton(
            kind = DesktopGlyphKind.QUEUE,
            description = "Queue",
            modifier = sharedElements.queue,
            enabled = playback.mediaItemCount > 0,
            clipContent = false,
            onClick = onQueueClick,
        )
    }
}

@Composable
private fun DesktopQueuePanel(
    queue: PlaybackQueueSnapshot,
    controller: PlaybackQueueController,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .width(520.dp)
            .heightIn(max = 460.dp)
            .shadow(18.dp, shape)
            .clip(shape)
            .background(Color(0xF025252A), shape)
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Up next", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                Text("${queue.entries.size} songs", color = DesktopMuted, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DesktopIconButton(
                    kind = DesktopGlyphKind.CLEAR_UP_NEXT,
                    description = "Clear songs after current track",
                    enabled = queue.currentIndex in queue.entries.indices && queue.currentIndex < queue.entries.lastIndex,
                    onClick = controller::removeQueueItemsAfterCurrent,
                )
                DesktopIconButton(
                    kind = DesktopGlyphKind.CLOSE,
                    description = "Close queue",
                    onClick = onDismiss,
                )
            }
        }
        if (queue.entries.isEmpty()) {
            Text("Your queue is empty.", color = DesktopMuted, fontSize = 13.sp, modifier = Modifier.padding(vertical = 22.dp))
        } else {
            LazyColumn(
                modifier = Modifier.heightIn(max = 340.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(queue.entries, key = { index, entry -> "${entry.id}:$index" }) { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (index == queue.currentIndex) Color.White.copy(alpha = 0.12f) else Color.Transparent)
                            .clickable { controller.playQueueItem(index) }
                            .padding(horizontal = 8.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Box(Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)).background(Color.White.copy(alpha = 0.08f))) {
                            RemoteArtwork(entry.artworkUrl, Modifier.fillMaxSize())
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                entry.title,
                                color = if (index == queue.currentIndex) Color(0xFFFF7B87) else Color.White,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(entry.artists.joinToString(), color = DesktopMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        DesktopIconButton(
                            kind = DesktopGlyphKind.MOVE_UP,
                            description = "Move ${entry.title} up",
                            enabled = index > 0,
                            onClick = { controller.moveQueueItem(index, index - 1) },
                        )
                        DesktopIconButton(
                            kind = DesktopGlyphKind.MOVE_DOWN,
                            description = "Move ${entry.title} down",
                            enabled = index < queue.entries.lastIndex,
                            onClick = { controller.moveQueueItem(index, index + 1) },
                        )
                        DesktopIconButton(
                            kind = DesktopGlyphKind.REMOVE,
                            description = "Remove ${entry.title} from queue",
                            onClick = { controller.removeQueueItem(index) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DesktopIconButton(
    kind: DesktopGlyphKind,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    prominent: Boolean = false,
    clipContent: Boolean = true,
    controlSize: Dp = ConvxDesignTokens.Metrics.MiniPlayerControlSize,
    onClick: () -> Unit,
) {
    val shape = if (prominent) CircleShape else RoundedCornerShape(12.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .then(if (clipContent) Modifier.clip(shape) else Modifier)
            .size(controlSize)
            .background(if (prominent) Color.White else Color.Transparent, shape)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        DesktopGlyph(
            kind = kind,
            color = when {
                !enabled -> DesktopMuted.copy(alpha = 0.38f)
                prominent -> Color(0xFF17171B)
                selected -> DesktopAccent
                else -> Color.White.copy(alpha = 0.88f)
            },
            size = when (kind) {
                DesktopGlyphKind.PLAY, DesktopGlyphKind.PAUSE, DesktopGlyphKind.PREVIOUS, DesktopGlyphKind.NEXT -> ConvxDesignTokens.Metrics.MiniPlayerPlayIconSize
                else -> ConvxDesignTokens.Metrics.MiniPlayerIconSize
            },
        )
    }
}

private enum class DesktopGlyphKind(val symbolName: String) {
    HOUSE("house.fill"),
    SEARCH("magnifyingglass"),
    GRID("square.grid.2x2.fill"),
    MUSIC_NOTE("music.note"),
    CHARTS("chart.bar.fill"),
    LIBRARY("music.note.square.stack.fill"),
    PLAYLIST("music.note.list"),
    ALBUM("square.stack.fill"),
    ARTIST("person.crop.square"),
    SIDEBAR("line.3.horizontal"),
    SHUFFLE("shuffle"),
    PREVIOUS("backward.fill"),
    PLAY("play.fill"),
    PAUSE("pause.fill"),
    NEXT("forward.fill"),
    REPEAT("repeat"),
    CROSSFADE("arrow.left.arrow.right"),
    QUEUE("list.bullet"),
    MOVE_UP("arrow.up"),
    MOVE_DOWN("arrow.down"),
    REMOVE("xmark"),
    CLEAR_UP_NEXT("text.badge.minus"),
    CLOSE("xmark.circle.fill"),
    LYRICS("quote.bubble"),
    TIMER("moon.fill"),
    MORE("ellipsis"),
    HISTORY("clock.arrow.circlepath"),
    SAVE("plus"),
    SAVED("checkmark"),
    ADD_TO_PLAYLIST("text.badge.plus");
}

@Composable
private fun DesktopGlyph(kind: DesktopGlyphKind, color: Color, size: Dp) {
    val symbol = remember(kind) { SfSymbol.named(kind.symbolName) ?: error("Unknown SF Symbol: ${kind.symbolName}") }
    SfSymbolIcon(
        symbol = symbol,
        modifier = Modifier.size(size),
        size = size,
        weight = SfWeight.Bold,
        mode = SfRenderingMode.Monochrome,
        tint = color,
        contentDescription = null,
    )
}

@Composable
private fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    fontSize: TextUnit = 14.sp,
    fontWeight: FontWeight? = null,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    ComposeText(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = fontSize, fontWeight = fontWeight, letterSpacing = letterSpacing, lineHeight = lineHeight),
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
