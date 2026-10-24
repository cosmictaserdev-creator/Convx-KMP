@file:OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)

package com.convx.music.playback.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.SeekableTransitionState
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.composables.sfsymbols.SfRenderingMode
import com.composables.sfsymbols.SfSymbol
import com.composables.sfsymbols.SfSymbolIcon
import com.composables.sfsymbols.SfWeight
import com.convx.music.designsystem.ConvxDesignTokens
import com.convx.music.designsystem.MotionTokens
import com.convx.music.playback.PlaybackController
import com.convx.music.playback.PlaybackSnapshot
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import zone.ien.hig.CupertinoSlider
import zone.ien.hig.glass.LiquidGlassMaterials
import zone.ien.hig.glass.liquidGlass
import kotlinx.coroutines.launch
import kotlin.math.roundToLong

@Immutable
data class PlaybackSharedElements(
    val artwork: Modifier = Modifier,
    val title: Modifier = Modifier,
    val artist: Modifier = Modifier,
    val controls: Modifier = Modifier,
    val queue: Modifier = Modifier,
    val playPause: Modifier = Modifier,
)

/** Animates the compact and expanded player through a single shared-element scope. */
@Composable
fun PlaybackPlayerMorph(
    trackId: String?,
    expanded: Boolean,
    onExpand: () -> Unit,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    compactContent: @Composable BoxScope.(PlaybackSharedElements, () -> Unit) -> Unit,
    expandedContent: @Composable BoxScope.(PlaybackSharedElements, () -> Unit) -> Unit,
) {
    val transitionState = remember(trackId) { SeekableTransitionState(expanded) }
    val transition = rememberTransition(transitionState, label = "PlaybackMiniFullMorph")
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(transitionState, expanded) {
        transitionState.animateTo(
            targetState = expanded,
            animationSpec = if (expanded) {
                MotionTokens.playerSheetOpen()
            } else {
                MotionTokens.playerSheetClose(landing = true)
            },
        )
    }

    SharedTransitionLayout(modifier) {
        val sharedTransitionScope = this
        transition.AnimatedContent(
            transitionSpec = {
                val enterSpec = MotionTokens.playerSheetOpen<Float>()
                val exitSpec = MotionTokens.playerSheetClose<Float>(landing = true)
                val contentTransform =
                    (fadeIn(enterSpec) + scaleIn(initialScale = 0.98f, animationSpec = enterSpec)) togetherWith
                        (fadeOut(exitSpec) + scaleOut(targetScale = 0.98f, animationSpec = exitSpec))
                contentTransform.using(SizeTransform(clip = false)).apply {
                    targetContentZIndex = if (targetState) 1f else 0f
                }
            },
            modifier = Modifier.fillMaxSize().pointerInput(expanded, trackId, transitionState) {
                val travelPx = size.height.coerceAtLeast(1).toFloat()
                val velocityTracker = VelocityTracker()
                var openProgress = if (expanded) 1f else 0f
                var hasDragged = false

                detectVerticalDragGestures(
                    onDragStart = {
                        velocityTracker.resetTracking()
                        openProgress = if (expanded) 1f else 0f
                        hasDragged = false
                    },
                    onVerticalDrag = { change, dragAmount ->
                        velocityTracker.addPointerInputChange(change)
                        val movesTowardPlayer = if (expanded) dragAmount > 0f else dragAmount < 0f
                        if (hasDragged || movesTowardPlayer) {
                            hasDragged = true
                            change.consume()
                            openProgress = (openProgress - dragAmount / travelPx).coerceIn(0f, 1f)
                            val fraction = if (expanded) 1f - openProgress else openProgress
                            coroutineScope.launch {
                                transitionState.seekTo(fraction = fraction, targetState = !expanded)
                            }
                        }
                    },
                    onDragCancel = {
                        if (hasDragged) {
                            coroutineScope.launch {
                                transitionState.animateTo(
                                    targetState = expanded,
                                    animationSpec = if (expanded) {
                                        MotionTokens.playerSheetOpen()
                                    } else {
                                        MotionTokens.playerSheetClose(landing = true)
                                    },
                                )
                            }
                        }
                    },
                    onDragEnd = {
                        if (!hasDragged) return@detectVerticalDragGestures
                        val openingVelocity = -velocityTracker.calculateVelocity().y
                        val settleExpanded = PlayerMorphGeometry.shouldExpandAfterRelease(
                            progress = openProgress,
                            openingVelocityPxPerSecond = openingVelocity,
                            travelPx = travelPx,
                        )
                        when {
                            settleExpanded == expanded -> coroutineScope.launch {
                                transitionState.animateTo(
                                    targetState = expanded,
                                    animationSpec = if (expanded) {
                                        MotionTokens.playerSheetOpen()
                                    } else {
                                        MotionTokens.playerSheetClose(landing = true)
                                    },
                                )
                            }
                            settleExpanded -> onExpand()
                            else -> onCollapse()
                        }
                    },
                )
            },
        ) { isExpanded ->
            val visibilityScope = this
            val sharedElements = if (trackId.isNullOrBlank()) {
                PlaybackSharedElements()
            } else {
                with(sharedTransitionScope) {
                    PlaybackSharedElements(
                        artwork = Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState("player-artwork:$trackId"),
                            animatedVisibilityScope = visibilityScope,
                            boundsTransform = { _, _ ->
                                if (isExpanded) MotionTokens.playerSheetOpen() else MotionTokens.playerSheetClose(landing = true)
                            },
                            renderInOverlayDuringTransition = true,
                            zIndexInOverlay = 2f,
                        ),
                        title = Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState("player-title:$trackId"),
                            animatedVisibilityScope = visibilityScope,
                            boundsTransform = { _, _ ->
                                if (isExpanded) MotionTokens.playerSheetOpen() else MotionTokens.playerSheetClose(landing = true)
                            },
                            renderInOverlayDuringTransition = true,
                            zIndexInOverlay = 3f,
                        ),
                        artist = Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState("player-artist:$trackId"),
                            animatedVisibilityScope = visibilityScope,
                            boundsTransform = { _, _ ->
                                if (isExpanded) MotionTokens.playerSheetOpen() else MotionTokens.playerSheetClose(landing = true)
                            },
                            renderInOverlayDuringTransition = true,
                            zIndexInOverlay = 3f,
                        ),
                        controls = Modifier.sharedBounds(
                            sharedContentState = rememberSharedContentState("player-controls:$trackId"),
                            animatedVisibilityScope = visibilityScope,
                            boundsTransform = { _, _ ->
                                if (isExpanded) MotionTokens.playerSheetOpen() else MotionTokens.playerSheetClose(landing = true)
                            },
                            renderInOverlayDuringTransition = true,
                            zIndexInOverlay = 4f,
                        ),
                        queue = Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState("player-queue:$trackId"),
                            animatedVisibilityScope = visibilityScope,
                            boundsTransform = { _, _ ->
                                if (isExpanded) MotionTokens.playerSheetOpen() else MotionTokens.playerSheetClose(landing = true)
                            },
                            renderInOverlayDuringTransition = true,
                            zIndexInOverlay = 5f,
                        ),
                        playPause = Modifier.sharedElement(
                            sharedContentState = rememberSharedContentState("player-play-pause:$trackId"),
                            animatedVisibilityScope = visibilityScope,
                            boundsTransform = { _, _ ->
                                if (isExpanded) MotionTokens.playerSheetOpen() else MotionTokens.playerSheetClose(landing = true)
                            },
                            renderInOverlayDuringTransition = true,
                            zIndexInOverlay = 6f,
                        ),
                    )
                }
            }
            Box(Modifier.fillMaxSize()) {
                if (isExpanded) {
                    expandedContent(sharedElements, onCollapse)
                } else {
                    compactContent(sharedElements, onExpand)
                }
            }
        }
    }
}

