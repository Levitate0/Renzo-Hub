package app.renzoshiori.client.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import app.renzoshiori.client.ShioriRuntime
import app.renzoshiori.client.data.model.InLibraryStatus
import app.renzoshiori.client.data.model.LatestGenreDto
import app.renzoshiori.client.data.model.LatestSeriesRowDto
import app.renzoshiori.client.data.model.SearchSourceDto
import app.renzoshiori.client.data.network.BrowseApi
import app.renzoshiori.client.data.network.absoluteUrl
import app.renzoshiori.client.ui.components.RibbonSelect
import app.renzoshiori.client.ui.components.SelectOption
import app.renzoshiori.client.ui.components.TvSearchBar
import app.renzoshiori.client.ui.components.tvFocusTarget
import app.renzoshiori.client.ui.library.LibraryViewModel
import app.renzoshiori.client.ui.library.formatChapter
import app.renzoshiori.client.ui.library.getStatusDisplay
import app.renzoshiori.client.ui.library.persistedCardWidth
import app.renzoshiori.client.ui.theme.RenzoColors
import app.renzoshiori.client.ui.tv.LocalIsTv
import app.renzoshiori.client.ui.tv.focusRing
import app.renzoshiori.client.ui.tv.rememberFocusState
import app.renzoshiori.client.ui.tv.tvClickable
import app.renzoshiori.client.ui.tv.tvContentColor
import app.renzoshiori.client.ui.util.AdultFilter
import app.renzoshiori.client.ui.util.rememberHideAdult
import app.renzoshiori.client.ui.util.screenWidthDp
import coil3.compose.AsyncImage

// ---------------------------------------------------------------------------
// Constants (cloud-latest/page.tsx)
// ---------------------------------------------------------------------------

private const val MAX_VISIBLE_GENRES = 200

private data class BrowseCardSize(val value: String, val label: String, val width: Dp, val title: TextUnit)

private val BROWSE_CARD_SIZES = listOf(
    BrowseCardSize("w-20", "XS", 80.dp, 6.4.sp),
    BrowseCardSize("w-32", "S", 128.dp, 12.sp),
    BrowseCardSize("w-45", "M", 180.dp, 14.sp),
    BrowseCardSize("w-58", "L", 232.dp, 16.sp),
    BrowseCardSize("w-70", "XL", 280.dp, 18.sp),
)

private fun browseCardSizeOf(value: String) =
    BROWSE_CARD_SIZES.firstOrNull { it.value == value } ?: BROWSE_CARD_SIZES[1]

/** Items fetched per page — the web computes this from the viewport; a phone
 *  comfortably fits two screens' worth at 40, the web's own floor. */
private const val ITEMS_PER_PAGE = 40

/**
 * Browse — 1:1 port of RenzoFrontend src/app/cloud-latest/page.tsx: the source
 * picker + tag-filter popover + card-size ribbon, the cinematic spotlight hero
 * over the first page's not-yet-in-library picks, the selected-tag chips with
 * Clear, and the infinite-scrolling catalogue grid whose cards carry the status
 * strip, provider badge, latest-chapter badge and in-library heart. Tapping a
 * card opens the details sheet with Add to Library / View Source.
 */
