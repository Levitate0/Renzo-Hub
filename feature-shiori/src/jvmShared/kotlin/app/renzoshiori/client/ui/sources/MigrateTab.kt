package app.renzoshiori.client.ui.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.renzoshiori.client.data.model.MigrationApplyItemDto
import app.renzoshiori.client.data.model.MigrationApplyRequestDto
import app.renzoshiori.client.data.model.MigrationCandidateDto
import app.renzoshiori.client.data.model.MigrationFromDto
import app.renzoshiori.client.data.model.MigrationItemDto
import app.renzoshiori.client.data.model.MigrationRunDto
import app.renzoshiori.client.data.model.MigrationSourcesDto
import app.renzoshiori.client.data.model.MigrationStartRequestDto
import app.renzoshiori.client.data.model.MigrationTargetDto
import app.renzoshiori.client.data.model.SourceSpeedDto
import app.renzoshiori.client.data.network.SourcesApi
import app.renzoshiori.client.data.network.absoluteUrl
import app.renzoshiori.client.data.network.serverErrorMessage
import app.renzoshiori.client.ui.library.formatChapter
import app.renzoshiori.client.ui.theme.RenzoColors
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

private const val PHASE_SEARCHING = "searching"
private const val PHASE_READY = "ready"
private const val PHASE_APPLYING = "applying"
private const val PHASE_DONE = "done"

private const val STATUS_MATCHED = "matched"
private const val STATUS_MIGRATED = "migrated"
private const val STATUS_SKIPPED = "skipped"
private const val STATUS_FAILED = "failed"

private const val OLD_SOURCE_DISABLE = "disable"
private const val OLD_SOURCE_KEEP = "keep"

/** A match at least this complete starts ticked in the preview. */
private const val PRECHECK_COVERAGE = 0.8

private const val POLL_MS = 2_000L

private fun isMatched(item: MigrationItemDto) = item.status == STATUS_MATCHED && item.match != null

private fun percent(fraction: Double): Int = (fraction * 100).roundToInt()

/** "{provider}", plus " ({language})" unless the language is "en" or "" (from and target rows). Twin: migrate-tab.tsx sourceName. */
private fun migrateSourceName(provider: String, language: String): String =
    if (language.isEmpty() || language == "en") provider else "$provider ($language)"

/** The SPEED line. Twin: migrate-tab.tsx speedLine. */
private fun migrateSpeedLine(speed: SourceSpeedDto): String {
    if (speed.attempts == 0) return "No downloads lately"
    val median = speed.medianSeconds
    val head = if (median != null) "≈ ${median.roundToInt()} s a chapter · " else ""
    return head + "${percent(speed.successRate ?: 0.0)}% downloaded"
}

private fun fromKey(f: MigrationFromDto) = f.provider + "\u0000" + f.language

/** A run's preview choices. */
private data class MigrateChoices(
    val checked: List<String>,
    val picked: Map<String, MigrationCandidateDto>,
    val oldSource: String,
)

/**
 * The preview's choices per run, kept while the app runs: leaving the tab drops
 * it from composition, and coming back must not re-tick what was unticked or undo
 * a picked match. Twin: migrate-tab.tsx savedChoices.
 */
private val savedMigrateChoices = mutableMapOf<String, MigrateChoices>()

/** A candidate in a match menu. Twin: migrate-tab.tsx candidateLabel. */
private fun migrateCandidateLabel(c: MigrationCandidateDto): String =
    "${c.provider}: ${c.title} · ${percent(c.coverage)}%" + if (c.suspect) " · many more chapters" else ""

/** Under a chosen candidate the server flagged `suspect`. Twin: migrate-tab.tsx SUSPECT_NOTE. */
private const val SUSPECT_NOTE = "Lists many more chapters than this series: it may be another series with the same name."

/** text-amber-400 (the web's dark-mode amber). */
private val SuspectAmber = Color(0xFFFBBF24)