@Composable
fun PlaybackMiniPlayer(
    playback: PlaybackSnapshot,
    sharedElements: PlaybackSharedElements,
    backdrop: LayerBackdrop,
    onExpand: () -> Unit,
    onOpenQueue: () -> Unit,
    onTogglePlayPause: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val track = playback.track
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .shadow(14.dp, shape)
            .clip(shape)
            .liquidGlass(backdrop, shape, LiquidGlassMaterials.MiniPlayer)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = sharedElements.artwork
                .size(46.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .clickable(onClick = onExpand),
        ) {
            AsyncImage(
                model = track?.artworkUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(
            modifier = Modifier.weight(1f)
                .then(sharedElements.title)
                .clickable(role = Role.Button, onClick = onExpand),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            BasicText(
                text = track?.title ?: "Choose a song",
                style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                text = playback.errorMessage ?: track?.artists?.joinToString().orEmpty(),
                style = TextStyle(color = Color.White.copy(alpha = 0.68f), fontSize = 12.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = sharedElements.artist,
            )
        }
        Row(
            modifier = sharedElements.controls,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            PlaybackSymbolButton(
                symbolName = "list.bullet",
                description = "Queue",
                enabled = playback.mediaItemCount > 0,
                onClick = onOpenQueue,
                modifier = sharedElements.queue,
            )
            PlaybackSymbolButton(
                symbolName = if (playback.isPlaying) "pause.fill" else "play.fill",
                description = if (playback.isPlaying) "Pause" else "Play",
                emphasized = true,
                enabled = track != null,
                onClick = onTogglePlayPause,
                modifier = sharedElements.playPause,
            )
        }
    }
}

@Composable
fun PlaybackFullPlayer(
    playback: PlaybackSnapshot,
    controller: PlaybackController,
    sharedElements: PlaybackSharedElements,
    darkTheme: Boolean,
    sleepTimerActive: Boolean,
    crossfadeEnabled: Boolean = false,
    crossfadeAvailable: Boolean = false,
    crossfadeLocked: Boolean = false,
    playbackSettingsError: String? = null,
    onToggleCrossfade: () -> Unit = {},
    onCollapse: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val foreground = if (darkTheme) Color.White else Color(0xFF17171B)
    val muted = foreground.copy(alpha = if (darkTheme) 0.68f else 0.62f)
    val background = if (darkTheme) Color(0xFF111116) else Color(0xFFF7F7FA)
    val progressBackdrop = rememberLayerBackdrop()
    var scrubPosition by remember(playback.track?.id) { mutableFloatStateOf(playback.positionMs.toFloat()) }
    var isScrubbing by remember(playback.track?.id) { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(playback.positionMs, isScrubbing) {
        if (!isScrubbing) scrubPosition = playback.positionMs.toFloat()
    }

    BoxWithConstraints(
        modifier = modifier.background(
            Brush.verticalGradient(
                if (darkTheme) listOf(Color(0xFF28242D), background, background)
                else listOf(Color(0xFFE7E8EF), background, background),
            ),
        ),
    ) {
        val wideLayout = maxWidth >= 760.dp
        val artworkSize = minOf(
            if (wideLayout) maxWidth * 0.40f else maxWidth * 0.82f,
            maxHeight * if (wideLayout) 0.68f else 0.42f,
            if (wideLayout) 520.dp else 420.dp,
        )
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = if (wideLayout) 44.dp else 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                PlaybackSymbolButton("chevron.down", "Collapse player", foreground, onClick = onCollapse)
                BasicText("Now Playing", style = TextStyle(color = muted, fontSize = 13.sp, fontWeight = FontWeight.Medium))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (crossfadeAvailable) {
                        PlaybackSymbolButton(
                            "arrow.left.arrow.right",
                            when {
                                crossfadeLocked -> "Crossfade is unavailable while bit-perfect playback is enabled"
                                crossfadeEnabled -> "Turn crossfade off"
                                else -> "Turn crossfade on"
                            },
                            if (crossfadeEnabled) ConvxDesignTokens.Colors.AccentRed else foreground,
                            enabled = !crossfadeLocked,
                            onClick = onToggleCrossfade,
                        )
                    }
                    PlaybackSymbolButton(
                        "moon.fill",
                        if (sleepTimerActive) "Sleep timer active" else "Sleep timer",
                        if (sleepTimerActive) ConvxDesignTokens.Colors.AccentRed else foreground,
                        onClick = onOpenSleepTimer,
                    )
                    PlaybackSymbolButton(
                        "list.bullet",
                        "Queue",
                        foreground,
                        enabled = playback.mediaItemCount > 0,
                        onClick = onOpenQueue,
                        modifier = sharedElements.queue,
                    )
                }
            }

            playbackSettingsError?.let { error ->
                BasicText(
                    error,
                    style = TextStyle(color = ConvxDesignTokens.Colors.AccentRed, fontSize = 12.sp),
                )
            }

            if (wideLayout) {
                Row(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(48.dp),
                ) {
                    PlaybackArtwork(
                        playback.track?.artworkUrl,
                        sharedElements.artwork.size(artworkSize).aspectRatio(1f),
                        darkTheme,
                    )
                    PlaybackDetails(
                        playback = playback,
                        controller = controller,
                        sharedElements = sharedElements,
                        foreground = foreground,
                        muted = muted,
                        progressBackdrop = progressBackdrop,
                        scrubPosition = scrubPosition,
                        onScrubPositionChange = { scrubPosition = it; isScrubbing = true },
                        onScrubFinished = { controller.seekTo(scrubPosition.roundToLong()); isScrubbing = false },
                        modifier = Modifier.weight(1f),
                    )
                }
            } else {
                Spacer(Modifier.weight(0.3f))
                PlaybackArtwork(
                    playback.track?.artworkUrl,
                    sharedElements.artwork.size(artworkSize).aspectRatio(1f).align(Alignment.CenterHorizontally),
                    darkTheme,
                )
                Spacer(Modifier.weight(0.22f))
                PlaybackDetails(
                    playback = playback,
                    controller = controller,
                    sharedElements = sharedElements,
                    foreground = foreground,
                    muted = muted,
                    progressBackdrop = progressBackdrop,
                    scrubPosition = scrubPosition,
                    onScrubPositionChange = { scrubPosition = it; isScrubbing = true },
                    onScrubFinished = { controller.seekTo(scrubPosition.roundToLong()); isScrubbing = false },
                )
            }
        }
    }
}

