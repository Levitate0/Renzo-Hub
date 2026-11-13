package app.renzoshiori.client.ui.series

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.renzoshiori.client.data.network.MatchInfoDto
import app.renzoshiori.client.data.network.NEW_PROVIDER_SENTINEL
import app.renzoshiori.client.data.network.ProviderMatchChapterDto
import app.renzoshiori.client.data.network.ProviderMatchDto
import app.renzoshiori.client.ui.components.RibbonSelect
import app.renzoshiori.client.ui.components.ScrimDialog
import app.renzoshiori.client.ui.components.SelectOption
import app.renzoshiori.client.ui.theme.RenzoColors
import app.renzoshiori.client.ui.tv.LocalIsTv
import app.renzoshiori.client.ui.tv.focusRing
import app.renzoshiori.client.ui.tv.rememberFocusState
import app.renzoshiori.client.ui.tv.tvClickable
import java.math.BigDecimal
import app.renzoshiori.client.ui.util.oneLine

/** The provider Select's "Create New Provider" item (web `value="__new__"`). */
private const val NEW_ITEM = "__new__"

/**
 * provider-match-dialog.tsx — "Search match" on an UNKNOWN source: its orphan
 * files listed against the series' other sources, so each file can be handed
 * to the source it really belongs to (or to a new user-made one).
 *
 * Transliterated row for row: the Filename / Name / Source / Number table with
 * the select-all checkbox, the "Providers:" Select + Match, "Range Fill:"
 * Start / Step / Fill, the Create New Provider form, and the "(n of m
 * selected)" + Cancel + OK footer. Deliberate departures, each from
 * hub/docs/PARITY-GAPS.md:
 *
 *  - Filename and Name are READ-ONLY here, though the web renders them as
 *    inputs. The backend finds each orphan BY its filename
 *    (SeriesProviderService.SetMatchAsync), so an edited filename silently
 *    drops the row; and the name sent back is never read. Number is the only
 *    value the server uses, so it is the only field.
 *  - OK accepts a PARTIAL match. The web demands every row be assigned; the
 *    backend skips unassigned rows and keeps the unknown source for them, so
 *    the user can fix the rows that line up now and come back for the rest.
 *    The footer counts the unassigned rows so a partial save is deliberate.
 *  - No mouse paint-drag, edge auto-scroll or Ctrl+A (meaningless on a D-pad
 *    or a touchscreen). Tap toggles a row; the chapters toolbar's own
 *    Select all / Select in-between / Swap chips cover ranges.
 *  - Range Fill adds in decimal, not binary floating point: 1.1 + 2 × 0.1
 *    must reach the server as 1.3, not 1.3000000000000003, or the
 *    `Number ==` lookup against the target source misses.
 */
