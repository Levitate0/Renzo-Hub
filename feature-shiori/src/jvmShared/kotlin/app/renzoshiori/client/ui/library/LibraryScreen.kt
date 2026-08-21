package app.renzoshiori.client.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import app.renzoshiori.client.ShioriRuntime
import app.renzoshiori.client.ui.util.screenWidthDp
import app.renzoshiori.client.data.model.LibraryRowDto
import app.renzoshiori.client.data.model.SeriesStatus
import app.renzoshiori.client.data.network.absoluteUrl
import app.renzoshiori.client.data.offline.OfflineRepository
import app.renzoshiori.client.ui.browse.AddSeriesSheet
import app.renzoshiori.client.ui.components.RibbonSelect
import app.renzoshiori.client.ui.components.RibbonToggleChip
import app.renzoshiori.client.ui.components.SelectOption
import app.renzoshiori.client.ui.components.TvSearchBar
import app.renzoshiori.client.ui.queue.parseUtcMillis
import app.renzoshiori.client.ui.series.flagForLanguage
import app.renzoshiori.client.ui.theme.RenzoColors
import app.renzoshiori.client.ui.tv.LocalIsTv
import app.renzoshiori.client.ui.tv.focusRing
import app.renzoshiori.client.ui.tv.rememberFocusState
import app.renzoshiori.client.ui.tv.tvClickable
import app.renzoshiori.client.ui.util.AdultFilter
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------------------
// Card sizes — the web's cardWidthOptions (XS/S/M/L/XL): same order, same
// labels, same widths (CSS px → dp) and the same per-size text scale.
// ---------------------------------------------------------------------------

private data class CardSize(
    val value: String,
    val label: String,
    val width: Dp,
    val title: TextUnit,
    val badge: TextUnit,
    val pauseDot: Dp,
    val pauseGlyph: Dp,
)

private val CARD_SIZES = listOf(
    CardSize("w-20", "XS", 80.dp, 6.4.sp, 6.4.sp, 12.dp, 6.dp),
    CardSize("w-32", "S", 128.dp, 12.sp, 12.sp, 16.dp, 8.dp),
    CardSize("w-45", "M", 180.dp, 14.sp, 12.sp, 20.dp, 10.dp),
    CardSize("w-58", "L", 232.dp, 16.sp, 14.sp, 24.dp, 12.dp),
    CardSize("w-70", "XL", 280.dp, 18.sp, 16.sp, 28.dp, 14.dp),
)

// Unconfigured defaults, per platform (user direction 2026-08-15): a phone
// opens on S, a desktop window on M, a couch-distance TV on L. Only the
// fallback varies — once the user picks a size it's persisted and wins.
private const val PHONE_CARD_SIZE = "w-32"
private const val DESKTOP_CARD_SIZE = "w-45"
private const val TV_CARD_SIZE = "w-58"

/** The persisted card-size choice — survives restarts on every platform. */
private const val CARD_WIDTH_PREFS = "renzo_prefs"
private const val CARD_WIDTH_KEY = "renzo_card_width"

/**
 * The persisted Library card size. Browse reads it too — one size choice
 * governs both grids (user direction 2026-08-14; Browse has no size dropdown
 * of its own).
 */
internal fun persistedCardWidth(isTv: Boolean): String =
    top.levitatemedia.renzo.hub.core.keyValuePrefs(CARD_WIDTH_PREFS).getString(CARD_WIDTH_KEY, null)
        ?: when {
            isTv -> TV_CARD_SIZE
            top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop -> DESKTOP_CARD_SIZE
            else -> PHONE_CARD_SIZE
        }

private fun cardSizeOf(value: String): CardSize =
    CARD_SIZES.firstOrNull { it.value == value } ?: CARD_SIZES[1]

/**
 * The unified Library content — the same view serves the live server library
 * and the on-device offline one, switched by the Online/Offline pill hosted
 * in HomeShell's top bar (which owns this screen's ViewModel so the pill and
 * the grid share state).
 *
 * Transliterated from RenzoFrontend src/app/library/page.tsx: the whole
 * contextual ribbon (status / categories / favourites / genres / sources /
 * My-library / sort / card size / Track all / Add Series) plus the ListSeries
 * card grid, with the desktop ribbon row becoming a horizontally scrollable
 * strip on the phone.
 */
