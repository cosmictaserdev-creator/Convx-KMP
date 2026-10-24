package com.convx.migration.desktop

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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.convx.music.playback.PlaybackSnapshot
import com.convx.music.designsystem.ConvxDesignTokens
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import org.koin.compose.koinInject
import zone.ien.hig.glass.LiquidGlassMaterials
import zone.ien.hig.glass.liquidGlass

private val DesktopBackground = ConvxDesignTokens.Colors.Background
private val DesktopMuted = ConvxDesignTokens.Colors.SecondaryLabel
private val DesktopAccent = ConvxDesignTokens.Colors.AccentRed
private val SidePanelShape = ContinuousRoundedRectangle(ConvxDesignTokens.Metrics.SidebarCorner)
private val SideRowShape = ContinuousRoundedRectangle(ConvxDesignTokens.Metrics.SidebarRowHeight / 2f)
private val PlayerShape = ContinuousRoundedRectangle(ConvxDesignTokens.Metrics.MiniPlayerHeight / 2f)

private enum class DesktopDestination(val title: String, val icon: DesktopGlyphKind) {
    HOME("Home", DesktopGlyphKind.HOUSE),
    SEARCH("Search", DesktopGlyphKind.SEARCH),
    EXPLORE("Explore", DesktopGlyphKind.GRID),
    SONGS("Songs", DesktopGlyphKind.MUSIC_NOTE),
    RADIO("Radio", DesktopGlyphKind.RADIO),
    LIBRARY("Library", DesktopGlyphKind.LIBRARY),
}

@Composable
fun ConvxDesktopShell() {
    val playbackController = koinInject<DesktopMusicController>()
    val playback by playbackController.playbackSnapshot.collectAsState()
    val backdrop = rememberLayerBackdrop()
    var destination by remember { mutableStateOf(DesktopDestination.HOME) }
    var searchQuery by remember { mutableStateOf("") }

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(DesktopBackground),
    ) {
        val sidebarInset = ConvxDesignTokens.Metrics.SidebarInset
        val sidebarWidth = ConvxDesignTokens.Metrics.SidebarWidth
        val contentStart = sidebarInset + sidebarWidth + ConvxDesignTokens.Metrics.SidebarContentGap
        val contentWidth = (maxWidth - contentStart - 30.dp).coerceAtLeast(0.dp)
        val playerWidth = minOf(
            (contentWidth * ConvxDesignTokens.Metrics.MiniPlayerWidthFraction).coerceAtLeast(480.dp),
            contentWidth,
        )
        val playerStart = contentStart + (contentWidth - playerWidth) / 2f

        Box(
            modifier = Modifier.fillMaxSize().layerBackdrop(backdrop),
        ) {
            DesktopBackdrop()
            DesktopPage(
                destination = destination,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                playback = playback,
                onTrackSelected = playbackController::selectTrack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = contentStart, end = 30.dp, top = 38.dp, bottom = 112.dp),
            )
        }

        DesktopSidebar(
            selected = destination,
            onDestinationSelected = { destination = it },
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = sidebarInset, top = 30.dp, bottom = 30.dp)
                .width(sidebarWidth)
                .fillMaxHeight(),
        )

        DesktopMiniPlayer(
            playback = playback,
            controller = playbackController,
            backdrop = backdrop,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = playerStart, bottom = 22.dp)
                .width(playerWidth)
                .height(ConvxDesignTokens.Metrics.MiniPlayerHeight),
        )
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

