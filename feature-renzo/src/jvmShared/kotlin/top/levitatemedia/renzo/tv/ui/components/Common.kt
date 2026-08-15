package top.levitatemedia.renzo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.CardItem
import top.levitatemedia.renzo.tv.api.ContentLevel
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/**
 * The web app's TV focus treatment: a primary-rose ring around the focused
 * element (globals.css body.tv-nav :focus — 3px #e11d48, radius 10px). Track
 * focus yourself and pass [focused] so callers can also scale/animate.
 */
fun Modifier.focusRing(focused: Boolean, radius: Dp = 10.dp): Modifier =
    if (focused) border(3.dp, RenzoColors.Primary, RoundedCornerShape(radius)) else this

/** Focusable + clickable with focus-state callback — the standard tile wrapper. */
@Composable
fun Modifier.tvClickable(
    onFocused: (Boolean) -> Unit,
    onClick: () -> Unit,
): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this
        .onFocusChanged { onFocused(it.isFocused) }
        .clickable(interactionSource = interaction, indication = null, onClick = onClick)
        .focusable(interactionSource = interaction)
}

/** Tailwind `border-dashed`: a 1dp dashed stroke (web dashed empty states / chips). */
fun Modifier.dashedBorder(color: Color, radius: Dp, width: Dp = 1.dp): Modifier =
    drawBehind {
        val strokePx = width.toPx()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(radius.toPx()),
            style = Stroke(
                width = strokePx,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()), 0f),
            ),
        )
    }

@Composable
fun LoadingBox(modifier: Modifier = Modifier, label: String = "Loading…") {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(label, color = RenzoColors.MutedForeground, fontSize = 14.sp)
    }
}

@Composable
fun ErrorBox(
    message: String,
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(message, color = RenzoColors.MutedForeground, fontSize = 14.sp)
            if (onRetry != null) {
                PillButton(label = "Try again", onClick = onRetry)
            }
        }
    }
}