@Composable
private fun PlaybackDetails(
    playback: PlaybackSnapshot,
    controller: PlaybackController,
    sharedElements: PlaybackSharedElements,
    foreground: Color,
    muted: Color,
    progressBackdrop: LayerBackdrop,
    scrubPosition: Float,
    onScrubPositionChange: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration = playback.durationMs?.takeIf { it > 0L }
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            BasicText(
                playback.track?.title ?: "Choose a song",
                modifier = sharedElements.title,
                style = TextStyle(color = foreground, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                playback.errorMessage ?: playback.track?.artists?.joinToString().orEmpty(),
                modifier = sharedElements.artist,
                style = TextStyle(color = muted, fontSize = 17.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.fillMaxWidth().height(28.dp).layerBackdrop(progressBackdrop)) {
                CupertinoSlider(
                    value = scrubPosition,
                    onValueChange = onScrubPositionChange,
                    modifier = Modifier.fillMaxSize(),
                    enabled = duration != null,
                    valueRange = 0f..(duration?.toFloat() ?: 1f),
                    backdrop = progressBackdrop,
                    onValueChangeFinished = onScrubFinished,
                )
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(formatPlaybackTime(if (duration == null) playback.positionMs else scrubPosition.toLong()), style = TextStyle(color = muted, fontSize = 12.sp))
                BasicText(duration?.let(::formatPlaybackTime) ?: "--:--", style = TextStyle(color = muted, fontSize = 12.sp))
            }
        }

        Row(
            modifier = Modifier.then(sharedElements.controls).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PlaybackSymbolButton(
                "shuffle",
                if (playback.shuffleEnabled) "Turn shuffle off" else "Turn shuffle on",
                if (playback.shuffleEnabled) ConvxDesignTokens.Colors.AccentRed else muted,
                onClick = { controller.setShuffleEnabled(!playback.shuffleEnabled) },
            )
            PlaybackSymbolButton(
                "backward.fill",
                "Previous track",
                foreground,
                emphasized = false,
                enabled = playback.canSkipPrevious,
                onClick = { controller.requestSkip(forward = false) },
                size = 52.dp,
            )
            PlaybackSymbolButton(
                if (playback.isPlaying) "pause.fill" else "play.fill",
                if (playback.isPlaying) "Pause" else "Play",
                Color(0xFF17171B),
                emphasized = true,
                enabled = playback.track != null,
                onClick = controller::togglePlayPause,
                size = 64.dp,
                iconSize = 28.dp,
                modifier = sharedElements.playPause,
            )
            PlaybackSymbolButton(
                "forward.fill",
                "Next track",
                foreground,
                emphasized = false,
                enabled = playback.canSkipNext,
                onClick = { controller.requestSkip(forward = true) },
                size = 52.dp,
            )
            PlaybackSymbolButton(
                "repeat",
                "Change repeat mode",
                if (playback.repeatMode != com.convx.music.playback.PlaybackRepeatMode.OFF) ConvxDesignTokens.Colors.AccentRed else muted,
                onClick = controller::cycleRepeatMode,
            )
        }
    }
}

