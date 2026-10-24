package com.convx.migration.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.BasicText as ComposeText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.convx.music.playback.PlaybackRepeatMode
import com.convx.music.playback.PlaybackSnapshot
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import org.koin.compose.koinInject

private val DesktopBackground = Color(0xFF101116)
private val DesktopMuted = Color(0xFFA8A8B4)
private val DesktopAccent = Color(0xFFB8A8FF)
private val GlassShape = RoundedCornerShape(28.dp)

private enum class DesktopDestination(val title: String, val symbol: String) {
    LISTEN_NOW("Listen Now", "◖"),
    SEARCH("Search", "⌕"),
    LIBRARY("Library", "▤"),
    PLAYLISTS("Playlists", "♫"),
}

@OptIn(ExperimentalHazeApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
fun ConvxDesktopShell() {
    val playbackController = koinInject<DesktopMusicController>()
    val playback by playbackController.playbackSnapshot.collectAsState()
    val hazeState = remember { HazeState() }
    var destination by remember { mutableStateOf(DesktopDestination.LISTEN_NOW) }
    var searchQuery by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DesktopBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
        ) {
            AmbientBackdrop()
            DesktopPage(
                destination = destination,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                playback = playback,
                onTrackSelected = playbackController::selectTrack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 270.dp, end = 24.dp, top = 30.dp, bottom = 118.dp),
            )
        }

        DesktopSidebar(
            selected = destination,
            onDestinationSelected = { destination = it },
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 18.dp, top = 18.dp, bottom = 18.dp)
                .width(232.dp)
                .fillMaxHeight(),
        )

        DesktopMiniPlayer(
            playback = playback,
            controller = playbackController,
            hazeState = hazeState,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 270.dp, end = 24.dp, bottom = 18.dp)
                .fillMaxWidth()
                .height(82.dp),
        )
    }
}

@Composable
private fun AmbientBackdrop() {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF4E3B72).copy(alpha = 0.38f), Color.Transparent),
                    center = Offset(80f, 30f),
                    radius = 760f,
                ),
            ),
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(Color(0xFF274F5A).copy(alpha = 0.24f), Color.Transparent),
                    center = Offset(1_200f, 500f),
                    radius = 840f,
                ),
            ),
    )
}

@OptIn(ExperimentalHazeApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
private fun DesktopSidebar(
    selected: DesktopDestination,
    onDestinationSelected: (DesktopDestination) -> Unit,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(GlassShape)
            .hazeEffect(state = hazeState, style = HazeMaterials.thin(Color(0xD91B1C25)))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.13f), Color.White.copy(alpha = 0.045f)),
                ),
            )
            .border(
                width = 1.dp,
                brush = Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.08f)),
                ),
                shape = GlassShape,
            )
            .padding(horizontal = 14.dp, vertical = 18.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(DesktopAccent, Color(0xFFFF9EBD)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("c", color = Color(0xFF211B2C), fontSize = 21.sp, fontWeight = FontWeight.Black)
            }
            Column {
                Text("CONVX", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp)
                Text("MUSIC", color = DesktopMuted, fontSize = 9.sp, letterSpacing = 2.1.sp)
            }
        }

        Text(
            "LISTEN",
            color = DesktopMuted.copy(alpha = 0.76f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 12.dp, top = 30.dp, bottom = 10.dp),
        )
        DesktopDestination.entries.forEach { destination ->
            SidebarItem(
                destination = destination,
                selected = selected == destination,
                onClick = { onDestinationSelected(destination) },
            )
        }

        Text(
            "YOUR PLAYLISTS",
            color = DesktopMuted.copy(alpha = 0.76f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(start = 12.dp, top = 28.dp, bottom = 10.dp),
        )
        listOf("Soft Focus", "Night Drive", "Sunday Morning", "On Repeat").forEachIndexed { index, name ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onDestinationSelected(DesktopDestination.PLAYLISTS) }
                    .padding(horizontal = 11.dp, vertical = 10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(27.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(desktopDemoTracks[index].artStartColor(), desktopDemoTracks[index].artEndColor()),
                            ),
                        ),
                )
                Text(name, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black.copy(alpha = 0.14f))
                .padding(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFFE9B8A1), Color(0xFF8A6F8D)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("C", color = Color.White, fontWeight = FontWeight.Bold)
            }
            Column {
                Text("Your library", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Desktop preview", color = DesktopMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.weight(1f))
            Text("···", color = DesktopMuted, fontSize = 20.sp)
        }
    }
}

