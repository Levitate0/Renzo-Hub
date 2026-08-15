@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package top.levitatemedia.renzo.tv.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import top.levitatemedia.renzo.tv.api.CardItem
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

// ---------------------------------------------------------------------------
// Grids — port of media-grid.tsx: EmptyState, MediaGrid (the wrapping cover
// grid used by search results, categories, library, updates and history),
// GridSkeleton pulse placeholders and BrowseRow (the Discover row: horizontal
// scroll with "See all ›" in the heading and a trailing More tile).
// ---------------------------------------------------------------------------

/** `empty mt-4 rounded-xl border border-dashed border-border px-5 py-14
 *  text-center text-sm text-muted-foreground` (rounded-xl = 16dp via the
 *  app's --radius-xl token). */
@Composable
fun EmptyState(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .dashedBorder(RenzoColors.Border, 16.dp)
            .padding(horizontal = 20.dp, vertical = 56.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = RenzoColors.MutedForeground,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
    }
}

/** Tailwind `animate-pulse` alpha for skeleton fills. */
@Composable
private fun pulseAlpha(): Float {
    val transition = rememberInfiniteTransition(label = "pulse")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    ).value
}

/** One skeleton card: rounded-xl border bg-card; pulsing 2/3 poster well +
 *  two caption lines (h-3 w-4/5, h-2.5 w-1/2) in a p-3 space-y-2 block. */
@Composable
private fun SkeletonCard(modifier: Modifier = Modifier) {
    val alpha = pulseAlpha()
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(RenzoColors.Card, RoundedCornerShape(16.dp))
            .border(1.dp, RenzoColors.Border, RoundedCornerShape(16.dp)),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .alpha(alpha)
                .background(RenzoColors.Muted),
        )
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier
                    .fillMaxWidth(0.8f)
                    .height(12.dp)
                    .alpha(alpha)
                    .background(RenzoColors.Muted, RoundedCornerShape(4.dp)),
            )
            Box(
                Modifier
                    .fillMaxWidth(0.5f)
                    .height(10.dp)
                    .alpha(alpha)
                    .background(RenzoColors.Muted, RoundedCornerShape(4.dp)),
            )
        }
    }
}

/** Pulse placeholders while a grid's first fetch is in flight (GridSkeleton).
 *  Non-lazy so it can also sit inside the Discover scroll column. */
