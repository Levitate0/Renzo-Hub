package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import top.levitatemedia.renzo.tv.ui.components.tvClickable
import top.levitatemedia.renzo.tv.ui.components.focusRing
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.LibraryAddCheck
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.EpisodeInfo
import top.levitatemedia.renzo.tv.api.JobItem
import top.levitatemedia.renzo.tv.api.SeasonRef
import top.levitatemedia.renzo.tv.api.TitleDetail
import top.levitatemedia.renzo.tv.api.Tracking
import top.levitatemedia.renzo.hub.core.offline.DownloadBus
import top.levitatemedia.renzo.tv.offline.enqueueEpisodes
import top.levitatemedia.renzo.tv.offline.episodeKey
import top.levitatemedia.renzo.tv.renzoScreenWidthDp
import top.levitatemedia.renzo.tv.ui.components.ErrorBox
import top.levitatemedia.renzo.tv.ui.components.LoadingBox
import top.levitatemedia.renzo.tv.ui.kit.Amber500
import top.levitatemedia.renzo.tv.ui.kit.Blue400
import top.levitatemedia.renzo.tv.ui.kit.Blue500
import top.levitatemedia.renzo.tv.ui.kit.Border40
import top.levitatemedia.renzo.tv.ui.kit.Border60
import top.levitatemedia.renzo.tv.ui.kit.Emerald400
import top.levitatemedia.renzo.tv.ui.kit.Emerald500
import top.levitatemedia.renzo.tv.ui.kit.FieldLabel
import top.levitatemedia.renzo.tv.ui.kit.ForegroundFaint06
import top.levitatemedia.renzo.tv.ui.kit.ForegroundFaint10
import top.levitatemedia.renzo.tv.ui.kit.Green400
import top.levitatemedia.renzo.tv.ui.kit.Green500
import top.levitatemedia.renzo.tv.ui.kit.HairLine
import top.levitatemedia.renzo.tv.ui.kit.HeroActionButton
import top.levitatemedia.renzo.tv.ui.kit.Hint
import top.levitatemedia.renzo.tv.ui.kit.Pink500
import top.levitatemedia.renzo.tv.ui.kit.PillShape
import top.levitatemedia.renzo.tv.ui.kit.RenzoButton
import top.levitatemedia.renzo.tv.ui.kit.RenzoChip
import top.levitatemedia.renzo.tv.ui.kit.RenzoDialog
import top.levitatemedia.renzo.tv.ui.kit.RenzoSelect
import top.levitatemedia.renzo.tv.ui.kit.RenzoTextField
import top.levitatemedia.renzo.tv.ui.kit.SquareIconButton
import top.levitatemedia.renzo.tv.ui.kit.StatusDot
import top.levitatemedia.renzo.tv.ui.kit.Yellow400
import top.levitatemedia.renzo.tv.ui.kit.Yellow500
import top.levitatemedia.renzo.tv.ui.kit.kitClickable
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors
import androidx.compose.ui.text.input.KeyboardType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Title page, built the way Shiori's series page is (feature-shiori ui/series
 * SeriesDetailScreen.kt + SeriesHeroSection.kt + SeriesChaptersSection.kt) —
 * the owner's direction (2026-10-11) is that Renzo look exactly like Shiori:
 *
 *  - wide: a 48dp "‹ Back" strip with the title centred, the blurred-cover
 *    hero, then a centred 1280dp two-column body — a 380dp left rail (download
 *    settings; Shiori's downloads panel) and the episodes on the right, each
 *    column scrolling on its own.
 *  - narrow and TV: one scrolling column — hero, episodes, then the rail.
 *
 * Owner direction 2026-10-11 ("keep renzo's original episode display"): the
 * episodes are Renzo's own 16:9 thumbnail tiles under Shiori's header chips,
 * and the seasons are Renzo's horizontal poster strip above them.
 * The cover expands in Shiori's lightbox. The previous banner-and-tiles page
 * is on branch backup/pre-shiori-restyle.
 */
@Composable
fun TitleScreen(
    app: AppServices,
    titleId: Int,
    onPlay: (TitleDetail, Int) -> Unit,
    onOpenTitle: (Int) -> Unit,
    onSessionLost: () -> Unit,
    /** Leaves the series for wherever you came from. */
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

    // Device-download state. Bumped locally on save/remove for an instant
    // response, and by the shared downloader when a transfer actually lands.
    var offlineRevision by remember { mutableStateOf(0) }
    // Bumped after a watched-progress write so the tracking dialog re-reads:
    // the server scrobbles to AniList/MAL on progress (api.ts:841) and can
    // escalate the status to "completed" on the final episode (api.ts:252-263).
    var trackingRevision by remember { mutableStateOf(0) }
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

    val wide = !app.isTv && renzoScreenWidthDp() >= 1024
    val d = detail

    Column(Modifier.fillMaxSize().background(RenzoColors.Background)) {
        TitleTopStrip(wide = wide, title = d?.displayTitle ?: "", onBack = onBack)
        when {
            error != null -> ErrorBox(error ?: "Failed to load title", onRetry = { reload++ }, autoFocus = app.isTv)
            d == null -> LoadingBox(label = "Loading title details…")
            else -> {
                val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
                var progressBusy by remember { mutableStateOf(false) }
                var trackOpen by remember { mutableStateOf(false) }
                // Live per-episode download state (web overlays the ["jobs"] poll).
                var jobs by remember { mutableStateOf<List<JobItem>>(emptyList()) }
                LaunchedEffect(d.id) {
                    while (true) {
                        jobs = try { app.repo.jobs().filter { it.titleId == d.id } } catch (_: Exception) { jobs }
                        kotlinx.coroutines.delay(4000)
                    }
                }
                val downloadsDenied = app.user.value?.downloadsDenied == true

                fun setProgress(n: Int) {
                    if (progressBusy) return
                    progressBusy = true
                    scope.launch {
                        try {
                            watchedThrough = app.repo.setProgress(d.id, n).watchedThrough
                            trackingRevision++
                        } catch (e: ApiError) {
                            if (e.status == 401) onSessionLost()
                        } catch (_: Exception) {
                        } finally { progressBusy = false }
                    }
                }

                val hero: @Composable () -> Unit = {
                    TitleHero(
                        d = d,
                        heroWide = wide,
                        inLibrary = inLibrary,
                        lists = lists,
                        watchedThrough = watchedThrough,
                        downloadsDenied = downloadsDenied,
                        app = app,
                        onPlay = { ep -> onPlay(d, ep) },
                        onTrack = { trackOpen = true },
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
                        onToggleLibrary = {
                            scope.launch {
                                try {
                                    if (inLibrary) {
                                        app.repo.removeFromLibrary(d.id); inLibrary = false
                                    } else {
                                        app.repo.addToLibrary(d.id); inLibrary = true
                                    }
                                } catch (e: ApiError) {
                                    if (e.status == 401) onSessionLost()
                                } catch (_: Exception) {
                                    // keep prior state — the UI stays truthful
                                }
                            }
                        },
                    )
                }

                // Renzo's ep-grid: minmax(240px, 1fr) tiles with 16dp gaps, in
                // whatever the episode column is given (the right column when
                // wide, the full width otherwise), less its 16dp side padding.
                val screenW = renzoScreenWidthDp()
                val epColumnW = if (wide) minOf(screenW - 16, 1280) - 16 - 380 - 16 else screenW
                val epCols = ((epColumnW - 32 + 16) / (240 + 16)).coerceAtLeast(1)

                val episodes: LazyListScope.() -> Unit = {
                    item(key = "season-bar") { SeasonBar(d, onOpenTitle) }
                    if (d.episodeList.isNotEmpty()) {
                        item(key = "eps-header") {
                            EpisodesHeader(
                                d = d,
                                todayIso = todayIso,
                                watchedThrough = watchedThrough,
                                isTv = app.isTv,
                                downloadsDenied = downloadsDenied,
                                onMarkSeason = { target -> setProgress(target) },
                                onSaveSeason = {
                                    app.prefs.serverUrl?.let { base ->
                                        val nums = d.episodeList.filter { it.hasFile }.map { it.number }
                                        if (nums.isNotEmpty()) {
                                            enqueueEpisodes(d, nums, base)
                                            offlineRevision++
                                        }
                                    }
                                },
                                onDownloadSeason = {
                                    scope.launch {
                                        try {
                                            app.repo.downloadSeason(d.id)
                                            toast("Season queued for download")
                                        } catch (e: Exception) { toast(e.message ?: "Couldn't queue the season") }
                                    }
                                },
                            )
                        }
                        val chunks = d.episodeList.chunked(epCols)
                        items(chunks.size, key = { "eps-row-$it" }) { i ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                chunks[i].forEach { ep ->
                                    EpisodeTile(
                                        ep = ep,
                                        seriesTitle = d.displayTitle,
                                        fallbackImage = ep.thumbnail ?: d.banner ?: d.poster,
                                        watched = ep.number <= watchedThrough,
                                        unaired = isUnaired(ep, d, todayIso),
                                        durationMin = d.duration,
                                        job = jobs.firstOrNull { it.episode == ep.number },
                                        downloadsDenied = downloadsDenied,
                                        savedOffline = remember(offlineRevision, downloadRevision, d.id, ep.number) {
                                            app.offline.isOffline(episodeKey(d.id, ep.number))
                                        },
                                        onClick = { onPlay(d, ep.number) },
                                        onSetProgress = { n -> setProgress(n) },
                                        onDownload = { priority ->
                                            scope.launch {
                                                try { app.repo.downloadEpisode(d.id, ep.number, priority) } catch (_: Exception) {}
                                            }
                                        },
                                        onSaveOffline = {
                                            app.prefs.serverUrl?.let { base ->
                                                enqueueEpisodes(d, listOf(ep.number), base)
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
                    }
                }

                if (wide) {
                    Column(Modifier.fillMaxSize()) {
                        hero()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .widthIn(max = 1280.dp)
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(24.dp),
                                modifier = Modifier
                                    .width(380.dp)
                                    .fillMaxHeight()
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                DownloadSettingsSection(app, d, downloadsDenied)
                                Spacer(Modifier.height(16.dp))
                            }
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                            ) { episodes() }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item(key = "hero") { hero() }
                        episodes()
                        item(key = "dl-settings") {
                            Spacer(Modifier.height(16.dp))
                            DownloadSettingsSection(app, d, downloadsDenied)
                        }
                    }
                }

                if (trackOpen) {
                    TrackingDialog(app, d.id, revision = trackingRevision, onDismiss = { trackOpen = false })
                }
            }
        }
    }
}

// --- top strip (Shiori SeriesDetailScreen topBar) ---------------------------

@Composable
private fun TitleTopStrip(wide: Boolean, title: String, onBack: () -> Unit) {
    if (wide) {
        // Shiori: "‹ Back to Library" hugging the left edge, title dead centre.
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
                    .clip(MaterialTheme.shapes.small)
                    .kitClickable(onClick = onBack)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null, tint = RenzoColors.MutedForeground, modifier = Modifier.size(16.dp))
                Text(
                    "Back",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = RenzoColors.MutedForeground,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = RenzoColors.Foreground,
                modifier = Modifier.align(Alignment.Center).widthIn(max = (renzoScreenWidthDp() * 0.5f).dp),
            )
        }
    } else {
        // Shiori's narrow TopAppBar: back arrow + title.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(48.dp).clip(PillShape).kitClickable(radius = 24.dp, onClick = onBack),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = RenzoColors.Foreground)
            }
            Text(
                title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = RenzoColors.Foreground,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

// --- hero (Shiori SeriesHeroSection) ----------------------------------------

/** Shiori's hero status pill, from AniList's airing status. */
private data class AiringPill(val label: String, val text: Color, val dot: Color, val pulse: Boolean)

private fun airingPill(status: String?): AiringPill? = when (status?.uppercase(Locale.US)) {
    "RELEASING" -> AiringPill("Ongoing", Green400, Green500, true)
    "FINISHED" -> AiringPill("Completed", Blue400, Blue500, false)
    "NOT_YET_RELEASED" -> AiringPill("Upcoming", Yellow400, Yellow500, false)
    "HIATUS" -> AiringPill("On Hiatus", Yellow400, Yellow500, false)
    "CANCELLED" -> AiringPill("Cancelled", Color(0xFFF87171), Color(0xFFEF4444), false)
    else -> null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TitleHero(
    d: TitleDetail,
    heroWide: Boolean,
    inLibrary: Boolean,
    lists: List<String>,
    watchedThrough: Int,
    downloadsDenied: Boolean,
    app: AppServices,
    onPlay: (Int) -> Unit,
    onTrack: () -> Unit,
    onToggleList: (String) -> Unit,
    onToggleLibrary: () -> Unit,
) {
    var descriptionExpanded by rememberSaveable(d.id) { mutableStateOf(false) }
    val desc = remember(d.id) { cleanDescription(d.description) }
    val maxAvailable = (d.availableEpisodes ?: d.episodeList.size).coerceAtLeast(1)
    val playEp = (watchedThrough + 1).coerceIn(1, maxAvailable)
    val scope = rememberCoroutineScope()
    var auto by remember(d.id) { mutableStateOf(d.autoDownload) }
    // Shiori's cover lightbox (SeriesHeroSection): click the cover to expand.
    var coverExpanded by remember(d.id) { mutableStateOf(false) }

    Box(Modifier.fillMaxWidth().clipToBounds()) {
        // Blurred cover + fade to the page + a faint primary tint, as Shiori.
        val art = d.poster?.takeIf { it.isNotBlank() } ?: d.banner
        if (art != null) {
            AsyncImage(
                model = art,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = BiasAlignment(0f, -0.4f),
                modifier = Modifier
                    .matchParentSize()
                    .blur(28.dp, BlurredEdgeTreatment.Unbounded)
                    .alpha(0.4f),
            )
        }
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(
                    listOf(
                        RenzoColors.Background.copy(alpha = 0.30f),
                        RenzoColors.Background.copy(alpha = 0.70f),
                        RenzoColors.Background,
                    ),
                ),
            ),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(listOf(RenzoColors.Primary.copy(alpha = 0.10f), Color.Transparent)),
            ),
        )

        val coverBox: @Composable (Dp, Dp) -> Unit = { w, h ->
            Box(
                Modifier
                    .width(w)
                    .height(h)
                    .clip(RoundedCornerShape(12.dp))
                    .background(RenzoColors.Card)
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp))
                    .then(if (d.poster.isNullOrBlank()) Modifier else Modifier.kitClickable(radius = 12.dp) { coverExpanded = true }),
            ) {
                AsyncImage(
                    model = d.poster,
                    contentDescription = d.displayTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        val heroInfo: @Composable () -> Unit = {
            airingPill(d.airingStatus)?.let { pill ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(PillShape)
                        .background(pill.dot.copy(alpha = 0.12f))
                        .border(1.dp, pill.dot.copy(alpha = 0.25f), PillShape)
                        .padding(horizontal = 10.dp, vertical = 3.dp),
                ) {
                    if (pill.pulse) {
                        val transition = rememberInfiniteTransition(label = "status-pulse")
                        val pulse by transition.animateFloat(
                            initialValue = 1f,
                            targetValue = 0.35f,
                            animationSpec = infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                            label = "status-pulse-alpha",
                        )
                        StatusDot(pill.dot.copy(alpha = pulse))
                    } else {
                        StatusDot(pill.dot)
                    }
                    Text(pill.label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = pill.text)
                }
            }

            androidx.compose.foundation.text.selection.SelectionContainer {
                Text(
                    d.displayTitle,
                    fontSize = if (heroWide) 34.sp else 26.sp,
                    lineHeight = if (heroWide) 40.sp else 30.sp,
                    fontWeight = FontWeight.Bold,
                    color = RenzoColors.Foreground,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Shiori's "by author" line: the romaji title, when it differs.
            if (d.romaji.isNotBlank() && d.romaji != d.displayTitle) {
                androidx.compose.foundation.text.selection.SelectionContainer {
                    Text(d.romaji, style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
                }
            }

            // Inline meta row.
            val meta = listOfNotNull(
                if (d.type == "movie") "Movie" else seasonWordFor(d),
                d.year?.toString(),
                (d.episodeCount ?: d.episodeList.size.takeIf { it > 0 })?.let { "$it episode${if (it == 1) "" else "s"}" },
                d.duration?.takeIf { it > 0 }?.let { "${it}m" },
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                meta.forEachIndexed { i, part ->
                    if (i > 0) Text("·", style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground.copy(alpha = 0.4f))
                    Text(part, style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
                }
            }

            if (d.genres.isNotEmpty() || d.isAdult) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (d.isAdult) {
                        Text(
                            "18+",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFF87171),
                            modifier = Modifier
                                .clip(PillShape)
                                .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.35f), PillShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    d.genres.forEach { g ->
                        Text(
                            g,
                            fontSize = 11.sp,
                            color = RenzoColors.Foreground.copy(alpha = 0.8f),
                            modifier = Modifier
                                .clip(PillShape)
                                .background(ForegroundFaint06)
                                .border(1.dp, Border40, PillShape)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            if (!desc.isNullOrBlank()) {
                Column(modifier = if (heroWide) Modifier.widthIn(max = 640.dp) else Modifier) {
                    androidx.compose.foundation.text.selection.SelectionContainer {
                        Text(
                            desc,
                            style = MaterialTheme.typography.bodySmall,
                            color = RenzoColors.MutedForeground,
                            maxLines = if (descriptionExpanded) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (desc.length > 240) {
                        Text(
                            if (descriptionExpanded) "Show less" else "Read more",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = RenzoColors.Primary,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .kitClickable(radius = 4.dp) { descriptionExpanded = !descriptionExpanded },
                        )
                    }
                }
            }

            // Action toolbar.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                val label = if (watchedThrough > 0 && playEp > 1) "Continue E$playEp" else "Play E$playEp"
                HeroActionButton(
                    icon = Icons.Filled.PlayArrow,
                    contentDescription = label,
                    label = if (heroWide) label else null,
                    primary = true,
                    onClick = { onPlay(playEp) },
                )
                val inW = lists.contains("watchlist")
                HeroActionButton(
                    icon = if (inW) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (inW) "On your watchlist" else "Add to watchlist",
                    label = if (!heroWide) null else if (inW) "On watchlist" else "Watchlist",
                    tint = if (inW) RenzoColors.Primary else RenzoColors.Foreground,
                    borderColor = if (inW) RenzoColors.Primary.copy(alpha = 0.5f) else Border60,
                    onClick = { onToggleList("watchlist") },
                )
                val inF = lists.contains("favorites")
                HeroActionButton(
                    icon = if (inF) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = if (inF) "In your favourites" else "Add to favourites",
                    label = if (!heroWide) null else if (inF) "Favourited" else "Favourite",
                    tint = if (inF) Pink500 else RenzoColors.Foreground,
                    borderColor = if (inF) Pink500.copy(alpha = 0.6f) else Border60,
                    background = if (inF) Pink500.copy(alpha = 0.15f) else Color.Transparent,
                    onClick = { onToggleList("favorites") },
                )
                HeroActionButton(
                    icon = if (inLibrary) Icons.Filled.LibraryAddCheck else Icons.Filled.LibraryAdd,
                    contentDescription = if (inLibrary) "In your library — remove" else "Add to library",
                    label = if (!heroWide) null else if (inLibrary) "In library" else "Add to library",
                    tint = if (inLibrary) RenzoColors.Primary else RenzoColors.Foreground,
                    borderColor = if (inLibrary) RenzoColors.Primary.copy(alpha = 0.5f) else Border60,
                    onClick = onToggleLibrary,
                )
                HeroActionButton(
                    icon = Icons.Filled.Sensors,
                    contentDescription = "Track this title",
                    label = if (heroWide) "Track" else null,
                    onClick = onTrack,
                )
                // A saved per-title setting, so it reads as an on/off control
                // with visible state — Shiori's Hide Decimals / Prioritize Free.
                if (d.type != "movie" && !downloadsDenied) {
                    HeroActionButton(
                        icon = Icons.Filled.Autorenew,
                        contentDescription = if (auto) "Auto-download on" else "Auto-download off",
                        label = if (!heroWide) null else if (auto) "Auto-download on" else "Auto-download",
                        tint = if (auto) RenzoColors.Primary else RenzoColors.Foreground,
                        borderColor = if (auto) RenzoColors.Primary.copy(alpha = 0.5f) else Border60,
                        onClick = {
                            val next = !auto
                            auto = next
                            scope.launch {
                                try { auto = app.repo.setAuto(d.id, next).autoDownload } catch (_: Exception) { auto = !next }
                            }
                        },
                    )
                }
            }
        }

        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            if (heroWide) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier
                        .widthIn(max = 1280.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 40.dp),
                ) {
                    coverBox(210.dp, 315.dp)
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) { heroInfo() }
                }
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { coverBox(150.dp, 225.dp) }
                    heroInfo()
                }
            }
        }
    }

    // ── Cover lightbox (Shiori SeriesHeroSection) ──
    if (coverExpanded && !d.poster.isNullOrBlank()) {
        Dialog(onDismissRequest = { coverExpanded = false }) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .kitClickable { coverExpanded = false },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = d.poster,
                    contentDescription = d.displayTitle,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

/** "Season 2" / "Season 2 Part 2" / "OVA" — the meta row's first entry. */
private fun seasonWordFor(d: TitleDetail): String =
    if (d.seasonKind == "extra") {
        when (d.format?.uppercase(Locale.US)) { "MOVIE" -> "Movie"; "OVA" -> "OVA"; else -> "Special" }
    } else {
        "Season ${d.seasonNum ?: 1}" + (d.seasonPart?.takeIf { it > 1 }?.let { " Part $it" } ?: "")
    }

// --- episodes (Shiori ChaptersSectionHeader + ChapterRow) -------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EpisodesHeader(
    d: TitleDetail,
    todayIso: String,
    watchedThrough: Int,
    isTv: Boolean,
    downloadsDenied: Boolean,
    onMarkSeason: (Int) -> Unit,
    onSaveSeason: () -> Unit,
    onDownloadSeason: () -> Unit,
) {
    val total = d.episodeList.size
    val aired = d.episodeList.count { !isUnaired(it, d, todayIso) }
    val onServer = d.episodeList.count { it.hasFile }
    val upcoming = total - aired
    val fullyWatched = aired > 0 && watchedThrough >= aired
    Column(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Text(
                "$total episode${if (total == 1) "" else "s"}",
                style = MaterialTheme.typography.titleMedium,
                color = RenzoColors.Foreground,
                modifier = Modifier.weight(1f),
            )
            Row {
                Text("$onServer downloaded", fontSize = 12.sp, color = RenzoColors.MutedForeground)
                if (upcoming > 0) {
                    Text(" · ", fontSize = 12.sp, color = RenzoColors.MutedForeground)
                    Text("$upcoming upcoming", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Amber500)
                }
            }
        }
        HairLine()
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            if (!downloadsDenied && d.type != "movie" && onServer < aired) {
                RenzoChip(
                    label = "Download season",
                    icon = Icons.Filled.Download,
                    count = aired - onServer,
                    accent = RenzoColors.Primary,
                    onClick = onDownloadSeason,
                )
            }
            // Device copies of what the server holds — never on TV, like the
            // web's offlineSupported gate.
            if (!isTv && onServer > 0) {
                RenzoChip(
                    label = "Save season offline",
                    icon = Icons.Filled.CloudDownload,
                    count = onServer,
                    accent = Emerald400,
                    onClick = onSaveSeason,
                )
            }
            if (aired > 0) {
                RenzoChip(
                    label = if (fullyWatched) "Season watched" else "Mark season watched",
                    icon = Icons.Filled.DoneAll,
                    active = fullyWatched,
                    onClick = { onMarkSeason(if (fullyWatched) 0 else aired) },
                )
            }
        }
    }
}

// Renzo's own episode tiles (the pre-restyle page, branch
// backup/pre-shiori-restyle) — kept at the owner's direction (2026-10-11):
// the Shiori header and hero above, Renzo's 16:9 thumbnail grid below.

@Composable
private fun EpisodeTile(
    ep: EpisodeInfo,
    /** The web's .ep-series overline — the series name above every card. */
    seriesTitle: String,
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
        // Web .ep-series: uppercase muted overline with the series name.
        Text(
            seriesTitle.uppercase(),
            color = RenzoColors.MutedForeground,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 9.dp),
        )
        // Web .ep-title: "E{n} – {title}", falling back to "Episode n".
        Text(
            "E${ep.number} – " + (ep.epTitle?.takeIf { it.isNotBlank() } ?: "Episode ${ep.number}"),
            color = RenzoColors.Foreground,
            fontSize = 14.sp,
            fontWeight = FontWeight(650),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp),
        )
        // Web `.ep-foot`: "Subtitled" left, ⋮ kebab right.
        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Subtitled",
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

// --- left rail (Shiori SeriesSourcesSection / SeriesDownloadsPanel) ---------

/** Shiori's rail section heading: titleMedium + a muted count pill. */
@Composable
private fun RailHeading(title: String, count: Int? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = RenzoColors.Foreground)
        if (count != null) {
            Text(
                "$count",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = RenzoColors.MutedForeground,
                modifier = Modifier.clip(PillShape).background(ForegroundFaint10).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/**
 * The franchise as Renzo's horizontal poster strip (the pre-restyle season
 * chain, Crunchyroll-style) — this title outlined in the accent, the next
 * season marked "Up next". Owner direction 2026-10-11: brought back over
 * Shiori's rail list.
 */
@Composable
private fun SeasonBar(d: TitleDetail, onOpenTitle: (Int) -> Unit) {
    val chain = remember(d) { orderedSeasons(d) }
    if (chain.size < 2) return
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        RailHeading("Seasons", chain.size)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp), contentPadding = PaddingValues(end = 24.dp)) {
            items(chain, key = { it.id }) { ref ->
                SeasonCard(
                    ref = ref,
                    current = ref.id == d.id,
                    upNext = d.nextUp?.id == ref.id,
                    onOpen = { if (ref.id != d.id) onOpenTitle(ref.id) },
                )
            }
        }
    }
}

@Composable
private fun SeasonCard(ref: SeasonRef, current: Boolean, upNext: Boolean, onOpen: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(118.dp)
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
            seasonLabel(ref, short = true) + if (current) " (this)" else if (upNext) " · Up next" else "",
            color = if (current) RenzoColors.Foreground else RenzoColors.MutedForeground,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Where downloads land and which release group they come from. */
@Composable
private fun DownloadSettingsSection(app: AppServices, d: TitleDetail, downloadsDenied: Boolean) {
    if (downloadsDenied) return
    val scope = rememberCoroutineScope()
    var folder by remember(d.id) { mutableStateOf(d.folder ?: d.folders.firstOrNull() ?: "Library") }
    var folders by remember(d.id) { mutableStateOf(d.folders.ifEmpty { listOf(folder) }) }
    var provider by remember(d.id) { mutableStateOf(d.provider) }
    var providers by remember(d.id) { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(d.id) {
        try { folders = app.repo.folders().map { it.name }.ifEmpty { listOf("Library") } } catch (_: Exception) {}
        try { providers = app.repo.providers(d.id).map { it.group } } catch (_: Exception) {}
    }

    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RailHeading("Downloads")
        FieldLabel("Library folder")
        RenzoSelect(
            options = folders.map { it to it },
            value = folder,
            onChange = { next ->
                folder = next
                scope.launch { try { folder = app.repo.setFolder(d.id, next).folder } catch (_: Exception) {} }
            },
        )
        Spacer(Modifier.height(4.dp))
        FieldLabel("Release group")
        // "" is the web's "Auto (best)".
        RenzoSelect(
            options = listOf("" to "Auto (best)") + providers.map { it to it },
            value = provider ?: "",
            onChange = { next ->
                provider = next.ifEmpty { null }
                scope.launch { try { provider = app.repo.setProvider(d.id, next).provider } catch (_: Exception) {} }
            },
        )
        Hint("Used for every episode and season of this series.")
    }
}

// --- tracking (Shiori: the hero's Track button opens a dialog) --------------

/** Web `toast(...)` from sonner. */
private fun toast(msg: String) = top.levitatemedia.renzo.tv.renzoToast(msg)

/**
 * Status vocabulary, transliterated from `tracking-row.tsx:15-23` — the
 * server's lowercase TRACK_STATUSES. AniList's uppercase enum never crosses the
 * wire (the server silently drops it with a 200).
 *
 * Deliberately NOT shared with AccountScreen's TRACK_OPTIONS: the web keeps two
 * lists in two different orders.
 */
private val TRACK_STATUS_OPTIONS = listOf(
    "" to "— Not tracked —",
    "watching" to "Watching",
    "completed" to "Completed",
    "planning" to "Plan to watch",
    "paused" to "Paused",
    "dropped" to "Dropped",
    "rewatching" to "Rewatching",
)

@Composable
private fun TrackingDialog(app: AppServices, titleId: Int, revision: Int, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var t by remember(titleId) { mutableStateOf<Tracking?>(null) }
    var loaded by remember(titleId) { mutableStateOf(false) }
    var status by remember(titleId) { mutableStateOf("") }
    var progress by remember(titleId) { mutableStateOf("") }
    var score by remember(titleId) { mutableStateOf("") }
    var saving by remember(titleId) { mutableStateOf(false) }

    LaunchedEffect(titleId, revision) {
        t = try { app.repo.tracking(titleId) } catch (_: Exception) { null }
        loaded = true
    }
    LaunchedEffect(t) {
        val e = t?.entry
        status = e?.status ?: ""
        progress = (e?.progress ?: 0).toString()
        score = e?.score?.takeIf { it > 0 }?.let { formatScore(it) } ?: ""
    }

    val tr = t
    RenzoDialog(
        onDismiss = onDismiss,
        title = "Track",
        description = if (tr?.connected == true) "Syncs to " + tr.providers.joinToString(" + ") + "." else null,
        maxWidth = 480.dp,
    ) {
        when {
            !loaded -> Text("Loading…", style = MaterialTheme.typography.bodyMedium, color = RenzoColors.MutedForeground)
            tr == null || !tr.connected -> {
                Text(
                    "Connect AniList or MyAnimeList under Account → Required credentials to track this title.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RenzoColors.MutedForeground,
                )
                Spacer(Modifier.height(16.dp))
                RenzoButton(text = "Close", variant = "outline", onClick = onDismiss)
            }
            else -> {
                FieldLabel("Status")
                RenzoSelect(options = TRACK_STATUS_OPTIONS, value = status, onChange = { status = it })
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        // The tracker's own episode count, not the local library's.
                        FieldLabel("Progress" + (tr.entry?.total?.let { " (of $it)" } ?: ""))
                        RenzoTextField(
                            value = progress,
                            onValueChange = { progress = it.filter { c -> c.isDigit() } },
                            placeholder = "0",
                            keyboardType = KeyboardType.Number,
                        )
                    }
                    Column(Modifier.weight(1f)) {
                        FieldLabel("Score")
                        RenzoTextField(
                            value = score,
                            onValueChange = { score = it.filter { c -> c.isDigit() || c == '.' } },
                            placeholder = "/10",
                            keyboardType = KeyboardType.Number,
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                // An explicit commit, like the web's Sync button: cycling a
                // select must not push every intermediate status to the user's
                // real AniList/MAL account.
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RenzoButton(
                        text = if (saving) "Syncing…" else "Sync",
                        busy = saving,
                        onClick = {
                            saving = true
                            scope.launch {
                                try {
                                    t = app.repo.setTracking(
                                        titleId,
                                        status = status.takeIf { it.isNotEmpty() },
                                        progress = progress.toIntOrNull() ?: 0,
                                        score = score.takeIf { it.isNotEmpty() }?.toDoubleOrNull(),
                                    )
                                    toast("Tracking synced")
                                    onDismiss()
                                } catch (e: Exception) {
                                    toast(e.message ?: "Tracking sync failed")
                                } finally {
                                    saving = false
                                }
                            }
                        },
                    )
                    RenzoButton(text = "Cancel", variant = "outline", onClick = onDismiss)
                }
            }
        }
    }
}

/** Trims a whole-number score to "8" rather than "8.0" (web sends String(e.score)). */
private fun formatScore(v: Double): String =
    if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

// --- shared helpers ---------------------------------------------------------

/**
 * An episode is unaired when it has no file AND any signal says future:
 * status text, the AniList next-airing pointer, or an ISO air date > today.
 */
internal fun isUnaired(ep: EpisodeInfo, d: TitleDetail, todayIso: String): Boolean {
    if (ep.hasFile) return false
    // The server sends `aired` as a boolean (web: `ep.aired === false`); the
    // lenient decoder hands it over as the string "false".
    if (ep.aired == "false") return true
    val st = ep.status?.lowercase(Locale.US)
    if (st != null && (st.contains("soon") || st.contains("unaired") || st.contains("upcoming") || st.contains("future"))) {
        return true
    }
    d.nextAiringEpisode?.let { if (ep.number >= it) return true }
    val a = ep.aired?.take(10)
    if (a != null && Regex("^\\d{4}-\\d{2}-\\d{2}$").matches(a) && a > todayIso) return true
    return false
}

/** AniList descriptions carry light HTML — strip tags, decode common entities. */
internal fun cleanDescription(html: String?): String? = html
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

/** Web orderedSeasons (season-utils.ts): this title plus its chain, seasons first, then extras. */
internal fun orderedSeasons(d: TitleDetail): List<SeasonRef> {
    val current = SeasonRef(
        id = d.id, num = d.seasonNum, part = d.seasonPart, kind = d.seasonKind,
        format = d.format, title = d.displayTitle, poster = d.poster, year = d.year,
    )
    return (listOf(current) + d.seasons.filter { it.id != d.id }).sortedWith(
        compareBy<SeasonRef>({ it.num ?: 0 }, { if (it.kind == "extra") 1 else 0 }, { it.part ?: 0 }, { it.year ?: 0 }, { it.id }),
    )
}

/** Web seasonChip + year: "S1 · 2013", "OVA · 2023". */
internal fun seasonLabel(ref: SeasonRef, short: Boolean = false): String {
    val chip = when {
        ref.kind == "movie" -> "Movie"
        ref.kind == "extra" -> when (ref.format?.uppercase(Locale.US)) {
            "MOVIE" -> "Movie"
            "OVA" -> "OVA"
            else -> "Special"
        }
        // short = the web seasonChip on the poster strip: "S2 Pt2".
        short -> "S${ref.num ?: 1}" + (ref.part?.takeIf { it > 1 }?.let { " Pt$it" } ?: "")
        else -> "Season ${ref.num ?: 1}" + (ref.part?.takeIf { it > 1 }?.let { " Part $it" } ?: "")
    }
    return chip + (ref.year?.let { " · $it" } ?: "")
}