@Composable
fun ProviderMatchDialog(
    match: ProviderMatchDto?,
    loading: Boolean,
    saving: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (ProviderMatchDto) -> Unit,
) {
    val isTv = LocalIsTv.current
    // Working copy of the rows, reset whenever a new match arrives (the web's
    // useEffect on providerMatch). Number text is kept apart from the parsed
    // value so "12." survives being typed.
    val chapters = remember(match) { mutableStateListOf<ProviderMatchChapterDto>().apply { match?.chapters?.let { addAll(it) } } }
    val numberText = remember(match) { mutableStateListOf<String>().apply { match?.chapters?.forEach { add(formatNumber(it.chapterNumber)) } } }
    var selected by remember(match) { mutableStateOf(emptySet<Int>()) }
    var selectedMatchInfoId by remember(match) { mutableStateOf("") }
    var rangeStart by remember(match) { mutableStateOf("") }
    var rangeStep by remember(match) { mutableStateOf("1") }
    var showNewProviderForm by remember(match) { mutableStateOf(false) }
    var newProviderName by remember(match) { mutableStateOf("") }
    var newProviderScanlator by remember(match) { mutableStateOf("") }
    var newProviderLanguage by remember(match) { mutableStateOf("en") }

    // Range start follows the first selected row's number (web useEffect on
    // the selection). `chapterNumber` truthiness: 0 does not count, as on the web.
    val firstSelected = selected.minOrNull()
    val firstNumber = firstSelected?.let { chapters.getOrNull(it)?.chapterNumber }
    LaunchedEffect(selected, firstNumber) {
        if (firstNumber != null && firstNumber != 0.0) rangeStart = formatNumber(firstNumber)
    }

    val matchInfos = match?.matchInfos.orEmpty()

    fun matchInfoFor(id: String?): MatchInfoDto? = when {
        id == NEW_PROVIDER_SENTINEL -> MatchInfoDto(
            id = NEW_PROVIDER_SENTINEL,
            provider = newProviderName.ifEmpty { "New Provider" },
            scanlator = newProviderScanlator,
            language = newProviderLanguage,
        )
        id.isNullOrEmpty() -> null
        else -> matchInfos.firstOrNull { it.id == id }
    }

    fun handleMatchSelected() {
        if (selectedMatchInfoId.isEmpty()) return
        selected.forEach { i -> chapters[i] = chapters[i].copy(matchInfoId = selectedMatchInfoId) }
    }

    fun handleFillRange() {
        val start = rangeStart.trim().toBigDecimalOrNull() ?: return
        val step = rangeStep.trim().toBigDecimalOrNull() ?: return
        selected.sorted().forEachIndexed { i, index ->
            val n = start + step * BigDecimal(i)
            chapters[index] = chapters[index].copy(chapterNumber = n.toDouble())
            numberText[index] = formatNumber(n.toDouble())
        }
    }

    fun handleProviderSelect(value: String) {
        if (value == NEW_ITEM) {
            showNewProviderForm = true
            selectedMatchInfoId = ""
        } else {
            selectedMatchInfoId = value
            showNewProviderForm = false
        }
    }

    fun handleNewProviderCancel() {
        showNewProviderForm = false
        newProviderName = ""
        newProviderScanlator = ""
        newProviderLanguage = "en"
    }

    val usesNewProvider = chapters.any { it.matchInfoId == NEW_PROVIDER_SENTINEL }
    val unassigned = chapters.count { it.matchInfoId == null }
    val canSave = chapters.any { it.matchInfoId != null } &&
        (!usesNewProvider || newProviderName.trim().isNotEmpty())

    fun handleSave() {
        if (match == null || !canSave) return
        val infos = if (usesNewProvider) {
            // The sentinel MatchInfo tells the backend what source to create.
            matchInfos + MatchInfoDto(
                id = NEW_PROVIDER_SENTINEL,
                provider = newProviderName,
                scanlator = newProviderScanlator,
                language = newProviderLanguage,
            )
        } else matchInfos
        onSave(match.copy(matchInfos = infos, chapters = chapters.toList()))
    }

    ScrimDialog(onDismiss = onDismiss) {
        // DialogContent max-w-[85vw]; the table's h-[70vh] becomes "the rest of
        // a 90%-tall card" so the controls below it never fall off a TV.
        Column(
            modifier = Modifier
                .fillMaxWidth(if (isTv) 0.85f else 0.92f)
                .widthIn(max = 1400.dp)
                .fillMaxHeight(0.9f)
                .clip(MaterialTheme.shapes.large)
                .background(RenzoColors.Background)
                .border(1.dp, RenzoColors.Border, MaterialTheme.shapes.large)
                .padding(if (isTv) 20.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Match Source to Chapters",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = RenzoColors.Foreground,
            )

            when {
                loading -> CenteredNote(
                    title = "Loading match data...",
                    detail = "Please wait while we fetch the sources information.",
                    titleColor = RenzoColors.Foreground,
                    modifier = Modifier.weight(1f),
                )
                match == null -> CenteredNote(
                    title = "Failed to load match data",
                    detail = "Please try again.",
                    titleColor = Red500,
                    modifier = Modifier.weight(1f),
                )
                else -> {
                    // ── Upper scrollable area with chapter rows ──
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .border(1.dp, RenzoColors.Border, MaterialTheme.shapes.small),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(RenzoColors.Muted.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                        ) {
                            MatchCheckbox(
                                checked = chapters.isNotEmpty() && selected.size == chapters.size,
                                onToggle = {
                                    selected = if (selected.size == chapters.size) emptySet() else chapters.indices.toSet()
                                },
                            )
                            HeaderCell("Filename", Modifier.weight(0.35f))
                            HeaderCell("Name", Modifier.weight(0.35f))
                            HeaderCell("Source", Modifier.weight(0.2f))
                            HeaderCell("Number", Modifier.width(NUMBER_WIDTH))
                        }
                        HairLine(RenzoColors.Border)
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().weight(1f),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                        ) {
                            itemsIndexed(chapters) { index, chapter ->
                                ChapterRow(
                                    chapter = chapter,
                                    numberText = numberText.getOrElse(index) { "" },
                                    isSelected = index in selected,
                                    source = matchInfoFor(chapter.matchInfoId),
                                    onToggle = {
                                        selected = if (index in selected) selected - index else selected + index
                                    },
                                    onNumberChange = { text ->
                                        numberText[index] = text
                                        chapters[index] = chapters[index].copy(chapterNumber = text.trim().toDoubleOrNull())
                                    },
                                )
                            }
                        }
                    }

                    // ── Selection helpers (SeriesChaptersSection's toolbar) ──
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val allSelected = chapters.isNotEmpty() && selected.size == chapters.size
                        RenzoChip(
                            label = if (allSelected) "Deselect all" else "Select all",
                            onClick = { selected = if (allSelected) emptySet() else chapters.indices.toSet() },
                        )
                        RenzoChip(
                            label = "Select in-between",
                            enabled = selected.isNotEmpty(),
                            onClick = {
                                val lo = selected.minOrNull()
                                val hi = selected.maxOrNull()
                                if (lo != null && hi != null) selected = selected + (lo..hi)
                            },
                        )
                        RenzoChip(
                            label = "Swap",
                            icon = Icons.Filled.SwapHoriz,
                            onClick = { selected = chapters.indices.toSet() - selected },
                        )
                    }

                    // ── Bottom control area ──
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .background(RenzoColors.Muted.copy(alpha = 0.2f))
                            .border(1.dp, RenzoColors.Border, MaterialTheme.shapes.small)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ControlLabel("Providers:")
                                val options = matchInfos.filter { it.id.isNotEmpty() }.map {
                                    SelectOption(value = it.id, label = sourceLabel(it))
                                } + SelectOption(
                                    value = NEW_ITEM,
                                    // Once created, the trigger names the new
                                    // source, as the web's renderProviderWithFlag does.
                                    label = if (selectedMatchInfoId == NEW_PROVIDER_SENTINEL) {
                                        sourceLabel(matchInfoFor(NEW_PROVIDER_SENTINEL)!!)
                                    } else "Create New Provider",
                                    icon = Icons.Filled.Add,
                                )
                                RibbonSelect(
                                    options = options,
                                    value = if (selectedMatchInfoId == NEW_PROVIDER_SENTINEL) NEW_ITEM else selectedMatchInfoId,
                                    onChange = ::handleProviderSelect,
                                    placeholder = "Select source",
                                    triggerWidth = 192.dp, // sm:w-48
                                    maxTriggerWidth = 150.dp,
                                )
                                PrimaryDialogButton(
                                    text = "Match",
                                    enabled = selectedMatchInfoId.isNotEmpty() && selected.isNotEmpty(),
                                    onClick = ::handleMatchSelected,
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ControlLabel("Range Fill:")
                                CompactField(rangeStart, { rangeStart = it }, placeholder = "Start", width = 80.dp, decimal = true)
                                CompactField(rangeStep, { rangeStep = it }, placeholder = "Step", width = 80.dp, decimal = true)
                                PrimaryDialogButton(
                                    text = "Fill",
                                    enabled = rangeStart.isNotEmpty() && rangeStep.isNotEmpty() && selected.isNotEmpty(),
                                    onClick = ::handleFillRange,
                                )
                            }
                        }

                        // ── New provider form ("Create New Provider" picked) ──
                        if (showNewProviderForm) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.small)
                                    .background(RenzoColors.Background)
                                    .border(1.dp, RenzoColors.Border, MaterialTheme.shapes.small)
                                    .padding(12.dp),
                            ) {
                                LabelledField("Name") {
                                    CompactField(newProviderName, { newProviderName = it }, placeholder = "Provider name", width = 180.dp)
                                }
                                LabelledField("Scanlator") {
                                    CompactField(newProviderScanlator, { newProviderScanlator = it }, placeholder = "Scanlator (optional)", width = 180.dp)
                                }
                                LabelledField("Language") {
                                    CompactField(newProviderLanguage, { newProviderLanguage = it }, placeholder = "en", width = 80.dp)
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.align(Alignment.Bottom),
                                ) {
                                    PrimaryDialogButton(
                                        text = "Create",
                                        enabled = newProviderName.trim().isNotEmpty(),
                                    ) {
                                        // The sentinel is the matchInfoId; the backend creates the source.
                                        selectedMatchInfoId = NEW_PROVIDER_SENTINEL
                                        showNewProviderForm = false
                                    }
                                    OutlineDialogButton(text = "Cancel", onClick = ::handleNewProviderCancel)
                                }
                            }
                        }
                    }
                }
            }

            // ── Footer ──
            if (error != null) {
                Text(error, style = MaterialTheme.typography.bodySmall, color = DestructiveText)
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    buildString {
                        append("(${selected.size} of ${chapters.size} selected)")
                        if (match != null && unassigned > 0 && unassigned < chapters.size) {
                            append(" · $unassigned unassigned — they stay on the unknown source")
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.weight(1f),
                )
                OutlineDialogButton(text = "Cancel", enabled = !saving, onClick = onDismiss)
                PrimaryDialogButton(
                    text = if (saving) "Saving..." else "OK",
                    enabled = canSave && !saving && !loading,
                    onClick = ::handleSave,
                )
            }
        }
    }
}

