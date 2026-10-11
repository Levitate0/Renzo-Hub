package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import top.levitatemedia.renzo.tv.api.EpisodeInfo
import top.levitatemedia.renzo.tv.api.ResumePoint
import top.levitatemedia.renzo.tv.api.SeasonRef
import top.levitatemedia.renzo.tv.api.TitleDetail
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/*
 * Everything below the stage, laid out like Crunchyroll's watch page: series
 * link, episode title, badges and release date on the left with the bookmark
 * beside them; the next (or previous) episode card and "See more episodes" on
 * the right; the full episode list under both. The content is the web watch
 * page's (watch-view.tsx: series link, season, source badge, description,
 * download-to-library, the episode rows with the pinned "Up next" row).
 */

internal class InfoModel(
    val detail: TitleDetail,
    val ep: Int,
    val todayIso: String,
    val watchedThrough: Int,
    val resume: Map<String, ResumePoint>,
    val inWatchlist: Boolean,
    /** local | realdebrid | alldebrid | offline, or null while resolving. */
    val source: String?,
    val failed: Boolean,
    val hasSubtitles: Boolean,
    val downloadsDenied: Boolean,
    val downloadRequested: Boolean,
    val note: String?,
    val linkCopied: Boolean,
)

internal class InfoActions(
    val openSeries: () -> Unit,
    val toggleWatchlist: () -> Unit,
    val copyLink: () -> Unit,
    val download: () -> Unit,
    val playEpisode: (Int) -> Unit,
    val playTitle: (id: Int, name: String) -> Unit,
    val seeMoreEpisodes: () -> Unit,
)

internal fun episodeTitle(d: TitleDetail, ep: Int): String {
    if (d.type == "movie") return d.displayTitle
    val t = d.episodeList.firstOrNull { it.number == ep }?.epTitle?.takeIf { it.isNotBlank() }
    return "E$ep – " + (t ?: "Episode $ep")
}

/** Web seasonNumber: the backend's number, else the position in the chain. */
private fun seasonWord(d: TitleDetail): String = when {
    d.type == "movie" -> "Movie"
    d.seasonKind == "extra" -> when (d.format?.uppercase()) { "MOVIE" -> "Movie"; "OVA" -> "OVA"; else -> "Special" }
    else -> "Season ${d.seasonNum ?: 1}" + (d.seasonPart?.takeIf { it > 1 }?.let { " Part $it" } ?: "")
}

