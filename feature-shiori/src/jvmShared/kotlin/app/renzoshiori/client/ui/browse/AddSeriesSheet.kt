package app.renzoshiori.client.ui.browse

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.renzoshiori.client.ShioriRuntime
import app.renzoshiori.client.data.model.LinkedSeriesRowDto
import app.renzoshiori.client.data.model.SearchSourceDto
import app.renzoshiori.client.data.network.BrowseApi
import app.renzoshiori.client.data.network.LibraryExtrasApi
import app.renzoshiori.client.data.network.serverErrorMessage
import app.renzoshiori.client.data.network.absoluteUrl
import app.renzoshiori.client.ui.components.TvSearchBar
import app.renzoshiori.client.ui.components.tvFocusTarget
import app.renzoshiori.client.ui.tv.LocalIsTv
import app.renzoshiori.client.ui.tv.focusRing
import app.renzoshiori.client.ui.tv.rememberFocusState
import app.renzoshiori.client.ui.tv.tvClickable
import app.renzoshiori.client.ui.tv.tvContentColor
import app.renzoshiori.client.ui.util.screenHeightDp
import app.renzoshiori.client.ui.util.screenWidthDp
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import app.renzoshiori.client.ui.theme.RenzoColors

private val looseJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

/**
 * Add Series — the two-stage flow from
 * RenzoFrontend src/components/comp/series/add-series (STAGE 01 / SEARCH →
 * STAGE 02 / CONFIRM), transliterated as a full-screen sheet.
 *
 * Stage 1 searches every enabled source (3-character minimum, 800ms debounce),
 * lets the user tick the matching entries and shows the same "✓ added" tail.
 * Stage 2 augments the picks and exposes the per-source Storage / Cover /
 * Title / Status switches plus the selection checkbox, then POSTs the whole
 * augmented payload back verbatim.
 */