@Composable
private fun SidebarItem(
    destination: DesktopDestination,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(
            destination.symbol,
            color = if (selected) DesktopAccent else DesktopMuted,
            fontSize = 18.sp,
            modifier = Modifier.width(22.dp),
        )
        Text(
            destination.title,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.79f),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
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
        verticalArrangement = Arrangement.spacedBy(26.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("YOUR MUSIC, IN GOOD COMPANY", color = DesktopMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                Text(destination.title, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            }
            if (destination == DesktopDestination.SEARCH) {
                SearchField(value = searchQuery, onValueChange = onSearchQueryChange, modifier = Modifier.width(330.dp))
            } else {
                Text("⌕", color = Color.White.copy(alpha = 0.78f), fontSize = 23.sp)
            }
        }

        if (destination == DesktopDestination.SEARCH) {
            val results = desktopDemoTracks.filter { track ->
                val query = searchQuery.trim().lowercase()
                query.isEmpty() || track.snapshot.title.lowercase().contains(query) ||
                    track.snapshot.artists.any { it.lowercase().contains(query) } ||
                    track.album.lowercase().contains(query)
            }
            SectionTitle("Songs and albums", "${results.size} RESULTS")
            TrackList(tracks = results, playback = playback, onTrackSelected = onTrackSelected)
        } else {
            if (destination == DesktopDestination.LISTEN_NOW) {
                HeroCard(
                    onPlay = {
                        onTrackSelected(desktopDemoTracks.first().snapshot.id)
                    },
                )
                SectionTitle("Made for your mood", "FRESH PICKS")
                TrackCards(onTrackSelected = onTrackSelected)
                SectionTitle("Up next for you", "PLAY SOMETHING")
                TrackList(tracks = desktopDemoTracks, playback = playback, onTrackSelected = onTrackSelected)
            } else if (destination == DesktopDestination.LIBRARY) {
                SectionTitle("Your collection", "5 SONGS")
                TrackList(tracks = desktopDemoTracks, playback = playback, onTrackSelected = onTrackSelected)
                SectionTitle("Albums you saved", "YOUR LIBRARY")
                TrackCards(onTrackSelected = onTrackSelected)
            } else {
                SectionTitle("Playlists for every hour", "4 PLAYLISTS")
                TrackCards(onTrackSelected = onTrackSelected)
                SectionTitle("Recently played", "PICK UP WHERE YOU LEFT OFF")
                TrackList(tracks = desktopDemoTracks, playback = playback, onTrackSelected = onTrackSelected)
            }
        }
    }
}