/** Renzo's pill button — primary rose fill, full-round (web .tp-primary). */
@Composable
fun PillButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        filled -> RenzoColors.Primary
        focused -> RenzoColors.Secondary
        else -> Color.Transparent
    }
    val fg = if (filled) RenzoColors.PrimaryForeground else RenzoColors.Foreground
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .focusRing(focused, 999.dp)
            .background(bg, RoundedCornerShape(999.dp))
            .let { if (!filled) it.border(1.dp, RenzoColors.Border, RoundedCornerShape(999.dp)) else it }
            .tvClickable(onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

/**
 * shadcn `Button variant="outline" size="sm"` (web button.tsx: h-8 rounded-md
 * px-3 text-xs, border border-input bg-background). [height]/[horizontalPadding]
 * let the jobs-list rows apply their `h-7 px-2` override.
 */
@Composable
fun OutlineButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 32.dp,
    horizontalPadding: Dp = 12.dp,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .focusRing(focused, 6.dp)
            .background(
                if (focused) RenzoColors.Accent else RenzoColors.Background,
                RoundedCornerShape(6.dp),
            )
            .border(1.dp, RenzoColors.Input, RoundedCornerShape(6.dp))
            .tvClickable(onFocused = { focused = it }, onClick = { if (enabled) onClick() })
            .padding(horizontal = horizontalPadding),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = RenzoColors.Foreground.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** `search-head mb-1.5 flex items-center gap-3` (page.tsx BackHeading): the
 *  "‹ Back" pill (rounded-full border-border bg-card px-3 py-1.5 text-[13px]
 *  muted) and the 20sp/600 (text-xl) truncating heading. */
@Composable
fun BackHeading(heading: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth().padding(bottom = 6.dp),
    ) {
        var focused by remember { mutableStateOf(false) }
        Box(
            Modifier
                .clip(RoundedCornerShape(999.dp))
                .focusRing(focused, 999.dp)
                .background(RenzoColors.Card, RoundedCornerShape(999.dp))
                .border(
                    1.dp,
                    if (focused) RenzoColors.Primary else RenzoColors.Border,
                    RoundedCornerShape(999.dp),
                )
                .tvClickable(onFocused = { focused = it }, onClick = onBack)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                "‹ Back",
                color = if (focused) RenzoColors.Foreground else RenzoColors.MutedForeground,
                fontSize = 13.sp,
            )
        }
        Text(
            heading,
            color = RenzoColors.Foreground,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}

/** Section heading, 18sp semibold like the web's discover row headers. */
@Composable
fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        color = RenzoColors.Foreground,
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

/**
 * Web-mobile parity for poster grids (media-grid.tsx GRID_COLS): a HARD 2
 * columns under 420dp (`grid-cols-2`), auto-fill minmax(148px,1fr) from 420,
 * and minmax(158px,1fr) from the sm break (640). Adaptive alone collapsed to
 * ONE giant column on display-zoomed phones (reported).
 */
@Composable
fun posterGridCells(): androidx.compose.foundation.lazy.grid.GridCells {
    val w = top.levitatemedia.renzo.tv.ui.theme.logicalScreenSize().first
    return when {
        w < 420 -> androidx.compose.foundation.lazy.grid.GridCells.Fixed(2)
        // Above the phone tiers, size cells by the screen so a whole tile
        // (poster + caption) fits — a fixed 158dp cell is TALLER than a TV's
        // visible area and the tiles were being clipped.
        w < 640 -> androidx.compose.foundation.lazy.grid.GridCells.Adaptive(148.dp)
        else -> androidx.compose.foundation.lazy.grid.GridCells.Adaptive(responsiveCardWidth())
    }
}

/** Title + meta + up-next + the paddings under a poster. */
private const val CAPTION_ALLOWANCE_DP = 74f

/** Width tier — how big a tile may get on this screen. */
@Composable
private fun tileWidthTier(): Dp {
    val w = top.levitatemedia.renzo.tv.ui.theme.logicalScreenSize().first
    return when {
        w >= 1400 -> 210.dp
        w >= 1100 -> 190.dp
        w >= 768 -> 172.dp
        else -> 150.dp
    }
}

/**
 * Tile width for a surface with [available] vertical space. A tile is a 2:3
 * poster plus ~74dp of caption, so on a TV (only ~540dp tall in dp terms) a
 * fixed width makes tiles TALLER than what's left after the chrome and they
 * get cut off — and how much is left differs per page (Library carries three
 * chip rows, Updates only a subtitle). Measuring beats guessing: pass the real
 * remaining height and the tile shrinks to fit it, capped by the width tier.
 */
@Composable
fun tileWidthFor(available: Dp): Dp {
    val tier = tileWidthTier()
    val logicalHeight = top.levitatemedia.renzo.tv.ui.theme.logicalScreenSize().second
    // Unbounded (inside a scrolling column): fall back to a screen fraction.
    val h = if (available.isSpecified && available.value.isFinite() && available > 0.dp) {
        available
    } else {
        (logicalHeight * 0.58f).dp
    }
    val byHeight = ((h.value - CAPTION_ALLOWANCE_DP) * 2f / 3f).dp
    return minOf(tier, byHeight).coerceAtLeast(108.dp)
}

/** Screen-only variant for callers with no measured box. */
@Composable
fun responsiveCardWidth(): Dp = tileWidthFor(Dp.Unspecified)


/** Web grid gap (GRID_COLS): gap-3 (12dp) below the sm break, 18dp above. */
@Composable
fun posterGridGap(): Dp =
    if (top.levitatemedia.renzo.tv.ui.theme.logicalScreenSize().first < 640) 12.dp else 18.dp

// --- content filter (web content-filter.tsx) --------------------------------

/** A card's adult categories: prefer server-computed `content`, else derive
 *  from genres (covers MAL fallback cards — old contentCatsOf). */
fun contentCatsOf(item: CardItem): List<String> {
    if (item.content.isNotEmpty()) return item.content
    val g = item.genres.map { it.lowercase() }
    return buildList {
        if ("hentai" in g) add("hentai")
        if ("ecchi" in g) add("ecchi")
    }
}

/** True when the item sits ABOVE the chosen level (old isHidden). */
fun isHidden(item: CardItem, level: String): Boolean =
    !ContentLevel.allows(level, contentCatsOf(item))

private val CONTENT_LADDER = listOf(
    "none" to "Off",
    "ecchi" to "Ecchi",
    "erotica" to "Erotica",
    "hentai" to "Hentai",
)

/**
 * "🔞 Show up to" chip ladder — doubles as the current-mode indicator
 * (content-filter.tsx ContentChips: my-1.5 flex flex-wrap items-center gap-2;
 * label mr-0.5 text-xs font-semibold text-muted-foreground opacity-80; chips
 * rounded-full border px-3.5 py-1.5 text-[13px], active bg-primary).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContentChips(app: AppServices, modifier: Modifier = Modifier) {
    FlowRow(
        modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "🔞 Show up to",
            color = RenzoColors.MutedForeground.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp),
        )
        CONTENT_LADDER.forEach { (id, label) ->
            val active = app.contentLevel.value == id
            var focused by remember { mutableStateOf(false) }
            Box(
                Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .focusRing(focused, 999.dp)
                    .background(
                        if (active) RenzoColors.Primary else RenzoColors.Card,
                        RoundedCornerShape(999.dp),
                    )
                    .let {
                        if (!active) it.border(
                            1.dp,
                            if (focused) RenzoColors.Primary else RenzoColors.Border,
                            RoundedCornerShape(999.dp),
                        ) else it
                    }
                    .tvClickable(onFocused = { focused = it }, onClick = { app.setContentLevel(id) })
                    .padding(horizontal = 14.dp, vertical = 6.dp),
            ) {
                Text(
                    label,
                    color = when {
                        active -> RenzoColors.PrimaryForeground
                        focused -> RenzoColors.Foreground
                        else -> RenzoColors.MutedForeground
                    },
                    fontSize = 13.sp,
                )
            }
        }
    }
}
