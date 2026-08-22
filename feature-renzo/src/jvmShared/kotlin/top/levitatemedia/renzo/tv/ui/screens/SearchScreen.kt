package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.CardItem
import top.levitatemedia.renzo.tv.ui.components.BackHeading
import top.levitatemedia.renzo.tv.ui.components.ContentChips
import top.levitatemedia.renzo.tv.ui.components.ErrorBox
import top.levitatemedia.renzo.tv.ui.components.GridSkeleton
import top.levitatemedia.renzo.tv.ui.components.MediaGrid
import top.levitatemedia.renzo.tv.ui.components.focusRing
import top.levitatemedia.renzo.tv.ui.components.isHidden
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/**
 * File-private holder so the last query + results survive tab switches
 * (MainActivity swaps tab composables out of composition, which discards
 * `remember` state; this lives for the process instead).
 */
private object SearchState {
    val query = mutableStateOf("")
    val results = mutableStateOf<List<CardItem>>(emptyList())
    /** The query the current results are for; null = nothing searched yet. */
    val searchedFor = mutableStateOf<String?>(null)

    /** The type filter the current results were fetched with. */
    val searchedType = mutableStateOf("")
}

/**
 * Search tab: TV text input + the discover page's search mode (page.tsx):
 * `Results for “q”` behind a "‹ Back" pill that clears the search, then the
 * wrapping MediaGrid with the web's skeleton / "Nothing here yet." states.
 */
@Composable
fun SearchScreen(
    app: AppServices,
    onOpen: (CardItem) -> Unit,
    /** ‹ Back / cleared query: return to Discover (web clearSearch parity). */
    onExit: () -> Unit = {},
) {
    var query by SearchState.query
    var results by SearchState.results
    var searchedFor by SearchState.searchedFor
    var searchedType by SearchState.searchedType

    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var fieldFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun submit() {
        val q = query.trim()
        if (q.isEmpty() || searching) return
        scope.launch {
            searching = true
            error = null
            try {
                results = app.repo.search(q, app.searchType.value.takeIf { it.isNotBlank() })
                searchedFor = q
                searchedType = app.searchType.value
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = searchErrorMessage(e)
            }
            searching = false
        }
    }

    // Old #discoverBack: leaving search clears the query and the results —
    // including the topbar-driven query, or its effect would just re-submit.
    fun clearSearch() {
        app.searchQuery.value = ""
        query = ""
        results = emptyList()
        searchedFor = null
        error = null
    }

    // Queries submitted from the TOPBAR search box (web parity: the topbar
    // input is the primary search entry point; this screen shows the results).
    LaunchedEffect(app.searchQuery.value, app.searchType.value) {
        val q = app.searchQuery.value.trim()
        // Re-run on a type change too (web: changing #searchType re-queries).
        if (q.isNotEmpty() && (q != searchedFor || app.searchType.value != searchedType)) {
            query = q
            submit()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // The in-page input is TV-only: with a D-pad the topbar box is a tiny
        // target behind the chrome. Everywhere else the web's model applies —
        // the TOPBAR search is the entry point and this view is purely the
        // results (page.tsx searchWrap: ContentChips + BackHeading + grid,
        // updating live as the query changes).
        if (app.isTv) {
            Box(
                Modifier
                    .widthIn(max = 560.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .focusRing(fieldFocused, 10.dp)
                    .background(RenzoColors.Card, RoundedCornerShape(10.dp))
                    .border(1.dp, RenzoColors.Border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = TextStyle(color = RenzoColors.Foreground, fontSize = 15.sp, fontFamily = top.levitatemedia.renzo.hub.core.GeistFamily),
                    cursorBrush = SolidColor(RenzoColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = { submit() },
                        onDone = { submit() },
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { fieldFocused = it.isFocused },
                    decorationBox = { innerTextField ->
                        Box {
                            if (query.isEmpty()) {
                                Text(
                                    "Search anime…",
                                    color = RenzoColors.MutedForeground,
                                    fontSize = 15.sp,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }

        Column(Modifier.fillMaxSize().padding(top = if (app.isTv) 20.dp else 0.dp)) {
            // Web parity: the chips row stays above the results, exactly as on
            // the browse rows it replaced.
            ContentChips(app)
            when {
                error != null -> ErrorBox(message = error!!, onRetry = { submit() }, autoFocus = app.isTv)
                searchedFor == null && !searching -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (app.isTv) "Type a title and press Search." else "Type in the search box above.",
                        color = RenzoColors.MutedForeground,
                        fontSize = 14.sp,
                    )
                }
                else -> {
                    // page.tsx search mode: BackHeading + MediaGrid; the grid
                    // shows its skeleton under the heading while a query is
                    // in flight, instead of blanking the whole view.
                    BackHeading(
                        heading = "Results for “${searchedFor ?: query.trim()}”",
                        onBack = { clearSearch(); onExit() },
                    )
                    val level = app.contentLevel.value
                    MediaGrid(
                        items = results.filter { !isHidden(it, level) },
                        onOpen = onOpen,
                        loading = searching,
                        empty = "Nothing here yet.",
                    )
                }
            }
        }
    }
}

private fun searchErrorMessage(e: Exception): String = when {
    e is ApiError && e.status == 0 -> "Can't reach your Renzo server — check the connection."
    e is ApiError && e.status == 401 -> "Session expired — sign in again from the account menu."
    else -> e.message ?: "Something went wrong."
}
