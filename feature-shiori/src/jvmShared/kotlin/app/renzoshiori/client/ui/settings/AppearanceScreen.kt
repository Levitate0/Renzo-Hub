package app.renzoshiori.client.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.renzoshiori.client.ShioriRuntime
import app.renzoshiori.client.data.model.UpdateUserDto
import app.renzoshiori.client.data.model.UserDto
import app.renzoshiori.client.data.network.AccountApi
import app.renzoshiori.client.ui.theme.RenzoColors
import app.renzoshiori.client.ui.tv.LocalIsTv
import app.renzoshiori.client.ui.util.screenWidthDp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * Per-user Appearance — transliterated from
 * RenzoFrontend/src/app/appearance/page.tsx.
 *
 * Named presets with live preview swatches plus a custom-accent override; the
 * app is dark-only (light mode was permanently removed on the web, so there is
 * no mode switch to port). Every change is written straight to
 * `PUT /api/auth/me { preferences }` so it follows the account across devices —
 * MERGED into the existing blob, never overwriting it, because that same blob
 * also carries the onboarding flag and the per-user source-priority prefs.
 *
 * The web's `<input type="color">` has no native counterpart, so the custom
 * accent is picked with hue/saturation/lightness sliders that write the exact
 * same `"H S% L%"` string the web stores.
 */