@Composable
private fun DesktopSidebar(
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .shadow(elevation = 16.dp, shape = SidePanelShape)
            .clip(SidePanelShape)
            .liquidGlass(backdrop, SidePanelShape, LiquidGlassMaterials.SidePanel)
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth().height(54.dp).padding(start = 14.dp, end = 5.dp),
        ) {
            Text("Edit", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(42.dp).clip(CircleShape).clickable { },
            ) {
                DesktopGlyph(DesktopGlyphKind.SIDEBAR, color = Color.White.copy(alpha = 0.78f), size = 19.dp)
            }
        }

        DesktopDestination.entries.forEach { destination ->
            SidebarItem(
                destination = destination,
                selected = selected == destination,
                backdrop = backdrop,
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
            listOf("Soft Focus", "Long Way Home", "Rooms of Light", "Night Lines").forEachIndexed { index, name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(SideRowShape)
                        .clickable { onDestinationSelected(DesktopDestination.LIBRARY) },
                ) {
                    TrackCover(desktopDemoTracks[index], Modifier.size(34.dp), index)
                    Text(name, color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }

            SidebarSectionTitle("Playlists")
            listOf("Sunday Morning", "On Repeat", "Made for the road").forEachIndexed { index, name ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(SideRowShape)
                        .clickable { onDestinationSelected(DesktopDestination.LIBRARY) },
                ) {
                    PlaylistArtwork(index)
                    Text(name, color = Color.White.copy(alpha = 0.86f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                Text("C", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Your library", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Desktop preview", color = DesktopMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            DesktopGlyph(DesktopGlyphKind.MORE, color = DesktopMuted, size = 19.dp)
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
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
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
                    .liquidGlass(backdrop, SideRowShape, LiquidGlassMaterials.SelectedTab),
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
    playback: PlaybackSnapshot,
    onTrackSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(ConvxDesignTokens.Metrics.SectionGap + ConvxDesignTokens.Metrics.TextGap * 3),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Home", color = DesktopMuted, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("New", color = Color.White, fontSize = ConvxDesignTokens.Type.ScreenTitle, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
            }
            if (destination == DesktopDestination.SEARCH) {
                SearchField(value = searchQuery, onValueChange = onSearchQueryChange, modifier = Modifier.width(340.dp))
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.09f)),
                ) {
                    Text("C", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        when (destination) {
            DesktopDestination.SEARCH -> {
                val query = searchQuery.trim().lowercase()
                val results = desktopDemoTracks.filter { track ->
                    query.isEmpty() || track.snapshot.title.lowercase().contains(query) ||
                        track.snapshot.artists.any { it.lowercase().contains(query) } || track.album.lowercase().contains(query)
                }
                SectionTitle("Trending searches", "")
                TrackCards(onTrackSelected = onTrackSelected, cardWidth = 176.dp)
                SectionTitle("Songs", "${results.size} RESULTS")
                TrackList(results, playback, onTrackSelected)
            }

            DesktopDestination.HOME, DesktopDestination.EXPLORE -> {
                EditorialFeature(onPlay = { onTrackSelected(desktopDemoTracks.first().snapshot.id) })
                EditorialRow("Best New Songs", "See All", onTrackSelected)
                EditorialRow("Updated Playlists", "See All", onTrackSelected)
                EditorialRow("Trending Songs", "See All", onTrackSelected)
            }

            DesktopDestination.SONGS -> {
                SectionTitle("Songs", "${desktopDemoTracks.size} SONGS")
                TrackList(desktopDemoTracks, playback, onTrackSelected)
                EditorialRow("Recently Played", "See All", onTrackSelected)
            }

            DesktopDestination.RADIO -> {
                EditorialFeature(onPlay = { onTrackSelected(desktopDemoTracks[1].snapshot.id) })
                EditorialRow("Stations for You", "See All", onTrackSelected)
                EditorialRow("Featured Shows", "See All", onTrackSelected)
            }

            DesktopDestination.LIBRARY -> {
                SectionTitle("Recently Added", "${desktopDemoTracks.size} ITEMS")
                TrackCards(onTrackSelected = onTrackSelected, cardWidth = 184.dp)
                SectionTitle("Songs", "ALL SONGS")
                TrackList(desktopDemoTracks, playback, onTrackSelected)
            }
        }
    }
}

@Composable
private fun EditorialFeature(onPlay: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(282.dp)
            .clip(RoundedCornerShape(ConvxDesignTokens.Metrics.LargeCardCorner))
            .background(ConvxDesignTokens.Colors.Card),
    ) {
        TrackCover(
            track = desktopDemoTracks[2],
            modifier = Modifier.fillMaxHeight().width(350.dp),
            index = 2,
            editorial = true,
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f).padding(horizontal = 32.dp, vertical = 24.dp),
        ) {
            Text("CONVX EDITORIAL", color = DesktopAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
            Text("New music,\ncloser to you", color = Color.White, fontSize = 31.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp)
            Text(
                "A fresh selection of songs and stories, chosen for the week ahead.",
                color = Color.White.copy(alpha = 0.68f),
                fontSize = 13.sp,
                lineHeight = 19.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            DesktopActionButton(label = "Listen Now", onClick = onPlay)
        }
    }
}

@Composable
private fun EditorialRow(title: String, action: String, onTrackSelected: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SectionTitle(title, action.uppercase())
        TrackCards(onTrackSelected = onTrackSelected, cardWidth = 178.dp)
    }
}

@Composable
private fun TrackCards(
    onTrackSelected: (String) -> Unit,
    cardWidth: Dp = 178.dp,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        desktopDemoTracks.forEachIndexed { index, track ->
            Column(
                modifier = Modifier.width(cardWidth).clickable { onTrackSelected(track.snapshot.id) },
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TrackCover(track, Modifier.fillMaxWidth().height(cardWidth), index)
                Text(track.snapshot.title, color = Color.White, fontSize = ConvxDesignTokens.Type.ItemTitle, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.snapshot.artists.joinToString(), color = DesktopMuted, fontSize = ConvxDesignTokens.Type.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<DesktopDemoTrack>,
    playback: PlaybackSnapshot,
    onTrackSelected: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        tracks.forEachIndexed { index, track ->
            val selected = playback.track?.id == track.snapshot.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(ConvxDesignTokens.Metrics.ArtworkCorner))
                    .background(if (selected) Color.White.copy(alpha = 0.055f) else Color.Transparent)
                    .clickable { onTrackSelected(track.snapshot.id) }
                    .padding(horizontal = 10.dp),
            ) {
                TrackCover(track, Modifier.size(42.dp), index)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(track.snapshot.title, color = if (selected) Color.White else Color.White.copy(alpha = 0.94f), fontSize = ConvxDesignTokens.Type.ItemTitle, fontWeight = FontWeight.SemiBold)
                    Text("${track.snapshot.artists.joinToString()}  |  ${track.album}", color = DesktopMuted, fontSize = ConvxDesignTokens.Type.Caption, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (selected && playback.isPlaying) DesktopGlyph(DesktopGlyphKind.PAUSE, color = DesktopAccent, size = 15.dp)
                Text(formatTime(track.durationMs), color = DesktopMuted, fontSize = 11.sp, modifier = Modifier.width(42.dp))
            }
        }
    }
}

@Composable
private fun TrackCover(
    track: DesktopDemoTrack,
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
private fun PlaylistArtwork(index: Int) {
    val tracks = desktopDemoTracks
    Box(Modifier.size(34.dp).clip(RoundedCornerShape(ConvxDesignTokens.Metrics.ArtworkCorner))) {
        TrackCover(tracks[index % tracks.size], Modifier.fillMaxSize(), index)
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
    playback: PlaybackSnapshot,
    controller: DesktopMusicController,
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop,
    modifier: Modifier = Modifier,
) {
    val track = desktopDemoTracks.firstOrNull { it.snapshot.id == playback.track?.id } ?: desktopDemoTracks.first()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier
            .shadow(12.dp, PlayerShape)
            .clip(PlayerShape)
            .liquidGlass(backdrop, PlayerShape, LiquidGlassMaterials.MiniPlayer)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        DesktopIconButton(
            kind = DesktopGlyphKind.SHUFFLE,
            description = if (playback.shuffleEnabled) "Turn shuffle off" else "Turn shuffle on",
            selected = playback.shuffleEnabled,
            onClick = { controller.setShuffleEnabled(!playback.shuffleEnabled) },
        )
        DesktopIconButton(
            kind = DesktopGlyphKind.PREVIOUS,
            description = "Previous track",
            enabled = playback.canSkipPrevious,
            onClick = { controller.requestSkip(forward = false) },
        )
        DesktopIconButton(
            kind = if (playback.isPlaying) DesktopGlyphKind.PAUSE else DesktopGlyphKind.PLAY,
            description = if (playback.isPlaying) "Pause" else "Play",
            prominent = true,
            onClick = controller::togglePlayPause,
        )
        DesktopIconButton(
            kind = DesktopGlyphKind.NEXT,
            description = "Next track",
            enabled = playback.canSkipNext,
            onClick = { controller.requestSkip(forward = true) },
        )
        DesktopIconButton(
            kind = DesktopGlyphKind.REPEAT,
            description = "Change repeat mode",
            selected = playback.repeatMode != PlaybackRepeatMode.OFF,
            onClick = controller::cycleRepeatMode,
        )

        Box(Modifier.padding(start = 5.dp)) {
            TrackCover(track, Modifier.size(ConvxDesignTokens.Metrics.MiniPlayerArtworkSize), playback.mediaItemIndex.coerceAtLeast(0))
        }
        Column(
            modifier = Modifier.weight(1f).padding(start = 3.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(track.snapshot.title, color = Color.White, fontSize = ConvxDesignTokens.Type.MiniPlayerTitle, lineHeight = ConvxDesignTokens.Type.MiniPlayerTitleLineHeight, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.snapshot.artists.joinToString(), color = DesktopMuted, fontSize = ConvxDesignTokens.Type.MiniPlayerArtist, lineHeight = ConvxDesignTokens.Type.MiniPlayerArtistLineHeight, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DesktopIconButton(
            kind = DesktopGlyphKind.LYRICS,
            description = "Lyrics",
            onClick = { },
        )
        DesktopIconButton(
            kind = DesktopGlyphKind.QUEUE,
            description = "Queue",
            onClick = { },
        )
    }
}

@Composable
private fun DesktopActionButton(label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        modifier = Modifier
            .clip(CircleShape)
            .background(Color.White)
            .semantics { contentDescription = label; role = Role.Button }
            .clickable(onClick = onClick)
            .padding(horizontal = 17.dp, vertical = 10.dp),
    ) {
        Text(label, color = Color(0xFF18181B), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        DesktopGlyph(DesktopGlyphKind.PLAY, color = Color(0xFF18181B), size = 11.dp)
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
    onClick: () -> Unit,
) {
    val size = ConvxDesignTokens.Metrics.MiniPlayerControlSize
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(if (prominent) CircleShape else RoundedCornerShape(12.dp))
            .background(if (prominent) Color.White else Color.Transparent)
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

private enum class DesktopGlyphKind {
    HOUSE, SEARCH, GRID, MUSIC_NOTE, RADIO, LIBRARY, SIDEBAR,
    SHUFFLE, PREVIOUS, PLAY, PAUSE, NEXT, REPEAT, QUEUE, LYRICS, MORE,
}

@Composable
private fun DesktopGlyph(kind: DesktopGlyphKind, color: Color, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = (w * 0.085f).coerceAtLeast(1.4.dp.toPx())
        val center = Offset(w / 2f, h / 2f)
        fun path(block: Path.() -> Unit) = Path().apply(block)
        when (kind) {
            DesktopGlyphKind.HOUSE -> {
                drawPath(path { moveTo(w * 0.12f, h * 0.48f); lineTo(w * 0.5f, h * 0.15f); lineTo(w * 0.88f, h * 0.48f); moveTo(w * 0.23f, h * 0.43f); lineTo(w * 0.23f, h * 0.86f); lineTo(w * 0.77f, h * 0.86f); lineTo(w * 0.77f, h * 0.43f) }, color, style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(color, Offset(w * 0.44f, h * 0.86f), Offset(w * 0.44f, h * 0.61f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.56f, h * 0.86f), Offset(w * 0.56f, h * 0.61f), stroke, StrokeCap.Round)
            }
            DesktopGlyphKind.SEARCH -> {
                drawCircle(color, w * 0.31f, Offset(w * 0.42f, h * 0.42f), style = Stroke(stroke))
                drawLine(color, Offset(w * 0.65f, h * 0.65f), Offset(w * 0.9f, h * 0.9f), stroke, StrokeCap.Round)
            }
            DesktopGlyphKind.GRID -> {
                val cell = w * 0.34f
                listOf(Offset(w * 0.1f, h * 0.1f), Offset(w * 0.56f, h * 0.1f), Offset(w * 0.1f, h * 0.56f), Offset(w * 0.56f, h * 0.56f)).forEach {
                    drawRoundRect(color, it, Size(cell, cell), androidx.compose.ui.geometry.CornerRadius(stroke * 1.5f), style = Stroke(stroke))
                }
            }
            DesktopGlyphKind.MUSIC_NOTE -> {
                drawLine(color, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.62f, h * 0.7f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.62f, h * 0.18f), Offset(w * 0.9f, h * 0.25f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.62f, h * 0.35f), Offset(w * 0.88f, h * 0.41f), stroke, StrokeCap.Round)
                drawOval(color, Offset(w * 0.12f, h * 0.61f), Size(w * 0.43f, h * 0.28f))
                drawOval(color, Offset(w * 0.5f, h * 0.75f), Size(w * 0.43f, h * 0.25f))
            }
            DesktopGlyphKind.RADIO -> {
                drawCircle(color, w * 0.12f, center, style = Stroke(stroke))
                drawArc(color, 220f, 100f, false, Offset(w * 0.12f, h * 0.12f), Size(w * 0.76f, h * 0.76f), style = Stroke(stroke, cap = StrokeCap.Round))
                drawArc(color, 220f, 100f, false, Offset(w * 0.27f, h * 0.27f), Size(w * 0.46f, h * 0.46f), style = Stroke(stroke, cap = StrokeCap.Round))
            }
            DesktopGlyphKind.LIBRARY -> {
                drawRoundRect(color, Offset(w * 0.13f, h * 0.14f), Size(w * 0.74f, h * 0.72f), androidx.compose.ui.geometry.CornerRadius(stroke * 2f), style = Stroke(stroke))
                drawLine(color, Offset(w * 0.32f, h * 0.36f), Offset(w * 0.68f, h * 0.36f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.32f, h * 0.52f), Offset(w * 0.68f, h * 0.52f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.32f, h * 0.68f), Offset(w * 0.58f, h * 0.68f), stroke, StrokeCap.Round)
            }
            DesktopGlyphKind.SIDEBAR -> {
                drawRoundRect(color, Offset(w * 0.08f, h * 0.16f), Size(w * 0.84f, h * 0.68f), androidx.compose.ui.geometry.CornerRadius(stroke * 2f), style = Stroke(stroke))
                drawLine(color, Offset(w * 0.38f, h * 0.17f), Offset(w * 0.38f, h * 0.83f), stroke)
            }
            DesktopGlyphKind.SHUFFLE -> {
                drawPath(path { moveTo(w * 0.12f, h * 0.28f); cubicTo(w * 0.44f, h * 0.28f, w * 0.55f, h * 0.72f, w * 0.88f, h * 0.72f); moveTo(w * 0.7f, h * 0.53f); lineTo(w * 0.88f, h * 0.72f); lineTo(w * 0.7f, h * 0.9f); moveTo(w * 0.12f, h * 0.72f); cubicTo(w * 0.44f, h * 0.72f, w * 0.55f, h * 0.28f, w * 0.88f, h * 0.28f); moveTo(w * 0.7f, h * 0.1f); lineTo(w * 0.88f, h * 0.28f); lineTo(w * 0.7f, h * 0.46f) }, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            DesktopGlyphKind.PREVIOUS, DesktopGlyphKind.NEXT -> {
                val backward = kind == DesktopGlyphKind.PREVIOUS
                drawLine(color, Offset(if (backward) w * 0.22f else w * 0.78f, h * 0.18f), Offset(if (backward) w * 0.22f else w * 0.78f, h * 0.82f), stroke, StrokeCap.Round)
                val triangle = path {
                    if (backward) { moveTo(w * 0.72f, h * 0.18f); lineTo(w * 0.34f, h * 0.5f); lineTo(w * 0.72f, h * 0.82f) }
                    else { moveTo(w * 0.28f, h * 0.18f); lineTo(w * 0.66f, h * 0.5f); lineTo(w * 0.28f, h * 0.82f) }
                    close()
                }
                drawPath(triangle, color)
            }
            DesktopGlyphKind.PLAY -> drawPath(path { moveTo(w * 0.28f, h * 0.15f); lineTo(w * 0.82f, h * 0.5f); lineTo(w * 0.28f, h * 0.85f); close() }, color)
            DesktopGlyphKind.PAUSE -> {
                drawRoundRect(color, Offset(w * 0.2f, h * 0.14f), Size(w * 0.2f, h * 0.72f), androidx.compose.ui.geometry.CornerRadius(stroke))
                drawRoundRect(color, Offset(w * 0.6f, h * 0.14f), Size(w * 0.2f, h * 0.72f), androidx.compose.ui.geometry.CornerRadius(stroke))
            }
            DesktopGlyphKind.REPEAT -> {
                drawArc(color, 205f, 235f, false, Offset(w * 0.15f, h * 0.18f), Size(w * 0.7f, h * 0.64f), style = Stroke(stroke, cap = StrokeCap.Round))
                drawPath(path { moveTo(w * 0.16f, h * 0.2f); lineTo(w * 0.16f, h * 0.43f); lineTo(w * 0.37f, h * 0.43f); moveTo(w * 0.84f, h * 0.8f); lineTo(w * 0.84f, h * 0.57f); lineTo(w * 0.63f, h * 0.57f) }, color, style = Stroke(stroke, cap = StrokeCap.Round))
            }
            DesktopGlyphKind.QUEUE -> {
                drawLine(color, Offset(w * 0.12f, h * 0.24f), Offset(w * 0.68f, h * 0.24f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.12f, h * 0.47f), Offset(w * 0.68f, h * 0.47f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.12f, h * 0.7f), Offset(w * 0.52f, h * 0.7f), stroke, StrokeCap.Round)
                drawPath(path { moveTo(w * 0.66f, h * 0.62f); lineTo(w * 0.88f, h * 0.77f); lineTo(w * 0.66f, h * 0.91f); close() }, color)
            }
            DesktopGlyphKind.LYRICS -> {
                drawRoundRect(color, Offset(w * 0.12f, h * 0.14f), Size(w * 0.76f, h * 0.65f), androidx.compose.ui.geometry.CornerRadius(stroke * 2f), style = Stroke(stroke))
                drawPath(path { moveTo(w * 0.32f, h * 0.79f); lineTo(w * 0.22f, h * 0.92f); lineTo(w * 0.52f, h * 0.79f) }, color, style = Stroke(stroke, cap = StrokeCap.Round))
                drawLine(color, Offset(w * 0.28f, h * 0.36f), Offset(w * 0.7f, h * 0.36f), stroke, StrokeCap.Round)
                drawLine(color, Offset(w * 0.28f, h * 0.56f), Offset(w * 0.6f, h * 0.56f), stroke, StrokeCap.Round)
            }
            DesktopGlyphKind.MORE -> {
                listOf(0.2f, 0.5f, 0.8f).forEach { x -> drawCircle(color, stroke * 0.75f, Offset(w * x, center.y)) }
            }
        }
    }
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