private val NUMBER_WIDTH: Dp = 88.dp

/** ChapterRow — one orphan file; the whole row toggles selection. */
@Composable
private fun ChapterRow(
    chapter: ProviderMatchChapterDto,
    numberText: String,
    isSelected: Boolean,
    source: MatchInfoDto?,
    onToggle: () -> Unit,
    onNumberChange: (String) -> Unit,
) {
    val focus = rememberFocusState()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .clip(MaterialTheme.shapes.extraSmall)
            // isSelected ? 'bg-primary' : 'hover:bg-muted/50'
            .background(
                when {
                    isSelected -> RenzoColors.Primary
                    focus.focused -> RenzoColors.Muted.copy(alpha = 0.5f)
                    else -> Color.Transparent
                },
            )
            .focusRing(focus.focused, 6.dp)
            .tvClickable(onFocused = focus::set, onClick = onToggle)
            .padding(8.dp),
    ) {
        // Filename and Name are deliberately NOT inputs (see the file comment):
        // the backend matches on the filename, so an edit would drop the row.
        ReadOnlyCell(chapter.filename, Modifier.weight(0.35f))
        ReadOnlyCell(chapter.chapterName, Modifier.weight(0.35f))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(0.2f).cellChrome(),
        ) {
            if (source == null) {
                CellText("Unknown")
            } else {
                SourceGlyph(source.language)
                CellText(providerText(source))
            }
        }
        CompactField(
            value = numberText,
            onValueChange = onNumberChange,
            placeholder = "",
            width = NUMBER_WIDTH,
            decimal = true,
            alignEnd = true,
        )
    }
}