@Composable
fun LibraryContent(
    vm: LibraryViewModel,
    onOpenSeries: (seriesId: String) -> Unit,
    onOpenOfflineSeries: (seriesId: String) -> Unit,
) {
    val state by vm.state.collectAsState()
    val isTv = LocalIsTv.current

    var statusFilter by rememberSaveable { mutableStateOf("all") }
    var selectedGenre by rememberSaveable { mutableStateOf("__ALL__") }
    var selectedProvider by rememberSaveable { mutableStateOf("__ALL__") }
    var selectedCategory by rememberSaveable { mutableStateOf("__ALL__") }
    var selectedFavList by rememberSaveable { mutableStateOf("__ALL__") }
    var orderBy by rememberSaveable { mutableStateOf("title") }
    // Default M; a television starts at L (an M card is unreadable across a
    // room). Whatever the user picks is persisted and wins on the next launch.
    var cardWidth by rememberSaveable {
        mutableStateOf(persistedCardWidth(isTv))
    }
    var addSeriesOpen by rememberSaveable { mutableStateOf(false) }

    val hideAdult = AdultFilter.isHidden()

    // The series the grid actually renders. Every filter option and tab count
    // in the ribbon derives from this rather than state.series — otherwise the
    // ribbon advertises genres, sources and counts belonging to series you
    // cannot see, and picking one of them lands you on an empty grid.
    val visibleSeries = remember(state.series, hideAdult) {
        if (hideAdult) {
            state.series.filter { !AdultFilter.isAdultItem(it.isNsfw, it.genre) }
        } else {
            state.series
        }
    }

    // Search lives in the shell's command bar (like the web app) — this view
    // only renders the ribbon + grid.
    //
    // Except on TV: the command bar's 176dp field is a poor D-pad target and
    // sits behind the shell's chrome, so the library gets its own full-width
    // search row (with voice, where the set has a recogniser). It commits on the
    // IME Search action rather than per keystroke — a remote's IME makes every
    // character expensive, and re-filtering mid-word is just noise.
    Column(modifier = Modifier.fillMaxSize()) {
        if (isTv) {
            var draft by rememberSaveable { mutableStateOf(state.searchTerm) }
            // The shell's command bar writes the same term; follow it so the two
            // fields never disagree about what's being searched.
            LaunchedEffect(state.searchTerm) {
                if (state.searchTerm != draft.trim()) draft = state.searchTerm
            }
            TvSearchBar(
                value = draft,
                onValueChange = { draft = it },
                onSubmit = { vm.setSearch(it.trim()) },
                placeholder = "Search your library…",
                voicePrompt = "Say a series title",
            )
        }

        if (!state.offlineMode) {
            LibraryRibbon(
                state = state,
                visibleSeries = visibleSeries,
                statusFilter = statusFilter,
                onStatusFilter = { statusFilter = it },
                selectedGenre = selectedGenre,
                onGenre = { selectedGenre = it },
                selectedProvider = selectedProvider,
                onProvider = { selectedProvider = it },
                selectedCategory = selectedCategory,
                onCategory = { selectedCategory = it },
                selectedFavList = selectedFavList,
                onFavList = { selectedFavList = it },
                orderBy = orderBy,
                onOrderBy = { orderBy = it },
                cardWidth = cardWidth,
                onCardWidth = {
                    cardWidth = it
                    top.levitatemedia.renzo.hub.core.keyValuePrefs(CARD_WIDTH_PREFS).putString(CARD_WIDTH_KEY, it)
                },
                onToggleViewAll = { vm.setViewAllLibraries(!state.viewAllLibraries) },
                onTrackAll = vm::trackAll,
                onAddSeries = { addSeriesOpen = true },
                hideAdult = hideAdult,
            )
        }

        // Track-all confirmation / failure line (the web's sonner toast).
        val toast = state.toast
        if (toast != null) {
            LaunchedEffect(toast) {
                delay(4000)
                vm.clearToast()
            }
            Text(
                toast,
                style = MaterialTheme.typography.bodySmall,
                color = RenzoColors.Primary,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RenzoColors.Primary.copy(alpha = 0.10f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RenzoColors.Primary)
            }
            state.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(state.error!!, color = MaterialTheme.colorScheme.error)
            }
            state.offlineMode -> OfflineGrid(state, cardWidth, onOpenOfflineSeries)
            else -> OnlineGrid(
                state = state,
                visibleSeries = visibleSeries,
                baseUrl = vm.baseUrl,
                statusFilter = statusFilter,
                selectedGenre = selectedGenre,
                selectedProvider = selectedProvider,
                selectedCategory = selectedCategory,
                favoriteFilterIds = favoriteFilterIds(state, selectedFavList),
                orderBy = orderBy,
                cardWidth = cardWidth,
                onOpenSeries = onOpenSeries,
            )
        }
    }

    if (addSeriesOpen) {
        AddSeriesSheet(
            initialTitle = null,
            canAddSeries = state.canAddSeries,
            onDismiss = { addSeriesOpen = false },
            onAdded = {
                addSeriesOpen = false
                vm.refresh()
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Ribbon
// ---------------------------------------------------------------------------

/** Membership set for the currently selected favourites entry (page.tsx). */
private fun favoriteFilterIds(state: LibraryUiState, selected: String): Set<String>? {
    if (selected == "__ALL__") return null
    val list = state.favoriteLists.firstOrNull { it.id == selected } ?: return null
    val ids = list.seriesIds.toMutableSet()
    if (list.parentId == null) {
        state.favoriteLists.filter { it.parentId == list.id }.forEach { ids.addAll(it.seriesIds) }
    }
    return ids
}

/**
 * The ribbon container: at lg the row fits and the action cluster is pushed
 * to the right edge (weight spacer, no scroll), exactly like the web ribbon;
 * everywhere else — phones AND TV — the row scrolls with edge hints.
 */
@Composable
private fun RibbonRow(
    wide: Boolean,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    if (wide) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .focusGroup(),
            content = content,
        )
    } else {
        app.renzoshiori.client.ui.components.EdgeHintScrollRow(
            modifier = Modifier.fillMaxWidth(),
            contentModifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

@Composable
private fun LibraryRibbon(
    state: LibraryUiState,
    /** What the grid renders (18+ already dropped when hidden) — the option
     *  lists and counts must describe THIS set, not state.series. */
    visibleSeries: List<LibraryRowDto>,
    statusFilter: String,
    onStatusFilter: (String) -> Unit,
    selectedGenre: String,
    onGenre: (String) -> Unit,
    selectedProvider: String,
    onProvider: (String) -> Unit,
    selectedCategory: String,
    onCategory: (String) -> Unit,
    selectedFavList: String,
    onFavList: (String) -> Unit,
    orderBy: String,
    onOrderBy: (String) -> Unit,
    cardWidth: String,
    onCardWidth: (String) -> Unit,
    onToggleViewAll: () -> Unit,
    onTrackAll: () -> Unit,
    onAddSeries: () -> Unit,
    hideAdult: Boolean,
) {
    val favIds = favoriteFilterIds(state, selectedFavList)

    // Live counts per status tab with genre/provider/category/favourites
    // already applied — the web's baseFilter, verbatim. Derived from the
    // VISIBLE set (hideAdult kept as an explicit key even though visibleSeries
    // covers it — these blocks are read far more often than they are edited).
    val counts = remember(visibleSeries, hideAdult, selectedGenre, selectedProvider, selectedCategory, selectedFavList) {
        val base = visibleSeries.filter { s ->
            (selectedGenre == "__ALL__" || s.genre.contains(selectedGenre)) &&
                (selectedProvider == "__ALL__" || s.providers.any { it.provider == selectedProvider }) &&
                (selectedCategory == "__ALL__" || s.category == selectedCategory) &&
                (favIds == null || favIds.contains(s.id))
        }
        mapOf(
            "all" to base.size,
            "active" to base.count {
                it.status != SeriesStatus.COMPLETED && it.status != SeriesStatus.PUBLISHING_FINISHED &&
                    it.isActive && !it.pausedDownloads
            },
            "paused" to base.count { it.pausedDownloads },
            "unassigned" to base.count { it.hasUnknown },
            "completed" to base.count {
                it.status == SeriesStatus.COMPLETED || it.status == SeriesStatus.PUBLISHING_FINISHED
            },
        )
    }

    val genres = remember(visibleSeries, hideAdult) {
        visibleSeries.flatMap { it.genre }
            .filter { it.isNotBlank() }
            // Belt-and-braces on top of visibleSeries: a series carrying an
            // 18+ tag the server never flagged is exactly the case the toggle
            // exists for. The web keeps both layers too.
            .filter { !hideAdult || !AdultFilter.isAdultTag(it) }
            .distinct()
            .sortedBy { it.lowercase() }
    }
    val providers = remember(visibleSeries, hideAdult) {
        visibleSeries.flatMap { it.providers }.map { it.provider }
            .filter { it.isNotBlank() }.distinct().sortedBy { it.lowercase() }
    }

    // Drop a selection the ribbon no longer offers. Both persist across
    // process death via rememberSaveable, so without this, hiding 18+ while
    // "Hentai" (or a source whose only titles were adult) is selected leaves
    // the filter silently applied to an option that is no longer in its own
    // dropdown: the grid comes up empty and the select shows its placeholder,
    // with nothing on screen to undo it. The isNotEmpty() guard is
    // load-bearing: state.series is empty while the library loads, and
    // without it every cold start would reset a valid saved filter to "All".
    LaunchedEffect(genres, providers) {
        if (genres.isNotEmpty() && selectedGenre != "__ALL__" && selectedGenre !in genres) {
            onGenre("__ALL__")
        }
        if (providers.isNotEmpty() && selectedProvider != "__ALL__" && selectedProvider !in providers) {
            onProvider("__ALL__")
        }
    }
    val categories = remember(state.settings) {
        (state.settings?.categories ?: emptyList()).filter { it.isNotBlank() }.sortedBy { it.lowercase() }
    }

    // Favourites entries: each top-level tab followed by its indented sub-lists;
    // a tab's count aggregates its own series plus every sub-list's.
    val favoriteOptions = remember(state.favoriteLists) {
        val lists = state.favoriteLists
        val out = ArrayList<SelectOption>()
        lists.filter { it.parentId == null }.sortedBy { it.sortOrder }.forEach { tab ->
            val children = lists.filter { it.parentId == tab.id }.sortedBy { it.sortOrder }
            val aggregate = HashSet(tab.seriesIds)
            children.forEach { aggregate.addAll(it.seriesIds) }
            out.add(SelectOption(tab.id, tab.name, count = aggregate.size))
            children.forEach { out.add(SelectOption(it.id, it.name, count = it.seriesIds.size, indented = true)) }
        }
        out
    }

    // Web wrapper widths per select (page.tsx: w-36 sm:w-44 etc.) — the sm
    // breakpoint is 640px.
    val sm = screenWidthDp() >= 640.dp
    // TV is NOT wide here (user direction 2026-08-21): the fitted row packed
    // every control too tightly, so the set scrolls the ribbon instead —
    // with edge hints saying so (EdgeHintScrollRow).
    val isTvRibbon = LocalIsTv.current
    val ribbonWide = !isTvRibbon && screenWidthDp() >= 1024.dp
    RibbonRow(
        wide = ribbonWide,
    ) {
        // Status filter — status-colored dots + live count badges.
        RibbonSelect(
            options = listOf(
                SelectOption("all", "All", dotColor = Color.White.copy(alpha = 0.6f), count = counts["all"]),
                SelectOption("active", "Active", dotColor = Color(0xFF22C55E), count = counts["active"]),
                SelectOption("paused", "Paused", dotColor = Color(0xFFEAB308), count = counts["paused"]),
                SelectOption("unassigned", "Unassigned", dotColor = Color(0xFFF59E0B), count = counts["unassigned"]),
                SelectOption("completed", "Completed", dotColor = Color(0xFF3B82F6), count = counts["completed"]),
            ),
            value = statusFilter,
            onChange = onStatusFilter,
            triggerWidth = if (sm) 176.dp else 144.dp,
        )

        // Categories — only when categorized folders are enabled in settings.
        if (state.settings?.categorizedFolders == true) {
            RibbonSelect(
                options = listOf(SelectOption("__ALL__", "All Categories")) +
                    categories.map { SelectOption(it, it) },
                value = selectedCategory,
                onChange = onCategory,
                placeholder = "All Categories",
                triggerWidth = if (sm) 160.dp else 128.dp,
            )
        }

        // Favourites — hidden until the user creates their first list.
        if (favoriteOptions.isNotEmpty()) {
            RibbonSelect(
                options = listOf(SelectOption("__ALL__", "Favourites: All")) + favoriteOptions,
                value = selectedFavList,
                onChange = onFavList,
                placeholder = "Favourites",
                triggerWidth = if (sm) 176.dp else 128.dp,
            )
        }

        // Genres.
        RibbonSelect(
            options = listOf(SelectOption("__ALL__", "All Genres")) + genres.map { SelectOption(it, it) },
            value = selectedGenre,
            onChange = onGenre,
            placeholder = "All Genres",
            triggerWidth = if (sm) 160.dp else 128.dp,
        )

        // Sources.
        RibbonSelect(
            options = listOf(SelectOption("__ALL__", "All Sources")) + providers.map { SelectOption(it, it) },
            value = selectedProvider,
            onChange = onProvider,
            placeholder = "All Sources",
            triggerWidth = if (sm) 192.dp else 128.dp,
        )

        // Right cluster: My library / sort / card size / Track all / Add Series —
        // right-aligned on a wide window (the web pushes it with ml-auto).
        if (ribbonWide) Spacer(Modifier.weight(1f))
        if (state.canOwner) {
            RibbonToggleChip(
                label = if (state.viewAllLibraries) "All libraries" else "My library",
                active = state.viewAllLibraries,
                onClick = onToggleViewAll,
            )
        }

        RibbonSelect(
            options = listOf(
                SelectOption("title", "Alphabetical"),
                SelectOption("lastChange", "Last Change"),
            ),
            value = orderBy,
            onChange = onOrderBy,
            triggerWidth = if (sm) 128.dp else 112.dp,
        )

        RibbonSelect(
            options = CARD_SIZES.map { SelectOption(it.value, it.label) },
            value = cardWidth,
            onChange = onCardWidth,
            placeholder = "Card Size",
            maxTriggerWidth = 32.dp,
            triggerWidth = if (sm) 64.dp else 56.dp,
        )

        // Track all — self-hides when no tracker is connected, like the web.
        if (state.connectedTrackers.isNotEmpty()) {
            RibbonToggleChip(
                label = "Track all",
                active = state.trackingAll,
                onClick = onTrackAll,
                icon = Icons.Filled.PlaylistAddCheck,
            )
        }

        // Add Series — relabelled "Request Series" below Manager, exactly as the
        // web relabels the same always-available button.
        val isTv = LocalIsTv.current
        val addFocus = rememberFocusState()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(if (isTv) 40.dp else 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RenzoColors.Primary)
                .then(
                    if (isTv) {
                        Modifier
                            .focusRing(addFocus.focused, 8.dp)
                            .tvClickable(onFocused = addFocus::set, onClick = onAddSeries)
                    } else {
                        Modifier.clickable(onClick = onAddSeries)
                    },
                )
                .padding(horizontal = 12.dp),
        ) {
            Icon(
                Icons.Filled.AddCircle,
                contentDescription = null,
                tint = RenzoColors.PrimaryForeground,
                modifier = Modifier.size(16.dp),
            )
            Text(
                if (state.canAddSeries) "Add Series" else "Request Series",
                style = MaterialTheme.typography.labelMedium,
                color = RenzoColors.PrimaryForeground,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Online grid
// ---------------------------------------------------------------------------

@Composable
private fun OnlineGrid(
    state: LibraryUiState,
    /** ONE place decides what's visible: the 18+ cut happens in
     *  LibraryContent's visibleSeries, shared with the ribbon. */
    visibleSeries: List<LibraryRowDto>,
    baseUrl: String,
    statusFilter: String,
    selectedGenre: String,
    selectedProvider: String,
    selectedCategory: String,
    favoriteFilterIds: Set<String>?,
    orderBy: String,
    cardWidth: String,
    onOpenSeries: (String) -> Unit,
) {
    val size = cardSizeOf(cardWidth)
    val search = state.searchTerm.trim()

    // ListSeries' own search filter then the page's filterFn/sortFn.
    val filtered = remember(
        visibleSeries, search, statusFilter, selectedGenre, selectedProvider,
        selectedCategory, favoriteFilterIds, orderBy,
    ) {
        visibleSeries
            .filter { search.isEmpty() || it.title.contains(search, ignoreCase = true) }
            .filter { s ->
                val matchesTab = when (statusFilter) {
                    "completed" -> s.status == SeriesStatus.COMPLETED || s.status == SeriesStatus.PUBLISHING_FINISHED
                    "active" -> s.status != SeriesStatus.COMPLETED && s.status != SeriesStatus.PUBLISHING_FINISHED &&
                        s.isActive && !s.pausedDownloads
                    "paused" -> s.pausedDownloads
                    "unassigned" -> s.hasUnknown
                    else -> true
                }
                val matchesGenre = selectedGenre == "__ALL__" || s.genre.contains(selectedGenre)
                val matchesProvider =
                    selectedProvider == "__ALL__" || s.providers.any { it.provider == selectedProvider }
                val matchesCategory = selectedCategory == "__ALL__" || s.category == selectedCategory
                val matchesFavorites = favoriteFilterIds == null || favoriteFilterIds.contains(s.id)
                matchesTab && matchesGenre && matchesProvider && matchesCategory && matchesFavorites
            }
            .let { list ->
                if (orderBy == "lastChange") {
                    list.sortedByDescending { it.lastChangeUTC?.let(::parseUtcMillis) ?: 0L }
                } else {
                    list.sortedBy { it.title.lowercase() }
                }
            }
    }

    if (filtered.isEmpty()) {
        EmptyLibraryState(search)
        return
    }

    val wide = screenWidthDp() >= 1024.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = size.width),
        // Web library page: `p-2 pb-16 sm:px-6 sm:py-4` with a gap-4 grid —
        // desktop gets real edge zones and air between cards, phones keep the
        // tight packing.
        contentPadding = if (wide) {
            PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
        } else {
            PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 64.dp)
        },
        horizontalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(filtered, key = { it.id }) { series ->
            val effectiveStatus = if (series.isActive) series.status else SERIES_STATUS_DISABLED
            SeriesCard(
                title = series.title,
                coverUrl = series.thumbnailUrl.takeIf { it.isNotBlank() }?.let { absoluteUrl(baseUrl, it) },
                size = size,
                // The status strip is hidden while sorting by Last Change: it
                // clashes with the age-graded card border (web comment).
                statusColor = if (orderBy == "lastChange") null else getStatusDisplay(effectiveStatus).color,
                ringColor = if (orderBy == "lastChange") lastChangeRingColor(series.lastChangeUTC) else null,
                providerBadge = series.lastChangeProvider?.provider,
                lastChapter = series.lastChapter,
                lastChapterColor = getStatusDisplay(effectiveStatus).color,
                paused = series.pausedDownloads,
                hasUnknown = series.hasUnknown,
                // Desktop hover preview (list-series/index.tsx Tooltip) needs
                // the full row: author/artist/genres/description/providers.
                preview = series,
                onClick = { onOpenSeries(series.id) },
            )
        }
    }
}

/** ListSeries' empty / no-results block, wording verbatim. */
@Composable
private fun EmptyLibraryState(search: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize().padding(24.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(80.dp).clip(CircleShape).background(RenzoColors.Muted),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.MenuBook,
                contentDescription = null,
                tint = RenzoColors.MutedForeground,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            if (search.isNotEmpty()) "No results for \"$search\"" else "No series found",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = RenzoColors.Foreground,
            textAlign = TextAlign.Center,
        )
        Text(
            if (search.isNotEmpty()) "Try a different search term." else "Add some manga to get started.",
            style = MaterialTheme.typography.bodyMedium,
            color = RenzoColors.MutedForeground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// ---------------------------------------------------------------------------
// Offline grid (offline-library-grid.tsx)
// ---------------------------------------------------------------------------

@Composable
private fun OfflineGrid(
    state: LibraryUiState,
    cardWidth: String,
    onOpenOfflineSeries: (String) -> Unit,
) {
    val size = cardSizeOf(cardWidth)
    val search = state.searchTerm.trim()
    val filtered = state.offlineSeries.filter {
        search.isEmpty() || it.title.contains(search, ignoreCase = true)
    }

    if (filtered.isEmpty()) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize().padding(24.dp),
        ) {
            Icon(
                Icons.Filled.WifiOff,
                contentDescription = null,
                tint = RenzoColors.MutedForeground.copy(alpha = 0.5f),
                modifier = Modifier.size(32.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Nothing saved offline yet",
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                color = RenzoColors.MutedForeground,
            )
            Text(
                "Open a series and tap \"Save offline\" on a chapter to read it without a connection.",
                style = MaterialTheme.typography.bodySmall,
                color = RenzoColors.MutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp),
            )
        }
        return
    }

    val wide = screenWidthDp() >= 1024.dp
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = size.width),
        // Web library page: `p-2 pb-16 sm:px-6 sm:py-4` with a gap-4 grid —
        // desktop gets real edge zones and air between cards, phones keep the
        // tight packing.
        contentPadding = if (wide) {
            PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 24.dp)
        } else {
            PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 64.dp)
        },
        horizontalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 8.dp),
        verticalArrangement = Arrangement.spacedBy(if (wide) 16.dp else 8.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(filtered, key = { it.seriesId }) { series ->
            OfflineSeriesCard(series, size) { onOpenOfflineSeries(series.seriesId) }
        }
    }
}