@Composable
private fun PlaybackArtwork(
    artworkUrl: String?,
    modifier: Modifier,
    darkTheme: Boolean,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (darkTheme) Color(0xFF29292D) else Color(0xFFE1E1E5)),
    ) {
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PlaybackSymbolButton(
    symbolName: String,
    description: String,
    tint: Color = Color.White,
    emphasized: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp = 44.dp,
    iconSize: androidx.compose.ui.unit.Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    val symbol = remember(symbolName) { requireNotNull(SfSymbol.named(symbolName)) { "Unknown SF Symbol: $symbolName" } }
    Box(
        modifier = modifier
            .size(size)
            .clip(if (emphasized) CircleShape else RoundedCornerShape(14.dp))
            .background(if (emphasized) Color.White else Color.Transparent)
            .semantics { contentDescription = description; role = Role.Button }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        SfSymbolIcon(
            symbol = symbol,
            modifier = Modifier.size(iconSize),
            size = iconSize,
            weight = SfWeight.Bold,
            mode = SfRenderingMode.Monochrome,
            tint = if (!enabled) tint.copy(alpha = 0.35f) else if (emphasized) Color(0xFF17171B) else tint,
            contentDescription = null,
        )
    }
}

private fun formatPlaybackTime(milliseconds: Long): String {
    val totalSeconds = milliseconds.coerceAtLeast(0L) / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = (totalSeconds % 60L).toString().padStart(2, '0')
    return "$minutes:$seconds"
}
