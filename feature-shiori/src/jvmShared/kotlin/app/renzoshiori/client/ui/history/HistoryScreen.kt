package app.renzoshiori.client.ui.history

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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import app.renzoshiori.client.ShioriRuntime
import app.renzoshiori.client.data.model.HistoryChapterDto
import app.renzoshiori.client.data.model.HistoryFeedItemDto
import app.renzoshiori.client.data.network.HistoryApi
import app.renzoshiori.client.data.network.absoluteUrl
import app.renzoshiori.client.ui.home.DpadToggleChip
import app.renzoshiori.client.ui.home.dpadClickable
import app.renzoshiori.client.ui.library.LibraryViewModel
import app.renzoshiori.client.ui.library.formatChapter
import app.renzoshiori.client.ui.queue.DateBucket
import app.renzoshiori.client.ui.queue.formatRelativeTime
import app.renzoshiori.client.ui.queue.getDateBucket
import app.renzoshiori.client.ui.queue.parseUtcMillis
import app.renzoshiori.client.ui.theme.RenzoColors
import app.renzoshiori.client.ui.util.screenWidthDp
import coil3.compose.AsyncImage

// ---------------------------------------------------------------------------
// Constants (history/page.tsx)
// ---------------------------------------------------------------------------

/**
 * The server caps the feed at 500 ENTRIES and stacks before capping, so asking
 * for more gains nothing. Stacking and capping deliberately do NOT happen
 * here: a client-side pass would re-split runs the server already grouped, and
 * a client-side cap would count a binge as many entries again
 * (HANDOFF_renzohub_historytab.md §2 — the feed renders as given).
 */
private const val FETCH_LIMIT = 500

private val BUCKET_ORDER = listOf(
    DateBucket.TODAY, DateBucket.YESTERDAY, DateBucket.THIS_WEEK, DateBucket.EARLIER,
)

private data class FeedRow(
    val key: String,
    val item: HistoryFeedItemDto,
    val sortTime: Long,
    val displayTime: String,
)

/** What a chapter row says under the title, whether stacked or not. */
private fun chapterLabel(chapterName: String?, chapterNumber: Double?): String =
    chapterName?.takeIf { it.isNotBlank() }
        ?: chapterNumber?.let { "Chapter ${formatChapter(it)}" }
        ?: "Chapter"

/**
 * Part-read chapters are the ones worth returning to, so they are called out
 * rather than left to look identical to a finished one.
 */
private fun progressLabel(progress: Double, completed: Boolean): String? {
    if (completed) return null
    if (progress <= 0.0) return null
    return "${Math.round(progress * 100)}%"
}

/**
 * History — 1:1 port of RenzoFrontend src/app/history/page.tsx: the
 * "History <count>" header (count is CHAPTERS, a stack contributes its chapter
 * count) with the Owner-only My-library toggle, the four shared date buckets,
 * chapter rows, and the server-built collapsible stacks. Deliberately no
 * "Update now" button (that belongs to Updates) and no read-dimming — every
 * history row is by definition already read.
 */
