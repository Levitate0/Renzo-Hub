package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
 *    hero, then a centred 1280dp two-column body — a 380dp left rail (seasons,
 *    download settings; Shiori's sources + downloads) and the episode list on
 *    the right (Shiori's chapter list), each column scrolling on its own.
 *  - narrow and TV: one scrolling column in Shiori's mobile order — hero,
 *    episodes, then the rail sections.
 *
 * Episodes are rows in Shiori's chapter-row form rather than thumbnail tiles.
 * The previous banner-and-tiles page is on branch backup/pre-shiori-restyle.
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

                val episodes: LazyListScope.() -> Unit = {
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
                        d.episodeList.forEach { ep ->
                            item(key = "ep-${ep.number}") {
                                EpisodeRow(
                                    ep = ep,
                                    durationMin = d.duration,
                                    watched = ep.number <= watchedThrough,
                                    unaired = isUnaired(ep, d, todayIso),
                                    job = jobs.firstOrNull { it.episode == ep.number },
                                    downloadsDenied = downloadsDenied,
                                    isTv = app.isTv,
                                    savedOffline = remember(offlineRevision, downloadRevision, d.id, ep.number) {
                                        app.offline.isOffline(episodeKey(d.id, ep.number))
                                    },
                                    onPlay = { onPlay(d, ep.number) },
                                    onToggleWatched = { w -> setProgress(if (w) ep.number else ep.number - 1) },
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
                                )
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
                                SeasonsSection(d, onOpenTitle)
                                DownloadSettingsSection(app, d, downloadsDenied)
                                Spacer(Modifier.height(16.dp))
                            }
                            LazyColumn(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentPadding = PaddingValues(bottom = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        item(key = "seasons") {
                            Spacer(Modifier.height(16.dp))
                            SeasonsSection(d, onOpenTitle)
                        }
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
                    .border(1.dp, Color.White.copy(alpha = 0.06f), RoundedCornerShape(12.dp)),
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

@Composable
private fun EpisodeRow(
    ep: EpisodeInfo,
    durationMin: Int?,
    watched: Boolean,
    unaired: Boolean,
    job: JobItem?,
    downloadsDenied: Boolean,
    isTv: Boolean,
    /** Already saved to THIS DEVICE — distinct from the server holding a file. */
    savedOffline: Boolean,
    onPlay: () -> Unit,
    onToggleWatched: (Boolean) -> Unit,
    onDownload: (Boolean) -> Unit,
    onSaveOffline: () -> Unit,
    onRemoveOffline: () -> Unit,
) {
    val busy = job != null && job.active
    val pct = ((job?.progress ?: 0.0) * 100).toInt()
    val stacked = !isTv && renzoScreenWidthDp() < 640

    val leading: @Composable RowScope.() -> Unit = {
        when {
            ep.hasFile -> Icon(Icons.Filled.CheckCircle, contentDescription = "Downloaded", tint = Emerald500, modifier = Modifier.size(16.dp))
            unaired -> Icon(Icons.Filled.Schedule, contentDescription = "Not yet aired", tint = Amber500, modifier = Modifier.size(16.dp))
            busy -> Icon(Icons.Filled.Download, contentDescription = "Downloading", tint = RenzoColors.Primary, modifier = Modifier.size(16.dp))
            else -> Icon(Icons.Outlined.PlayCircle, contentDescription = "Streams on demand", tint = RenzoColors.MutedForeground, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f).then(if (watched) Modifier.alpha(0.5f) else Modifier)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "E${ep.number}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = RenzoColors.Foreground,
                )
                val name = ep.epTitle?.takeIf { it.isNotBlank() }
                if (name != null) {
                    Text(
                        name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = RenzoColors.MutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp).weight(1f, fill = false),
                    )
                }
                if (busy) {
                    Text(
                        if (pct > 0) "$pct%" else job!!.status,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = RenzoColors.Primary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(RenzoColors.Primary.copy(alpha = 0.15f))
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                }
            }
            Row(Modifier.padding(top = 2.dp)) {
                val dur = durationMin?.takeIf { it > 0 }?.let { " · ${it}m" } ?: ""
                when {
                    unaired -> Text("Not yet aired", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Amber500)
                    ep.hasFile -> Text("On your server · Subtitled$dur", fontSize = 11.sp, color = RenzoColors.MutedForeground, maxLines = 1)
                    busy -> Text("Downloading · ${job!!.status}", fontSize = 11.sp, color = RenzoColors.MutedForeground, maxLines = 1)
                    else -> Text("Streams on demand · Subtitled$dur", fontSize = 11.sp, color = RenzoColors.MutedForeground, maxLines = 1)
                }
                if (watched) Text(" · Watched", fontSize = 11.sp, color = RenzoColors.MutedForeground.copy(alpha = 0.7f), maxLines = 1)
            }
        }
    }

    val actions: @Composable RowScope.() -> Unit = {
        if (!unaired) {
            // Device copy (Shiori: Save offline / phone icon once saved).
            if (savedOffline) {
                SquareIconButton(
                    icon = Icons.Filled.PhoneAndroid,
                    contentDescription = "Saved on this device — remove",
                    tint = Emerald500,
                    borderColor = Color.Transparent,
                    background = Color.Transparent,
                    onClick = onRemoveOffline,
                )
            } else if (ep.hasFile && !isTv) {
                SquareIconButton(
                    icon = Icons.Filled.CloudDownload,
                    contentDescription = "Save to this device",
                    borderColor = Color.Transparent,
                    background = Color.Transparent,
                    onClick = onSaveOffline,
                )
            }
            SquareIconButton(
                icon = if (watched) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (watched) "Mark unwatched" else "Mark watched",
                tint = if (watched) Emerald500 else RenzoColors.MutedForeground.copy(alpha = 0.5f),
                borderColor = Color.Transparent,
                background = Color.Transparent,
                onClick = { onToggleWatched(!watched) },
            )
            // Server-side fetch (NOT a device copy — that is the icon above).
            if (!ep.hasFile && !downloadsDenied && !busy) {
                val smLabel = !isTv && renzoScreenWidthDp() >= 640
                if (smLabel) {
                    RenzoButton(text = "Download", icon = Icons.Filled.Download, variant = "outline", small = true, onClick = { onDownload(false) })
                } else {
                    SquareIconButton(icon = Icons.Filled.Download, contentDescription = "Download", onClick = { onDownload(false) })
                }
            }
        }
    }

    val rowModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp)
        .clip(MaterialTheme.shapes.small)
        .background(RenzoColors.Card.copy(alpha = 0.5f))
        .border(1.dp, Border40, MaterialTheme.shapes.small)
        .then(if (unaired) Modifier.alpha(0.6f) else Modifier.kitClickable(onClick = onPlay))
        .padding(horizontal = 12.dp, vertical = 10.dp)

    if (stacked) {
        Column(rowModifier) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) { leading() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.End),
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            ) { actions() }
        }
    } else {
        Row(rowModifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            leading()
            actions()
        }
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

/** The franchise: this title and its seasons/movies/OVAs, one row each. */
@Composable
private fun SeasonsSection(d: TitleDetail, onOpenTitle: (Int) -> Unit) {
    val chain = remember(d) { orderedSeasons(d) }
    if (chain.size < 2) return
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RailHeading("Seasons", chain.size)
        chain.forEach { ref ->
            val current = ref.id == d.id
            SeasonRow(ref, current = current, upNext = d.nextUp?.id == ref.id, onClick = { if (!current) onOpenTitle(ref.id) })
        }
    }
}

@Composable
private fun SeasonRow(ref: SeasonRef, current: Boolean, upNext: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(if (current) RenzoColors.Primary.copy(alpha = 0.10f) else RenzoColors.Card.copy(alpha = 0.5f))
            .border(1.dp, if (current) RenzoColors.Primary.copy(alpha = 0.5f) else Border40, MaterialTheme.shapes.small)
            .kitClickable(onClick = onClick)
            .padding(8.dp),
    ) {
        AsyncImage(
            model = ref.poster,
            contentDescription = ref.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.width(36.dp).height(54.dp).clip(RoundedCornerShape(6.dp)).background(RenzoColors.Muted),
        )
        Column(Modifier.weight(1f)) {
            Text(
                seasonLabel(ref),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = if (current) RenzoColors.Primary else RenzoColors.Foreground,
                maxLines = 1,
            )
            ref.title?.let {
                Text(it, fontSize = 11.sp, color = RenzoColors.MutedForeground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            current -> Text("Viewing", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = RenzoColors.Primary)
            upNext -> Text(
                "Up next",
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = RenzoColors.Primary,
                modifier = Modifier.clip(PillShape).background(RenzoColors.Primary.copy(alpha = 0.15f)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
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
internal fun seasonLabel(ref: SeasonRef): String {
    val chip = when {
        ref.kind == "movie" -> "Movie"
        ref.kind == "extra" -> when (ref.format?.uppercase(Locale.US)) {
            "MOVIE" -> "Movie"
            "OVA" -> "OVA"
            else -> "Special"
        }
        else -> "Season ${ref.num ?: 1}" + (ref.part?.takeIf { it > 1 }?.let { " Part $it" } ?: "")
    }
    return chip + (ref.year?.let { " · $it" } ?: "")
}
