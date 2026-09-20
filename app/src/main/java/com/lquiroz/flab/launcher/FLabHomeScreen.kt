package com.lquiroz.flab.launcher

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lquiroz.flab.motion.MotionChannels
import com.lquiroz.flab.ui.motion.MotionLayer
import com.lquiroz.flab.ui.theme.FLabTokens
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The home screen itself: a chip row, pages of icons, page dots, a dock — over whatever wallpaper
 * the system is showing behind the window.
 *
 * Three motions are layered here, and they come from the same engine:
 *
 *  - The cover hand-off, from the first degree. On the cover the whole home is drawn toward the
 *    hinge edge, shrinks a little and fades as the opening begins (`handoffAmount`), so that when
 *    One UI wakes the inner panel the content is already "in the fold" and simply continues.
 *  - The Duo pinch. Every icon's horizontal position passes through [HomeLayout.warpX] with the
 *    live `warpAmount`, read in the *placement* block only, so a moving hinge re-places icons
 *    without recomposing or re-measuring anything. Applied only on a wide (inner) window — the
 *    cover display is a separate flat panel whose middle is not a hinge.
 *  - The reflow. When the window changes size (cover to inner and back) the grid re-columns, and
 *    each icon springs from its old slot to its new one instead of jumping — the "icons reorganise"
 *    of the source footage, on a device whose two displays are physically separate.
 */
@Composable
fun FLabHomeScreen(
    channels: State<MotionChannels>,
    catalogue: LauncherCatalogue,
    homePresses: Int,
    isDefaultHome: Boolean,
    isFoldWallpaperActive: Boolean,
    onRequestDefaultHome: () -> Unit,
    onSetWallpaper: () -> Unit,
    onOpenFLab: () -> Unit,
    onLaunch: (LauncherEntry, Rect) -> Unit,
    onAppDetails: (LauncherEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val wide = maxWidth >= HomeLayout.INNER_MIN_WIDTH_DP.dp
        // The app's own screens sit slightly scaled and lowered while the device is closed and
        // grow into place as it opens. A home screen on the cover cannot: at rest it has to be
        // exactly where it is, or the wallpaper shows as a border around it. The cover gets the
        // veil and the hand-off; the inner display gets everything.
        val live = channels.value
        val layered = if (wide) {
            live
        } else {
            live.copy(contentScale = 1f, translationYDp = 0f, elevationDp = 0f, expansion = 1f)
        }
        MotionLayer(layered, Modifier.fillMaxSize()) {
            HomeContent(
                wide = wide,
                channels = channels,
                catalogue = catalogue,
                homePresses = homePresses,
                isDefaultHome = isDefaultHome,
                isFoldWallpaperActive = isFoldWallpaperActive,
                onRequestDefaultHome = onRequestDefaultHome,
                onSetWallpaper = onSetWallpaper,
                onOpenFLab = onOpenFLab,
                onLaunch = onLaunch,
                onAppDetails = onAppDetails,
            )
        }
    }
}

