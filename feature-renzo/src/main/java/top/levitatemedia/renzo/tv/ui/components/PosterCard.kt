package top.levitatemedia.renzo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import top.levitatemedia.renzo.tv.api.CardItem
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/**
 * Per-row / per-grid caption metrics. Cards report what they need (wrapped
 * title, meta line, up-next line) and every card then reserves the MAXIMUM —
 * so one wrapping title makes the whole row match that taller card instead of
 * leaving ragged edges.
 */
class CardMetrics {
    var titleLines by mutableStateOf(1)
        private set
    var anyMeta by mutableStateOf(false)
        private set
    var anyUpNext by mutableStateOf(false)
        private set

    fun reportTitleLines(n: Int) { if (n > titleLines) titleLines = n }
    fun reportMeta() { if (!anyMeta) anyMeta = true }
    fun reportUpNext() { if (!anyUpNext) anyUpNext = true }
}

/** One metrics holder per row/grid; cards in it size together. */
@Composable
fun rememberCardMetrics(): CardMetrics = remember { CardMetrics() }

/**
 * PosterCard — literal port of poster-card.tsx. Root: `rounded-xl border
 * border-border bg-card` (16dp — globals.css sets --radius-xl: 16px — / 1dp
 * Border / Card; rendered card computes to 16px). Ribbon pill top-left,
 * downloaded dot (emerald + 3dp halo) top-right, 2/3 poster on bg black/40,
 * caption `px-3 pb-3 pt-2`: 13sp/600 two-line title, 11sp muted meta
 * ("year · genre · N seasons"), optional 11sp primary "▶ Up next · E7".
 * Web hover lift/ring maps to the TV focus ring + scale.
 */
@Composable
fun PosterCard(
    item: CardItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixed width for rows; null = fill the parent (grid cells size the card). */
    width: Dp? = 172.dp,
    /** Shared with the other cards in this row/grid so all heights match. */
    metrics: CardMetrics? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .let { if (width != null) it.width(width) else it.fillMaxWidth() }
            .scale(if (focused) 1.04f else 1f)
            .clip(RoundedCornerShape(16.dp))
            .background(RenzoColors.Card, RoundedCornerShape(16.dp))
            .border(1.dp, if (focused) RenzoColors.Primary else RenzoColors.Border, RoundedCornerShape(16.dp))
            .focusRing(focused, 16.dp)
            .tvClickable(onFocused = { focused = it }, onClick = onClick),
    ) {
        // LazyImage: `poster block aspect-[2/3] w-full bg-black/40 object-cover`.
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).background(Color(0x66000000))) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f),
            )
            Ribbon(item, Modifier.align(Alignment.TopStart).padding(8.dp))
            if ((item.downloaded ?: 0) > 0) {
                // `dot`: h/w 9px emerald-400, shadow-[0_0_0_3px_rgba(61,220,132,0.2)]
                // — the 3dp halo ring sits OUTSIDE the 9dp dot (box-shadow spread),
                // so the dot itself still lands 8dp (right-2 top-2) from the edge.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(5.dp)
                        .size(15.dp)
                        .background(RenzoColors.DownloadedGreenHalo, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(9.dp).background(RenzoColors.DownloadedGreen, CircleShape))
                }
            }
        }
        // `cap px-3 pb-3 pt-2`
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 12.dp)) {
            // `t line-clamp-2 text-[13px] font-semibold leading-[1.3]`
            Text(
                item.title,
                color = RenzoColors.Foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.9.sp,
                minLines = metrics?.titleLines ?: 1,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { metrics?.reportTitleLines(it.lineCount) },
            )
            // meta = [year, genres[0], seasonCount>1 && "N seasons"].join(" · ")
            val meta = listOfNotNull(
                item.year?.toString(),
                item.genres.firstOrNull(),
                item.seasonCount?.takeIf { it > 1 }?.let { "$it seasons" },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                metrics?.reportMeta()
                // `m mt-[3px] text-[11px] text-muted-foreground`
                Text(
                    meta,
                    color = RenzoColors.MutedForeground,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            } else if (metrics?.anyMeta == true) {
                Spacer(Modifier.padding(top = 3.dp).height(14.dp))
            }
            val up = item.upNext
            if (up != null) {
                metrics?.reportUpNext()
                // `upnext mt-1 text-[11px] font-semibold text-primary`
                Text(
                    "▶ Up next · E$up",
                    color = RenzoColors.Primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 4.dp),
                )
            } else if (metrics?.anyUpNext == true) {
                Spacer(Modifier.padding(top = 4.dp).height(14.dp))
            }
        }
    }
}

/**
 * Ribbon — literal port of poster-card.tsx Ribbon. Updates ribbons ("New ·
 * S2 E5" / "New season · S2" / "Soon" / "Available") are the dark pill with
 * the text in the THEME ACCENT (text-primary); everything else gets the type
 * pill ("Movie"/"Series", font-semibold, text-foreground). Both: rounded-full
 * border-white/10 bg-black/70 px-2 py-[3px] 10sp uppercase tracking 0.4.
 */
@Composable
private fun Ribbon(item: CardItem, modifier: Modifier) {
    // The updates feed sends `kind`; the client-side name is `updKind`.
    val kind = item.updKind ?: item.kind
    val (text, weight, color) = when (kind) {
        "episode" -> {
            val sTag = item.season?.let { "S$it " } ?: ""
            Triple("New · ${sTag}E${item.ep ?: ""}", FontWeight.Bold, RenzoColors.Primary)
        }
        "season" -> {
            val head = if (item.upcoming == true) "Soon" else "New season"
            val sTag = item.season?.let { " · S$it" } ?: ""
            Triple(head + sTag, FontWeight.Bold, RenzoColors.Primary)
        }
        // Old client sent updKind "soon" for upcoming seasons — same ribbon.
        "soon" -> Triple("Soon", FontWeight.Bold, RenzoColors.Primary)
        "movie" -> Triple("Available", FontWeight.Bold, RenzoColors.Primary)
        else -> Triple(
            if (item.type == "movie") "Movie" else "Series",
            FontWeight.SemiBold,
            RenzoColors.Foreground,
        )
    }
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(RenzoColors.OverlayBlack, RoundedCornerShape(999.dp))
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text.uppercase(),
            color = color,
            fontSize = 10.sp,
            fontWeight = weight,
            letterSpacing = 0.4.sp,
        )
    }
}