@Composable
private fun OfflineSeriesCard(
    series: OfflineRepository.OfflineSeries,
    size: CardSize,
    onClick: () -> Unit,
) {
    val renzoApp = ShioriRuntime.app
    var cover by remember(series.seriesId) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(series.coverPath) {
        val path = series.coverPath
        cover = if (path == null) null else {
            withContext(Dispatchers.IO) { runCatching { renzoApp.offline.readPage(path) }.getOrNull() }
        }
    }

    val isTv = LocalIsTv.current
    TvFocusTile(onClick = onClick) {
    Column(modifier = if (isTv) Modifier else Modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(RenzoColors.Muted),
        ) {
            val bytes = cover
            if (bytes != null) {
                AsyncImage(
                    model = bytes,
                    contentDescription = series.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.WifiOff,
                    contentDescription = null,
                    tint = RenzoColors.MutedForeground.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.Center).size(24.dp),
                )
            }
            Text(
                series.title,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = size.title,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color.White,
                // The web card never clamps its title (see the old exe): the
                // overlay grows with the name. Phones keep the 2-line clamp.
                maxLines = if (screenWidthDp() >= 1024.dp) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Text(
            "${series.chapterCount} ch · ${formatBytes(series.bytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = RenzoColors.MutedForeground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
    }
}

// ---------------------------------------------------------------------------
// Card
// ---------------------------------------------------------------------------

/**
 * The web ListSeries card, cloned: 2/3 cover, a 2px status strip across the top
 * edge, provider badge top-left (black/70), the status-colored last-chapter
 * badge top-RIGHT, an amber attention dot for unassigned providers, the yellow
 * pause glyph riding the title strip, and the centered semibold title bar
 * (black/60) along the bottom. When sorting by Last Change the whole card gets
 * the age-graded 1.5px border instead of the status strip.
 */
@Composable
private fun SeriesCard(
    title: String,
    coverUrl: String?,
    size: CardSize,
    statusColor: Color?,
    ringColor: Color?,
    providerBadge: String?,
    lastChapter: Double?,
    lastChapterColor: Color,
    paused: Boolean,
    hasUnknown: Boolean,
    preview: LibraryRowDto? = null,
    onClick: () -> Unit,
) {
    val isTv = LocalIsTv.current

    // Hover preview — desktop pointer only (the web Tooltip in
    // list-series/index.tsx). Hover doesn't exist on touch, and TV never gets
    // the hoverable modifier, so phone/TV behavior is untouched.
    val previewEnabled = preview != null && !isTv && screenWidthDp() >= 1024.dp
    val hoverSource = remember { MutableInteractionSource() }
    val hovered by hoverSource.collectIsHoveredAsState()
    var previewVisible by remember { mutableStateOf(false) }
    LaunchedEffect(hovered, previewEnabled) {
        if (hovered && previewEnabled) {
            delay(500) // the Tooltip's open delay; hide is immediate
            previewVisible = true
        } else {
            previewVisible = false
        }
    }

    TvFocusTile(onClick = onClick) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .then(
                    if (ringColor != null) {
                        Modifier
                            .border(1.5.dp, ringColor, RoundedCornerShape(6.dp))
                            .padding(1.5.dp)
                    } else {
                        Modifier
                    },
                )
                .clip(RoundedCornerShape(6.dp))
                .background(RenzoColors.Muted)
                .then(if (previewEnabled) Modifier.hoverable(hoverSource) else Modifier)
                // On TV the wrapper owns the click (it owns the focus ring too).
                .then(if (isTv) Modifier else Modifier.clickable(onClick = onClick)),
        ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 2px status strip across the top edge.
        if (statusColor != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(statusColor),
            )
        }

        // Provider badge — top-left.
        if (!providerBadge.isNullOrBlank()) {
            Text(
                providerBadge,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = size.badge,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        // Last-chapter badge — top-right, filled with the status color.
        if (lastChapter != null) {
            Text(
                formatChapter(lastChapter),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = size.badge,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(lastChapterColor)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        // Attention dot — this series has unassigned providers needing a match.
        if (hasUnknown) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 28.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF59E0B)),
            ) {
                Icon(
                    Icons.Filled.Warning,
                    contentDescription = "Has unassigned providers",
                    tint = Color(0xFFFFFBEB),
                    modifier = Modifier.size(10.dp),
                )
            }
        }

        // Paused indicator — yellow circle riding just above the title strip.
        if (paused) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 4.dp, bottom = 26.dp)
                    .size(size.pauseDot)
                    .clip(CircleShape)
                    .background(Color(0xFFEAB308)),
            ) {
                Icon(
                    Icons.Filled.Pause,
                    contentDescription = "Downloads paused",
                    tint = Color.Black,
                    modifier = Modifier.size(size.pauseGlyph),
                )
            }
        }

        // Title bar.
        Text(
            title,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = size.title,
                fontWeight = FontWeight.SemiBold,
            ),
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

        // Hover preview panel — anchored to this card, shown beside it.
        if (previewVisible && preview != null) {
            SeriesHoverPreview(preview)
        }
        }
    }
}