@Composable
private fun HomeContent(
    wide: Boolean,
    channels: State<MotionChannels>,
    catalogue: LauncherCatalogue,
    homePresses: Int,
    isDefaultHome: Boolean,
    isFoldWallpaperActive: Boolean,
    onRequestDefaultHome: () -> Unit,
    onSetWallpaper: () -> Unit,
    onOpenFLab: () -> Unit,
    onLaunch: (LauncherEntry, Rect) -> Unit,
    onAppDetails: (LauncherEntry) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                // The cover hand-off (see MotionChannels.handoffAmount). On a Galaxy Fold the
                // hinge is the cover display's left edge — the closed device opens like a book
                // with the spine on the left — so the whole home is drawn toward that edge,
                // shrinks a little and fades as the opening begins, and the inner display picks
                // the same content up pinched at the same hinge. Read here, in the draw block:
                // a moving hinge redraws this layer and recomposes nothing.
                val handoff = if (wide) 0f else channels.value.handoffAmount
                transformOrigin = TransformOrigin(0f, 0.5f)
                translationX = -handoff * size.width * HANDOFF_TRAVEL
                scaleX = 1f - HANDOFF_SHRINK * handoff
                scaleY = 1f - HANDOFF_SHRINK * handoff
                alpha = 1f - HANDOFF_FADE * handoff
            }
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp),
    ) {
        Spacer(Modifier.height(8.dp))
        HomeChips(
            isDefaultHome = isDefaultHome,
            isFoldWallpaperActive = isFoldWallpaperActive,
            onRequestDefaultHome = onRequestDefaultHome,
            onSetWallpaper = onSetWallpaper,
            onOpenFLab = onOpenFLab,
        )
        Spacer(Modifier.height(8.dp))

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            val density = LocalDensity.current
            val columns = HomeLayout.columnsFor(maxWidth.value.toInt())
            val spec = remember(maxWidth, maxHeight, columns) {
                with(density) {
                    HomeLayout.spec(
                        widthPx = maxWidth.toPx(),
                        heightPx = (maxHeight - DOTS_HEIGHT).toPx(),
                        columns = columns,
                        horizontalPaddingPx = 4.dp.toPx(),
                        minCellHeightPx = MIN_CELL_HEIGHT.toPx(),
                    )
                }
            }
            val pages = remember(catalogue.grid, spec.pageSize) {
                catalogue.grid.chunked(spec.pageSize).ifEmpty { listOf(emptyList()) }
            }
            val pagerState = rememberPagerState(pageCount = { pages.size })
            LaunchedEffect(homePresses) {
                if (homePresses > 0 && pagerState.currentPage != 0) pagerState.animateScrollToPage(0)
            }
            val warp: () -> Float = remember(wide, channels) {
                { if (wide) channels.value.warpAmount else 0f }
            }

            Column(Modifier.fillMaxSize()) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    beyondViewportPageCount = 1,
                ) { page ->
                    IconPage(
                        entries = pages[page],
                        spec = spec,
                        warp = warp,
                        onLaunch = onLaunch,
                        onAppDetails = onAppDetails,
                    )
                }
                PageDots(count = pages.size, current = pagerState.currentPage)
            }
        }

        Spacer(Modifier.height(10.dp))
        Dock(entries = catalogue.dock, onLaunch = onLaunch, onAppDetails = onAppDetails)
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun IconPage(
    entries: List<LauncherEntry>,
    spec: GridSpec,
    warp: () -> Float,
    onLaunch: (LauncherEntry, Rect) -> Unit,
    onAppDetails: (LauncherEntry) -> Unit,
) {
    val targets = remember(spec, entries) {
        entries.indices.map { index ->
            val slot = HomeLayout.slot(spec, index)
            Offset(slot.x, slot.y)
        }
    }
    val positions = remember(entries) {
        entries.map { Animatable(Offset.Zero, Offset.VectorConverter) }
    }
    var placedOnce by remember(entries) { mutableStateOf(false) }

    // First layout snaps; every later change of grid (a fold changed the window) springs each icon
    // from where it was to where it now belongs.
    LaunchedEffect(targets) {
        if (!placedOnce) {
            positions.forEachIndexed { index, animatable -> animatable.snapTo(targets[index]) }
            placedOnce = true
        } else {
            positions.forEachIndexed { index, animatable ->
                launch {
                    animatable.animateTo(
                        targets[index],
                        spring(dampingRatio = 0.82f, stiffness = 260f),
                    )
                }
            }
        }
    }

    val iconSize = with(LocalDensity.current) {
        (spec.cellWidth * ICON_FRACTION_OF_CELL).toDp().coerceIn(44.dp, 68.dp)
    }

    Layout(
        content = {
            entries.forEach { entry ->
                AppIcon(
                    entry = entry,
                    iconSize = iconSize,
                    showLabel = true,
                    onLaunch = onLaunch,
                    onAppDetails = onAppDetails,
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { measurables, constraints ->
        val cellWidth = spec.cellWidth.roundToInt().coerceAtLeast(1)
        val cellHeight = spec.cellHeight.roundToInt().coerceAtLeast(1)
        val placeables = measurables.map { it.measure(Constraints.fixed(cellWidth, cellHeight)) }
        val width = constraints.maxWidth
        layout(width, constraints.maxHeight) {
            // Both reads happen here, in placement: a moving hinge or a springing icon re-places
            // this page and nothing else.
            val warpAmount = warp()
            placeables.forEachIndexed { index, placeable ->
                val position = positions[index].value
                val centreX = position.x + cellWidth / 2f
                val warpedCentre = HomeLayout.warpX(centreX, width.toFloat(), warpAmount)
                placeable.placeRelative(
                    x = (warpedCentre - cellWidth / 2f).roundToInt(),
                    y = position.y.roundToInt(),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppIcon(
    entry: LauncherEntry,
    iconSize: Dp,
    showLabel: Boolean,
    onLaunch: (LauncherEntry, Rect) -> Unit,
    onAppDetails: (LauncherEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            bitmap = entry.icon,
            contentDescription = entry.label,
            modifier = Modifier
                .size(iconSize)
                .onGloballyPositioned { bounds = it.boundsInWindow() }
                .clip(RoundedCornerShape(ICON_CORNER_PERCENT))
                .combinedClickable(
                    onClick = { onLaunch(entry, bounds) },
                    onLongClick = { onAppDetails(entry) },
                ),
        )
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    shadow = Shadow(Color.Black.copy(alpha = 0.55f), Offset(0f, 1f), 6f),
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun Dock(
    entries: List<LauncherEntry>,
    onLaunch: (LauncherEntry, Rect) -> Unit,
    onAppDetails: (LauncherEntry) -> Unit,
) {
    if (entries.isEmpty()) return
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color.White.copy(alpha = 0.16f))
            .border(1.dp, Color.White.copy(alpha = 0.30f), shape)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        entries.forEach { entry ->
            AppIcon(
                entry = entry,
                iconSize = 56.dp,
                showLabel = false,
                onLaunch = onLaunch,
                onAppDetails = onAppDetails,
            )
        }
    }
}

@Composable
private fun HomeChips(
    isDefaultHome: Boolean,
    isFoldWallpaperActive: Boolean,
    onRequestDefaultHome: () -> Unit,
    onSetWallpaper: () -> Unit,
    onOpenFLab: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHIPS_HEIGHT),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // One ask at a time, in the order that matters: be the home first, then dress it.
        Box(Modifier.weight(1f)) {
            when {
                !isDefaultHome -> GlassChip("Make this your home screen", onRequestDefaultHome)
                !isFoldWallpaperActive -> GlassChip("Add the Fold wallpaper", onSetWallpaper)
            }
        }
        GlassChip("F/LAB", onOpenFLab)
    }
}

@Composable
private fun GlassChip(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(FLabTokens.RadiusPill)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.18f))
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                shadow = Shadow(Color.Black.copy(alpha = 0.45f), Offset(0f, 1f), 4f),
            ),
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun PageDots(count: Int, current: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DOTS_HEIGHT),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            Box(
                Modifier
                    .padding(horizontal = 3.dp)
                    .size(if (index == current) 7.dp else 5.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (index == current) 0.95f else 0.45f)),
            )
        }
    }
}

private val CHIPS_HEIGHT = 40.dp
private val DOTS_HEIGHT = 20.dp
private val MIN_CELL_HEIGHT = 104.dp
private const val ICON_FRACTION_OF_CELL = 0.62f
private const val ICON_CORNER_PERCENT = 24

/** How far, as a fraction of the cover's width, the home travels toward the hinge at full hand-off. */
private const val HANDOFF_TRAVEL = 0.22f
private const val HANDOFF_SHRINK = 0.10f
private const val HANDOFF_FADE = 0.35f