/** The web's read-only cell: `h-7 px-3 py-1 text-sm bg-muted rounded border border-input`. */
@Composable
private fun ReadOnlyCell(text: String, modifier: Modifier) {
    Box(modifier.cellChrome(), contentAlignment = Alignment.CenterStart) { CellText(text) }
}

private fun Modifier.cellChrome(): Modifier = this
    .heightIn(min = 28.dp)
    .clip(RoundedCornerShape(6.dp))
    .background(RenzoColors.Muted)
    .border(1.dp, RenzoColors.Border, RoundedCornerShape(6.dp))
    .padding(horizontal = 12.dp, vertical = 4.dp)

@Composable
private fun CellText(text: String) {
    Text(
        text,
        fontSize = 14.sp,
        color = RenzoColors.Foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier) {
    Text(text, modifier = modifier, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = RenzoColors.Foreground)
}

@Composable
private fun ControlLabel(text: String) {
    Text(text, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = RenzoColors.Foreground)
}

@Composable
private fun LabelledField(label: String, field: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, fontSize = 12.sp, color = Muted)
        field()
    }
}

/** language "all" (or none) = Globe, else the flag — renderProviderWithFlag. */
@Composable
private fun SourceGlyph(language: String) {
    val lang = language.ifEmpty { "all" }
    if (lang == "all") {
        Icon(Icons.Filled.Public, contentDescription = null, tint = RenzoColors.Foreground, modifier = Modifier.size(16.dp))
    } else {
        Text(flagForLanguage(lang), fontSize = 13.sp)
    }
}