@Composable
fun BrowseScreen(
    /** "Read" in the details view: preview (mihonId, title) live from the source. */
    onPreviewRead: (String, String) -> Unit = { _, _ -> },
) {
    val renzoApp = ShioriRuntime.app
    val uriHandler = LocalUriHandler.current

    // Shared search context — the shell's command bar writes into LibraryViewModel.
    val libraryVm: LibraryViewModel = viewModel(
        factory = LibraryViewModel.factory(),
    )
    val libraryState by libraryVm.state.collectAsState()
    val searchTerm = libraryState.searchTerm.trim()

    val api = remember { renzoApp.network.currentServiceOf<BrowseApi>() }
    val baseUrl = renzoApp.tokenStore.serverUrl ?: ""
    // Same flag the account menu toggles — Browse is where an unwanted adult
    // cover actually ambushes you (the catalogue is whatever the source
    // returns), so the control belongs in this ribbon too, not three taps away.
    val adultFilter = rememberHideAdult()
    val hideAdult by adultFilter.hidden

    val isTv = LocalIsTv.current
    // lg breakpoint — the web (cloud-latest/page.tsx) portals the tag filter as
    // a popover anchored under its trigger chip on desktop; narrow/TV keep the
    // centered dialog.
    val tagsAsPopover = !isTv && screenWidthDp() >= 1024.dp

    var selectedSourceId by remember { mutableStateOf("__ALL__") }
    // One size choice governs both grids: Browse mirrors the Library's
    // persisted card size and has no size dropdown of its own.
    val cardWidth = remember { persistedCardWidth(isTv) }
    val selectedGenres = remember { mutableStateListOf<String>() }
    // TV search draft — committed on the IME Search action (or a voice result)
    // rather than per keystroke, because every keystroke here is a live query
    // against every enabled source.
    var searchDraft by remember { mutableStateOf(searchTerm) }

    var sources by remember { mutableStateOf<List<SearchSourceDto>>(emptyList()) }
    var genresData by remember { mutableStateOf<List<LatestGenreDto>?>(null) }

    var items by remember { mutableStateOf<List<LatestSeriesRowDto>>(emptyList()) }
    var firstPageItems by remember { mutableStateOf<List<LatestSeriesRowDto>>(emptyList()) }
    var currentPage by remember { mutableStateOf(0) }
    var hasMore by remember { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    var tagPopoverOpen by remember { mutableStateOf(false) }
    var detailsItem by remember { mutableStateOf<LatestSeriesRowDto?>(null) }
    var addSeriesTitle by remember { mutableStateOf<String?>(null) }
    var addSeriesOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        runCatching { api?.searchSources() }.getOrNull()?.let {
            sources = it.sortedBy { s -> s.provider.lowercase() }
        }
        runCatching { api?.latestGenres() }.getOrNull()?.let { genresData = it }
    }

    val genreSignature = selectedGenres.sorted().joinToString("|")

    // Reset pagination when filters change.
    LaunchedEffect(searchTerm, selectedSourceId, genreSignature) {
        items = emptyList()
        firstPageItems = emptyList()
        currentPage = 0
        hasMore = true
    }

    LaunchedEffect(searchTerm, selectedSourceId, genreSignature, currentPage) {
        if (currentPage == 0) isLoading = true else isLoadingMore = true
        error = null
        runCatching {
            api?.latest(
                start = currentPage * ITEMS_PER_PAGE,
                count = ITEMS_PER_PAGE,
                sourceId = selectedSourceId.takeIf { it != "__ALL__" },
                keyword = searchTerm.ifBlank { null },
                genre = selectedGenres.toList().takeIf { it.isNotEmpty() },
            )
        }
            .onSuccess { page ->
                val rows = page.orEmpty()
                if (currentPage == 0) {
                    items = rows
                    // The spotlight pool comes from the FIRST page only, so it
                    // stays stable while the user pages through the grid.
                    firstPageItems = rows
                } else {
                    items = items + rows
                }
                hasMore = rows.size >= ITEMS_PER_PAGE
            }
            .onFailure { error = it.message ?: "Error loading latest series" }
        isLoading = false
        isLoadingMore = false
    }

    // Spotlight pool — first-page rows not already in the library, shuffled,
    // capped at 7. Re-shuffles only when the filters refresh the first page.
    val spotlightItems = remember(firstPageItems, hideAdult) {
        firstPageItems
            .filter { it.inLibrary == InLibraryStatus.NOT_IN_LIBRARY }
            .filter { !hideAdult || !AdultFilter.isAdultItem(it.isNsfw.takeIf { flag -> flag }, it.genre) }
            .shuffled()
            .take(7)
            .map { s ->
                SpotlightItem(
                    id = s.seriesId ?: s.mihonId,
                    title = s.title,
                    author = s.author,
                    description = s.description,
                    thumbnailUrl = s.thumbnailUrl?.let { absoluteUrl(baseUrl, it) },
                    status = s.status,
                    genres = s.genre,
                    availableChapters = s.chapterCount,
                    sourceName = s.provider,
                )
            }
    }

    // Temporary 18+ view filter — purely client-side, pages stay intact.
    val visibleItems = remember(items, hideAdult) {
        if (hideAdult) {
            items.filter { !AdultFilter.isAdultItem(it.isNsfw.takeIf { flag -> flag }, it.genre) }
        } else {
            items
        }
    }

    val size = browseCardSizeOf(cardWidth)
    val gridState = rememberLazyGridState()

    // Infinite scroll — load the next page as the tail comes into view.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = gridState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - 6
        }
    }
    LaunchedEffect(shouldLoadMore, hasMore, isLoading, isLoadingMore) {
        if (shouldLoadMore && hasMore && !isLoading && !isLoadingMore) {
            currentPage += 1
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── TV search ────────────────────────────────────────────────────
        // The shell's command-bar field is a 176dp target behind the chrome —
        // fine with a thumb, hopeless with a D-pad — so discovery carries its
        // own full-width field, plus voice where the set has a recogniser.
        if (isTv) {
            // The shell's command bar writes the same term; follow it so the two
            // fields never disagree about what's being searched.
            LaunchedEffect(searchTerm) {
                if (searchTerm != searchDraft.trim()) searchDraft = searchTerm
            }
            TvSearchBar(
                value = searchDraft,
                onValueChange = { searchDraft = it },
                onSubmit = { libraryVm.setSearch(it.trim()) },
                placeholder = "Search every source…",
                voicePrompt = "Say a series title",
            )
        }

        // ── Ribbon ───────────────────────────────────────────────────────
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            // Source picker.
            RibbonSelect(
                options = listOf(SelectOption("__ALL__", "All Sources", icon = Icons.Filled.Language)) +
                    sources.filter { it.mihonProviderId.isNotBlank() }.map {
                        SelectOption(it.mihonProviderId, it.provider, icon = Icons.Filled.Language)
                    },
                value = selectedSourceId,
                onChange = { selectedSourceId = it },
                placeholder = "All Sources",
            )

            // Tag popover trigger — Box-wrapped so the desktop popover can
            // anchor to the chip (page.tsx tagButtonRef / tagPopoverPos).
            Box {
                val tagFocus = rememberFocusState()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(if (isTv) 40.dp else 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
                        .background(RenzoColors.Card)
                        .then(
                            if (isTv) {
                                Modifier
                                    .focusRing(tagFocus.focused, 8.dp)
                                    .tvClickable(
                                        onFocused = tagFocus::set,
                                        onClick = { tagPopoverOpen = !tagPopoverOpen },
                                    )
                            } else {
                                Modifier.clickable { tagPopoverOpen = !tagPopoverOpen }
                            },
                        )
                        .padding(horizontal = 10.dp),
                ) {
                    Icon(
                        Icons.Filled.LocalOffer,
                        contentDescription = null,
                        tint = RenzoColors.MutedForeground,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        when (selectedGenres.size) {
                            0 -> "Tags"
                            1 -> "Tag: ${selectedGenres[0]}"
                            else -> "Tags · ${selectedGenres.size}"
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = RenzoColors.Foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    if (selectedGenres.isNotEmpty()) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .clip(RoundedCornerShape(50))
                                .background(RenzoColors.Primary)
                                .padding(horizontal = 6.dp, vertical = 1.dp),
                        ) {
                            Text(
                                selectedGenres.size.toString(),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                                color = RenzoColors.PrimaryForeground,
                            )
                        }
                    }
                }

                // Desktop: the tag filter as a popover anchored below the chip —
                // page.tsx TAG_POPOVER_MAX_WIDTH_PX = 352, list max-h-72.
                if (tagsAsPopover) {
                    DropdownMenu(
                        expanded = tagPopoverOpen,
                        onDismissRequest = { tagPopoverOpen = false },
                        containerColor = RenzoColors.Popover,
                        modifier = Modifier.width(352.dp),
                    ) {
                        TagFilterBody(
                            genres = genresData,
                            hideAdult = hideAdult,
                            selected = selectedGenres,
                            listMaxHeight = 288.dp,
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // 18+ visibility — mirrors the account menu's "Adult (18+)" item.
            // The amber fill/border is the *state* (18+ shown) and stays put
            // while the cursor moves; the ring is only ever focus.
            val adultFocus = rememberFocusState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .height(if (isTv) 40.dp else 32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(
                        1.dp,
                        if (hideAdult) RenzoColors.Border else RenzoColors.Amber.copy(alpha = 0.5f),
                        RoundedCornerShape(8.dp),
                    )
                    .background(if (hideAdult) RenzoColors.Card else RenzoColors.Amber.copy(alpha = 0.12f))
                    .then(
                        if (isTv) {
                            Modifier
                                .focusRing(adultFocus.focused, 8.dp)
                                .tvClickable(onFocused = adultFocus::set, onClick = { adultFilter.toggle() })
                        } else {
                            Modifier.clickable { adultFilter.toggle() }
                        },
                    )
                    .padding(horizontal = 10.dp),
            ) {
                Icon(
                    if (hideAdult) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = null,
                    tint = if (hideAdult) RenzoColors.MutedForeground else RenzoColors.Amber,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    if (hideAdult) "18+ Hidden" else "18+ Shown",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hideAdult) RenzoColors.Foreground else RenzoColors.Amber,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        // Selected-tag chips + Clear.
        if (selectedGenres.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                selectedGenres.toList().forEach { name ->
                    // On TV the whole chip is one target that removes the tag —
                    // a 14dp close glyph is not something a D-pad can aim at.
                    val chipFocus = rememberFocusState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(RenzoColors.Secondary.copy(alpha = 0.7f))
                            .then(
                                if (isTv) {
                                    Modifier
                                        .focusRing(chipFocus.focused, 50.dp)
                                        .tvClickable(
                                            onFocused = chipFocus::set,
                                            onClick = { selectedGenres.remove(name) },
                                        )
                                } else {
                                    Modifier
                                },
                            )
                            .padding(start = 10.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
                    ) {
                        Text(
                            name,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isTv && chipFocus.focused) RenzoColors.Primary else RenzoColors.Foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Remove $name",
                            tint = RenzoColors.MutedForeground,
                            modifier = Modifier
                                .padding(start = 4.dp)
                                .size(14.dp)
                                .then(
                                    if (isTv) Modifier else Modifier.clickable { selectedGenres.remove(name) },
                                ),
                        )
                    }
                }
                val clearFocus = rememberFocusState()
                Text(
                    "Clear",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isTv && clearFocus.focused) RenzoColors.Primary else RenzoColors.MutedForeground,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (isTv) {
                                Modifier
                                    .focusRing(clearFocus.focused, 4.dp)
                                    .tvClickable(
                                        onFocused = clearFocus::set,
                                        onClick = { selectedGenres.clear() },
                                    )
                            } else {
                                Modifier.clickable { selectedGenres.clear() }
                            },
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }

        // ── Body ─────────────────────────────────────────────────────────
        when {
            error != null -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Error loading latest series",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = RenzoColors.Red,
                    )
                    Text(
                        error!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = RenzoColors.MutedForeground,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            isLoading && currentPage == 0 -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = RenzoColors.Primary)
            }
            visibleItems.isEmpty() -> Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "No series found",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = RenzoColors.Foreground,
                    )
                    Text(
                        "Try adjusting your search or source filter",
                        style = MaterialTheme.typography.bodySmall,
                        color = RenzoColors.MutedForeground,
                    )
                }
            }
            else -> LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = size.width),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 64.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Cinematic spotlight, spanning the full grid width.
                if (spotlightItems.isNotEmpty()) {
                    item(key = "spotlight", span = { GridItemSpan(maxLineSpan) }) {
                        Box(modifier = Modifier.padding(bottom = 16.dp)) {
                            SpotlightHero(
                                items = spotlightItems,
                                eyebrow = "DISCOVER · From your sources",
                                ctaLabel = "Add to library",
                                onCtaClick = { spot ->
                                    addSeriesTitle = spot.title
                                    addSeriesOpen = true
                                },
                            )
                        }
                    }
                }

                items(visibleItems, key = { "${it.mihonId}-${it.provider}" }) { row ->
                    CloudLatestCard(row, baseUrl, size) { detailsItem = row }
                }

                item(key = "tail", span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    ) {
                        when {
                            isLoadingMore -> Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    color = RenzoColors.MutedForeground,
                                    strokeWidth = 1.5.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    "Loading more...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = RenzoColors.MutedForeground,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                            hasMore -> Text(
                                "Scroll to load more",
                                style = MaterialTheme.typography.labelSmall,
                                color = RenzoColors.MutedForeground,
                            )
                            else -> Text(
                                "No more results",
                                style = MaterialTheme.typography.bodySmall,
                                color = RenzoColors.MutedForeground,
                            )
                        }
                    }
                }
            }
        }
    }

    // ── Tag filter popover ───────────────────────────────────────────────
    // Desktop anchors it to the ribbon chip above; this centered dialog is
    // the narrow/TV fallback.
    if (tagPopoverOpen && !tagsAsPopover) {
        TagFilterDialog(
            genres = genresData,
            hideAdult = hideAdult,
            selected = selectedGenres,
            onDismiss = { tagPopoverOpen = false },
        )
    }

    // ── Details sheet ────────────────────────────────────────────────────
    val details = detailsItem
    if (details != null) {
        CloudLatestDetailsSheet(
            item = details,
            baseUrl = baseUrl,
            canAddSeries = libraryState.canAddSeries,
            onDismiss = { detailsItem = null },
            onViewSource = { url -> runCatching { uriHandler.openUri(url) } },
            onRead = if (details.mihonId.isNotBlank()) {
                {
                    detailsItem = null
                    onPreviewRead(details.mihonId, details.title)
                }
            } else {
                null
            },
            onAddSeries = {
                detailsItem = null
                addSeriesTitle = details.title
                addSeriesOpen = true
            },
        )
    }

    if (addSeriesOpen) {
        AddSeriesSheet(
            initialTitle = addSeriesTitle,
            canAddSeries = libraryState.canAddSeries,
            onDismiss = { addSeriesOpen = false; addSeriesTitle = null },
            onAdded = {
                addSeriesOpen = false
                addSeriesTitle = null
                libraryVm.refresh()
            },
        )
    }
}