@Composable
internal fun PlayerInfo(m: InfoModel, a: InfoActions, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val wide = maxWidth >= 860.dp
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                InfoMain(m, a, Modifier.weight(1f))
                InfoSide(m, a, Modifier.width(340.dp))
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(28.dp)) {
                InfoMain(m, a, Modifier.fillMaxWidth())
                InfoSide(m, a, Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun InfoMain(m: InfoModel, a: InfoActions, modifier: Modifier) {
    val d = m.detail
    Column(modifier) {
        Row {
            Column(Modifier.weight(1f)) {
                // Web #watchSeriesLink: accent, semibold, underline on hover.
                var hover by remember { mutableStateOf(false) }
                Text(
                    d.displayTitle,
                    color = RenzoColors.Primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    textDecoration = if (hover) TextDecoration.Underline else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .onPointerEvent(PointerEventType.Enter) { hover = true }
                        .onPointerEvent(PointerEventType.Exit) { hover = false }
                        .clickable(onClick = a.openSeries),
                )
                Text(
                    episodeTitle(d, m.ep),
                    color = RenzoColors.Foreground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 30.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Row(
                    Modifier.padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (d.isAdult || d.content.isNotEmpty()) RatingBadge("18+")
                    val bits = listOfNotNull(seasonWord(d), if (m.hasSubtitles) "Subtitled" else null)
                    Text(bits.joinToString("  •  "), color = RenzoColors.MutedForeground, fontSize = 13.sp)
                    SourceBadge(m.source, m.failed)
                }
            }
            // Crunchyroll keeps the bookmark beside the title, the share link under it.
            Column(
                Modifier.padding(start = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                InfoIcon(
                    if (m.inWatchlist) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                    if (m.inWatchlist) "Remove from Watchlist" else "Add to Watchlist",
                    on = m.inWatchlist,
                    onClick = a.toggleWatchlist,
                )
                InfoIcon(
                    if (m.linkCopied) Icons.Rounded.Check else Icons.Rounded.Link,
                    if (m.linkCopied) "Link copied" else "Copy link to this episode",
                    on = m.linkCopied,
                    onClick = a.copyLink,
                )
            }
        }

        cleanDescription(d.description)?.takeIf { it.isNotBlank() }?.let { text ->
            var expanded by remember { mutableStateOf(false) }
            Text(
                text,
                color = RenzoColors.MutedForeground,
                fontSize = 14.sp,
                lineHeight = 22.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 18.dp).widthIn(max = 760.dp),
            )
            Text(
                if (expanded) "FEWER DETAILS" else "MORE DETAILS",
                color = RenzoColors.Primary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.6.sp,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { expanded = !expanded },
            )
        }

        // Web #watchDownload + #watchNote.
        if (!m.downloadsDenied && m.source != "offline") {
            Row(
                Modifier.padding(top = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val inLibrary = m.source == "local"
                SmallButton(
                    label = if (inLibrary) "✓ In library" else "⬇ Download to library",
                    filled = !inLibrary,
                    enabled = !inLibrary && !m.downloadRequested && m.source != null,
                    onClick = a.download,
                )
                m.note?.let { Text(it, color = RenzoColors.MutedForeground, fontSize = 13.sp) }
            }
        }
    }
}

/** Crunchyroll's maturity tag: a small outlined box, italic. */
@Composable
private fun RatingBadge(text: String) {
    Text(
        text,
        color = RenzoColors.Foreground,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        fontStyle = FontStyle.Italic,
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(RenzoColors.Muted)
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** Web #watchSource: "● Local file" (emerald) / "● Real-Debrid" (sky) pill. */
@Composable
private fun SourceBadge(source: String?, failed: Boolean) {
    val (text, color) = when {
        failed -> "failed" to RenzoColors.MutedForeground
        source == null -> "resolving…" to RenzoColors.MutedForeground
        source == "offline" -> "● Offline copy" to RenzoColors.Emerald400
        source == "local" -> "● Local file" to RenzoColors.Emerald400
        source == "alldebrid" -> "● AllDebrid" to RenzoColors.Sky400
        else -> "● Real-Debrid" to RenzoColors.Sky400
    }
    val accent = color != RenzoColors.MutedForeground
    Text(
        text,
        color = color,
        fontSize = 11.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (accent) color.copy(alpha = 0.1f) else Color.Transparent)
            .border(1.dp, if (accent) color else RenzoColors.Border, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
@Composable
private fun InfoIcon(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    var hover by remember { mutableStateOf(false) }
    TooltipArea(
        tooltip = {
            Text(
                label,
                color = RenzoColors.Foreground,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(RenzoColors.Popover)
                    .border(1.dp, RenzoColors.Border, RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
        delayMillis = 400,
        tooltipPlacement = TooltipPlacement.ComponentRect(Alignment.BottomCenter, Alignment.BottomCenter, DpOffset(0.dp, 4.dp)),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (hover) RenzoColors.Secondary else Color.Transparent)
                .onPointerEvent(PointerEventType.Enter) { hover = true }
                .onPointerEvent(PointerEventType.Exit) { hover = false }
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = if (on || hover) RenzoColors.Primary else RenzoColors.Foreground, modifier = Modifier.size(26.dp))
        }
    }
}

// --- right column: next / previous episode ------------------------------------

private sealed class SideCard {
    class Episode(val heading: String, val ep: EpisodeInfo) : SideCard()
    class Title(val ref: SeasonRef) : SideCard()
}

private fun sideCard(m: InfoModel): SideCard? {
    val d = m.detail
    if (d.type != "movie") {
        val next = d.episodeList.firstOrNull { it.number == m.ep + 1 }
        if (next != null && !isUnaired(next, d, m.todayIso)) return SideCard.Episode("NEXT EPISODE", next)
    }
    d.nextUp?.let { return SideCard.Title(it) }
    if (d.type != "movie") {
        d.episodeList.firstOrNull { it.number == m.ep - 1 }?.let { return SideCard.Episode("PREVIOUS EPISODE", it) }
    }
    return null
}

@Composable
private fun InfoSide(m: InfoModel, a: InfoActions, modifier: Modifier) {
    val d = m.detail
    val card = sideCard(m)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        when (card) {
            is SideCard.Episode -> {
                SideHeading(card.heading)
                val n = card.ep.number
                val point = m.resume[n.toString()]
                val watched = n <= m.watchedThrough
                val leftMin = point?.takeIf { !watched && it.durationMs > 0 }?.let { ((it.durationMs - it.positionMs) / 60_000).coerceAtLeast(1) }
                SideEpisode(
                    thumb = card.ep.thumbnail ?: d.banner ?: d.poster,
                    title = "E$n – " + (card.ep.epTitle?.takeIf { it.isNotBlank() } ?: "Episode $n"),
                    sub = if (m.hasSubtitles) "Subtitled" else seasonWord(d),
                    badge = when {
                        watched -> "Watched"
                        leftMin != null -> "${leftMin}m left"
                        d.duration != null && d.duration > 0 -> "${d.duration}m"
                        else -> null
                    },
                    progress = when {
                        watched -> 1f
                        point != null && point.durationMs > 0 -> (point.positionMs.toFloat() / point.durationMs).coerceIn(0f, 1f)
                        else -> null
                    },
                    watched = watched,
                    onClick = { a.playEpisode(n) },
                )
            }
            is SideCard.Title -> {
                SideHeading("UP NEXT")
                SideEpisode(
                    thumb = card.ref.poster ?: d.banner ?: d.poster,
                    title = card.ref.title?.takeIf { it.isNotBlank() } ?: seasonLabel(card.ref),
                    sub = seasonLabel(card.ref),
                    badge = null,
                    progress = null,
                    watched = false,
                    onClick = { a.playTitle(card.ref.id, card.ref.title ?: d.displayTitle) },
                )
            }
            null -> Unit
        }
        if (d.type != "movie" && d.episodeList.size > 1) SeeMoreButton(a.seeMoreEpisodes)
    }
}

@Composable
private fun SideHeading(text: String) {
    Text(text, color = RenzoColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
}

/** Crunchyroll's card: 16:9 still, play glyph on hover, "Nm left" + progress line. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SideEpisode(
    thumb: String?,
    title: String,
    sub: String,
    badge: String?,
    progress: Float?,
    watched: Boolean,
    onClick: () -> Unit,
) {
    var hover by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .onPointerEvent(PointerEventType.Enter) { hover = true }
            .onPointerEvent(PointerEventType.Exit) { hover = false }
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(152.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(4.dp))
                .background(RenzoColors.EpThumbWell),
        ) {
            AsyncImage(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (hover || watched) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(
                        if (watched) Icons.Rounded.Replay else Icons.Rounded.PlayArrow,
                        null,
                        tint = Color.White,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
            badge?.let {
                Text(
                    it,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            progress?.let {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f)))
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(it).height(3.dp).background(RenzoColors.Primary))
            }
        }
        Column(Modifier.weight(1f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                color = if (hover) RenzoColors.Primary else RenzoColors.Foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 19.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(sub, color = RenzoColors.MutedForeground, fontSize = 13.sp, maxLines = 1)
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeeMoreButton(onClick: () -> Unit) {
    var hover by remember { mutableStateOf(false) }
    Row(
        Modifier
            .border(2.dp, if (hover) RenzoColors.Primary else RenzoColors.MutedForeground.copy(alpha = 0.7f))
            .background(if (hover) RenzoColors.Primary.copy(alpha = 0.08f) else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hover = true }
            .onPointerEvent(PointerEventType.Exit) { hover = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.VideoLibrary, null, tint = if (hover) RenzoColors.Primary else RenzoColors.Foreground, modifier = Modifier.size(20.dp))
        Text(
            "SEE MORE EPISODES",
            color = if (hover) RenzoColors.Primary else RenzoColors.Foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
    }
}

// --- the episode list (web EpisodeList) ---------------------------------------

@Composable
internal fun PlayerEpisodes(m: InfoModel, a: InfoActions, modifier: Modifier = Modifier) {
    val d = m.detail
    if (d.type == "movie" && d.nextUp == null) return
    val seasons = remember(d) { orderedSeasons(d) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Episodes", color = RenzoColors.Foreground, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            // Web #watchSeason: switching season plays its first episode.
            if (seasons.size > 1) {
                SelectBox(
                    value = d.id.toString(),
                    options = seasons.mapIndexed { i, s ->
                        s.id.toString() to (seasonChipWord(s, i) + (s.year?.let { " · $it" } ?: ""))
                    },
                    onSelect = { id ->
                        val s = seasons.firstOrNull { it.id.toString() == id }
                        if (s != null && s.id != d.id) a.playTitle(s.id, s.title ?: d.displayTitle)
                    },
                    modifier = Modifier.widthIn(min = 200.dp, max = 280.dp),
                )
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val cols = if (maxWidth >= 860.dp) 2 else 1
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Web .wep.upnext: the series chain's next entry, pinned on top.
                d.nextUp?.let { nu ->
                    UpNextRow(nu, fallback = d.banner ?: d.poster) { a.playTitle(nu.id, nu.title ?: d.displayTitle) }
                }
                d.episodeList.chunked(cols).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { ep ->
                            EpisodeRow(ep, m, Modifier.weight(1f)) { a.playEpisode(ep.number) }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** Web seasonChip(s, i, word = true). */
private fun seasonChipWord(s: SeasonRef, i: Int): String {
    if (s.kind == "extra") return when (s.format?.uppercase()) { "MOVIE" -> "Movie"; "OVA" -> "OVA"; else -> "Special" }
    val n = s.num ?: (i + 1)
    return "Season $n" + (s.part?.let { " Part $it" } ?: "")
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EpisodeRow(ep: EpisodeInfo, m: InfoModel, modifier: Modifier, onClick: () -> Unit) {
    val d = m.detail
    val unaired = isUnaired(ep, d, m.todayIso)
    val playing = ep.number == m.ep
    val watched = ep.number <= m.watchedThrough
    val point = m.resume[ep.number.toString()]
    var hover by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier
            .alpha(if (unaired) 0.55f else 1f)
            .clip(shape)
            .background(
                when {
                    playing -> RenzoColors.Primary.copy(alpha = 0.1f)
                    hover && !unaired -> RenzoColors.Accent.copy(alpha = 0.5f)
                    else -> Color.Transparent
                },
            )
            .border(1.dp, if (playing) RenzoColors.Primary else Color.Transparent, shape)
            .onPointerEvent(PointerEventType.Enter) { hover = true }
            .onPointerEvent(PointerEventType.Exit) { hover = false }
            .clickable(enabled = !unaired && !playing, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(RenzoColors.Muted),
        ) {
            AsyncImage(ep.thumbnail ?: d.banner ?: d.poster, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            val frac = when {
                watched -> 1f
                point != null && point.durationMs > 0 -> (point.positionMs.toFloat() / point.durationMs).coerceIn(0f, 1f)
                else -> null
            }
            frac?.let {
                Box(Modifier.align(Alignment.BottomStart).fillMaxWidth(it).height(3.dp).background(RenzoColors.Primary))
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "E${ep.number} – " + (ep.epTitle?.takeIf { it.isNotBlank() } ?: "Episode ${ep.number}"),
                color = RenzoColors.Foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                when {
                    playing -> "Now playing"
                    unaired -> "Soon"
                    watched -> "Watched"
                    else -> d.duration?.takeIf { it > 0 }?.let { "${it}m" } ?: "Subtitled"
                },
                color = if (playing) RenzoColors.Primary else RenzoColors.MutedForeground,
                fontSize = 12.sp,
                fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        if (ep.hasFile) Text("✓", color = RenzoColors.Emerald400, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun UpNextRow(nu: SeasonRef, fallback: String?, onClick: () -> Unit) {
    var hover by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(RenzoColors.Primary.copy(alpha = if (hover) 0.1f else 0.05f))
            .border(1.dp, RenzoColors.Primary.copy(alpha = 0.7f), shape)
            .onPointerEvent(PointerEventType.Enter) { hover = true }
            .onPointerEvent(PointerEventType.Exit) { hover = false }
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            nu.poster ?: fallback,
            null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(128.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(RenzoColors.Muted),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "Up next · " + seasonLabel(nu),
                color = RenzoColors.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            nu.title?.let { Text(it, color = RenzoColors.MutedForeground, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
        Text("▶", color = RenzoColors.MutedForeground, fontSize = 14.sp)
    }
}