/**
 * Sources › Migrate — move every series on a source to other sources, for when
 * a site breaks or blocks you.
 *
 * PARITY: transliterated from RenzoFrontend
 * src/components/comp/sources/migrate-tab.tsx; the wording is shared word for
 * word, so change both together. Server: RenzoBackend
 * Controllers/MigrateController.cs + Services/Series/SourceMigrationService.cs.
 *
 * The view follows the run's phase: none → start, searching/applying →
 * progress (polled every 2 s), ready → preview, done → results, failed → the
 * error and "Start over".
 */
@Composable
internal fun MigrateTab(api: SourcesApi?, baseUrl: String, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()

    var loaded by remember { mutableStateOf(false) }
    var run by remember { mutableStateOf<MigrationRunDto?>(null) }
    var busy by remember { mutableStateOf(false) }

    // ── Start view state ─────────────────────────────────────────────────────
    var sources by remember { mutableStateOf<MigrationSourcesDto?>(null) }
    var sourcesError by remember { mutableStateOf<String?>(null) }
    var selectedFromKey by remember { mutableStateOf<String?>(null) }
    /** Every target in the user's order; the from-source's own provider is hidden, not removed. */
    val targetOrder = remember { mutableStateListOf<MigrationTargetDto>() }
    val checkedTargets = remember { mutableStateListOf<String>() }

    // ── Preview state ────────────────────────────────────────────────────────
    val checkedItems = remember { mutableStateListOf<String>() }
    /** A row's chosen candidate when it is one of its alternatives (else its match). */
    val picked = remember { mutableStateMapOf<String, MigrationCandidateDto>() }
    var oldSource by remember { mutableStateOf(OLD_SOURCE_DISABLE) }

    fun chosenOf(item: MigrationItemDto): MigrationCandidateDto? = picked[item.seriesId] ?: item.match

    fun saveChoices() {
        val current = run ?: return
        if (current.phase == PHASE_READY) {
            savedMigrateChoices[current.id] = MigrateChoices(checkedItems.toList(), picked.toMap(), oldSource)
        }
    }

    /** Every write to [run] goes through here, so a run that turns ready is seeded once. */
    fun adopt(next: MigrationRunDto?) {
        val prev = run
        if (next != null && next.phase == PHASE_READY && (prev?.id != next.id || prev.phase != PHASE_READY)) {
            checkedItems.clear()
            picked.clear()
            oldSource = OLD_SOURCE_DISABLE
            val saved = savedMigrateChoices[next.id]
            if (saved != null) {
                checkedItems.addAll(saved.checked)
                picked.putAll(saved.picked)
                oldSource = saved.oldSource
            } else {
                next.items.forEach { item ->
                    val m = item.match
                    if (isMatched(item) && m != null && m.coverage >= PRECHECK_COVERAGE) checkedItems.add(item.seriesId)
                }
            }
        }
        run = next
    }

    // On open: GET current (204 = none → start view).
    LaunchedEffect(Unit) {
        val result = runCatching { api?.currentMigration() }
        val response = result.getOrNull()
        val error = when {
            result.isFailure -> result.exceptionOrNull()!!.serverErrorMessage("Failed to load the migration.")
            response == null -> null
            response.isSuccessful -> {
                adopt(response.body())
                null
            }
            else -> retrofit2.HttpException(response)
                .serverErrorMessage("Failed to load the migration (HTTP ${response.code()}).")
        }
        // Shown after: a snackbar is awaited until it goes (4 s), and the tab must
        // not sit on "Loading…" meanwhile.
        loaded = true
        error?.let { snackbar.showSnackbar(it) }
    }

    // PROGRESS: poll every 2 s while searching or applying. A phase change
    // restarts this effect (it is a key), so the loop only ever watches one phase.
    val runId = run?.id
    val phase = run?.phase
    LaunchedEffect(runId, phase) {
        if (runId == null || (phase != PHASE_SEARCHING && phase != PHASE_APPLYING)) return@LaunchedEffect
        while (true) {
            delay(POLL_MS)
            val next = try {
                api?.migration(runId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: retrofit2.HttpException) {
                if (e.code() == 404) {
                    adopt(null)
                    return@LaunchedEffect
                }
                null // transient — try again on the next tick
            } catch (e: Exception) {
                null
            }
            if (next != null) adopt(next)
        }
    }

    // START VIEW: (re)load the sources whenever it is shown.
    val showStart = loaded && run == null
    LaunchedEffect(showStart) {
        if (!showStart) return@LaunchedEffect
        val result = runCatching { api?.migrationSources() }
        val list = result.getOrNull()
        if (list == null) {
            result.exceptionOrNull()?.let { sourcesError = it.serverErrorMessage(it.message ?: "Could not load the sources.") }
            return@LaunchedEffect
        }
        sourcesError = null
        sources = list
        if (list.from.none { fromKey(it) == selectedFromKey }) selectedFromKey = null
        targetOrder.clear()
        targetOrder.addAll(list.targets)
        checkedTargets.clear()
        checkedTargets.addAll(list.targets.filter { it.suggested }.map { it.id })
    }

    fun findMatches(from: MigrationFromDto, visibleTargets: List<MigrationTargetDto>) {
        // Checked targets keep their on-screen order: that is the order tried.
        val ids = visibleTargets.filter { it.id in checkedTargets }.map { it.id }
        if (ids.isEmpty()) return
        scope.launch {
            busy = true
            val result = runCatching {
                api?.startMigration(MigrationStartRequestDto(from = from.provider, language = from.language, targets = ids))
                    ?: error("Not connected to a server.")
            }
            busy = false // before the snackbar, which is awaited until it goes
            result
                .onSuccess { adopt(it) }
                // 409 {error}: the server's own words.
                .onFailure { snackbar.showSnackbar(it.serverErrorMessage(it.message ?: "Could not start the migration.")) }
        }
    }

    fun applyMigration(current: MigrationRunDto) {
        val items = current.items.filter { it.seriesId in checkedItems }.mapNotNull { item ->
            chosenOf(item)?.let { MigrationApplyItemDto(seriesId = item.seriesId, mihonId = it.mihonId) }
        }
        if (items.isEmpty()) return
        scope.launch {
            busy = true
            val result = runCatching {
                api?.applyMigration(current.id, MigrationApplyRequestDto(items = items, oldSource = oldSource))
                    ?: error("Not connected to a server.")
            }
            busy = false // before the snackbar, which is awaited until it goes
            result
                .onSuccess { adopt(it) }
                .onFailure { snackbar.showSnackbar(it.serverErrorMessage(it.message ?: "Could not start migrating.")) }
        }
    }

    /** "Start over" / "Done": DELETE, then the start view. */
    fun discard(current: MigrationRunDto) {
        savedMigrateChoices.remove(current.id)
        scope.launch {
            busy = true
            runCatching { api?.discardMigration(current.id) }
            busy = false
            checkedItems.clear()
            picked.clear()
            adopt(null)
        }
    }

    val current = run
    when {
        !loaded -> CenteredMuted("Loading…")

        current == null -> {
            val all = sources
            val loadError = sourcesError
            if (all == null && loadError != null) {
                Text(
                    loadError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RenzoColors.Red,
                    modifier = Modifier.padding(16.dp),
                )
            } else if (all == null) {
                CenteredMuted("Loading…")
            } else {
                val selectedFrom = all.from.firstOrNull { fromKey(it) == selectedFromKey }
                val visibleTargets = targetOrder.filter { selectedFrom == null || it.provider != selectedFrom.provider }
                StartView(
                    sources = all,
                    selectedFromKey = selectedFromKey,
                    onSelectFrom = { selectedFromKey = fromKey(it) },
                    visibleTargets = visibleTargets,
                    checkedTargets = checkedTargets,
                    onToggleTarget = { id ->
                        if (id in checkedTargets) checkedTargets.remove(id) else checkedTargets.add(id)
                    },
                    onMoveTarget = { index, up ->
                        // Swap with the neighbour ON SCREEN; hidden targets keep their place.
                        val swap = if (up) index - 1 else index + 1
                        if (swap in visibleTargets.indices) {
                            val a = targetOrder.indexOfFirst { it.id == visibleTargets[index].id }
                            val b = targetOrder.indexOfFirst { it.id == visibleTargets[swap].id }
                            if (a >= 0 && b >= 0) {
                                val tmp = targetOrder[a]
                                targetOrder[a] = targetOrder[b]
                                targetOrder[b] = tmp
                            }
                        }
                    },
                    canFind = selectedFrom != null && visibleTargets.any { it.id in checkedTargets } && !busy,
                    finding = busy,
                    onFind = { if (selectedFrom != null) findMatches(selectedFrom, visibleTargets) },
                )
            }
        }

        current.phase == PHASE_SEARCHING || current.phase == PHASE_APPLYING -> ProgressView(
            run = current,
            busy = busy,
            onCancel = if (current.phase == PHASE_SEARCHING) ({ discard(current) }) else null,
        )

        current.phase == PHASE_READY -> PreviewView(
            run = current,
            baseUrl = baseUrl,
            checkedItems = checkedItems,
            chosenOf = { chosenOf(it) },
            onToggleItem = { id ->
                if (id in checkedItems) checkedItems.remove(id) else checkedItems.add(id)
                saveChoices()
            },
            onPick = { id, candidate ->
                picked[id] = candidate
                saveChoices()
            },
            oldSource = oldSource,
            onOldSource = {
                oldSource = it
                saveChoices()
            },
            busy = busy,
            onMigrate = { applyMigration(current) },
            onStartOver = { discard(current) },
        )

        current.phase == PHASE_DONE -> ResultsView(
            run = current,
            chosenOf = { chosenOf(it) },
            busy = busy,
            onDone = { discard(current) },
        )

        // "failed" (or a phase this client does not know).
        else -> FailedView(
            error = current.error,
            busy = busy,
            onStartOver = { discard(current) },
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// START VIEW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StartView(
    sources: MigrationSourcesDto,
    selectedFromKey: String?,
    onSelectFrom: (MigrationFromDto) -> Unit,
    visibleTargets: List<MigrationTargetDto>,
    checkedTargets: List<String>,
    onToggleTarget: (String) -> Unit,
    onMoveTarget: (Int, Boolean) -> Unit,
    canFind: Boolean,
    finding: Boolean,
    onFind: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "from-heading") {
                MigrateHeading(
                    "Migrate from",
                    "Move every series on a source to other sources, for when a site breaks or blocks you. " +
                        "Downloads and read progress stay; the new source is added to each series.",
                )
            }
            items(sources.from, key = { "from-" + fromKey(it) }) { from ->
                val selected = fromKey(from) == selectedFromKey
                MigrateCardRow(selected = selected, onClick = { onSelectFrom(from) }) {
                    RadioButton(
                        selected = selected,
                        onClick = { onSelectFrom(from) },
                        colors = RadioButtonDefaults.colors(
                            selectedColor = RenzoColors.Primary,
                            unselectedColor = RenzoColors.MutedForeground,
                        ),
                    )
                    Column(modifier = Modifier.padding(start = 4.dp).weight(1f)) {
                        Text(
                            migrateSourceName(from.provider, from.language),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = RenzoColors.Foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "${from.seriesCount} series" +
                                (if (from.disabledCount > 0) " · ${from.disabledCount} switched off" else ""),
                            fontSize = 12.sp,
                            color = RenzoColors.MutedForeground,
                        )
                        SpeedText(from.speed)
                    }
                }
            }

            item(key = "targets-heading") {
                Spacer(Modifier.height(16.dp))
                MigrateHeading(
                    "Look for them on",
                    "Tried in this order; the first that lists nine in ten of a series' chapters is used.",
                )
            }
            itemsIndexed(visibleTargets, key = { _, t -> "t-" + t.id }) { index, target ->
                val checked = target.id in checkedTargets
                MigrateCardRow(selected = false, onClick = { onToggleTarget(target.id) }) {
                    Checkbox(
                        checked = checked,
                        onCheckedChange = { onToggleTarget(target.id) },
                        colors = migrateCheckboxColors(),
                    )
                    Column(modifier = Modifier.padding(start = 4.dp).weight(1f)) {
                        Text(
                            migrateSourceName(target.provider, target.language),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = RenzoColors.Foreground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        SpeedText(target.speed)
                    }
                    // Same up/down control as the Default priority order tab.
                    MoveArrows(
                        name = target.provider,
                        canUp = index > 0,
                        canDown = index < visibleTargets.lastIndex,
                        onMove = { up -> onMoveTarget(index, up) },
                    )
                }
            }
        }

        StickyBar {
            PrimaryActionButton(
                label = "Find matches",
                enabled = canFind,
                loading = finding,
                onClick = onFind,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PROGRESS VIEW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
/** [onCancel] only while searching: the server stops the search; an apply runs to the end. */
private fun ProgressView(run: MigrationRunDto, busy: Boolean, onCancel: (() -> Unit)?) {
    val verb = if (run.phase == PHASE_APPLYING) "Migrating" else "Searching"
    val fraction = if (run.total > 0) (run.done.toFloat() / run.total).coerceIn(0f, 1f) else 0f
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        Text(
            "$verb ${run.done} of ${run.total} series…",
            style = MaterialTheme.typography.bodyMedium,
            color = RenzoColors.Foreground,
        )
        // shadcn <Progress>: a muted track with a primary fill.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(50))
                .background(RenzoColors.Muted),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(50))
                    .background(RenzoColors.Primary),
            )
        }
        if (onCancel != null) {
            OutlineActionButton(label = "Cancel", icon = null, enabled = !busy, onClick = onCancel)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PREVIEW VIEW
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewView(
    run: MigrationRunDto,
    baseUrl: String,
    checkedItems: List<String>,
    chosenOf: (MigrationItemDto) -> MigrationCandidateDto?,
    onToggleItem: (String) -> Unit,
    onPick: (String, MigrationCandidateDto) -> Unit,
    oldSource: String,
    onOldSource: (String) -> Unit,
    busy: Boolean,
    onMigrate: () -> Unit,
    onStartOver: () -> Unit,
) {
    // Listed with a checkbox first: the matched, and any other row once one of
    // its candidates is picked ("Found only titles with many more chapters…
    // check before choosing"). Then the rest; each group by title.
    // Twin: migrate-tab.tsx PreviewView `listed`.
    val byTitle = compareBy(String.CASE_INSENSITIVE_ORDER) { it: MigrationItemDto -> it.title }
    val matched = run.items.filter { isMatched(it) }
    val listed = run.items.filter { chosenOf(it) != null }.sortedWith(byTitle)
    val rest = run.items.filter { chosenOf(it) == null }.sortedWith(byTitle)
    val n = listed.count { it.seriesId in checkedItems }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") {
                Text(
                    "${matched.size} of ${run.total} series found on the chosen sources",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = RenzoColors.Foreground,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(listed, key = { "m-" + it.seriesId }) { item ->
                val chosen = chosenOf(item) ?: return@items
                MatchedRow(
                    item = item,
                    chosen = chosen,
                    baseUrl = baseUrl,
                    checked = item.seriesId in checkedItems,
                    onToggle = { onToggleItem(item.seriesId) },
                    onPick = { onPick(item.seriesId, it) },
                )
            }
            items(rest, key = { "u-" + it.seriesId }) { item ->
                MigrateCardRow(selected = false, onClick = null) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = RenzoColors.Foreground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        item.message?.let {
                            Text(it, fontSize = 12.sp, color = RenzoColors.MutedForeground)
                        }
                        if (item.alternatives.isNotEmpty()) {
                            // Picking one lists the row above, ticked. Twin: migrate-tab.tsx "Choose a match".
                            OtherMatchesPicker(item.alternatives, label = "Choose a match") { alt ->
                                onPick(item.seriesId, alt)
                                if (item.seriesId !in checkedItems) onToggleItem(item.seriesId)
                            }
                        }
                    }
                }
            }
        }

        StickyBar {
            // Wraps on a phone: label + both choices do not fit 360dp on one line.
            FlowRow {
                Text(
                    "Old source:",
                    fontSize = 12.sp,
                    color = RenzoColors.MutedForeground,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
                OldSourceChoice("Switch it off", oldSource == OLD_SOURCE_DISABLE) { onOldSource(OLD_SOURCE_DISABLE) }
                OldSourceChoice("Leave it on", oldSource == OLD_SOURCE_KEEP) { onOldSource(OLD_SOURCE_KEEP) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PrimaryActionButton(
                    label = "Migrate $n series",
                    enabled = n > 0 && !busy,
                    loading = busy,
                    onClick = onMigrate,
                )
                OutlineActionButton(
                    label = "Start over",
                    icon = null,
                    enabled = !busy,
                    onClick = onStartOver,
                )
            }
        }
    }
}

@Composable
private fun MatchedRow(
    item: MigrationItemDto,
    chosen: MigrationCandidateDto,
    baseUrl: String,
    checked: Boolean,
    onToggle: () -> Unit,
    onPick: (MigrationCandidateDto) -> Unit,
) {
    MigrateCardRow(selected = false, onClick = onToggle) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() }, colors = migrateCheckboxColors())
        SeriesThumb(item, baseUrl)
        Column(modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp).weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = RenzoColors.Foreground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "→ ${chosen.provider}: ${chosen.title}" +
                    (chosen.foundAs?.let { " · found as “$it”" } ?: ""),
                fontSize = 12.sp,
                color = RenzoColors.Foreground.copy(alpha = 0.85f),
            )
            Text(
                listOfNotNull(
                    "${chosen.chapterCount} chapters",
                    chosen.latest?.let { "up to ${formatChapter(it)}" },
                    "${percent(chosen.coverage)}% of ${item.chapters}",
                ).joinToString(" · "),
                fontSize = 12.sp,
                color = RenzoColors.MutedForeground,
            )
            if (chosen.suspect) {
                Text(SUSPECT_NOTE, fontSize = 12.sp, color = SuspectAmber)
            }
            // Every candidate but the one shown, so a pick can be undone (before
            // any pick that is exactly the alternatives). Twin: migrate-tab.tsx `others`.
            val others = (listOfNotNull(item.match) + item.alternatives).filter { it.mihonId != chosen.mihonId }
            if (others.isNotEmpty()) {
                OtherMatchesPicker(others, label = "Other matches", onPick = onPick)
            }
        }
    }
}

