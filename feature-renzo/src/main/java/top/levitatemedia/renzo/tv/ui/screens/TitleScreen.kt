@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import androidx.compose.runtime.collectAsState
import coil3.compose.AsyncImage
import top.levitatemedia.renzo.hub.core.offline.DownloadBus
import top.levitatemedia.renzo.tv.offline.enqueueEpisodes
import top.levitatemedia.renzo.tv.offline.episodeKey
import kotlinx.coroutines.launch
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import top.levitatemedia.renzo.tv.api.Tracking
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.EpisodeInfo
import top.levitatemedia.renzo.tv.api.JobItem
import top.levitatemedia.renzo.tv.api.SeasonRef
import top.levitatemedia.renzo.tv.api.TitleDetail
import top.levitatemedia.renzo.tv.ui.components.ErrorBox
import top.levitatemedia.renzo.tv.ui.components.LoadingBox
import top.levitatemedia.renzo.tv.ui.components.PillButton
import top.levitatemedia.renzo.tv.ui.components.SectionHeading
import top.levitatemedia.renzo.tv.ui.components.focusRing
import top.levitatemedia.renzo.tv.ui.components.tvClickable
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Title detail page — mirrors the web title page: hero banner + poster +
 * actions, season chain row, next-up banner, and the episode grid (rows of 3
 * inside one LazyColumn so the D-pad scrolls the whole page naturally).
 */