@Composable
fun AddSeriesSheet(
    initialTitle: String?,
    canAddSeries: Boolean,
    onDismiss: () -> Unit,
    onAdded: () -> Unit,
    /**
     * Add-sources mode, matching the web UI's `isAddSourcesMode`: attach the
     * chosen sources to THIS series instead of creating a new one.
     *
     * This is the difference between a deliberate merge and a surprise one. If
     * the client sends no series id, the server falls back to
     * FindExistingSeriesAsync — a fuzzy title match against the whole library —
     * and silently attaches to whatever it picks. Naming the target removes the
     * guess.
     */
    existingSeriesId: String? = null,
    existingSeriesTitle: String? = null,
) {
    val renzoApp = ShioriRuntime.app
    val scope = rememberCoroutineScope()
    val api = remember { renzoApp.network.currentServiceOf<BrowseApi>() }
    val baseUrl = renzoApp.tokenStore.serverUrl ?: ""
    val isTv = LocalIsTv.current

    var stage by remember { mutableStateOf(0) }
    var searchValue by remember { mutableStateOf(initialTitle ?: "") }
    var debounced by remember { mutableStateOf(initialTitle ?: "") }
    var searching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf(false) }

    var sources by remember { mutableStateOf<List<SearchSourceDto>>(emptyList()) }
    val selectedSources = remember { mutableStateListOf<String>() }
    var sourcesExpanded by remember { mutableStateOf(false) }

    // Raw search rows, kept verbatim so augment gets exactly what the API sent.
    var rawResults by remember { mutableStateOf<JsonArray?>(null) }
    var results by remember { mutableStateOf<List<LinkedSeriesRowDto>>(emptyList()) }
    val selectedRows = remember { mutableStateListOf<String>() }

    // Stage 2 state — the augmented payload plus the per-source toggles.
    var augmented by remember { mutableStateOf<JsonObject?>(null) }
    var confirmRows by remember { mutableStateOf<List<ConfirmRow>>(emptyList()) }
    /** Set when the server says this add will land on an existing series. */
    var mergeWarning by remember { mutableStateOf<String?>(null) }

    // Which sources to search, remembered between visits — the web keeps the
    // same choice in localStorage. Preselecting EVERY source (what this did
    // before) fans a single keystroke out across every installed extension;
    // that takes long enough that a reverse proxy in front of the server
    // returns 502 before the search finishes.
    LaunchedEffect(Unit) {
        runCatching { api?.searchSources() }.getOrNull()?.let { list ->
            sources = list.sortedBy { it.provider.lowercase() }
            val available = list.mapNotNull { it.mihonProviderId.takeIf(String::isNotBlank) }.toSet()
            selectedSources.clear()
            selectedSources.addAll(loadSearchSources().filter { it in available })
        }
    }
    LaunchedEffect(selectedSources.size, selectedSources.toList()) {
        if (sources.isNotEmpty()) saveSearchSources(selectedSources.toList())
    }

    LaunchedEffect(searchValue) {
        delay(800)
        debounced = searchValue
    }

    LaunchedEffect(debounced, selectedSources.size) {
        // A new search invalidates the confirm stage. Without this, `augmented`
        // and `confirmRows` keep the PREVIOUS series' payload while the results
        // list shows the new one — and pressing Add submits the old series.
        // Re-adding something already in the library is a no-op server-side, so
        // it looks exactly like "Add did nothing, with no error".
        stage = 0
        augmented = null
        confirmRows = emptyList()
        mergeWarning = null

        if (debounced.trim().length < 3 || selectedSources.isEmpty()) {
            results = emptyList()
            rawResults = null
            return@LaunchedEffect
        }
        searching = true
        error = null
        runCatching {
            api?.searchRaw(debounced.trim(), selectedSources.toList())
        }
            .onSuccess { arr ->
                rawResults = arr
                results = arr?.mapNotNull { element ->
                    runCatching { looseJson.decodeFromJsonElement(LinkedSeriesRowDto.serializer(), element) }.getOrNull()
                }.orEmpty()
                val valid = results.map { it.rowId }.toSet()
                selectedRows.retainAll { it in valid }
            }
            .onFailure { cause ->
                val code = (cause as? retrofit2.HttpException)?.code()
                error = when {
                    code == 502 || code == 504 ->
                        "The search took too long and the connection timed out. " +
                            "Try fewer sources, or a longer keyword."
                    code != null -> "Search failed (HTTP $code)."
                    cause is java.net.SocketTimeoutException ->
                        "The sources are taking too long to answer. Try fewer of them."
                    cause is java.io.IOException -> "Can't reach the server."
                    else -> cause.message ?: "Search failed."
                }
            }
        searching = false
    }

    // Web: mobile keeps the full-screen sheet; a desktop window gets the
    // centred glass command card (`cmd-card`: w-[min(980px,·)], top-[10vh],
    // max-h min(88dvh, 900px)). One content stack serves both containers.
    val deskDialog = !isTv && screenWidthDp() >= 768.dp
    val sheetContent: @Composable ColumnScope.() -> Unit = {
            // ── Stage label + close ──────────────────────────────────────
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 16.dp, bottom = 8.dp),
            ) {
                Text(
                    if (stage == 0) "STAGE 01 / SEARCH" else "STAGE 02 / CONFIRM",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                    color = RenzoColors.MutedForeground,
                    letterSpacing = 1.6.sp,
                    modifier = Modifier.weight(1f),
                )
                val closeFocus = rememberFocusState()
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(if (isTv) 44.dp else 32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .then(
                            if (isTv) {
                                Modifier
                                    .focusRing(closeFocus.focused, 8.dp)
                                    .tvClickable(onFocused = closeFocus::set, onClick = onDismiss)
                            } else {
                                Modifier.clickable(onClick = onDismiss)
                            },
                        ),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close",
                        tint = if (closeFocus.focused) RenzoColors.Foreground else RenzoColors.MutedForeground,
                        modifier = Modifier.size(if (isTv) 22.dp else 16.dp),
                    )
                }
            }
            HorizontalDivider(color = RenzoColors.Border)

            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                if (stage == 0) {
                    SearchStage(
                        searchValue = searchValue,
                        onSearchValue = { searchValue = it },
                        // IME Search / a voice result skips the 800ms debounce —
                        // the user has explicitly said "go".
                        onSubmitSearch = {
                            searchValue = it
                            debounced = it
                        },
                        searching = searching,
                        sources = sources,
                        selectedSources = selectedSources,
                        sourcesExpanded = sourcesExpanded,
                        onToggleSourcesExpanded = { sourcesExpanded = !sourcesExpanded },
                        results = results,
                        selectedRows = selectedRows,
                        baseUrl = baseUrl,
                    )
                } else {
                    ConfirmStage(
                        rows = confirmRows,
                        onRows = { confirmRows = it },
                        baseUrl = baseUrl,
                    )
                }
            }

            // ── Merge notice ─────────────────────────────────────────────
            // Not an error: the add is legitimate, it just will not create a new
            // series. Shown before the user commits, because after the fact it
            // is invisible — the sheet closes and nothing appears to happen.
            val merge = mergeWarning
            if (merge != null && stage == 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, RenzoColors.Primary.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = RenzoColors.Primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        merge,
                        color = RenzoColors.Foreground,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            // Add-sources mode: say what we are attaching to, so the target is
            // never in doubt.
            if (existingSeriesTitle != null) {
                Text(
                    "Adding sources to \u201C$existingSeriesTitle\u201D",
                    color = RenzoColors.MutedForeground,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }

            // ── Error banner ─────────────────────────────────────────────
            val err = error
            if (err != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, RenzoColors.Red.copy(alpha = 0.25f), RoundedCornerShape(6.dp))
                        .background(Color.White.copy(alpha = 0.04f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = RenzoColors.Red,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        err,
                        style = MaterialTheme.typography.bodySmall,
                        color = RenzoColors.Red,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            // ── CTA row ──────────────────────────────────────────────────
            HorizontalDivider(color = RenzoColors.Border)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Text(
                    if (stage == 0) {
                        "${results.size} results · ${selectedRows.size} selected"
                    } else {
                        val sel = confirmRows.filter { it.isSelected }
                        "${sel.size} sources · ${sel.sumOf { it.chapterCount }} chapters"
                    },
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = RenzoColors.MutedForeground,
                    modifier = Modifier.weight(1f),
                )
                val backFocus = rememberFocusState()
                if (stage > 0) {
                    Text(
                        "Back",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (backFocus.focused) RenzoColors.Foreground else RenzoColors.MutedForeground,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (isTv) {
                                    Modifier
                                        .focusRing(backFocus.focused, 8.dp)
                                        .tvClickable(onFocused = backFocus::set, onClick = { stage = 0 })
                                } else {
                                    Modifier.clickable { stage = 0 }
                                },
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                val canProgress = if (stage == 0) selectedRows.isNotEmpty() else confirmRows.any { it.isSelected }
                val ctaFocus = rememberFocusState()
                if (canProgress && !pending) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .height(if (isTv) 44.dp else 36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(RenzoColors.Primary)
                            // The CTA's onClick is a long coroutine block, so it
                            // keeps its clickable (already D-pad activatable) and
                            // only gains the ring + focus reporting.
                            .then(
                                if (isTv) {
                                    Modifier
                                        .focusRing(ctaFocus.focused, 8.dp)
                                        .onFocusChanged { ctaFocus.set(it.isFocused) }
                                } else {
                                    Modifier
                                },
                            )
                            .clickable {
                                scope.launch {
                                    pending = true
                                    error = null
                                    if (stage == 0) {
                                        val picks = buildJsonArrayOfSelected(rawResults, selectedRows.toSet())
                                        // Surface the server's OWN words. This
                                        // used to swallow the exception with
                                        // getOrNull(), so a 500, a timeout and a
                                        // dead source all read as one generic
                                        // line with nothing to act on.
                                        val attempt = runCatching {
                                            api?.augment(picks)
                                                ?: error("Not connected to a server.")
                                        }
                                        val response = attempt.getOrNull()
                                        if (response == null) {
                                            error = attempt.exceptionOrNull()
                                                ?.serverErrorMessage("Failed to load series details.")
                                                ?: "Failed to load series details."
                                        } else {
                                            val rows = buildConfirmRows(response)
                                            if (rows.isEmpty()) {
                                                error = droppedMessage(response)
                                            } else {
                                                augmented = response
                                                confirmRows = rows
                                                stage = 1
                                                // The augment response reports whether this
                                                // already matches something in the library.
                                                // Say so BEFORE the user commits: otherwise
                                                // "Add" silently becomes "edit that other
                                                // series", which is how a Hourglass source
                                                // ended up attached to Backstabbed.
                                                mergeWarning = if (
                                                    existingSeriesId == null &&
                                                    runCatching {
                                                        response["existingSeries"]?.jsonPrimitive?.content == "true"
                                                    }.getOrNull() == true
                                                ) {
                                                    "This matches a series already in your library. " +
                                                        "Adding will attach these sources to it rather than " +
                                                        "creating a new series."
                                                } else {
                                                    null
                                                }
                                            }
                                        }
                                    } else {
                                        // confirmRows are indexed into augmented["series"]; if
                                        // they ever disagree the payload silently loses sources.
                                        val augmentedSize = augmented?.get("series")
                                            ?.let { runCatching { it.jsonArray.size }.getOrNull() } ?: 0
                                        val payload = augmented
                                            ?.takeIf { confirmRows.isNotEmpty() && confirmRows.size == augmentedSize }
                                            ?.let { buildSubmitPayload(it, confirmRows, existingSeriesId) }
                                        val chosen = payload?.get("series")
                                            ?.let { runCatching { it.jsonArray.size }.getOrNull() } ?: 0
                                        if (payload == null) {
                                            error = "Series details are out of date — go back and press Next again."
                                        } else if (chosen == 0) {
                                            // The server answers 400 "No series provided
                                            // to add" for this, which surfaces as a bare
                                            // failure. Catch it here where we can say
                                            // what the user actually needs to do.
                                            error = "Select at least one source to add."
                                        } else {
                                            // NOT `api?.addSeries(...)`: with a
                                            // safe-call, a null api makes the whole
                                            // expression null, runCatching SUCCEEDS,
                                            // and the sheet closes as though the
                                            // series had been added — while nothing
                                            // was ever sent.
                                            val browse = api
                                            if (browse == null) {
                                                error = "Not connected to a server."
                                                pending = false
                                                return@launch
                                            }
                                            runCatching { browse.addSeries(payload) }
                                                .onSuccess { resp ->
                                                    // A 2xx is not proof it was stored. The
                                                    // endpoint answers {"id": ...}; without
                                                    // one, something accepted the request
                                                    // and did nothing — which is precisely
                                                    // the "it says it added but the server
                                                    // never got it" symptom. Do not close
                                                    // the sheet on that.
                                                    val newId = runCatching {
                                                        resp["id"]?.jsonPrimitive?.content
                                                    }.getOrNull()
                                                    // Server-side merge report (additive field,
                                                    // 2026-08-13): the add matched a series that
                                                    // already existed and UPDATED it instead of
                                                    // creating one. In add-sources mode that is
                                                    // exactly what was asked for; on a normal add
                                                    // it is the silent-edit damage — say so and
                                                    // keep the sheet open.
                                                    val merged = runCatching {
                                                        resp["merged"]?.jsonPrimitive?.content == "true"
                                                    }.getOrNull() ?: false
                                                    if (merged && existingSeriesId == null) {
                                                        error = "That matched a series already in " +
                                                            "your library, so its sources were " +
                                                            "updated instead of a new series " +
                                                            "being added."
                                                    } else if (newId.isNullOrBlank()) {
                                                        error = "The server accepted the request " +
                                                            "but returned no series id. Response: " +
                                                            resp.toString().take(200)
                                                    } else {
                                                        // The server said it stored this. Prove it
                                                        // appears in THIS account's library before
                                                        // claiming success — "added, id returned,
                                                        // still not there" is the whole symptom,
                                                        // and it usually means the add landed under
                                                        // a different user than the one you are
                                                        // browsing with elsewhere.
                                                        val extras = renzoApp.network
                                                            .currentServiceOf<LibraryExtrasApi>()
                                                        val present = runCatching {
                                                            extras?.library(false)?.any { it.id == newId }
                                                        }.getOrNull()
                                                        if (present == false) {
                                                            val who = runCatching {
                                                                renzoApp.network.currentApi()?.me()?.username
                                                            }.getOrNull().orEmpty()
                                                            error = buildString {
                                                                append("Server created the series (id ")
                                                                append(newId.take(8))
                                                                append("…) but it is not in this account's library")
                                                                if (who.isNotBlank()) append(", signed in as $who")
                                                                append(". Check whether the web UI is signed in as the same user.")
                                                            }
                                                        } else {
                                                            onAdded()
                                                        }
                                                    }
                                                }
                                                .onFailure {
                                                    // it.message on an HttpException is just
                                                    // "HTTP 500 Internal Server Error"; the
                                                    // status and body carry the real reason,
                                                    // and there is no adb here to read them.
                                                    error = it.addFailureDetail()
                                                }
                                        }
                                    }
                                    pending = false
                                }
                            }
                            .padding(horizontal = 16.dp),
                    ) {
                        Icon(
                            if (stage == 0) Icons.Filled.Check else Icons.Filled.Add,
                            contentDescription = null,
                            tint = RenzoColors.PrimaryForeground,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            if (stage == 0) "Next" else if (canAddSeries) "Add Series" else "Request Series",
                            style = MaterialTheme.typography.labelLarge,
                            color = RenzoColors.PrimaryForeground,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                } else if (pending) {
                    CircularProgressIndicator(
                        color = RenzoColors.Primary,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(start = 12.dp).size(20.dp),
                    )
                }
            }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (deskDialog) {
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    .fillMaxSize()
                    // The web dialog's overlay: clicking outside the card closes.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
                    .padding(top = maxOf(screenHeightDp() * 0.1f, 0.dp), bottom = 24.dp),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 980.dp)
                        .fillMaxWidth(0.94f)
                        .height(minOf(screenHeightDp() * 0.78f, 900.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .border(1.dp, RenzoColors.Border, RoundedCornerShape(14.dp))
                        .background(RenzoColors.Card)
                        // Swallow clicks so interacting with the card never
                        // falls through to the overlay's dismiss.
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {},
                ) {
                    sheetContent()
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(RenzoColors.Background),
            ) {
                sheetContent()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stage 1
// ---------------------------------------------------------------------------

@Composable
private fun SearchStage(
    searchValue: String,
    onSearchValue: (String) -> Unit,
    onSubmitSearch: (String) -> Unit,
    searching: Boolean,
    sources: List<SearchSourceDto>,
    selectedSources: MutableList<String>,
    sourcesExpanded: Boolean,
    onToggleSourcesExpanded: () -> Unit,
    results: List<LinkedSeriesRowDto>,
    selectedRows: MutableList<String>,
    baseUrl: String,
) {
    val isTv = LocalIsTv.current
    Column(modifier = Modifier.fillMaxSize()) {
        // Search input row. On TV this becomes the bordered field + mic (voice
        // fills the field and searches, but the transcript stays editable — a
        // romanised title comes back mangled often enough that the user has to
        // be able to fix it in place).
        if (isTv) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.weight(1f)) {
                    TvSearchBar(
                        value = searchValue,
                        onValueChange = onSearchValue,
                        onSubmit = onSubmitSearch,
                        placeholder = "Search for a series…",
                        voicePrompt = "Say the series title",
                    )
                }
                if (searching) {
                    CircularProgressIndicator(
                        color = RenzoColors.MutedForeground,
                        strokeWidth = 2.dp,
                        modifier = Modifier.padding(end = 16.dp).size(20.dp),
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    tint = RenzoColors.MutedForeground,
                    modifier = Modifier.size(22.dp),
                )
                BasicTextField(
                    value = searchValue,
                    onValueChange = onSearchValue,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = RenzoColors.Foreground),
                    cursorBrush = SolidColor(RenzoColors.Foreground),
                    decorationBox = { inner ->
                        Box(modifier = Modifier.padding(start = 12.dp)) {
                            if (searchValue.isEmpty()) {
                                Text(
                                    "Search for a series…",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = RenzoColors.MutedForeground,
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
                if (searching) {
                    CircularProgressIndicator(
                        color = RenzoColors.MutedForeground,
                        strokeWidth = 1.5.dp,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }

        // Sources selector — its own row, as in the web.
        if (sources.isNotEmpty()) {
            val expandFocus = rememberFocusState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (isTv) {
                            Modifier
                                .padding(horizontal = 12.dp)
                                .tvFocusTarget(
                                    focused = expandFocus.focused,
                                    onFocused = expandFocus::set,
                                    radius = 8.dp,
                                    fill = RenzoColors.Card,
                                    onClick = onToggleSourcesExpanded,
                                )
                        } else {
                            Modifier.clickable(onClick = onToggleSourcesExpanded)
                        },
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    "Sources: ${selectedSources.size}/${sources.size}",
                    style = MaterialTheme.typography.bodySmall,
                    color = RenzoColors.MutedForeground,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    if (sourcesExpanded) "Hide" else "Choose…",
                    style = MaterialTheme.typography.labelMedium,
                    color = RenzoColors.Primary,
                )
            }
            if (sourcesExpanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = if (isTv) 320.dp else 220.dp)
                        .padding(horizontal = 16.dp),
                ) {
                    // Select all / none. Note that selecting everything makes
                    // each search query every installed extension live, which
                    // is slow enough to trip a proxy's gateway timeout — hence
                    // the count next to it rather than a silent toggle.
                    val allSelected = selectedSources.size == sources.size && sources.isNotEmpty()
                    val allFocus = rememberFocusState()
                    val toggleAll = {
                        if (allSelected) {
                            selectedSources.clear()
                        } else {
                            selectedSources.clear()
                            selectedSources.addAll(
                                sources.mapNotNull { it.mihonProviderId.takeIf(String::isNotBlank) },
                            )
                        }
                        Unit
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (isTv) {
                                    Modifier.tvFocusTarget(
                                        focused = allFocus.focused,
                                        onFocused = allFocus::set,
                                        radius = 8.dp,
                                        fill = RenzoColors.Card,
                                        onClick = toggleAll,
                                    )
                                } else {
                                    Modifier.clickable(onClick = toggleAll)
                                },
                            )
                            .padding(horizontal = if (isTv) 8.dp else 0.dp, vertical = if (isTv) 10.dp else 6.dp),
                    ) {
                        CheckBox(allSelected)
                        Text(
                            if (allSelected) "Select none" else "Select all",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RenzoColors.Primary,
                            modifier = Modifier.padding(start = 8.dp).weight(1f),
                        )
                        Text(
                            "${selectedSources.size}/${sources.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color = RenzoColors.MutedForeground,
                        )
                    }
                    HorizontalDivider(color = RenzoColors.Border)
                    LazyColumn {
                        items(sources, key = { it.mihonProviderId }) { source ->
                            val checked = selectedSources.contains(source.mihonProviderId)
                            val sourceFocus = rememberFocusState()
                            val toggleSource = {
                                if (checked) {
                                    selectedSources.remove(source.mihonProviderId)
                                } else {
                                    selectedSources.add(source.mihonProviderId)
                                }
                                Unit
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isTv) {
                                            // Ticked (colour + box) is state and
                                            // survives the cursor moving on; the
                                            // ring is only ever focus.
                                            Modifier.tvFocusTarget(
                                                focused = sourceFocus.focused,
                                                onFocused = sourceFocus::set,
                                                radius = 8.dp,
                                                fill = RenzoColors.Card,
                                                onClick = toggleSource,
                                            )
                                        } else {
                                            Modifier.clickable(onClick = toggleSource)
                                        },
                                    )
                                    .padding(
                                        horizontal = if (isTv) 8.dp else 0.dp,
                                        vertical = if (isTv) 10.dp else 6.dp,
                                    ),
                            ) {
                                CheckBox(checked)
                                Text(
                                    source.provider,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isTv) {
                                        tvContentColor(checked, sourceFocus.focused)
                                    } else {
                                        RenzoColors.Foreground
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(start = 8.dp).weight(1f),
                                )
                                Text(
                                    source.language.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RenzoColors.MutedForeground,
                                )
                            }
                        }
                    }
                }
            }
        }

        HorizontalDivider(color = RenzoColors.Border)

        // Results.
        when {
            selectedSources.isEmpty() -> CenteredNote("Pick at least one source to search")
            searchValue.isEmpty() -> CenteredNote("Start typing to search…")
            results.isEmpty() && !searching -> CenteredNote(
                if (searchValue.trim().length < 3) {
                    "Keep typing — search starts at 3 characters"
                } else {
                    "No results found"
                },
            )
            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.rowId }) { series ->
                    val isSelected = selectedRows.contains(series.rowId)
                    val rowFocus = rememberFocusState()
                    val toggleRow = {
                        if (isSelected) {
                            selectedRows.remove(series.rowId)
                        } else {
                            selectedRows.add(series.rowId)
                            // First pick also brings in its linked ids.
                            if (selectedRows.size == 1) {
                                series.linkedIds.forEach { linked ->
                                    if (!selectedRows.contains(linked)) selectedRows.add(linked)
                                }
                            }
                        }
                        Unit
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            // The primary wash is selection ("picked"); the ring
                            // is focus. A row can be both, and reads as both.
                            .background(if (isSelected) RenzoColors.Primary.copy(alpha = 0.08f) else Color.Transparent)
                            .then(
                                if (isTv) {
                                    Modifier
                                        .focusRing(rowFocus.focused, 8.dp)
                                        .tvClickable(onFocused = rowFocus::set, onClick = toggleRow)
                                } else {
                                    Modifier.clickable(onClick = toggleRow)
                                },
                            )
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        // Accent bar.
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(48.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (isSelected) RenzoColors.Primary else Color.Transparent),
                        )
                        Box(
                            modifier = Modifier
                                .padding(start = 10.dp)
                                .width(44.dp)
                                .height(62.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(RenzoColors.Muted),
                        ) {
                            if (!series.thumbnailUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = absoluteUrl(baseUrl, series.thumbnailUrl),
                                    contentDescription = series.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(
                                series.title,
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                color = RenzoColors.Foreground,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Text(
                                    series.provider,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RenzoColors.MutedForeground,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(RenzoColors.Muted)
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                                Text(
                                    series.lang.uppercase(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RenzoColors.MutedForeground,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                        if (isSelected) {
                            Text(
                                "✓ added",
                                style = MaterialTheme.typography.labelSmall,
                                color = RenzoColors.Primary,
                            )
                        }
                    }
                    HorizontalDivider(color = RenzoColors.Border.copy(alpha = 0.5f))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Stage 2
// ---------------------------------------------------------------------------

/** One augmented source, with the four per-source switches the web exposes. */
data class ConfirmRow(
    val index: Int,
    val provider: String,
    val scanlator: String,
    val lang: String,
    val title: String,
    val thumbnailUrl: String?,
    val chapterCount: Int,
    val isSelected: Boolean,
    val isStorage: Boolean,
    val useCover: Boolean,
    val useTitle: Boolean,
    val useStatus: Boolean,
)

@Composable
private fun ConfirmStage(
    rows: List<ConfirmRow>,
    onRows: (List<ConfirmRow>) -> Unit,
    baseUrl: String,
) {
    val isTv = LocalIsTv.current
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(rows, key = { it.index }) { row ->
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                val rowFocus = rememberFocusState()
                val toggleRow = {
                    onRows(rows.map { if (it.index == row.index) it.copy(isSelected = !it.isSelected) else it })
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    // TV: the whole row is the target — an 18dp checkbox is not
                    // something a D-pad can aim at.
                    modifier = if (isTv) {
                        Modifier
                            .fillMaxWidth()
                            .focusRing(rowFocus.focused, 8.dp)
                            .tvClickable(onFocused = rowFocus::set, onClick = toggleRow)
                            .padding(6.dp)
                    } else {
                        Modifier
                    },
                ) {
                    Box(
                        modifier = if (isTv) Modifier else Modifier.clickable(onClick = toggleRow),
                    ) {
                        CheckBox(row.isSelected)
                    }
                    Box(
                        modifier = Modifier
                            .padding(start = 10.dp)
                            .width(40.dp)
                            .height(56.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(RenzoColors.Muted),
                    ) {
                        if (!row.thumbnailUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = absoluteUrl(baseUrl, row.thumbnailUrl),
                                contentDescription = row.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(
                            row.title,
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                            color = RenzoColors.Foreground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            listOfNotNull(
                                row.provider.takeIf { it.isNotBlank() },
                                row.scanlator.takeIf { it.isNotBlank() && it != row.provider },
                                row.lang.uppercase().takeIf { it.isNotBlank() },
                                "${row.chapterCount} chapters",
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.labelSmall,
                            color = RenzoColors.MutedForeground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
                if (row.isSelected) {
                    // Storage / Cover / Title / Status — mutually exclusive
                    // across sources, exactly like the web's confirm step.
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(start = 62.dp, top = 8.dp),
                    ) {
                        ToggleChip("Storage", row.isStorage) {
                            onRows(rows.map { it.copy(isStorage = it.index == row.index && !row.isStorage) })
                        }
                        ToggleChip("Cover", row.useCover) {
                            onRows(rows.map { it.copy(useCover = it.index == row.index && !row.useCover) })
                        }
                        ToggleChip("Title", row.useTitle) {
                            onRows(rows.map { it.copy(useTitle = it.index == row.index && !row.useTitle) })
                        }
                        ToggleChip("Status", row.useStatus) {
                            onRows(rows.map { it.copy(useStatus = it.index == row.index && !row.useStatus) })
                        }
                    }
                }
            }
            HorizontalDivider(color = RenzoColors.Border.copy(alpha = 0.5f))
        }
    }
}

// ---------------------------------------------------------------------------
// Small pieces
// ---------------------------------------------------------------------------

@Composable
private fun CheckBox(checked: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(
                1.dp,
                if (checked) RenzoColors.Primary else RenzoColors.Border,
                RoundedCornerShape(4.dp),
            )
            .background(if (checked) RenzoColors.Primary else Color.Transparent),
    ) {
        if (checked) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = RenzoColors.PrimaryForeground,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun ToggleChip(label: String, active: Boolean, onClick: () -> Unit) {
    val isTv = LocalIsTv.current
    val focus = rememberFocusState()
    Text(
        label,
        style = MaterialTheme.typography.labelSmall,
        color = if (active) RenzoColors.PrimaryForeground else RenzoColors.MutedForeground,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(
                1.dp,
                if (active) RenzoColors.Primary else RenzoColors.Border,
                RoundedCornerShape(50),
            )
            // Filled = this source owns Storage/Cover/Title/Status (state);
            // the ring is focus. Both stay readable together.
            .background(if (active) RenzoColors.Primary else Color.Transparent)
            .then(
                if (isTv) {
                    Modifier
                        .focusRing(focus.focused, 50.dp)
                        .tvClickable(onFocused = focus::set, onClick = onClick)
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            )
            .padding(horizontal = if (isTv) 14.dp else 10.dp, vertical = if (isTv) 8.dp else 4.dp),
    )
}

@Composable
private fun CenteredNote(text: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxWidth().fillMaxHeight().padding(24.dp),
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = RenzoColors.MutedForeground)
    }
}

// ---------------------------------------------------------------------------
// JSON plumbing — the augment payload is round-tripped verbatim
// ---------------------------------------------------------------------------

private fun buildJsonArrayOfSelected(raw: JsonArray?, selected: Set<String>): JsonArray {
    val picks = raw.orEmpty().filter { element ->
        val obj = runCatching { element.jsonObject }.getOrNull() ?: return@filter false
        val id = runCatching { obj["mihonId"]?.jsonPrimitive?.content }.getOrNull()
            ?: runCatching { obj["providerId"]?.jsonPrimitive?.content }.getOrNull()
        id != null && selected.contains(id)
    }
    return JsonArray(picks)
}

/**
 * Web handleNext(): pick the first source whose language is in
 * preferredLanguages and default Storage/Cover/Title/Status to it. The confirm
 * step then guarantees at least one selected row.
 */
private fun buildConfirmRows(response: JsonObject): List<ConfirmRow> {
    val series = response["series"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: return emptyList()
    if (series.isEmpty()) return emptyList()
    val preferred = response["preferredLanguages"]?.let { runCatching { it.jsonArray }.getOrNull() }
        ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
        .orEmpty()

    var preferredIndex = 0
    outer@ for (lang in preferred) {
        series.forEachIndexed { index, element ->
            val l = runCatching { element.jsonObject["lang"]?.jsonPrimitive?.content }.getOrNull()
            if (l != null && l.equals(lang, ignoreCase = true)) {
                preferredIndex = index
                break@outer
            }
        }
    }

    return series.mapIndexed { index, element ->
        val obj = element.jsonObject
        fun str(key: String): String =
            runCatching { obj[key]?.jsonPrimitive?.content }.getOrNull().orEmpty()
        ConfirmRow(
            index = index,
            provider = str("provider"),
            scanlator = str("scanlator"),
            lang = str("lang"),
            title = str("title"),
            thumbnailUrl = str("thumbnailUrl").takeIf { it.isNotBlank() },
            chapterCount = runCatching { obj["chapterCount"]?.jsonPrimitive?.content?.toDouble()?.toInt() }
                .getOrNull() ?: 0,
            // At least one row must be selected — default to every returned
            // source, with the preferred one owning storage/cover/title/status.
            isSelected = true,
            isStorage = index == preferredIndex,
            useCover = index == preferredIndex,
            useTitle = index == preferredIndex,
            useStatus = index == preferredIndex,
        )
    }
}

/**
 * The on-screen failure text, made specific enough to diagnose from a photo of
 * the screen. Status code first, then the server's own words, then a snippet of
 * whatever it actually sent — a proxy error page and an application rejection
 * look identical without it.
 */
private fun Throwable.addFailureDetail(): String {
    val http = this as? retrofit2.HttpException
    if (http != null) {
        val body = runCatching { http.response()?.errorBody()?.string() }.getOrNull().orEmpty()
        val friendly = serverErrorMessage("")
        return buildString {
            append("Add failed — HTTP ${http.code()}")
            if (friendly.isNotBlank()) append(": ").append(friendly)
            else if (body.isNotBlank()) append(": ").append(body.take(200))
        }
    }
    if (this is java.io.IOException) {
        return "Add failed — couldn't reach the server (${this::class.simpleName}: ${message.orEmpty().take(120)})"
    }
    return "Add failed — ${this::class.simpleName}: ${message.orEmpty().take(160)}"
}

/** The backend tags each dropped source with a reason; explain it like the web. */
private fun droppedMessage(response: JsonObject): String {
    val dropped = response["droppedSeries"]?.let { runCatching { it.jsonArray }.getOrNull() }.orEmpty()
    val providers = dropped.mapNotNull {
        runCatching { it.jsonObject["provider"]?.jsonPrimitive?.content }.getOrNull()
    }.filter { it.isNotBlank() }.distinct()
    val providerList = if (providers.isEmpty()) "the selected source" else providers.joinToString(", ")
    val langs = response["preferredLanguages"]?.let { runCatching { it.jsonArray }.getOrNull() }
        ?.mapNotNull { runCatching { it.jsonPrimitive.content }.getOrNull() }
        ?.joinToString(", ")?.uppercase().orEmpty()
    val reasons = dropped.mapNotNull { runCatching { it.jsonObject["reason"]?.jsonPrimitive?.content }.getOrNull() }
    val hasNoChapters = reasons.contains("no-chapters")
    val hasUnreachable = reasons.contains("unreachable")
    return when {
        hasNoChapters && !hasUnreachable ->
            "No chapters available from $providerList" +
                (if (langs.isNotEmpty()) " in your enabled languages ($langs)" else "") +
                ". This title likely isn't translated in those languages — pick a different source, " +
                "or add the language in the source's settings."
        hasUnreachable && !hasNoChapters ->
            "Couldn't reach $providerList — it may be down or rate-limited. " +
                "Try again in a moment, or pick a different source."
        else ->
            "Couldn't load chapters for $providerList — either no chapters in your enabled languages" +
                (if (langs.isNotEmpty()) " ($langs)" else "") +
                ", or the source is down/rate-limited. Try again, or pick a different source."
    }
}

/**
 * Rebuilds the augmented response with only the selected sources and the
 * user's switch choices — every other field is carried over untouched, which
 * is why the payload stays raw JSON all the way through.
 */
private fun buildSubmitPayload(
    original: JsonObject,
    rows: List<ConfirmRow>,
    existingSeriesId: String? = null,
): JsonObject {
    val series = original["series"]?.let { runCatching { it.jsonArray }.getOrNull() } ?: JsonArray(emptyList())
    val byIndex = rows.associateBy { it.index }
    val updated = series.mapIndexedNotNull { index, element ->
        val row = byIndex[index] ?: return@mapIndexedNotNull null
        if (!row.isSelected) return@mapIndexedNotNull null
        val obj = element.jsonObject
        JsonObject(
            obj.toMutableMap().apply {
                put("isSelected", JsonPrimitive(true))
                put("isStorage", JsonPrimitive(row.isStorage))
                put("useCover", JsonPrimitive(row.useCover))
                put("useTitle", JsonPrimitive(row.useTitle))
                put("useStatus", JsonPrimitive(row.useStatus))
            },
        )
    }
    return JsonObject(
        original.toMutableMap().apply {
            put("series", JsonArray(updated))
            // Name the target explicitly in add-sources mode; the server then
            // skips its fuzzy library match entirely.
            if (existingSeriesId != null) {
                put("existingSeries", JsonPrimitive(true))
                put("existingSeriesId", JsonPrimitive(existingSeriesId))
            }
        },
    )
}

/**
 * The source selection for Add Series, remembered between visits — the native
 * twin of the web step's localStorage entry. Persisting it is what keeps a
 * search scoped to a handful of sources instead of every installed extension.
 */
private const val SEARCH_SOURCES_PREFS = "renzo_prefs"
private const val SEARCH_SOURCES_KEY = "renzo_add_series_sources"

// Stored joined by newline: KeyValuePrefs is a plain string store, and source
// ids cannot contain newlines. The Android pref FILE stays the same
// ("renzo_prefs") so existing installs keep their selection.
private fun loadSearchSources(): List<String> =
    top.levitatemedia.renzo.hub.core.keyValuePrefs(SEARCH_SOURCES_PREFS)
        .getString(SEARCH_SOURCES_KEY + "_v2", null)
        ?.split('\n')?.filter { it.isNotBlank() }
        .orEmpty()

private fun saveSearchSources(ids: List<String>) {
    top.levitatemedia.renzo.hub.core.keyValuePrefs(SEARCH_SOURCES_PREFS)
        .putString(SEARCH_SOURCES_KEY + "_v2", ids.joinToString("\n"))
}