/**
 * The library card's hover tooltip, cloned from list-series/index.tsx
 * (TooltipContent side="right"): last-chapter badge + bold title with the
 * status badge on the right, "by author" / "art by artist", the genre chips,
 * a 4-line-clamped description, and the provider chips (external-link +
 * name • scanlator + language flag + green storage icon). Anchored to the
 * card it decorates; rendered in a non-focusable Popup so it never steals
 * clicks or keyboard focus from the grid.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SeriesHoverPreview(series: LibraryRowDto) {
    val density = LocalDensity.current
    // side="right" with collision handling: the panel sits 8dp to the right of
    // the card and flips to the left near the window edge — always beside the
    // pointer, never under it, so hovering can't flicker.
    val positionProvider = remember(density) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize,
            ): IntOffset {
                val gap = with(density) { 8.dp.roundToPx() }
                var x = anchorBounds.right + gap
                if (x + popupContentSize.width > windowSize.width) {
                    x = anchorBounds.left - gap - popupContentSize.width
                }
                val y = anchorBounds.top
                    .coerceAtMost(windowSize.height - popupContentSize.height)
                    .coerceAtLeast(0)
                return IntOffset(x, y)
            }
        }
    }

    Popup(
        popupPositionProvider = positionProvider,
        properties = PopupProperties(focusable = false),
    ) {
        val status = getStatusDisplay(series.status)
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RenzoColors.Card)
                .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
                .padding(16.dp),
        ) {
            // Header row: last-chapter badge + title; status badge top-right.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val lastChapter = series.lastChapter
                if (lastChapter != null) {
                    Text(
                        formatChapter(lastChapter),
                        style = MaterialTheme.typography.labelSmall,
                        color = RenzoColors.Foreground,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(RenzoColors.Secondary)
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
                Text(
                    series.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = RenzoColors.Primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    status.text,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(status.color)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }

            // by {author} / art by {artist} — artist only when distinct.
            val author = series.author?.takeIf { it.isNotBlank() }
            val artist = series.artist?.takeIf { it.isNotBlank() }
            if (author != null || artist != null) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (author != null) {
                        Text(
                            "by $author",
                            style = MaterialTheme.typography.bodySmall,
                            color = RenzoColors.MutedForeground,
                        )
                    }
                    if (artist != null && artist != author) {
                        Text(
                            "art by $artist",
                            style = MaterialTheme.typography.bodySmall,
                            color = RenzoColors.MutedForeground,
                        )
                    }
                }
            }

            // Genre tag chips (the web's DynamicTags).
            val genres = series.genre.filter { it.isNotBlank() }
            if (genres.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    genres.forEach { genre ->
                        Text(
                            genre,
                            style = MaterialTheme.typography.labelSmall,
                            color = RenzoColors.MutedForeground,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(RenzoColors.Secondary)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
            }

            // Description — line-clamp-4, with the web's fallback wording.
            Text(
                series.description?.takeIf { it.isNotBlank() } ?: "No description available",
                style = MaterialTheme.typography.bodySmall,
                color = RenzoColors.MutedForeground,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )

            // Provider chips — link icon when a source url exists, language
            // flag, and the green storage marker for the permanent provider.
            if (series.providers.isNotEmpty()) {
                val uriHandler = LocalUriHandler.current
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    series.providers.forEach { p ->
                        val url = p.url?.takeIf { it.isNotBlank() }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, RenzoColors.Border, RoundedCornerShape(4.dp))
                                .background(RenzoColors.Secondary)
                                .then(
                                    if (url != null) {
                                        Modifier.clickable { app.renzoshiori.client.ui.util.openSourceUrl(uriHandler, url) }
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            if (url != null) {
                                Icon(
                                    Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open in the source",
                                    tint = RenzoColors.Foreground,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                            Text(
                                if (p.scanlator.isNotBlank() && p.scanlator != p.provider) {
                                    "${p.provider} • ${p.scanlator}"
                                } else {
                                    p.provider
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = RenzoColors.Foreground,
                                maxLines = 1,
                            )
                            if (p.language.isNotBlank()) {
                                Text(
                                    flagForLanguage(p.language),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            if (p.isStorage) {
                                Icon(
                                    Icons.Filled.Storage,
                                    contentDescription = "Stored permanently",
                                    tint = RenzoColors.Green,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Focus wrapper for a cover tile.
 *
 * The ring can't live on the card itself: a border modifier draws inside the
 * node's bounds and *before* its children, so the full-bleed cover image would
 * paint straight over it. It goes on a wrapper instead, with a 3dp gutter
 * reserved whether focused or not — a tile that grew on focus would reflow its
 * whole row under the cursor. On touch this is a pass-through.
 *
 * Scroll-into-view comes free: `focusable()` (inside `tvClickable`) asks its
 * scrollable parent to bring it into view, so a focused tile in a lazy grid is
 * never stranded off-screen.
 */
