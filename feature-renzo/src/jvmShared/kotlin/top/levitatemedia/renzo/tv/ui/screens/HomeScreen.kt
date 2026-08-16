package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import top.levitatemedia.renzo.tv.ui.components.ContentChips
import top.levitatemedia.renzo.tv.ui.components.ErrorBox
import top.levitatemedia.renzo.tv.ui.components.MediaGrid
import top.levitatemedia.renzo.tv.ui.components.MediaRow
import top.levitatemedia.renzo.tv.ui.components.isHidden
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

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
        ErrorBox(message = error!!, onRetry = { retryKey++ })
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
            ContentChips(app)
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
        // Browse mode: the three rows in the web's order (#browseWrap mt-2).
        // Desktop keeps the dashboard feel: the three rows split the window
        // height evenly and the page never scrolls vertically — the rows'
        // bounded height sizes their cards to fit (tileWidthFor). Phones/TV
        // keep the web's scrolling column.
        val fitToWindow = top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop
        Column(
            Modifier
                .fillMaxSize()
                .then(if (fitToWindow) Modifier else Modifier.verticalScroll(rememberScrollState())),
        ) {
            ContentChips(app)
            Notes(malNote, partialFail)
            Spacer(Modifier.height(8.dp))
            CAT_LABELS.forEach { (key, label) ->
                MediaRow(
                    heading = label,
                    items = rows[key] ?: emptyList(),
                    onOpen = open,
                    onMore = { category = key },
                    loading = loading,
                    modifier = if (fitToWindow) Modifier.weight(1f) else Modifier,
                )
            }
            if (!fitToWindow) Spacer(Modifier.height(16.dp))
        }
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
