package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.CardItem
import top.levitatemedia.renzo.tv.ui.components.BackHeading
import top.levitatemedia.renzo.tv.ui.components.ErrorBox
import top.levitatemedia.renzo.tv.ui.components.MediaGrid
import top.levitatemedia.renzo.tv.ui.components.MediaRow
import top.levitatemedia.renzo.tv.ui.components.isHidden
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors
import top.levitatemedia.renzo.tv.ui.kit.ContentLevelSelect
import top.levitatemedia.renzo.tv.ui.kit.SpotlightHero
import top.levitatemedia.renzo.tv.ui.kit.SpotlightItem

// Discover (app/page.tsx): content chips on top, then either the three browse
// rows (Trending / Recommended / New & this season) or a full category grid
// behind a "‹ Back" heading ("See all ›" / More tile — old showCategory).
private val CAT_LABELS = linkedMapOf(
    "trending" to "Trending",
    "recommended" to "Recommended",
    "newSeason" to "New & this season",
)

/** Discover tab, loaded concurrently like the web's three row queries. */
@Composable
fun HomeScreen(app: AppServices, onOpen: (CardItem) -> Unit) {
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var partialFail by remember { mutableStateOf(false) }
    var trending by remember { mutableStateOf<List<CardItem>>(emptyList()) }
    var newSeason by remember { mutableStateOf<List<CardItem>>(emptyList()) }
    var recommended by remember { mutableStateOf<List<CardItem>>(emptyList()) }
    var retryKey by remember { mutableIntStateOf(0) }

    /** Non-null = the full-grid category view (old showCategory). Process-
     *  scoped so leaving for a title and pressing Back returns to the page you
     *  were actually on (e.g. the Recommended grid), not the default rows. */
    var category by BrowseState.category

    // Inline note when a MAL fallback card fails to resolve to an AniList id.
    var malNote by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(retryKey) {
        loading = true
        error = null
        partialFail = false
        coroutineScope {
            val t = async { attempt { app.repo.trending() } }
            val n = async { attempt { app.repo.newSeason() } }
            val r = async { attempt { app.repo.recommended() } }
            val rt = t.await()
            val rn = n.await()
            val rr = r.await()
            trending = rt.getOrDefault(emptyList())
            newSeason = rn.getOrDefault(emptyList())
            recommended = rr.getOrDefault(emptyList())
            val results = listOf(rt, rn, rr)
            if (results.all { it.isFailure }) {
                error = homeErrorMessage(rt.exceptionOrNull())
            } else {
                partialFail = results.any { it.isFailure }
            }
        }
        loading = false
    }

    // Card open handler: MAL fallback cards (AniList down) resolve first.
    val open: (CardItem) -> Unit = { item ->
        val mal = item.malId
        if (item.id != null) {
            onOpen(item)
        } else if (item.source == "mal" && mal != null) {
            scope.launch {
                try {
                    val resolved = app.repo.resolveMal(mal)
                    onOpen(item.copy(id = resolved))
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    malNote = "Couldn't open \"${item.title}\" right now — try again later."
                }
            }
        }
    }

    if (error != null) {
        ErrorBox(message = error!!, onRetry = { retryKey++ }, autoFocus = app.isTv)
        return
    }

    val level = app.contentLevel.value
    val rows: Map<String, List<CardItem>> = mapOf(
        "trending" to trending.filter { !isHidden(it, level) },
        "recommended" to recommended.filter { !isHidden(it, level) },
        "newSeason" to newSeason.filter { !isHidden(it, level) },
    )

    val cat = category
    if (cat != null) {
        // Category mode: chips, "‹ Back" heading, full wrapping grid.
        Column(Modifier.fillMaxSize()) {
            ContentRibbon(app)
            Notes(malNote, partialFail)
            BackHeading(heading = CAT_LABELS[cat] ?: cat, onBack = { category = null })
            MediaGrid(
                items = rows[cat] ?: emptyList(),
                onOpen = open,
                loading = loading,
                empty = "Nothing here yet.",
            )
        }
    } else {
        // Shiori's Browse: the ribbon, the cinematic spotlight over the first
        // picks, then the catalogue — here Renzo's three rows, scrolling as one
        // page (the old fit-to-window split squeezed Shiori-sized cards).
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            ContentRibbon(app)
            Notes(malNote, partialFail)
            val spot = rows["trending"].orEmpty().filter { it.id != null }.take(7)
            if (spot.isNotEmpty()) {
                // Discover cards carry no description or airing status; the
                // title endpoint does (and the server caches it), so the
                // spotlight fills in as each detail arrives.
                val details = remember { androidx.compose.runtime.mutableStateMapOf<Int, top.levitatemedia.renzo.tv.api.TitleDetail>() }
                LaunchedEffect(spot.map { it.id }) {
                    for (c in spot) {
                        val id = c.id ?: continue
                        if (id in details) continue
                        try { details[id] = app.repo.title(id) } catch (e: CancellationException) { throw e } catch (_: Exception) {}
                    }
                }
                Spacer(Modifier.height(8.dp))
                SpotlightHero(
                    items = spot.map { c -> spotlightOf(c, details[c.id]) },
                    eyebrow = "TRENDING · THIS SEASON ON ANILIST",
                    ctaLabel = "View title",
                    onCtaClick = { s -> spot.firstOrNull { it.id.toString() == s.id }?.let(open) },
                )
                Spacer(Modifier.height(24.dp))
            }
            CAT_LABELS.forEach { (key, label) ->
                MediaRow(
                    heading = label,
                    items = rows[key] ?: emptyList(),
                    onOpen = open,
                    onMore = { category = key },
                    loading = loading,
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** A Discover card as a spotlight slide, enriched once its detail has loaded. */
private fun spotlightOf(c: CardItem, d: top.levitatemedia.renzo.tv.api.TitleDetail?): SpotlightItem {
    val (label, color) = when (d?.airingStatus?.uppercase()) {
        "RELEASING" -> "Airing" to androidx.compose.ui.graphics.Color(0xFF22C55E)
        "FINISHED" -> "Finished" to androidx.compose.ui.graphics.Color(0xFF3B82F6)
        "NOT_YET_RELEASED" -> "Upcoming" to androidx.compose.ui.graphics.Color(0xFFEAB308)
        else -> (if (c.type == "movie") "Movie" else "Series") to androidx.compose.ui.graphics.Color(0xFF71717A)
    }
    val eps = d?.episodeCount ?: d?.episodeList?.size?.takeIf { it > 0 }
    return SpotlightItem(
        id = c.id.toString(),
        title = c.title,
        author = c.year?.toString(),
        description = cleanDescription(d?.description),
        thumbnailUrl = c.poster,
        statusLabel = label,
        statusColor = color,
        genres = c.genres.ifEmpty { d?.genres.orEmpty() },
        meta = listOfNotNull(c.year?.toString(), eps?.let { "$it episodes" }).joinToString(" · ").ifEmpty { null },
    )
}

/** Shiori's library ribbon, Discover's share of it: the 18+ control. */
@Composable
private fun ContentRibbon(app: AppServices) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        ContentLevelSelect(app.contentLevel.value) { app.setContentLevel(it) }
    }
}

/** MAL-resolve failure note + partial-load note (native-only surfaces, kept). */
@Composable
private fun Notes(malNote: String?, partialFail: Boolean) {
    if (malNote != null) {
        Text(
            malNote,
            color = RenzoColors.Primary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
    if (partialFail) {
        Text(
            "Some rows failed to load.",
            color = RenzoColors.MutedForeground,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** try/catch wrapper that never swallows coroutine cancellation. */
private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    Result.failure(e)
}

private fun homeErrorMessage(e: Throwable?): String = when {
    e is ApiError && e.status == 0 -> "Can't reach your Renzo server — check the connection."
    e is ApiError && e.status == 401 -> "Session expired — sign in again from the account menu."
    else -> e?.message ?: "Something went wrong."
}


/**
 * Discover's page state, kept for the process: opening a title swaps this
 * composable out, and plain `remember` would drop the category view — Back
 * would then always dump you on the default browse rows.
 */
internal object BrowseState {
    val category = androidx.compose.runtime.mutableStateOf<String?>(null)
}