@Composable
fun GridSkeleton(count: Int = 12, modifier: Modifier = Modifier) {
    val w = top.levitatemedia.renzo.tv.renzoScreenWidthDp()
    // Same column math as posterGridCells (2 cols <420, auto-fill 148/158 above).
    val cols = if (w < 420) 2 else maxOf(2, (w - 32) / (if (w < 640) 148 + 12 else 158 + 18))
    val gap = posterGridGap()
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(gap)) {
        (0 until count).chunked(cols).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                rowItems.forEach { _ -> SkeletonCard(Modifier.weight(1f)) }
                repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/**
 * Wrapping cover grid with empty state (old renderGrid / MediaGrid): `mt-4`
 * over GRID_COLS (posterGridCells/posterGridGap). Content filtering stays in
 * the callers (they already hold the level). [loading] with no items yet shows
 * the GridSkeleton, exactly like the web.
 */
@Composable
fun MediaGrid(
    items: List<CardItem>,
    onOpen: (CardItem) -> Unit,
    modifier: Modifier = Modifier,
    empty: String = "Nothing here yet.",
    loading: Boolean = false,
) {
    val gridMetrics = rememberCardMetrics()
    when {
        loading -> GridSkeleton(modifier = modifier.padding(top = 16.dp))
        items.isEmpty() -> EmptyState(empty, modifier)
        else -> BoxWithConstraints(modifier.fillMaxSize()) {
            // contentPadding (16 top + 24 bottom) comes out of the tile budget.
            val cellWidth = tileWidthFor(if (maxHeight > 0.dp) maxHeight - 40.dp else Dp.Unspecified)
            val narrow = top.levitatemedia.renzo.tv.ui.theme.logicalScreenSize().first < 420
            val gridState = rememberLazyGridState()
            var focusedIndex by remember { mutableStateOf(-1) }
            // Centre the focused card instead of the default "scroll just far
            // enough": on a 10-foot screen the selection should sit mid-screen.
            LaunchedEffect(focusedIndex) {
                if (focusedIndex < 0) return@LaunchedEffect
                val info = gridState.layoutInfo
                val item = info.visibleItemsInfo.firstOrNull { it.index == focusedIndex }
                val viewport = info.viewportSize.height
                if (item != null && viewport > 0) {
                    val target = (viewport - item.size.height) / 2
                    gridState.animateScrollToItem(focusedIndex, -target)
                } else {
                    gridState.animateScrollToItem(focusedIndex)
                }
            }
            LazyVerticalGrid(
                state = gridState,
                columns = if (narrow) {
                    androidx.compose.foundation.lazy.grid.GridCells.Fixed(2)
                } else {
                    androidx.compose.foundation.lazy.grid.GridCells.Adaptive(cellWidth)
                },
                horizontalArrangement = Arrangement.spacedBy(posterGridGap()),
                verticalArrangement = Arrangement.spacedBy(posterGridGap()),
                contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(items) { index, item ->
                    PosterCard(
                        item = item,
                        onClick = { onOpen(item) },
                        width = null,
                        metrics = gridMetrics,
                        modifier = Modifier.onFocusChanged { if (it.hasFocus) focusedIndex = index },
                    )
                }
            }
        }
    }
}

/** Trailing "More ›" tile (`more-tile`): flex 0 0 96px, full row height,
 *  rounded-xl dashed border bg-card/50; "›" text-3xl + "More" text-xs/600. */
@Composable
private fun MoreTile(onMore: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (focused) RenzoColors.Foreground else RenzoColors.MutedForeground
    Column(
        Modifier
            .width(96.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(RenzoColors.Card.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .dashedBorder(if (focused) RenzoColors.Primary else RenzoColors.Border, 16.dp)
            .focusRing(focused, 16.dp)
            .tvClickable(onFocused = { focused = it }, onClick = onMore),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        Text("›", color = fg, fontSize = 30.sp, lineHeight = 30.sp)
        Text("More", color = fg, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * A Discover row (BrowseRowGrid): `browse-row mb-6 md:mb-8`; heading row
 * `mb-3 items-baseline justify-between` with the 18sp/600 title and the
 * primary "See all ›"; body = horizontal snap-scroll of 150dp cards (172dp
 * from the md break, gaps 12/18 — the `.browse-scroll` rules) + More tile.
 * The web's desktop paging arrows are pointer-only and never render on TV.
 */
@Composable
fun MediaRow(
    heading: String,
    items: List<CardItem>,
    onOpen: (CardItem) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
) {
    val md = top.levitatemedia.renzo.tv.ui.theme.logicalScreenSize().first >= 768
    val gap = if (md) 18.dp else 12.dp
    Column(modifier.fillMaxWidth().padding(bottom = if (md) 32.dp else 24.dp)) {
        // row-head: mb-3 flex items-baseline justify-between gap-2.5
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        ) {
            Text(
                heading,
                color = RenzoColors.Foreground,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 28.sp,               // text-lg line-height
                letterSpacing = (-0.45).sp,       // tracking-tight @18px
                modifier = Modifier.weight(1f),
            )
            var focused by remember { mutableStateOf(false) }
            Text(
                "See all ›",
                color = RenzoColors.Primary,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .focusRing(focused, 6.dp)
                    .tvClickable(onFocused = { focused = it }, onClick = onMore)
                    .padding(horizontal = 2.dp),
            )
        }
        when {
            loading -> GridSkeleton(count = 6)
            items.isEmpty() -> EmptyState("Nothing here yet.")
            else -> BoxWithConstraints {
                // Rows live in a scrolling column, so maxHeight is usually
                // unbounded — tileWidthFor() then falls back to the screen
                // fraction; on a bounded parent it uses the real space.
                val cardWidth = tileWidthFor(
                    if (maxHeight > 0.dp && maxHeight.value.isFinite()) maxHeight else Dp.Unspecified,
                )
                val rowMetrics = rememberCardMetrics()
                val firstCard = remember { FocusRequester() }
                val scroll = rememberScrollState()
                var focusedCard by remember { mutableStateOf(-1) }
                val density = LocalDensity.current
                // Centre the focused card in the row (the web's TV behaviour:
                // "D-pad centres the focused card"). Card pitch is fixed, so
                // the target offset is pure arithmetic.
                LaunchedEffect(focusedCard, scroll.viewportSize) {
                    if (focusedCard < 0 || scroll.viewportSize <= 0) return@LaunchedEffect
                    val pitchPx = with(density) { (cardWidth + gap).toPx() }
                    val cardPx = with(density) { cardWidth.toPx() }
                    val centre = focusedCard * pitchPx + cardPx / 2f
                    val target = (centre - scroll.viewportSize / 2f)
                        .coerceIn(0f, scroll.maxValue.toFloat())
                    scroll.animateScrollTo(target.toInt())
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Max)
                        .horizontalScroll(scroll)
                        .padding(bottom = 6.dp) // .browse-scroll padding-bottom: 6px
                        // Moving into this section (from the row above/below)
                        // always lands on its FIRST card, never on whatever
                        // happened to sit under the previous focus.
                        .focusGroup()
                        .focusProperties { enter = { firstCard } },
                    horizontalArrangement = Arrangement.spacedBy(gap),
                ) {
                    items.forEachIndexed { i, item ->
                        PosterCard(
                            item = item,
                            onClick = { onOpen(item) },
                            width = cardWidth,
                            metrics = rowMetrics,
                            // fillMaxHeight + the Row's IntrinsicSize.Max makes
                            // every card as tall as the tallest one.
                            modifier = (if (i == 0) Modifier.focusRequester(firstCard) else Modifier)
                                .fillMaxHeight()
                                .onFocusChanged { if (it.hasFocus) focusedCard = i },
                        )
                    }
                    MoreTile(onMore = onMore)
                }
            }
        }
    }
}
