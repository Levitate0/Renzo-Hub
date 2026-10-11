package top.levitatemedia.renzo.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextAlign
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
import androidx.compose.material3.Text
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
 * PosterCard — Shiori's library SeriesCard (feature-shiori ui/library
 * LibraryScreen.kt), the owner's direction being that Renzo look exactly like
 * Shiori: a 2/3 cover with 6dp corners, a 2px status strip across the top
 * (green = episodes on your server), the type / "New" badge top-left on
 * black/70, the up-next episode top-right filled with the accent, and the
 * centred semibold title on a black/60 bar along the bottom. Sized by the
 * shared card-size choice ([currentCardSize]) unless a row passes a width.
 *
 * On TV the focus ring sits on a wrapper with a 3dp gutter (Shiori's
 * TvFocusTile): drawn on the card itself, the full-bleed cover would paint
 * over it.
 */
@Composable
fun PosterCard(
    item: CardItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** Fixed width for rows; null = fill the parent (grid cells size the card). */
    width: Dp? = null,
    /** Kept for callers; cards are uniform now that the title sits on the cover. */
    @Suppress("UNUSED_PARAMETER") metrics: CardMetrics? = null,
) {
    val size = top.levitatemedia.renzo.tv.ui.kit.currentCardSize()
    val isTv = top.levitatemedia.renzo.tv.ui.kit.LocalIsTv.current
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .let { if (width != null) it.width(width) else it.fillMaxWidth() }
            .then(
                if (isTv) {
                    Modifier
                        .focusRing(focused, 9.dp)
                        .tvClickable(onFocused = { focused = it }, onClick = onClick)
                        .padding(3.dp)
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(RenzoColors.Muted)
                .then(if (isTv) Modifier else Modifier.clickable(onClick = onClick)),
        ) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // 2px status strip: episodes held on your server.
            if ((item.downloaded ?: 0) > 0) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .height(2.dp)
                        .background(top.levitatemedia.renzo.tv.ui.kit.Green500),
                )
            }

            // Top-left badge — Shiori's provider badge slot.
            ribbonText(item)?.let { (text, accent) ->
                Text(
                    text,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = size.badge, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (accent) RenzoColors.Primary else Color.Black.copy(alpha = 0.7f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            // Top-right — Shiori's last-chapter badge slot: the next episode.
            item.upNext?.let { up ->
                Text(
                    "E$up",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = size.badge, fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(RenzoColors.Primary)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            // Title bar.
            Text(
                item.title,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = size.title, fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * The top-left badge: the updates feed's "New · S2 E5" / "New season" /
 * "Soon" / "Available" on the accent, otherwise "Movie" for films. Series
 * carry no badge — it was on every card and said nothing.
 */
private fun ribbonText(item: CardItem): Pair<String, Boolean>? {
    // The updates feed sends `kind`; the client-side name is `updKind`.
    return when (item.updKind ?: item.kind) {
        "episode" -> {
            val sTag = item.season?.let { "S$it " } ?: ""
            "New · ${sTag}E${item.ep ?: ""}" to true
        }
        "season" -> {
            val head = if (item.upcoming == true) "Soon" else "New season"
            (head + (item.season?.let { " · S$it" } ?: "")) to true
        }
        // Old client sent updKind "soon" for upcoming seasons — same badge.
        "soon" -> "Soon" to true
        "movie" -> "Available" to true
        else -> if (item.type == "movie") "Movie" to false else null
    }
}
