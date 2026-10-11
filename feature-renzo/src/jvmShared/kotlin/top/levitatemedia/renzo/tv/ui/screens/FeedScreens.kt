package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.CardItem
import top.levitatemedia.renzo.tv.ui.components.ErrorBox
import top.levitatemedia.renzo.tv.ui.components.isHidden
import top.levitatemedia.renzo.tv.ui.kit.kitClickable
import top.levitatemedia.renzo.tv.ui.kit.screenWidthDp
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

// ---------------------------------------------------------------------------
// Updates and History, as Shiori's feeds (feature-shiori ui/updates
// UpdatesScreen.kt, ui/history/HistoryScreen.kt): a centred 1100dp column,
// the "Updates  N" header, date-bucket labels, and compact rows — 40x56 cover,
// title, an icon + what happened, the time on the right.
// ---------------------------------------------------------------------------

@Composable
fun UpdatesScreen(app: AppServices, onOpen: (CardItem) -> Unit) {
    var items by remember { mutableStateOf<List<CardItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        loading = true
        error = null
        try {
            // The feed sends `kind`; normalize onto updKind so the open-card
            // handler (episode/movie → playback) sees it — the web's
            // loadUpdates mapping (updKind: u.kind).
            items = app.repo.updates().map {
                if (it.updKind == null && it.kind != null) it.copy(updKind = it.kind) else it
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = feedErrorMessage(e)
        }
        loading = false
    }

    val level = app.contentLevel.value
    val shown = items.filter { !isHidden(it, level) }
    FeedPage(
        title = "Updates",
        count = shown.size,
        loading = loading,
        error = error,
        onRetry = { retryKey++ },
        isTv = app.isTv,
        empty = "You're all caught up — new episodes and seasons for your library show up here.",
        // The updates feed carries no time, so it is one list rather than
        // Shiori's Today / Yesterday buckets.
        groups = listOf(null to shown),
        row = { item -> UpdateRow(item, onOpen) },
    )
}

@Composable
fun HistoryScreen(app: AppServices, onOpen: (CardItem) -> Unit) {
    var items by remember { mutableStateOf<List<CardItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    LaunchedEffect(retryKey) {
        loading = true
        error = null
        try {
            items = app.repo.history()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = feedErrorMessage(e)
        }
        loading = false
    }

    val level = app.contentLevel.value
    val shown = items.filter { !isHidden(it, level) }
    val groups = remember(shown) {
        val byBucket = shown.groupBy { bucketOf(it.at) }
        Bucket.entries.mapNotNull { b -> byBucket[b]?.let { b.label to it } }
    }
    FeedPage(
        title = "History",
        count = shown.size,
        loading = loading,
        error = error,
        onRetry = { retryKey++ },
        isTv = app.isTv,
        empty = "Nothing watched yet — mark episodes watched or finish one in the player.",
        groups = groups,
        row = { item -> HistoryRow(item, onOpen) },
    )
}

/** Shiori's feed page: centred column, header with count, bucketed rows. */
@Composable
private fun FeedPage(
    title: String,
    count: Int,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    isTv: Boolean,
    empty: String,
    groups: List<Pair<String?, List<CardItem>>>,
    row: @Composable (CardItem) -> Unit,
) {
    val wide = screenWidthDp() >= 1024.dp
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.then(if (wide) Modifier.widthIn(max = 1100.dp) else Modifier).fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = if (wide) 12.dp else 4.dp, bottom = 8.dp),
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                    color = RenzoColors.Foreground,
                )
                if (!loading) {
                    Text(
                        count.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RenzoColors.MutedForeground.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }
            when {
                error != null -> ErrorBox(message = error, onRetry = onRetry, autoFocus = isTv)
                loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
                }
                count == 0 -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        empty,
                        style = MaterialTheme.typography.bodySmall,
                        color = RenzoColors.MutedForeground,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 64.dp), modifier = Modifier.fillMaxSize()) {
                    groups.forEach { (label, rows) ->
                        if (label != null) {
                            item(key = "hdr-$label") {
                                Text(
                                    label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = RenzoColors.MutedForeground,
                                    letterSpacing = 0.88.sp,
                                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
                                )
                            }
                        }
                        rows.forEachIndexed { i, item ->
                            item(key = "${label ?: ""}-${item.id}-${item.updKind ?: ""}-${item.ep ?: ""}-$i") { row(item) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UpdateRow(item: CardItem, onOpen: (CardItem) -> Unit) {
    val sTag = item.season?.let { "S$it " } ?: ""
    val (icon, label, accent) = when (item.updKind ?: item.kind) {
        "episode" -> Triple(Icons.Filled.PlayArrow, "New episode · ${sTag}E${item.ep ?: ""}", true)
        "season" -> if (item.upcoming == true) {
            Triple(Icons.Filled.Schedule, "Coming soon" + (item.season?.let { " · Season $it" } ?: ""), false)
        } else {
            Triple(Icons.Filled.LibraryAdd, "New season" + (item.season?.let { " · Season $it" } ?: ""), true)
        }
        "soon" -> Triple(Icons.Filled.Schedule, "Coming soon", false)
        "movie" -> Triple(Icons.Filled.Movie, "Movie available", true)
        else -> Triple(Icons.Filled.LibraryAdd, "Update", false)
    }
    FeedRow(item, icon, label, accent, trailing = item.year?.toString(), onOpen = onOpen)
}

@Composable
private fun HistoryRow(item: CardItem, onOpen: (CardItem) -> Unit) {
    val label = buildString {
        append("Watched")
        item.ep?.let { append(" E$it") }
        item.upNext?.let { append(" · up next E$it") }
    }
    FeedRow(item, Icons.Filled.History, label, accent = false, trailing = relativeTime(item.at), onOpen = onOpen)
}

/** Shiori's feed row (UpdateRow): cover, title, icon + label, trailing time. */
@Composable
private fun FeedRow(
    item: CardItem,
    icon: ImageVector,
    label: String,
    accent: Boolean,
    trailing: String?,
    onOpen: (CardItem) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .kitClickable { onOpen(item) }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .width(40.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(RenzoColors.Foreground.copy(alpha = 0.04f)),
        ) {
            AsyncImage(
                model = item.poster,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = RenzoColors.Foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = if (accent) RenzoColors.Primary.copy(alpha = 0.8f) else RenzoColors.MutedForeground,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodySmall,
                    color = RenzoColors.MutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.bodySmall,
                color = RenzoColors.MutedForeground.copy(alpha = 0.7f),
                maxLines = 1,
            )
        }
    }
}

// --- Shiori's date buckets ---------------------------------------------------

private enum class Bucket(val label: String) { TODAY("Today"), YESTERDAY("Yesterday"), THIS_WEEK("This week"), EARLIER("Earlier") }

private fun instantOf(iso: String?): Instant? = iso?.let { runCatching { Instant.parse(it) }.getOrNull() }

private fun bucketOf(iso: String?): Bucket {
    val at = instantOf(iso) ?: return Bucket.EARLIER
    val zone = ZoneId.systemDefault()
    val day = at.atZone(zone).toLocalDate()
    val today = LocalDate.now(zone)
    return when {
        day == today -> Bucket.TODAY
        day == today.minusDays(1) -> Bucket.YESTERDAY
        ChronoUnit.DAYS.between(day, today) < 7 -> Bucket.THIS_WEEK
        else -> Bucket.EARLIER
    }
}

/** "just now", "5m", "3h", "2d", then the date — Shiori's formatRelativeTime. */
private fun relativeTime(iso: String?): String? {
    val at = instantOf(iso) ?: return null
    val mins = ChronoUnit.MINUTES.between(at, Instant.now())
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        mins < 60 * 24 -> "${mins / 60}h ago"
        mins < 60 * 24 * 7 -> "${mins / (60 * 24)}d ago"
        else -> at.atZone(ZoneId.systemDefault()).toLocalDate().toString()
    }
}

private fun feedErrorMessage(e: Exception): String = when {
    e is ApiError && e.status == 0 -> "Can't reach your Renzo server — check the connection."
    e is ApiError && e.status == 401 -> "Session expired — sign in again from the account menu."
    else -> e.message ?: "Something went wrong."
}