@Composable
fun TitleScreen(
    app: AppServices,
    titleId: Int,
    onPlay: (TitleDetail, Int) -> Unit,
    onOpenTitle: (Int) -> Unit,
    onSessionLost: () -> Unit,
    /** Web title page's "‹ Back" pill — leaves the series for wherever you came from. */
    onBack: () -> Unit = {},
) {
    var detail by remember { mutableStateOf<TitleDetail?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableStateOf(0) }
    // Local echoes of server state so toggles update instantly.
    var inLibrary by remember { mutableStateOf(false) }
    var lists by remember { mutableStateOf(listOf<String>()) }
    var watchedThrough by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Device-download state. Bumped locally on save/remove for an instant
    // response, and by the shared downloader when a transfer actually lands.
    var offlineRevision by remember { mutableStateOf(0) }
    val downloadRevision by DownloadBus.revision.collectAsState()

    LaunchedEffect(titleId, reload) {
        detail = null
        error = null
        try {
            val d = app.repo.title(titleId)
            detail = d
            inLibrary = d.inLibrary
            lists = d.lists
            watchedThrough = d.watchedThrough
        } catch (e: ApiError) {
            when (e.status) {
                401 -> onSessionLost()
                0 -> error = "Can't reach your Renzo server"
                else -> error = e.message ?: "Failed to load title"
            }
        } catch (e: Exception) {
            error = e.message ?: "Failed to load title"
        }
    }

    val d = detail
    when {
        error != null -> ErrorBox(error ?: "Failed to load title", onRetry = { reload++ })
        d == null -> LoadingBox(label = "Loading title…")
        else -> {
            // Web grid: minmax(240px,1fr) — at phone width that's ONE tile
            // per row; ~3 across on wide screens (ground truth).
            val epCols = if (LocalConfiguration.current.screenWidthDp < 560) 1 else 3
            val chunks = remember(d, epCols) { d.episodeList.chunked(epCols) }
            val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
            var libBusy by remember { mutableStateOf(false) }
            var progressBusy by remember { mutableStateOf(false) }
            // Live per-episode download state (web overlays the ["jobs"] poll).
            var jobs by remember { mutableStateOf<List<JobItem>>(emptyList()) }
            LaunchedEffect(d.id) {
                while (true) {
                    jobs = try { app.repo.jobs().filter { it.titleId == d.id } } catch (_: Exception) { jobs }
                    kotlinx.coroutines.delay(4000)
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 28.dp, end = 28.dp, top = 20.dp, bottom = 44.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                // 0. BACK ---------------------------------------------------
                item(key = "back") {
                    var backFocused by remember { mutableStateOf(false) }
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .focusRing(backFocused, 999.dp)
                            .background(RenzoColors.Secondary, RoundedCornerShape(999.dp))
                            .border(1.dp, RenzoColors.Border, RoundedCornerShape(999.dp))
                            .tvClickable(onFocused = { backFocused = it }, onClick = onBack)
                            .padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("‹", color = RenzoColors.Foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(8.dp))
                        Text("Back", color = RenzoColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // 1. HERO ---------------------------------------------------
                item(key = "hero") {
                    TitleHero(
                        app = app,
                        d = d,
                        inLibrary = inLibrary,
                        lists = lists,
                        downloadsDenied = app.user.value?.downloadsDenied == true,
                        watchedThrough = watchedThrough,
                        onPlay = { ep -> onPlay(d, ep) },
                        onToggleList = { listName ->
                            scope.launch {
                                try {
                                    val r = app.repo.toggleList(d.id, listName, !lists.contains(listName))
                                    lists = r.lists
                                    inLibrary = r.inLibrary
                                } catch (e: ApiError) {
                                    if (e.status == 401) onSessionLost()
                                } catch (_: Exception) {}
                            }
                        },
                        onDownloadSeason = {
                            scope.launch {
                                try { app.repo.downloadSeason(d.id) } catch (_: Exception) {}
                            }
                        },
                        onToggleLibrary = {
                            if (!libBusy) {
                                libBusy = true
                                scope.launch {
                                    try {
                                        if (inLibrary) {
                                            app.repo.removeFromLibrary(d.id)
                                            inLibrary = false
                                        } else {
                                            app.repo.addToLibrary(d.id)
                                            inLibrary = true
                                        }
                                    } catch (e: ApiError) {
                                        if (e.status == 401) onSessionLost()
                                    } catch (_: Exception) {
                                        // keep prior state — the UI stays truthful
                                    } finally {
                                        libBusy = false
                                    }
                                }
                            }
                        },
                    )
                }

                // 2. SEASON CHAIN ------------------------------------------
                if (d.seasons.size > 1) {
                    item(key = "seasons") {
                        Column(Modifier.fillMaxWidth()) {
                            SectionHeading("Seasons")
                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                contentPadding = PaddingValues(end = 24.dp),
                            ) {
                                items(d.seasons, key = { it.id }) { ref ->
                                    SeasonCard(
                                        ref = ref,
                                        current = ref.id == d.id,
                                        onOpen = { if (ref.id != d.id) onOpenTitle(ref.id) },
                                    )
                                }
                            }
                        }
                    }
                }

                // 3. NEXT-UP banner ----------------------------------------
                d.nextUp?.let { nu ->
                    item(key = "nextup") {
                        NextUpBanner(nu, onClick = { onOpenTitle(nu.id) })
                    }
                }

                // 4. EPISODES ----------------------------------------------
                if (d.episodeList.isNotEmpty()) {
                    item(key = "eps-heading") {
                        SectionHeading(
                            "Episodes · ${watchedThrough.coerceIn(0, d.episodeList.size)}/${d.episodeList.size} watched",
                        )
                    }
                    items(chunks.size, key = { "eps-row-$it" }) { i ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            chunks[i].forEach { ep ->
                                EpisodeTile(
                                    ep = ep,
                                    fallbackImage = ep.thumbnail ?: d.banner ?: d.poster,
                                    watched = ep.number <= watchedThrough,
                                    unaired = isUnaired(ep, d, todayIso),
                                    durationMin = d.duration,
                                    job = jobs.firstOrNull { it.titleId == d.id && it.episode == ep.number },
                                    downloadsDenied = app.user.value?.downloadsDenied == true,
                                    savedOffline = remember(offlineRevision, downloadRevision, d.id, ep.number) {
                                        app.offline.isOffline(episodeKey(d.id, ep.number))
                                    },
                                    onClick = { onPlay(d, ep.number) },
                                    onSetProgress = { n ->
                                        if (!progressBusy) {
                                            progressBusy = true
                                            scope.launch {
                                                try {
                                                    watchedThrough = app.repo.setProgress(d.id, n).watchedThrough
                                                } catch (_: Exception) {
                                                } finally { progressBusy = false }
                                            }
                                        }
                                    },
                                    onDownload = { priority ->
                                        scope.launch {
                                            try { app.repo.downloadEpisode(d.id, ep.number, priority) } catch (_: Exception) {}
                                        }
                                    },
                                    onSaveOffline = {
                                        app.prefs.serverUrl?.let { base ->
                                            enqueueEpisodes(context, d, listOf(ep.number), base)
                                            offlineRevision++
                                        }
                                    },
                                    onRemoveOffline = {
                                        app.offline.deleteItem(episodeKey(d.id, ep.number))
                                        offlineRevision++
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(epCols - chunks[i].size) { Spacer(Modifier.weight(1f)) }
                        }
                    }

                    // 5. Mark/unmark season --------------------------------
                    item(key = "progress-action") {
                        val fullyWatched = watchedThrough >= d.episodeList.size
                        Row(Modifier.padding(top = 4.dp)) {
                            PillButton(
                                label = if (fullyWatched) "Unwatch season" else "Mark season watched",
                                filled = false,
                                onClick = {
                                    if (!progressBusy) {
                                        progressBusy = true
                                        val target = if (fullyWatched) 0 else d.episodeList.size
                                        scope.launch {
                                            try {
                                                app.repo.setProgress(titleId, target)
                                                watchedThrough = target
                                            } catch (e: ApiError) {
                                                if (e.status == 401) onSessionLost()
                                            } catch (_: Exception) {
                                            } finally {
                                                progressBusy = false
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

// --- hero -------------------------------------------------------------------

@Composable
private fun TitleHero(
    app: AppServices,
    d: TitleDetail,
    inLibrary: Boolean,
    lists: List<String>,
    downloadsDenied: Boolean,
    watchedThrough: Int,
    onPlay: (Int) -> Unit,
    onToggleLibrary: () -> Unit,
    onToggleList: (String) -> Unit,
    onDownloadSeason: () -> Unit,
) {
    val maxAvailable = (d.availableEpisodes ?: d.episodeList.size).coerceAtLeast(1)
    val playEp = (watchedThrough + 1).coerceIn(1, maxAvailable)
    val desc = remember(d.id) { cleanDescription(d.description) }

    val compact = LocalConfiguration.current.screenWidthDp < 768
    var descExpanded by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 540.dp else 340.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(RenzoColors.Card),
    ) {
        AsyncImage(
            model = d.banner ?: d.poster,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // Vertical scrim: fade into the page background at the bottom.
        Box(
            Modifier
                .matchParentSize()
                .background(Brush.verticalGradient(0f to Color.Transparent, 1f to RenzoColors.Background)),
        )
        // Left scrim for text legibility (web hero parity).
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.horizontalGradient(0f to Color.Black.copy(alpha = 0.78f), 1f to Color.Transparent),
                ),
        )
        if (compact) {
            // Web phone hero (ground truth title.png): everything CENTER-STACKED —
            // poster, title, metaline, description + "More details", actions.
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = d.poster,
                    contentDescription = d.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(128.dp)
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(RenzoColors.EpThumbWell),
                )
                Text(
                    d.displayTitle,
                    color = RenzoColors.Foreground,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 28.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text(
                        (d.format ?: d.type).uppercase(),
                        color = RenzoColors.Primary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    val metaRest = (
                        listOfNotNull(
                            d.year?.toString(),
                            (d.episodeCount ?: d.episodeList.size.takeIf { it > 0 })?.let { "$it ep" },
                        ) + d.genres.take(3)
                        ).joinToString("  ·  ")
                    if (metaRest.isNotEmpty()) {
                        Text(
                            "·  $metaRest",
                            color = RenzoColors.MutedForeground,
                            fontSize = 13.sp,
                            maxLines = 2,
                            textAlign = TextAlign.Center,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!desc.isNullOrBlank()) {
                    Text(
                        desc,
                        color = RenzoColors.Foreground.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        maxLines = if (descExpanded) Int.MAX_VALUE else 3,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    var moreFocused by remember { mutableStateOf(false) }
                    Text(
                        if (descExpanded) "Less" else "More details",
                        color = RenzoColors.Primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .focusRing(moreFocused, 6.dp)
                            .tvClickable(onFocused = { moreFocused = it }, onClick = { descExpanded = !descExpanded })
                            .padding(horizontal = 4.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    PillButton(label = "▶  Play E$playEp", onClick = { onPlay(playEp) })
                    // Web detail-actions icon pills: ★ watchlist · ♥ favorites ·
                    // ⬇ season download (hidden for download-denied users).
                    val inW = lists.contains("watchlist")
                    val inF = lists.contains("favorites")
                    IconPill(glyph = if (inW) "★" else "☆", on = inW) { onToggleList("watchlist") }
                    IconPill(glyph = if (inF) "♥" else "♡", on = inF) { onToggleList("favorites") }
                    if (!downloadsDenied) {
                        IconPill(glyph = "⬇", on = false, onClick = onDownloadSeason)
                    }
                }
                // Web `.detail-controls`: Auto pill + folder + provider selects.
                DetailControls(app, d, downloadsDenied, Modifier.padding(top = 12.dp))
                // Web `<TrackingRow>`: AniList/MAL status + score for this title.
                TrackingRow(app, d.id, Modifier.padding(top = 10.dp))
            }
            return@Box
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            AsyncImage(
                model = d.poster,
                contentDescription = d.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RenzoColors.EpThumbWell),
            )
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.displayTitle,
                    color = RenzoColors.Foreground,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 38.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                // Metaline: TYPE · year · N ep · first 3 genres
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Text(
                        (d.format ?: d.type).uppercase(),
                        color = RenzoColors.Primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                    )
                    val metaRest = (
                        listOfNotNull(
                            d.year?.toString(),
                            (d.episodeCount ?: d.episodeList.size.takeIf { it > 0 })?.let { "$it ep" },
                        ) + d.genres.take(3)
                        ).joinToString(" · ")
                    if (metaRest.isNotEmpty()) {
                        Text(
                            "· $metaRest",
                            color = RenzoColors.MutedForeground,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (!desc.isNullOrBlank()) {
                    Text(
                        desc,
                        color = RenzoColors.Foreground.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        lineHeight = 19.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 14.dp),
                ) {
                    PillButton(
                        label = "▶  Play E$playEp",
                        onClick = { onPlay(playEp) },
                    )
                    val inW = lists.contains("watchlist")
                    val inF = lists.contains("favorites")
                    IconPill(glyph = if (inW) "★" else "☆", on = inW) { onToggleList("watchlist") }
                    IconPill(glyph = if (inF) "♥" else "♡", on = inF) { onToggleList("favorites") }
                    if (!downloadsDenied) {
                        IconPill(glyph = "⬇", on = false, onClick = onDownloadSeason)
                    }
                    PillButton(
                        label = if (inLibrary) "In library ✓" else "Add to library",
                        filled = false,
                        onClick = onToggleLibrary,
                    )
                }
                DetailControls(app, d, downloadsDenied, Modifier.padding(top = 12.dp))
                TrackingRow(app, d.id, Modifier.padding(top = 10.dp))
            }
        }
    }
}

// --- season chain -----------------------------------------------------------

@Composable
private fun SeasonCard(ref: SeasonRef, current: Boolean, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(100.dp)
            .scale(if (focused) 1.05f else 1f)
            .tvClickable(onFocused = { focused = it }, onClick = onOpen),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(10.dp))
                .background(RenzoColors.Card)
                .border(
                    if (current) 2.dp else 1.dp,
                    if (current) RenzoColors.Primary else RenzoColors.Border,
                    RoundedCornerShape(10.dp),
                )
                .focusRing(focused, 10.dp),
        ) {
            AsyncImage(
                model = ref.poster,
                contentDescription = ref.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        Text(
            seasonLabel(ref),
            color = if (current) RenzoColors.Foreground else RenzoColors.MutedForeground,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun seasonLabel(ref: SeasonRef): String =
    if (ref.kind == "movie") "Movie"
    else "Season ${ref.num ?: 1}" + (ref.part?.takeIf { it > 1 }?.let { " Pt $it" } ?: "")

// --- next-up banner ---------------------------------------------------------

@Composable
private fun NextUpBanner(nextUp: SeasonRef, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RenzoColors.Card, RoundedCornerShape(12.dp))
            .border(1.dp, if (focused) RenzoColors.Primary else RenzoColors.Border, RoundedCornerShape(12.dp))
            .focusRing(focused, 12.dp)
            .tvClickable(onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "NEXT",
            color = RenzoColors.Primary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp,
        )
        Text(
            nextUp.title?.takeIf { it.isNotBlank() } ?: seasonLabel(nextUp),
            color = RenzoColors.Foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 10.dp).weight(1f),
        )
        Text("›", color = RenzoColors.MutedForeground, fontSize = 18.sp)
    }
}

// --- episode tiles ----------------------------------------------------------

/** Web ep-card: 16:9 thumb, badge bottom-right, watched strip, title + date. */
@Composable
private fun EpisodeTile(
    ep: EpisodeInfo,
    fallbackImage: String?,
    watched: Boolean,
    unaired: Boolean,
    /** Series runtime in minutes — the web's fallback badge ("24m"). */
    durationMin: Int?,
    /** Live job state for this episode from the jobs poll, if any. */
    job: JobItem?,
    downloadsDenied: Boolean,
    /** Already saved to THIS DEVICE — distinct from the server holding a file. */
    savedOffline: Boolean,
    onClick: () -> Unit,
    onSetProgress: (Int) -> Unit,
    onDownload: (Boolean) -> Unit,
    onSaveOffline: () -> Unit,
    onRemoveOffline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember(ep.number) { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .alpha(if (unaired) 0.5f else 1f)
            .scale(if (focused) 1.02f else 1f)
            .let {
                if (unaired) it
                else it.tvClickable(onFocused = { f -> focused = f }, onClick = onClick)
            },
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(10.dp))
                .background(RenzoColors.EpThumbWell)
                .focusRing(focused, 10.dp),
        ) {
            AsyncImage(
                model = fallbackImage,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            // Play overlay while focused (web: hover shows a centred ▶).
            if (focused && !unaired) {
                Box(
                    Modifier.matchParentSize().background(Color(0x47060B0F)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(if (watched) "↺" else "▶", color = Color.White, fontSize = 34.sp)
                }
            }
            // Badge ladder, in the web's exact order:
            // Soon → Watched → ✓ Saved → "<status> NN%" → "<duration>m".
            val busy = job != null && job.active
            val pct = ((job?.progress ?: 0.0) * 100).toInt()
            val badge: Pair<String, Color>? = when {
                unaired -> "Soon" to RenzoColors.Foreground
                watched -> "Watched" to RenzoColors.Foreground
                ep.hasFile -> "✓ Saved" to RenzoColors.DownloadedGreen
                busy -> (job!!.status + if (pct > 0) " $pct%" else "") to RenzoColors.Foreground
                durationMin != null && durationMin > 0 -> "${durationMin}m" to RenzoColors.Foreground
                else -> null
            }
            if (badge != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(RenzoColors.OverlayBlack, RoundedCornerShape(999.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Text(badge.first, color = badge.second, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (watched) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(RenzoColors.Primary),
                )
            } else if (job != null && job.active) {
                // Live download progress, overlaid from the jobs poll.
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(job.progress.toFloat().coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(RenzoColors.Primary),
                )
            }
        }
        Text(
            "E${ep.number}" + (ep.epTitle?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            color = RenzoColors.Foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight(650),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 7.dp),
        )
        // Web `.ep-foot`: air date left, ⋮ kebab right.
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                ep.aired?.takeIf { it.isNotBlank() }?.take(10) ?: "",
                color = RenzoColors.MutedForeground,
                fontSize = 12.5.sp,
                modifier = Modifier.weight(1f),
            )
            if (!unaired) {
                var kebabFocused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .focusRing(kebabFocused, 8.dp)
                        .background(
                            if (kebabFocused) RenzoColors.Secondary else Color.Transparent,
                            RoundedCornerShape(8.dp),
                        )
                        .tvClickable(onFocused = { kebabFocused = it }, onClick = { menuOpen = !menuOpen }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("⋮", color = RenzoColors.MutedForeground, fontSize = 18.sp)
                }
            }
        }
        // Web `.ep-menu` (160px popover) — inline here so the D-pad can walk it.
        if (menuOpen && !unaired) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(RenzoColors.Secondary, RoundedCornerShape(10.dp))
                    .border(1.dp, RenzoColors.Border, RoundedCornerShape(10.dp))
                    .padding(4.dp),
            ) {
                EpMenuItem(if (watched) "Mark unwatched" else "Mark watched") {
                    menuOpen = false
                    onSetProgress(if (watched) ep.number - 1 else ep.number)
                }
                if (!ep.hasFile && !downloadsDenied) {
                    // Server-side: ask the server to fetch the episode. NOT a
                    // copy on this device — that is "Save to device" below, and
                    // conflating the two is exactly the confusion to avoid.
                    EpMenuItem("Download") { menuOpen = false; onDownload(false) }
                    EpMenuItem("Download now") { menuOpen = false; onDownload(true) }
                }
                // Device-side: only offerable once the server actually has the
                // file to hand over.
                if (ep.hasFile) {
                    if (savedOffline) {
                        EpMenuItem("Remove from device") { menuOpen = false; onRemoveOffline() }
                    } else {
                        EpMenuItem("Save to device") { menuOpen = false; onSaveOffline() }
                    }
                }
            }
        }
    }
}

/** One row of the web's per-episode ⋮ menu (13sp, hover/focus = muted fill). */
@Composable
private fun EpMenuItem(label: String, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .focusRing(focused, 6.dp)
            .background(if (focused) RenzoColors.Muted else Color.Transparent, RoundedCornerShape(6.dp))
            .tvClickable(onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(label, color = RenzoColors.Foreground, fontSize = 13.sp)
    }
}

/**
 * An episode is unaired when it has no file AND any signal says future:
 * status text, the AniList next-airing pointer, or an ISO air date > today.
 */
private fun isUnaired(ep: EpisodeInfo, d: TitleDetail, todayIso: String): Boolean {
    if (ep.hasFile) return false
    val st = ep.status?.lowercase(Locale.US)
    if (st != null && (st.contains("soon") || st.contains("unaired") || st.contains("upcoming") || st.contains("future"))) {
        return true
    }
    d.nextAiringEpisode?.let { if (ep.number >= it) return true }
    val a = ep.aired?.take(10)
    if (a != null && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(a) && a > todayIso) return true
    return false
}

// --- helpers ----------------------------------------------------------------

/** AniList descriptions carry light HTML — strip tags, decode common entities. */
private fun cleanDescription(html: String?): String? = html
    ?.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), " ")
    ?.replace(Regex("<[^>]+>"), "")
    ?.replace("&amp;", "&")
    ?.replace("&quot;", "\"")
    ?.replace("&#039;", "'")
    ?.replace("&apos;", "'")
    ?.replace("&rsquo;", "'")
    ?.replace("&hellip;", "…")
    ?.replace("&mdash;", "—")
    ?.replace(Regex("\\s{2,}"), " ")
    ?.trim()


/** Web `.icon-pill`: 42dp circle, bg secondary, 1dp border; `on` = primary. */
@Composable
private fun IconPill(glyph: String, on: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(999.dp))
            .focusRing(focused, 999.dp)
            .background(
                if (on) RenzoColors.Primary.copy(alpha = 0.12f) else RenzoColors.Secondary,
                RoundedCornerShape(999.dp),
            )
            .border(
                1.dp,
                if (on) RenzoColors.Primary else RenzoColors.Border,
                RoundedCornerShape(999.dp),
            )
            .tvClickable(onFocused = { focused = it }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            glyph,
            color = if (on) RenzoColors.Primary else RenzoColors.Foreground,
            fontSize = 17.sp,
        )
    }
}

// --- hero detail-controls (web hero.tsx: Auto pill + Folder + Provider) -----

/**
 * `.detail-controls` — the ghost "Auto: on/off" pill (series only, hidden for
 * download-denied users), the 📁 folder picker and the release-group picker.
 * Selects become tap-to-cycle chips: a dropdown is a poor fit for a remote,
 * and every option set here is short.
 */
@Composable
private fun DetailControls(
    app: AppServices,
    d: TitleDetail,
    downloadsDenied: Boolean,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var auto by remember(d.id) { mutableStateOf(d.autoDownload) }
    var folder by remember(d.id) { mutableStateOf(d.folder ?: d.folders.firstOrNull() ?: "Library") }
    var folders by remember(d.id) { mutableStateOf(d.folders.ifEmpty { listOf(folder) }) }
    var provider by remember(d.id) { mutableStateOf(d.provider) }
    var providers by remember(d.id) { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(d.id) {
        try { folders = app.repo.folders().map { it.name }.ifEmpty { listOf("Library") } } catch (_: Exception) {}
        try { providers = app.repo.providers(d.id).map { it.group } } catch (_: Exception) {}
    }

    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (d.type != "movie" && !downloadsDenied) {
            GhostChip(label = if (auto) "Auto: on" else "Auto: off", on = auto) {
                val next = !auto
                auto = next
                scope.launch {
                    try { auto = app.repo.setAuto(d.id, next).autoDownload } catch (_: Exception) { auto = !next }
                }
            }
        }
        GhostChip(label = "📁  $folder", on = false) {
            if (folders.size > 1) {
                val next = folders[(folders.indexOf(folder).coerceAtLeast(0) + 1) % folders.size]
                folder = next
                scope.launch {
                    try { folder = app.repo.setFolder(d.id, next).folder } catch (_: Exception) {}
                }
            }
        }
        // "Auto (best)" is the web's empty-provider label.
        GhostChip(label = provider ?: "Auto (best)", on = provider != null) {
            if (providers.isNotEmpty()) {
                val order: List<String?> = listOf<String?>(null) + providers
                val next = order[(order.indexOf(provider) + 1).mod(order.size)]
                provider = next
                scope.launch {
                    try { provider = app.repo.setProvider(d.id, next ?: "").provider } catch (_: Exception) {}
                }
            }
        }
    }
}

/** Web `<TrackingRow>`: "Track [status] [score]/10" for AniList/MAL. */
@Composable
private fun TrackingRow(app: AppServices, titleId: Int, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var t by remember(titleId) { mutableStateOf<Tracking?>(null) }
    LaunchedEffect(titleId) {
        t = try { app.repo.tracking(titleId) } catch (_: Exception) { null }
    }
    val tr = t ?: return
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Track", color = RenzoColors.MutedForeground, fontSize = 13.sp)
        if (!tr.connected) {
            Text(
                "Connect AniList or MAL in Settings to track",
                color = RenzoColors.MutedForeground,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            return@Row
        }
        val statuses = listOf(
            null to "— Not tracked —",
            "CURRENT" to "Watching",
            "PLANNING" to "Plan to watch",
            "COMPLETED" to "Completed",
            "PAUSED" to "Paused",
            "DROPPED" to "Dropped",
            "REPEATING" to "Rewatching",
        )
        val curIdx = statuses.indexOfFirst { it.first == tr.status }.coerceAtLeast(0)
        GhostChip(label = statuses[curIdx].second, on = tr.status != null) {
            val next = statuses[(curIdx + 1) % statuses.size].first
            scope.launch {
                try { t = app.repo.setTracking(titleId, status = next ?: "") } catch (_: Exception) {}
            }
        }
        Text(
            (tr.score?.takeIf { it > 0 }?.let { "%.0f".format(it) } ?: "–") + "/10",
            color = RenzoColors.MutedForeground,
            fontSize = 13.sp,
        )
    }
}

/** Web `.tp-ghost`: rounded-full 1dp bordered chip; `on` = primary text/border. */
@Composable
private fun GhostChip(label: String, on: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .focusRing(focused, 999.dp)
            .background(
                if (on) RenzoColors.Primary.copy(alpha = 0.12f) else RenzoColors.Secondary,
                RoundedCornerShape(999.dp),
            )
            .border(
                1.dp,
                if (on) RenzoColors.Primary else RenzoColors.Border,
                RoundedCornerShape(999.dp),
            )
            .tvClickable(onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (on) RenzoColors.Primary else RenzoColors.Foreground,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