@Composable
fun HistoryScreen(onOpenSeries: (String) -> Unit) {
    val renzoApp = ShioriRuntime.app

    // The command bar's search box is bound to LibraryViewModel — the web's
    // history page reads that very same shared search context.
    val libraryVm: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(),
    )
    val libraryState by libraryVm.state.collectAsState()
    val search = libraryState.searchTerm.trim()

    val api = remember { renzoApp.network.currentServiceOf<HistoryApi>() }

    var items by remember { mutableStateOf<List<HistoryFeedItemDto>?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var viewAllLibraries by remember { mutableStateOf(false) }

    LaunchedEffect(viewAllLibraries) {
        isLoading = true
        runCatching { api?.history(0, FETCH_LIMIT, viewAllLibraries) }
            .onSuccess { items = it ?: emptyList() }
            .onFailure { items = emptyList() }
        isLoading = false
    }

    // Search filters on series title only, case-insensitive (page.tsx:307).
    val rows = remember(items, search) {
        val source = items ?: emptyList()
        val out = ArrayList<FeedRow>()
        source.forEachIndexed { i, item ->
            if (search.isNotEmpty() && !item.seriesTitle.contains(search, ignoreCase = true)) return@forEachIndexed
            val sortTime = parseUtcMillis(item.readAt) ?: System.currentTimeMillis()
            out.add(
                FeedRow(
                    key = "${item.seriesId}-${item.kind}-${item.chapterNumber ?: ""}-$i",
                    item = item,
                    sortTime = sortTime,
                    displayTime = formatRelativeTime(sortTime),
                ),
            )
        }
        out
    }

    val buckets = remember(rows) { rows.groupBy { getDateBucket(it.sortTime) } }
    val baseUrl = renzoApp.tokenStore.serverUrl ?: ""

    // A stack is one entry but many chapters; the header counts what was
    // actually read, which is the number a reader cares about (page.tsx:339).
    val chapterCount = remember(rows) {
        rows.sumOf { if (it.item.kind == "stack") it.item.chapters?.size ?: 0 else 1 }
    }

    // Web page container: `mx-auto max-w-[1100px] py-6 sm:py-10`.
    val wide = screenWidthDp() >= 1024.dp
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .then(if (wide) Modifier.widthIn(max = 1100.dp).padding(top = 24.dp) else Modifier)
                .fillMaxSize(),
        ) {
            // ── Header ───────────────────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
            ) {
                Text(
                    "History",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = RenzoColors.Foreground,
                )
                Text(
                    chapterCount.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RenzoColors.MutedForeground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                if (libraryState.canOwner) {
                    DpadToggleChip(
                        label = if (viewAllLibraries) "All libraries" else "My library",
                        active = viewAllLibraries,
                        onClick = { viewAllLibraries = !viewAllLibraries },
                    )
                }
            }

            // ── Body ─────────────────────────────────────────────────────
            when {
                isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
                }
                rows.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (search.isNotEmpty()) {
                            "No history matching \"$search\"."
                        } else {
                            "Nothing read yet — chapters you open will show up here."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = RenzoColors.MutedForeground,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = 64.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    BUCKET_ORDER.forEach { bucket ->
                        val bucketRows = buckets[bucket].orEmpty()
                        if (bucketRows.isNotEmpty()) {
                            item(key = "hdr-${bucket.name}") {
                                Text(
                                    bucket.label.uppercase(),
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = RenzoColors.MutedForeground,
                                    letterSpacing = TextUnit(0.88f, TextUnitType.Sp),
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 8.dp),
                                )
                            }
                            // The feed arrives stacked — render each entry as
                            // its kind says, no client-side grouping pass.
                            bucketRows.forEach { row ->
                                item(key = row.key) {
                                    if (row.item.kind == "stack") {
                                        HistoryStack(row, baseUrl, onOpenSeries)
                                    } else {
                                        HistoryRow(row, baseUrl, onOpenSeries)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Rows
// ---------------------------------------------------------------------------

/** Shared row anatomy (§4.4): cover, title, BookOpen subtitle, check + time. */
@Composable
private fun HistoryRowContent(
    seriesTitle: String,
    thumbnailUrl: String?,
    subtitle: String,
    completed: Boolean,
    displayTime: String,
    baseUrl: String,
    onClick: () -> Unit,
    indentStart: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(radius = 8.dp, onClick = onClick)
            .padding(start = if (indentStart) 36.dp else 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        FeedCover(thumbnailUrl, baseUrl, seriesTitle)
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                seriesTitle,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = RenzoColors.Foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                    tint = RenzoColors.MutedForeground,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = RenzoColors.MutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        if (completed) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Finished",
                tint = RenzoColors.Primary.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 6.dp).size(14.dp),
            )
        }
        Text(
            displayTime,
            style = MaterialTheme.typography.bodySmall,
            color = RenzoColors.MutedForeground.copy(alpha = 0.7f),
            maxLines = 1,
        )
    }
}

@Composable
private fun HistoryRow(row: FeedRow, baseUrl: String, onOpen: (String) -> Unit) {
    val item = row.item
    val label = chapterLabel(item.chapterName, item.chapterNumber)
    val pct = progressLabel(item.progress, item.completed)
    HistoryRowContent(
        seriesTitle = item.seriesTitle,
        thumbnailUrl = item.thumbnailUrl,
        subtitle = label + if (pct != null) " · $pct" else "",
        completed = item.completed,
        displayTime = row.displayTime,
        baseUrl = baseUrl,
        onClick = { onOpen(item.seriesId) },
    )
}

/** One chapter inside an expanded stack — its own cover, time and check. */
@Composable
private fun StackChapterRow(
    item: HistoryFeedItemDto,
    chapter: HistoryChapterDto,
    baseUrl: String,
    onOpen: (String) -> Unit,
) {
    val label = chapterLabel(chapter.chapterName, chapter.chapterNumber)
    val pct = progressLabel(chapter.progress, chapter.completed)
    val millis = parseUtcMillis(chapter.readAt) ?: System.currentTimeMillis()
    HistoryRowContent(
        seriesTitle = item.seriesTitle,
        thumbnailUrl = item.thumbnailUrl,
        subtitle = label + if (pct != null) " · $pct" else "",
        completed = chapter.completed,
        displayTime = formatRelativeTime(millis),
        baseUrl = baseUrl,
        onClick = { onOpen(item.seriesId) },
        indentStart = true,
    )
}

/** A server-built run of 5+ chapters read back-to-back (§4.5), closed by default. */
@Composable
private fun HistoryStack(row: FeedRow, baseUrl: String, onOpen: (String) -> Unit) {
    var isOpen by remember(row.key) { mutableStateOf(false) }
    val item = row.item
    val chapters = item.chapters.orEmpty()
    val count = chapters.size

    val numbers = chapters.mapNotNull { it.chapterNumber }
    val minChapter = numbers.minOrNull()
    val maxChapter = numbers.maxOrNull()
    val rangeLabel = when {
        minChapter != null && maxChapter != null && minChapter == maxChapter ->
            "Chapter ${formatChapter(minChapter)}"
        minChapter != null && maxChapter != null ->
            "Chapters ${formatChapter(minChapter)}-${formatChapter(maxChapter)}"
        else -> "$count chapters"
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Trigger: the same row shape, no check icon, chevron after the time.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .dpadClickable(radius = 8.dp) { isOpen = !isOpen }
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            FeedCover(item.thumbnailUrl, baseUrl, item.seriesTitle)
            Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                Text(
                    item.seriesTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = RenzoColors.Foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = RenzoColors.MutedForeground,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "$rangeLabel · $count read",
                        style = MaterialTheme.typography.bodySmall,
                        color = RenzoColors.MutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Text(
                row.displayTime,
                style = MaterialTheme.typography.bodySmall,
                color = RenzoColors.MutedForeground.copy(alpha = 0.7f),
                maxLines = 1,
            )
            Icon(
                if (isOpen) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = RenzoColors.MutedForeground.copy(alpha = 0.7f),
                modifier = Modifier.padding(start = 8.dp).size(14.dp),
            )
        }
        if (isOpen) {
            HorizontalDivider(color = RenzoColors.Foreground.copy(alpha = 0.04f))
            // Chapters arrive highest-first from the server; render in order.
            chapters.forEach { chapter ->
                StackChapterRow(item, chapter, baseUrl, onOpen)
            }
        }
    }
}

/** The feed row's 40×56 rounded cover. */
@Composable
private fun FeedCover(thumbnailUrl: String?, baseUrl: String, alt: String) {
    Box(
        modifier = Modifier
            .width(40.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(RenzoColors.Foreground.copy(alpha = 0.04f)),
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = absoluteUrl(baseUrl, thumbnailUrl),
                contentDescription = alt,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