@Composable
private fun TvFocusTile(onClick: () -> Unit, content: @Composable () -> Unit) {
    val isTv = LocalIsTv.current
    val focus = rememberFocusState()
    if (!isTv) {
        content()
        return
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .focusRing(focus.focused, 9.dp)
            .tvClickable(onFocused = focus::set, onClick = onClick)
            .padding(3.dp),
    ) {
        content()
    }
}

// ---------------------------------------------------------------------------
// Shared bits used by the shell and the other screens
// ---------------------------------------------------------------------------

@Composable
fun OnlineOfflinePill(offline: Boolean, onToggle: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(
                if (offline) Color(0x26F59E0B) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.06f),
            )
            .clickable(onClick = onToggle)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(if (offline) Color(0xFFF59E0B) else Color(0xFF10B981)),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            if (offline) "Offline" else "Online",
            style = MaterialTheme.typography.labelMedium,
            color = if (offline) Color(0xFFFBBF24) else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            // Never let a squeezed command bar break the word across lines
            // ("Onlin / e") — the pill is one token, like the web's
            // whitespace-nowrap.
            maxLines = 1,
            softWrap = false,
        )
    }
}

fun formatChapter(n: Double): String =
    if (n == n.toLong().toDouble()) n.toLong().toString() else n.toString()

/** formatBytes() from the web downloads page — shared with DownloadsScreen. */
fun formatBytes(n: Long): String {
    if (n < 1024) return "$n B"
    val units = listOf("KB", "MB", "GB")
    var v = n / 1024.0
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024.0
        i++
    }
    return if (v < 10) String.format("%.1f %s", v, units[i]) else String.format("%.0f %s", v, units[i])
}