@Composable
private fun HeroCard(onPlay: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .height(230.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF55416F), Color(0xFF283D50), Color(0xFF18262E)),
                ),
            )
            .padding(start = 32.dp, end = 30.dp, top = 24.dp, bottom = 24.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("A CONVX MIX", color = Color.White.copy(alpha = 0.73f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
            Text("A little more\nroom to listen.", color = Color.White, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold)
            Text("New sounds, picked for wherever your day goes.", color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
            DesktopActionButton(label = "Play mix", onClick = onPlay)
        }
        Box(
            modifier = Modifier
                .size(174.dp)
                .clip(RoundedCornerShape(25.dp))
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFE5A5B5), Color(0xFF75679D), Color(0xFF345461)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("C", color = Color.White.copy(alpha = 0.9f), fontSize = 104.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun TrackCards(onTrackSelected: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        desktopDemoTracks.forEachIndexed { index, track ->
            Column(
                modifier = Modifier
                    .width(174.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .clickable { onTrackSelected(track.snapshot.id) }
                    .padding(bottom = 5.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TrackCover(track, modifier = Modifier.fillMaxWidth().height(154.dp), index = index)
                Text(track.snapshot.title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(track.snapshot.artists.joinToString(), color = DesktopMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        tracks.forEachIndexed { index, track ->
            val selected = playback.track?.id == track.snapshot.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (selected) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                    .clickable { onTrackSelected(track.snapshot.id) }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            ) {
                TrackCover(track, modifier = Modifier.size(42.dp), index = index)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(track.snapshot.title, color = if (selected) DesktopAccent else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Text("${track.snapshot.artists.joinToString()}  ·  ${track.album}", color = DesktopMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(if (selected && playback.isPlaying) "Ⅱ" else "▶", color = DesktopMuted, fontSize = 13.sp)
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
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(track.artStartColor(), track.artEndColor()))),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            listOf("◌", "◍", "✳", "◖", "✦")[index % 5],
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 37.sp,
            fontWeight = FontWeight.Light,
        )
    }
}

@Composable
private fun SectionTitle(title: String, detail: String) {
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(detail, color = DesktopMuted, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
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
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(15.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text("⌕", color = DesktopMuted, fontSize = 17.sp)
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

@OptIn(ExperimentalHazeApi::class, ExperimentalHazeMaterialsApi::class)
@Composable
private fun DesktopMiniPlayer(
    playback: PlaybackSnapshot,
    controller: DesktopMusicController,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    val favoriteTrackIds by controller.favoriteTrackIds.collectAsState()
    val track = desktopDemoTracks.firstOrNull { it.snapshot.id == playback.track?.id } ?: desktopDemoTracks.first()
    val duration = (playback.durationMs ?: track.durationMs).coerceAtLeast(1L)
    val progress = (playback.positionMs.toFloat() / duration).coerceIn(0f, 1f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .hazeEffect(state = hazeState, style = HazeMaterials.thin(Color(0xE0181921)))
            .background(
                Brush.verticalGradient(
                    listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.055f)),
                ),
            )
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.30f), Color.White.copy(alpha = 0.08f))),
                RoundedCornerShape(24.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        TrackCover(track, modifier = Modifier.size(54.dp), index = playback.mediaItemIndex.coerceAtLeast(0))
        Column(modifier = Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(track.snapshot.title, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(track.snapshot.artists.joinToString(), color = DesktopMuted, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DesktopIconButton(
            symbol = if (track.snapshot.id in favoriteTrackIds) "♥" else "♡",
            description = if (track.snapshot.id in favoriteTrackIds) "Remove from favorites" else "Add to favorites",
            selected = track.snapshot.id in favoriteTrackIds,
            onClick = { controller.toggleFavorite(track.snapshot.id) },
        )
        Spacer(Modifier.weight(1f))
        DesktopIconButton(
            symbol = "⤨",
            description = if (playback.shuffleEnabled) "Turn shuffle off" else "Turn shuffle on",
            selected = playback.shuffleEnabled,
            onClick = { controller.setShuffleEnabled(!playback.shuffleEnabled) },
        )
        DesktopIconButton(
            symbol = "◀",
            description = "Previous track",
            enabled = playback.canSkipPrevious,
            onClick = { controller.requestSkip(forward = false) },
        )
        DesktopIconButton(
            symbol = if (playback.isPlaying) "Ⅱ" else "▶",
            description = if (playback.isPlaying) "Pause" else "Play",
            prominent = true,
            onClick = controller::togglePlayPause,
        )
        DesktopIconButton(
            symbol = "▶",
            description = "Next track",
            enabled = playback.canSkipNext,
            onClick = { controller.requestSkip(forward = true) },
        )
        DesktopIconButton(
            symbol = when (playback.repeatMode) {
                PlaybackRepeatMode.OFF -> "↻"
                PlaybackRepeatMode.ONE -> "↻¹"
                PlaybackRepeatMode.ALL -> "↻"
            },
            description = "Change repeat mode",
            selected = playback.repeatMode != PlaybackRepeatMode.OFF,
            onClick = {
                controller.cycleRepeatMode()
            },
        )
        Spacer(Modifier.width(8.dp))
        Text(formatTime(playback.positionMs), color = DesktopMuted, fontSize = 10.sp)
        DesktopSeekBar(
            progress = progress,
            onProgressChanged = { controller.seekTo((duration * it).toLong()) },
            modifier = Modifier.weight(1f),
        )
        Text(formatTime(duration), color = DesktopMuted, fontSize = 10.sp)
        Text("◖", color = DesktopMuted, fontSize = 18.sp)
    }
}

@Composable
private fun DesktopActionButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White)
            .semantics {
                contentDescription = label
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color(0xFF17171D), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun DesktopIconButton(
    symbol: String,
    description: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    prominent: Boolean = false,
    onClick: () -> Unit,
) {
    val shape = if (prominent) CircleShape else RoundedCornerShape(14.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(40.dp)
            .clip(shape)
            .background(if (prominent) Color.White else Color.Transparent)
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Text(
            symbol,
            color = when {
                !enabled -> DesktopMuted.copy(alpha = 0.4f)
                prominent -> Color(0xFF17171D)
                selected -> DesktopAccent
                else -> Color.White
            },
            fontSize = if (prominent) 16.sp else 17.sp,
            fontWeight = if (prominent || selected) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
private fun DesktopSeekBar(
    progress: Float,
    onProgressChanged: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .height(28.dp)
            .semantics {
                contentDescription = "Playback position"
                progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
                setProgress { requestedProgress ->
                    onProgressChanged(requestedProgress.coerceIn(0f, 1f))
                    true
                }
            }
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                val change = when (event.key) {
                    Key.DirectionLeft -> -0.01f
                    Key.DirectionRight -> 0.01f
                    else -> return@onKeyEvent false
                }
                onProgressChanged((progress + change).coerceIn(0f, 1f))
                true
            }
            .pointerInput(Unit) {
                detectTapGestures { point ->
                    if (size.width > 0) onProgressChanged((point.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    if (size.width > 0) onProgressChanged((change.position.x / size.width).coerceIn(0f, 1f))
                    change.consume()
                }
            },
    ) {
        val centerY = size.height / 2f
        val activeX = size.width * progress.coerceIn(0f, 1f)
        drawLine(
            color = Color.White.copy(alpha = 0.2f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = DesktopAccent,
            start = Offset(0f, centerY),
            end = Offset(activeX, centerY),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(color = Color.White, radius = 5.dp.toPx(), center = Offset(activeX, centerY))
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
        style = TextStyle(
            color = color,
            fontSize = fontSize,
            fontWeight = fontWeight,
            letterSpacing = letterSpacing,
            lineHeight = lineHeight,
        ),
        maxLines = maxLines,
        overflow = overflow,
    )
}

private fun DesktopDemoTrack.artStartColor() = artStart

private fun DesktopDemoTrack.artEndColor() = artEnd

private fun formatTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