/** `provider • scanlator`, the scanlator only when it differs and is set. */
private fun providerText(m: MatchInfoDto): String =
    if (m.scanlator.isNotEmpty() && m.scanlator != m.provider) "${m.provider} • ${m.scanlator}" else m.provider

/** A Select option can't host the flag composable, so the flag rides in the label text. */
private fun sourceLabel(m: MatchInfoDto): String {
    val lang = m.language.ifEmpty { "all" }
    val glyph = if (lang == "all") "🌐" else flagForLanguage(lang)
    return "$glyph ${providerText(m)}"
}

/** The header's select-all <Checkbox>: 16dp rounded box, primary when checked. */
@Composable
private fun MatchCheckbox(checked: Boolean, onToggle: () -> Unit) {
    val focus = rememberFocusState()
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(18.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (checked) RenzoColors.Primary else Color.Transparent)
            .border(1.dp, if (checked) RenzoColors.Primary else RenzoColors.MutedForeground, RoundedCornerShape(4.dp))
            .focusRing(focus.focused, 4.dp)
            .tvClickable(onFocused = focus::set, onClick = onToggle),
    ) {
        if (checked) {
            Icon(Icons.Filled.Check, contentDescription = "Deselect all", tint = RenzoColors.PrimaryForeground, modifier = Modifier.size(14.dp))
        }
    }
}

/**
 * The web's compact `<Input className="h-7|h-8">`. A Material OutlinedTextField
 * is 56dp tall, which would double every table row; this keeps the web height
 * with the "After Ch." box's colours (primary border when focused, Border60
 * otherwise).
 */
@Composable
private fun CompactField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    width: Dp,
    decimal: Boolean = false,
    alignEnd: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = Modifier
            .width(width)
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(RenzoColors.Background)
            .border(if (focused) 2.dp else 1.dp, if (focused) RenzoColors.Primary else Border60, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = { onValueChange(it.oneLine()) },
            singleLine = true,
            textStyle = TextStyle(
                color = RenzoColors.Foreground,
                fontSize = 14.sp,
                // text-right tabular-nums font-mono on the Number column
                fontFamily = if (alignEnd) FontFamily.Monospace else null,
                textAlign = if (alignEnd) TextAlign.End else TextAlign.Start,
            ),
            cursorBrush = SolidColor(RenzoColors.Foreground),
            keyboardOptions = KeyboardOptions(
                keyboardType = if (decimal) KeyboardType.Decimal else KeyboardType.Text,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box(contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart) {
                    if (value.isEmpty() && placeholder.isNotEmpty()) {
                        Text(placeholder, fontSize = 14.sp, color = Muted)
                    }
                    inner()
                }
            },
        )
    }
}

/** Loading / failed-to-load states: `text-lg font-medium` + muted `text-sm mt-2`. */
@Composable
private fun CenteredNote(title: String, detail: String, titleColor: Color, modifier: Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = 256.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = titleColor)
            Spacer(Modifier.height(8.dp))
            Text(detail, fontSize = 14.sp, color = Muted)
        }
    }
}

/** 22.0 → "22", 22.5 → "22.5" — via the decimal string, never a Float. */
private fun formatNumber(n: Double?): String =
    n?.let { BigDecimal(it.toString()).stripTrailingZeros().toPlainString() } ?: ""