@Composable
fun AppearanceScreen(onBack: () -> Unit) {
    val app = ShioriRuntime.app
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var username by remember { mutableStateOf("you") }
    var prefsRaw by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var preset by remember { mutableStateOf(DEFAULT_PRESET) }
    var customOn by remember { mutableStateOf(false) }
    var customHsl by remember { mutableStateOf(DEFAULT_CUSTOM_ACCENT) }

    LaunchedEffect(Unit) {
        runCatching { app.network.currentServiceOf<AccountApi>()?.me() }
            .onSuccess { me: UserDto? ->
                if (me != null) {
                    username = me.username
                    prefsRaw = me.preferences
                    preset = presetById(prefString(me.preferences, "preset")).id
                    customOn = prefString(me.preferences, "accent") == "custom"
                    customHsl = prefString(me.preferences, "accentCustom") ?: DEFAULT_CUSTOM_ACCENT
                }
                loaded = true
            }
            .onFailure {
                loaded = true
                snackbar.showSnackbar(it.apiMessage("Couldn't load your appearance settings"))
            }
    }

    // Dragging a slider must not fire one PUT per pixel — the web debounces
    // custom-accent writes by 400ms for the same reason.
    var saveJob by remember { mutableStateOf<Job?>(null) }

    /** Read → merge → write; the unknown keys in the blob must survive. */
    fun persist(debounce: Boolean = false) {
        saveJob?.cancel()
        saveJob = scope.launch {
            if (debounce) delay(400)
            val body = UpdateUserDto(
                preferences = mergePreferences(
                    prefsRaw,
                    mapOf(
                        "preset" to JsonPrimitive(preset),
                        "accent" to JsonPrimitive(if (customOn) "custom" else "preset"),
                        "accentCustom" to JsonPrimitive(customHsl),
                    ),
                ),
            )
            runCatching { app.network.currentServiceOf<AccountApi>()?.updateMe(body) }
                .onSuccess { prefsRaw = it?.preferences ?: prefsRaw }
                .onFailure { snackbar.showSnackbar("Couldn't save your appearance settings.") }
        }
    }

    val activePreset = presetById(preset)
    val accentColor = if (customOn) hslStrToColor(customHsl) else hslStrToColor(activePreset.accent)

    // Repaint the whole app as the choice changes — the web swaps the CSS
    // custom properties live, so the preview IS the app.
    LaunchedEffect(preset, customOn, customHsl) {
        RenzoColors.applyTheme(
            background = hslStrToColor(activePreset.bg),
            card = hslStrToColor(activePreset.card),
            primary = accentColor,
        )
    }

    SettingsScaffold(title = "Appearance", onBack = onBack, snackbar = snackbar) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
        ) {
            PageHeading(
                "Appearance",
                "Personalize how Renzo Shiori looks for $username — saved to your account, so it " +
                    "follows you on every device.",
            )
            Spacer(Modifier.height(20.dp))

            if (!loaded) {
                LoadingBlock()
                return@Column
            }

            // ── Theme ───────────────────────────────────────────────────
            SettingsCard(
                title = "Theme",
                description = "Pick a look. Applies instantly and syncs to your account.",
            ) {
                // appearance/page.tsx: `grid grid-cols-2 gap-3 sm:grid-cols-4`.
                val presetColumns = if (!LocalIsTv.current && screenWidthDp() >= 640.dp) 4 else 2
                THEME_PRESETS.chunked(presetColumns).forEach { row ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    ) {
                        row.forEach { themePreset ->
                            val active = themePreset.id == preset
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .border(
                                        if (active) 2.dp else 1.dp,
                                        if (active) RenzoColors.Primary else RenzoColors.Border,
                                        RoundedCornerShape(12.dp),
                                    )
                                    .clickable {
                                        preset = themePreset.id
                                        persist()
                                    }
                                    .padding(10.dp),
                            ) {
                                // The web's swatch: page background, a card
                                // block, and an accent dot.
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(60.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(hslStrToColor(themePreset.bg))
                                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp)),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(start = 8.dp, top = 16.dp)
                                            .width(60.dp)
                                            .height(22.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(hslStrToColor(themePreset.card)),
                                    )
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(hslStrToColor(themePreset.accent)),
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                ) {
                                    Text(
                                        themePreset.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = RenzoColors.Foreground,
                                        modifier = Modifier.weight(1f),
                                    )
                                    if (active) {
                                        Icon(
                                            Icons.Filled.Check, contentDescription = "Selected",
                                            tint = RenzoColors.Primary, modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                        // Keep partial rows column-aligned with the full ones.
                        repeat(presetColumns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Accent ──────────────────────────────────────────────────
            SettingsCard(
                title = "Accent color",
                description = "Recolors buttons, highlights, and focus rings across the app. Use " +
                    "a preset theme above or choose your own.",
            ) {
                Text("Custom accent", style = MaterialTheme.typography.titleSmall, color = RenzoColors.Foreground)
                Text(
                    "Override the selected theme's highlight color.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RenzoColors.MutedForeground,
                    modifier = Modifier.padding(bottom = 12.dp),
                )

                // Web: the swatch is an <input type="color"> — clicking it opens
                // the platform's picker (SV square + hue bar + RGB fields). No
                // Compose native picker exists, so the popover recreates it.
                var pickerOpen by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box {
                        Box(
                            modifier = Modifier
                                .width(56.dp)
                                .height(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor)
                                .border(
                                    if (customOn) 2.dp else 1.dp,
                                    if (customOn) RenzoColors.Foreground else RenzoColors.Border,
                                    RoundedCornerShape(8.dp),
                                )
                                .clickable { pickerOpen = !pickerOpen },
                        )
                        AccentPickerPopover(
                            open = pickerOpen,
                            onDismiss = { pickerOpen = false },
                            hslString = if (customOn) customHsl else activePreset.accent,
                            onPick = { hsl ->
                                customOn = true
                                customHsl = hsl
                                persist(debounce = true)
                            },
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (customOn) {
                        RenzoButton(
                            text = "Use preset accent",
                            variant = "outline",
                            small = true,
                            onClick = {
                                customOn = false
                                persist()
                            },
                        )
                    }
                }

                // ── Live preview ────────────────────────────────────────
                Spacer(Modifier.height(12.dp))
                Text(
                    "PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    color = RenzoColors.MutedForeground,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, RenzoColors.Border, RoundedCornerShape(12.dp))
                        .background(hslStrToColor(activePreset.card))
                        .padding(16.dp),
                ) {
                    Text("Series", style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
                    Text(
                        "Renzo Shiori",
                        style = MaterialTheme.typography.titleMedium,
                        color = RenzoColors.Foreground,
                    )
                    Text(
                        "2026 · Action",
                        style = MaterialTheme.typography.bodySmall,
                        color = RenzoColors.MutedForeground,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(accentColor)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(14.dp),
                            )
                            Text(
                                "Read",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                        ) {
                            Icon(
                                Icons.Filled.Add, contentDescription = null,
                                tint = RenzoColors.Foreground, modifier = Modifier.size(14.dp),
                            )
                            Text(
                                "List",
                                style = MaterialTheme.typography.labelMedium,
                                color = RenzoColors.Foreground,
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

// ── The picker popover (the browser's native color dialog, recreated) ──────

/** HSV working colour — the SV square's natural space; storage stays HSL. */
private data class Hsv(val h: Float, val s: Float, val v: Float)

private fun hslToHsv(h: Float, s: Float, l: Float): Hsv {
    val v = l + s * minOf(l, 1f - l)
    val sv = if (v <= 0f) 0f else 2f * (1f - l / v)
    return Hsv(h, sv.coerceIn(0f, 1f), v.coerceIn(0f, 1f))
}

private fun hsvToHslString(hsv: Hsv): String {
    val l = hsv.v * (1f - hsv.s / 2f)
    val s = if (l <= 0f || l >= 1f) 0f else (hsv.v - l) / minOf(l, 1f - l)
    return hslToStr(hsv.h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

private fun hsvToColor(hsv: Hsv): Color =
    Color.hsv(hsv.h.coerceIn(0f, 360f).let { if (it >= 360f) 359.99f else it }, hsv.s, hsv.v)

private fun rgbToHsv(r: Int, g: Int, b: Int): Hsv {
    val rf = r / 255f; val gf = g / 255f; val bf = b / 255f
    val max = maxOf(rf, gf, bf); val min = minOf(rf, gf, bf)
    val d = max - min
    val h = when {
        d == 0f -> 0f
        max == rf -> 60f * (((gf - bf) / d) % 6f)
        max == gf -> 60f * (((bf - rf) / d) + 2f)
        else -> 60f * (((rf - gf) / d) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return Hsv(h, if (max == 0f) 0f else d / max, max)
}

/**
 * The SV square + hue bar + RGB fields, anchored under the accent swatch —
 * what the web gets for free from `<input type="color">`. Edits stream out
 * through [onPick] as the stored "H S% L%" string; the caller debounces.
 */
@Composable
private fun AccentPickerPopover(
    open: Boolean,
    onDismiss: () -> Unit,
    hslString: String,
    onPick: (String) -> Unit,
) {
    androidx.compose.material3.DropdownMenu(
        expanded = open,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(RenzoColors.Popover),
    ) {
        val (h, s, l) = parseHsl(hslString)
        val hsv = hslToHsv(h, s, l)
        val current = hsvToColor(hsv)

        Column(modifier = Modifier.padding(12.dp).width(256.dp)) {
            // ── SV square ──
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .pointerInput(hsv.h) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val ns = (change.position.x / size.width).coerceIn(0f, 1f)
                            val nv = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                            onPick(hsvToHslString(Hsv(hsv.h, ns, nv)))
                        }
                    }
                    .pointerInput(hsv.h) {
                        detectTapGestures { offset ->
                            val ns = (offset.x / size.width).coerceIn(0f, 1f)
                            val nv = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                            onPick(hsvToHslString(Hsv(hsv.h, ns, nv)))
                        }
                    },
            ) {
                drawRect(
                    Brush.horizontalGradient(listOf(Color.White, hsvToColor(Hsv(hsv.h, 1f, 1f)))),
                )
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color.Black)),
                )
                val thumb = Offset(hsv.s * size.width, (1f - hsv.v) * size.height)
                drawCircle(Color.Black.copy(alpha = 0.6f), radius = 7.dp.toPx(), center = thumb)
                drawCircle(Color.White, radius = 6.dp.toPx(), center = thumb, style = Stroke(2.dp.toPx()))
            }

            Spacer(Modifier.height(10.dp))

            // ── Hue bar ──
            val hueStops = listOf(0f, 60f, 120f, 180f, 240f, 300f, 360f)
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val nh = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                            onPick(hsvToHslString(hsv.copy(h = nh)))
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val nh = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                            onPick(hsvToHslString(hsv.copy(h = nh)))
                        }
                    },
            ) {
                drawRect(
                    Brush.horizontalGradient(hueStops.map { hsvToColor(Hsv(it, 1f, 1f)) }),
                )
                val x = (hsv.h / 360f) * size.width
                drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(x, size.height / 2f), style = Stroke(2.dp.toPx()))
            }

            Spacer(Modifier.height(10.dp))

            // ── RGB fields ──
            val r = (current.red * 255f).toInt()
            val g = (current.green * 255f).toInt()
            val b = (current.blue * 255f).toInt()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                RgbField("R", r, Modifier.weight(1f)) { onPick(hsvToHslString(rgbToHsv(it, g, b))) }
                RgbField("G", g, Modifier.weight(1f)) { onPick(hsvToHslString(rgbToHsv(r, it, b))) }
                RgbField("B", b, Modifier.weight(1f)) { onPick(hsvToHslString(rgbToHsv(r, g, it))) }
            }
        }
    }
}

@Composable
private fun RgbField(label: String, value: Int, modifier: Modifier = Modifier, onCommit: (Int) -> Unit) {
    // Local draft re-seeded whenever the committed value changes elsewhere
    // (square/hue drags), committed on the IME action or focus loss.
    var text by remember(value) { mutableStateOf(value.toString()) }
    fun commit() {
        text.toIntOrNull()?.coerceIn(0, 255)?.let { if (it != value) onCommit(it) }
    }
    Column(modifier = modifier) {
        androidx.compose.foundation.text.BasicTextField(
            value = text,
            onValueChange = { text = it.filter { c -> c.isDigit() }.take(3) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodySmall.copy(
                color = RenzoColors.Foreground,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            ),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(RenzoColors.Foreground),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { commit() }),
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(RenzoColors.Secondary)
                .border(1.dp, RenzoColors.Border, RoundedCornerShape(6.dp))
                .onFocusChanged { if (!it.isFocused) commit() }
                .padding(vertical = 6.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = RenzoColors.MutedForeground,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