// ---------------------------------------------------------------------------
// Card (cloud-latest-grid.tsx)
// ---------------------------------------------------------------------------

@Composable
private fun CloudLatestCard(
    item: LatestSeriesRowDto,
    baseUrl: String,
    size: BrowseCardSize,
    onClick: () -> Unit,
) {
    val statusColor = getStatusDisplay(item.status).color
    val isTv = LocalIsTv.current
    TvFocusTile(onClick = onClick) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(6.dp))
            .background(RenzoColors.Muted)
            // On TV the wrapper owns the click, so the ring isn't clipped away.
            .then(if (isTv) Modifier else Modifier.clickable(onClick = onClick)),
    ) {
        if (!item.thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = absoluteUrl(baseUrl, item.thumbnailUrl),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 2px status strip across the top edge.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .height(2.dp)
                .background(statusColor),
        )

        // Provider badge — top-left.
        Text(
            item.provider,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
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

        // Latest-chapter badge — top-right, status-colored.
        if (item.latestChapter != null) {
            Text(
                formatChapter(item.latestChapter),
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(statusColor)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }

        // In-library heart — red when in library, yellow when disabled there.
        if (item.inLibrary != InLibraryStatus.NOT_IN_LIBRARY) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "In library",
                tint = if (item.inLibrary == InLibraryStatus.IN_LIBRARY) Color(0xFFEF4444) else Color(0xFFEAB308),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 28.dp, end = 4.dp)
                    .size(28.dp),
            )
        }

        Text(
            item.title,
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
    }
    }
}