/** Compact "Other matches" / "Choose a match" menu: picking one makes it the row's chosen candidate. */
@Composable
private fun OtherMatchesPicker(
    others: List<MigrationCandidateDto>,
    label: String,
    onPick: (MigrationCandidateDto) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier = Modifier.padding(top = 4.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .border(1.dp, RenzoColors.Border, RoundedCornerShape(6.dp))
                .clickable { open = true }
                .padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
        ) {
            Text(label, fontSize = 12.sp, color = RenzoColors.Foreground)
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = null,
                tint = RenzoColors.MutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
        DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            containerColor = RenzoColors.Popover,
        ) {
            others.forEach { alt ->
                DropdownMenuItem(
                    text = {
                        Text(migrateCandidateLabel(alt), color = RenzoColors.Foreground)
                    },
                    onClick = {
                        open = false
                        onPick(alt)
                    },
                )
            }
        }
    }
}

@Composable
private fun OldSourceChoice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable { onClick() }.padding(end = 8.dp),
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
            colors = RadioButtonDefaults.colors(
                selectedColor = RenzoColors.Primary,
                unselectedColor = RenzoColors.MutedForeground,
            ),
        )
        Text(label, fontSize = 12.sp, color = RenzoColors.Foreground)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RESULTS VIEW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ResultsView(
    run: MigrationRunDto,
    chosenOf: (MigrationItemDto) -> MigrationCandidateDto?,
    busy: Boolean,
    onDone: () -> Unit,
) {
    val migrated = run.items.count { it.status == STATUS_MIGRATED }
    val skipped = run.items.count { it.status == STATUS_SKIPPED }
    val failed = run.items.count { it.status == STATUS_FAILED }
    // Only what the apply touched (plus searches that failed), by title; rows left
    // unticked or not found are not listed. Twin: migrate-tab.tsx ResultsView `finished`.
    val items = run.items
        .filter { it.status == STATUS_MIGRATED || it.status == STATUS_SKIPPED || it.status == STATUS_FAILED }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "summary") {
                Text(
                    "Migrated $migrated · Skipped $skipped · Failed $failed",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = RenzoColors.Foreground,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            items(items, key = { "r-" + it.seriesId }) { item ->
                // Web: lucide CheckCircle2 (primary) / MinusCircle (muted) / XCircle (destructive).
                val (icon, tint) = when (item.status) {
                    STATUS_MIGRATED -> Icons.Outlined.CheckCircle to RenzoColors.Primary
                    STATUS_SKIPPED -> Icons.Outlined.RemoveCircleOutline to RenzoColors.MutedForeground
                    else -> Icons.Outlined.Cancel to RenzoColors.Red
                }
                val line = if (item.status == STATUS_MIGRATED) "Added ${chosenOf(item)?.provider.orEmpty()}" else item.message
                MigrateCardRow(selected = false, onClick = null) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                    Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 8.dp, bottom = 8.dp)) {
                        Text(
                            item.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = RenzoColors.Foreground,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!line.isNullOrBlank()) Text(line, fontSize = 12.sp, color = RenzoColors.MutedForeground)
                    }
                }
            }
        }
        StickyBar {
            PrimaryActionButton(label = "Done", enabled = !busy, loading = busy, onClick = onDone)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// FAILED
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun FailedView(error: String?, busy: Boolean, onStartOver: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        if (!error.isNullOrBlank()) {
            Text(error, style = MaterialTheme.typography.bodyMedium, color = RenzoColors.Red)
        }
        OutlineActionButton(label = "Start over", icon = null, enabled = !busy, loading = busy, onClick = onStartOver)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Small pieces
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MigrateHeading(title: String, helper: String) {
    Column(modifier = Modifier.padding(bottom = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = RenzoColors.Foreground,
        )
        Text(helper, style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
    }
}

/** Muted small text, as every SPEED line is. */
@Composable
private fun SpeedText(speed: SourceSpeedDto) {
    Text(migrateSpeedLine(speed), fontSize = 12.sp, color = RenzoColors.MutedForeground)
}

/** The card row the Default priority order tab uses (bg-card, border-border/60, rounded-lg). */
@Composable
private fun MigrateCardRow(
    selected: Boolean,
    onClick: (() -> Unit)?,
    content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) RenzoColors.Primary.copy(alpha = 0.06f) else RenzoColors.Card)
            .border(
                1.dp,
                if (selected) RenzoColors.Primary.copy(alpha = 0.60f) else RenzoColors.Border.copy(alpha = 0.60f),
                shape,
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
            .then(if (onClick == null) Modifier.padding(start = 8.dp) else Modifier),
        content = content,
    )
}

/** Up/down arrows — the SortableProviderRow control from DefaultPriorityOrderTab.kt. */
@Composable
private fun MoveArrows(name: String, canUp: Boolean, canDown: Boolean, onMove: (Boolean) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = canUp) { onMove(true) }
                .alpha(if (canUp) 1f else 0.3f),
        ) {
            Icon(
                Icons.Filled.KeyboardArrowUp,
                contentDescription = "Move $name up",
                tint = RenzoColors.MutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(4.dp))
                .clickable(enabled = canDown) { onMove(false) }
                .alpha(if (canDown) 1f else 0.3f),
        ) {
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = "Move $name down",
                tint = RenzoColors.MutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Small cover from the server's thumbnailUrl — the same AsyncImage + absoluteUrl the add-series rows use. */
@Composable
private fun SeriesThumb(item: MigrationItemDto, baseUrl: String) {
    Box(
        modifier = Modifier
            .width(36.dp)
            .height(50.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(RenzoColors.Muted),
    ) {
        val thumb = item.thumbnailUrl
        if (!thumb.isNullOrBlank()) {
            AsyncImage(
                model = absoluteUrl(baseUrl, thumb),
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** The sticky bottom bar of the Default priority order tab. */
@Composable
private fun StickyBar(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(RenzoColors.Background)
            .border(1.dp, RenzoColors.Border.copy(alpha = 0.60f), RoundedCornerShape(8.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun CenteredMuted(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
    }
}

@Composable
private fun migrateCheckboxColors() = CheckboxDefaults.colors(
    checkedColor = RenzoColors.Primary,
    uncheckedColor = RenzoColors.MutedForeground,
    checkmarkColor = RenzoColors.PrimaryForeground,
)