/**
 * Focus wrapper for a catalogue tile — same reasoning as the library's: a ring
 * drawn on the card itself would be painted over by the full-bleed cover, and
 * the 3dp gutter is reserved so focus never reflows the row. `focusable()` inside
 * `tvClickable` handles bring-into-view, so the cursor can't strand itself
 * below the fold of the lazy grid. Pass-through on touch.
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
// Tag filter popover
// ---------------------------------------------------------------------------

@Composable
private fun TagFilterDialog(
    genres: List<LatestGenreDto>?,
    hideAdult: Boolean,
    selected: MutableList<String>,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .heightIn(max = 560.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
                .background(RenzoColors.Popover),
        ) {
            TagFilterBody(
                genres = genres,
                hideAdult = hideAdult,
                selected = selected,
                listMaxHeight = 380.dp,
            )
        }
    }
}

/**
 * The popover's content — search field, capped checkbox list, "Clear all"
 * footer (page.tsx tag popover). One body serves both the centered dialog
 * (narrow/TV) and the desktop chip-anchored popover.
 */
@Composable
private fun TagFilterBody(
    genres: List<LatestGenreDto>?,
    hideAdult: Boolean,
    selected: MutableList<String>,
    /** max-h-72 (288dp) in the desktop popover; taller in the dialog. */
    listMaxHeight: Dp,
) {
    var tagSearch by remember { mutableStateOf("") }
    val isTv = LocalIsTv.current

    val filtered = remember(genres, tagSearch, hideAdult) {
        var list = genres.orEmpty()
        // Keep adult rating tags out of the picker when the filter is on.
        if (hideAdult) list = list.filter { !AdultFilter.isAdultItem(null, listOf(it.name)) }
        val term = tagSearch.trim().lowercase()
        (if (term.isEmpty()) list else list.filter { it.name.lowercase().contains(term) })
            .take(MAX_VISIBLE_GENRES)
    }

    val tagFieldFocus = rememberFocusState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .focusRing(isTv && tagFieldFocus.focused, 8.dp)
            .padding(if (isTv) 6.dp else 0.dp),
    ) {
        Icon(
            Icons.Filled.Search,
            contentDescription = null,
            tint = RenzoColors.MutedForeground,
            modifier = Modifier.size(16.dp),
        )
        BasicTextField(
            value = tagSearch,
            onValueChange = { tagSearch = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = RenzoColors.Foreground),
            cursorBrush = SolidColor(RenzoColors.Foreground),
            decorationBox = { inner ->
                Box(modifier = Modifier.padding(start = 8.dp)) {
                    if (tagSearch.isEmpty()) {
                        Text(
                            "Search tags…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RenzoColors.MutedForeground,
                        )
                    }
                    inner()
                }
            },
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { tagFieldFocus.set(it.isFocused) },
        )
    }
    HorizontalDivider(color = RenzoColors.Border.copy(alpha = 0.6f))

    Box(modifier = Modifier.fillMaxWidth()) {
        when {
            genres == null -> Text(
                "Loading tags…",
                style = MaterialTheme.typography.bodyMedium,
                color = RenzoColors.MutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
            filtered.isEmpty() -> Text(
                if (genres.isEmpty()) "No tags available yet" else "No tags match your search",
                style = MaterialTheme.typography.bodyMedium,
                color = RenzoColors.MutedForeground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
            else -> LazyColumn(modifier = Modifier.heightIn(max = listMaxHeight)) {
                items(filtered, key = { it.name }) { g ->
                    val isChecked = selected.contains(g.name)
                    val rowFocus = rememberFocusState()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isTv) {
                                    // Ticked stays ticked (and coloured)
                                    // wherever the cursor goes; the ring
                                    // is only ever "you are here".
                                    Modifier.tvFocusTarget(
                                        focused = rowFocus.focused,
                                        onFocused = rowFocus::set,
                                        radius = 8.dp,
                                        fill = RenzoColors.Card,
                                        onClick = {
                                            if (isChecked) {
                                                selected.remove(g.name)
                                            } else {
                                                selected.add(g.name)
                                            }
                                        },
                                    )
                                } else {
                                    Modifier.clickable {
                                        if (isChecked) selected.remove(g.name) else selected.add(g.name)
                                    }
                                },
                            )
                            .padding(horizontal = 10.dp, vertical = if (isTv) 12.dp else 8.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .border(
                                    1.dp,
                                    if (isChecked) RenzoColors.Primary else RenzoColors.Border,
                                    RoundedCornerShape(3.dp),
                                )
                                .background(if (isChecked) RenzoColors.Primary else Color.Transparent),
                        ) {
                            if (isChecked) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = RenzoColors.PrimaryForeground,
                                    modifier = Modifier.size(11.dp),
                                )
                            }
                        }
                        Text(
                            g.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (isTv) {
                                tvContentColor(isChecked, rowFocus.focused)
                            } else {
                                RenzoColors.Foreground
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                        Text(
                            g.count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = RenzoColors.MutedForeground,
                        )
                    }
                }
            }
        }
    }

    if (selected.isNotEmpty()) {
        HorizontalDivider(color = RenzoColors.Border.copy(alpha = 0.6f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                "${selected.size} selected",
                style = MaterialTheme.typography.labelSmall,
                color = RenzoColors.MutedForeground,
                modifier = Modifier.weight(1f),
            )
            val clearAllFocus = rememberFocusState()
            Text(
                "Clear all",
                style = MaterialTheme.typography.labelMedium,
                color = if (isTv && clearAllFocus.focused) {
                    RenzoColors.Primary
                } else {
                    RenzoColors.MutedForeground
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (isTv) {
                            Modifier
                                .focusRing(clearAllFocus.focused, 6.dp)
                                .tvClickable(
                                    onFocused = clearAllFocus::set,
                                    onClick = { selected.clear() },
                                )
                        } else {
                            Modifier.clickable { selected.clear() }
                        },
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Details sheet (cloud-latest-details-modal.tsx, the drawer variant)
// ---------------------------------------------------------------------------

@Composable
private fun CloudLatestDetailsSheet(
    item: LatestSeriesRowDto,
    baseUrl: String,
    canAddSeries: Boolean,
    onDismiss: () -> Unit,
    onViewSource: (String) -> Unit,
    /** Preview-read the item live from the source; null hides the button. */
    onRead: (() -> Unit)?,
    onAddSeries: () -> Unit,
) {
    val statusDisplay = getStatusDisplay(item.status)
    val isTv = LocalIsTv.current
    val sourceUrl = item.url?.takeIf { it.isNotBlank() }
    val byline = listOfNotNull(
        item.author?.takeIf { it.isNotBlank() }?.let { "by $it" },
        item.artist?.takeIf { it.isNotBlank() && it != item.author }?.let { "art by $it" },
    ).joinToString(" · ")
    val chapters = item.chapterCount ?: item.latestChapter?.toInt()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // Web md+: a landscape dialog card — cover left, metadata right, one
        // footer bar of actions. Below md (and on TV) the portrait drawer
        // stack below serves.
        if (!isTv && screenWidthDp() >= 768.dp) {
            CloudLatestDetailsDialog(
                item = item,
                baseUrl = baseUrl,
                statusText = statusDisplay.text,
                statusColor = statusDisplay.color,
                sourceUrl = sourceUrl,
                byline = byline,
                chapters = chapters,
                canAddSeries = canAddSeries,
                onDismiss = onDismiss,
                onViewSource = onViewSource,
                onRead = onRead,
                onAddSeries = onAddSeries,
            )
            return@Dialog
        }
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(RenzoColors.Card),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Cover section.
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .width(130.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, RenzoColors.Border, RoundedCornerShape(12.dp))
                            .background(RenzoColors.Muted),
                    ) {
                        if (!item.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = absoluteUrl(baseUrl, item.thumbnailUrl),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Text(
                        item.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = RenzoColors.Foreground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    if (byline.isNotEmpty()) {
                        Text(
                            byline,
                            style = MaterialTheme.typography.labelSmall,
                            color = RenzoColors.MutedForeground,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Text(
                            statusDisplay.text,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusDisplay.color)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                        if (chapters != null) {
                            Text(
                                "$chapters chapters",
                                style = MaterialTheme.typography.labelSmall,
                                color = RenzoColors.Foreground,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(RenzoColors.Secondary)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = RenzoColors.Border)

                // Tags section.
                if (item.genre.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                    ) {
                        item.genre.forEach { g ->
                            Text(
                                g,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                color = RenzoColors.MutedForeground,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .border(1.dp, RenzoColors.Border, RoundedCornerShape(50))
                                    .background(RenzoColors.Muted)
                                    .padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    HorizontalDivider(color = RenzoColors.Border)
                }

                // Description section.
                Text(
                    item.description?.takeIf { it.isNotBlank() } ?: "No description available",
                    style = MaterialTheme.typography.bodySmall,
                    color = RenzoColors.MutedForeground,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
                HorizontalDivider(color = RenzoColors.Border)

                // Source badge section.
                val sourceFocus = rememberFocusState()
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, RenzoColors.Border, RoundedCornerShape(4.dp))
                            .background(RenzoColors.Secondary)
                            .then(
                                when {
                                    sourceUrl == null -> Modifier
                                    isTv -> Modifier
                                        .focusRing(sourceFocus.focused, 4.dp)
                                        .tvClickable(
                                            onFocused = sourceFocus::set,
                                            onClick = { onViewSource(sourceUrl) },
                                        )
                                    else -> Modifier.clickable { onViewSource(sourceUrl) }
                                },
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    ) {
                        Text(
                            item.language.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = RenzoColors.MutedForeground,
                        )
                        Text(
                            item.provider,
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = RenzoColors.Foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                        if (!item.url.isNullOrBlank()) {
                            Icon(
                                Icons.Filled.OpenInNew,
                                contentDescription = null,
                                tint = RenzoColors.MutedForeground,
                                modifier = Modifier.padding(start = 6.dp).size(12.dp),
                            )
                        }
                    }
                }
            }

            // Footer.
            HorizontalDivider(color = RenzoColors.Border)
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                if (item.inLibrary == InLibraryStatus.NOT_IN_LIBRARY) {
                    SheetButton(
                        label = if (canAddSeries) "Add to Library" else "Request Series",
                        icon = Icons.Filled.Add,
                        primary = true,
                        onClick = onAddSeries,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (onRead != null) {
                    SheetButton(
                        label = "Read",
                        icon = Icons.Filled.MenuBook,
                        primary = false,
                        onClick = onRead,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                if (!item.url.isNullOrBlank()) {
                    SheetButton(
                        label = "View Source",
                        icon = Icons.Filled.OpenInNew,
                        primary = false,
                        onClick = { onViewSource(item.url) },
                    )
                    Spacer(Modifier.height(6.dp))
                }
                SheetButton(
                    label = "Close",
                    icon = Icons.Filled.Close,
                    primary = false,
                    onClick = onDismiss,
                )
            }
        }
    }
}

/**
 * cloud-latest-details-modal.tsx, the md+ Dialog variant — deliberately scaled
 * up from the web's 660px card (780dp, 200dp cover, one extra description
 * line) so a desktop window uses its room: cover left with the source badge
 * beneath it, metadata right, and a footer bar with the actions on the right.
 */
@Composable
private fun CloudLatestDetailsDialog(
    item: LatestSeriesRowDto,
    baseUrl: String,
    statusText: String,
    statusColor: Color,
    sourceUrl: String?,
    byline: String,
    chapters: Int?,
    canAddSeries: Boolean,
    onDismiss: () -> Unit,
    onViewSource: (String) -> Unit,
    onRead: (() -> Unit)?,
    onAddSeries: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth(0.94f)
            .widthIn(max = 780.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, RenzoColors.Border, RoundedCornerShape(12.dp))
            .background(RenzoColors.Card),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                // ── Cover + source badge ──
                Column(modifier = Modifier.width(200.dp)) {
                    Box(
                        modifier = Modifier
                            .width(200.dp)
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, RenzoColors.Border, RoundedCornerShape(12.dp))
                            .background(RenzoColors.Muted),
                    ) {
                        if (!item.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = absoluteUrl(baseUrl, item.thumbnailUrl),
                                contentDescription = item.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, RenzoColors.Border, RoundedCornerShape(4.dp))
                                .background(RenzoColors.Secondary)
                                .then(
                                    if (sourceUrl != null) {
                                        Modifier.clickable { onViewSource(sourceUrl) }
                                    } else {
                                        Modifier
                                    },
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                item.language.uppercase(),
                                style = MaterialTheme.typography.labelSmall,
                                color = RenzoColors.MutedForeground,
                            )
                            Text(
                                item.provider,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = RenzoColors.Foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                            if (sourceUrl != null) {
                                Icon(
                                    Icons.Filled.OpenInNew,
                                    contentDescription = null,
                                    tint = RenzoColors.MutedForeground,
                                    modifier = Modifier.padding(start = 6.dp).size(12.dp),
                                )
                            }
                        }
                    }
                }

                // ── Metadata ──
                Column(modifier = Modifier.weight(1f).padding(start = 22.dp)) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            item.title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 26.sp,
                            color = RenzoColors.Foreground,
                        )
                        Text(
                            statusText,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(statusColor)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                    if (byline.isNotEmpty()) {
                        Text(
                            byline,
                            fontSize = 13.sp,
                            color = RenzoColors.MutedForeground,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                    if (item.genre.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(top = 10.dp),
                        ) {
                            item.genre.forEach { g ->
                                Text(
                                    g,
                                    fontSize = 11.sp,
                                    color = RenzoColors.MutedForeground,
                                    maxLines = 1,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .border(1.dp, RenzoColors.Border, RoundedCornerShape(50))
                                        .background(RenzoColors.Muted)
                                        .padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        item.description?.takeIf { it.isNotBlank() } ?: "No description available",
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        color = RenzoColors.MutedForeground,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    val meta = listOfNotNull(
                        chapters?.let { "$it chapters" },
                        formatFetchDate(item.fetchDate),
                    ).joinToString(" · ")
                    if (meta.isNotEmpty()) {
                        Text(
                            meta,
                            fontSize = 12.sp,
                            color = RenzoColors.MutedForeground.copy(alpha = 0.6f),
                            modifier = Modifier.padding(top = 12.dp),
                        )
                    }
                }
            }
            // Web DialogContent's built-in top-right close.
            Icon(
                Icons.Filled.Close,
                contentDescription = "Close",
                tint = RenzoColors.MutedForeground,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onDismiss)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }

        // ── Footer ──
        HorizontalDivider(color = RenzoColors.Border)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(RenzoColors.Muted.copy(alpha = 0.25f))
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                item.provider,
                fontSize = 12.sp,
                color = RenzoColors.MutedForeground.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sourceUrl != null) {
                    DialogActionButton(
                        label = "View Source",
                        icon = Icons.Filled.OpenInNew,
                        primary = false,
                        onClick = { onViewSource(sourceUrl) },
                    )
                }
                if (onRead != null) {
                    DialogActionButton(
                        label = "Read",
                        icon = Icons.Filled.MenuBook,
                        primary = false,
                        onClick = onRead,
                    )
                }
                if (item.inLibrary == InLibraryStatus.NOT_IN_LIBRARY) {
                    DialogActionButton(
                        label = if (canAddSeries) "Add to Library" else "Request Series",
                        icon = Icons.Filled.Add,
                        primary = true,
                        onClick = onAddSeries,
                    )
                }
            }
        }
    }
}

/** The dialog footer's compact button (web Button size default, h-9-ish). */
@Composable
private fun DialogActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(38.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (primary) {
                    Modifier.background(RenzoColors.Primary)
                } else {
                    Modifier.border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
                },
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (primary) RenzoColors.PrimaryForeground else RenzoColors.Foreground,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = if (primary) RenzoColors.PrimaryForeground else RenzoColors.Foreground,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** "2026-08-05…" → "Updated Aug 2026" (the web's formatUpdatedDate). */
private fun formatFetchDate(iso: String?): String? {
    if (iso.isNullOrBlank() || iso.length < 7) return null
    val year = iso.substring(0, 4)
    val month = iso.substring(5, 7).toIntOrNull() ?: return null
    val names = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val name = names.getOrNull(month - 1) ?: return null
    return "Updated $name $year"
}

@Composable
private fun SheetButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    onClick: () -> Unit,
) {
    val isTv = LocalIsTv.current
    val focus = rememberFocusState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(if (isTv) 48.dp else 40.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (primary) {
                    Modifier.background(RenzoColors.Primary)
                } else {
                    Modifier.border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
                },
            )
            .then(
                if (isTv) {
                    Modifier
                        .focusRing(focus.focused, 8.dp)
                        .tvClickable(onFocused = focus::set, onClick = onClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (primary) RenzoColors.PrimaryForeground else RenzoColors.Foreground,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (primary) RenzoColors.PrimaryForeground else RenzoColors.Foreground,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
