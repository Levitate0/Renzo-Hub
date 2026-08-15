@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.ui.components.focusRing
import top.levitatemedia.renzo.tv.ui.components.tvClickable
import top.levitatemedia.renzo.tv.ui.theme.ACCENT_SWATCHES
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors
import top.levitatemedia.renzo.tv.ui.theme.THEME_PRESETS
import top.levitatemedia.renzo.tv.ui.theme.ThemePreset

/**
 * /appearance/ — port of the web appearance pane: theme presets rendered as
 * mock cards (a mini topbar + poster block painted in the preset's own
 * colours) and the accent swatch row. Selection applies instantly, persists
 * per-device, and is pushed to the server so the web UI matches.
 */
@Composable
fun AppearanceScreen(app: AppServices, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var presetId by remember { mutableStateOf(RenzoColors.presetId) }
    var accent by remember { mutableStateOf(RenzoColors.accent) }

    fun push(nextPreset: String, nextAccent: Color?) {
        presetId = nextPreset
        accent = nextAccent
        app.setTheme(nextPreset, nextAccent)
        // Sync to the server so the web client picks up the same theme.
        scope.launch {
            try {
                val hex = nextAccent?.let {
                    "#%02X%02X%02X".format(
                        (it.red * 255).toInt(), (it.green * 255).toInt(), (it.blue * 255).toInt(),
                    )
                }
                app.client.raw(
                    "/api/account/theme", "POST",
                    buildString {
                        append("{\"preset\":\"").append(nextPreset).append("\"")
                        if (hex != null) append(",\"accent\":\"").append(hex).append("\"")
                        append("}")
                    },
                )
            } catch (_: Exception) { /* device-local setting still applied */ }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .widthIn(max = 720.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("Appearance", color = RenzoColors.Foreground, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    "Theme and accent — saved on this device.",
                    color = RenzoColors.MutedForeground,
                    // Shiori PageHeading subtitle: bodySmall (12sp).
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            var xFocused by remember { mutableStateOf(false) }
            // Shiori's close affordance: the Close glyph in a 1dp circle.
            Box(
                Modifier
                    .clip(CircleShape)
                    .focusRing(xFocused, 999.dp)
                    .background(if (xFocused) RenzoColors.Card else Color.Transparent, CircleShape)
                    .tvClickable(onFocused = { xFocused = it }, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close, contentDescription = "Close",
                    tint = RenzoColors.MutedForeground,
                    modifier = Modifier.border(1.dp, RenzoColors.Border, CircleShape).padding(6.dp),
                )
            }
        }

        Text(
            "Theme",
            color = RenzoColors.Foreground,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 22.dp, bottom = 8.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            THEME_PRESETS.forEach { preset ->
                PresetCard(preset, selected = preset.id == presetId) { push(preset.id, accent) }
            }
        }

        Text(
            "Accent",
            color = RenzoColors.Foreground,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 24.dp, bottom = 2.dp),
        )
        Text(
            "Used for the play button, active tabs and focus rings.",
            color = RenzoColors.MutedForeground,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 10.dp),
        )
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // "Theme default" first — clears any custom accent.
            AccentSwatch(
                color = top.levitatemedia.renzo.tv.ui.theme.presetById(presetId).primary,
                label = "Default",
                selected = accent == null,
            ) { push(presetId, null) }
            ACCENT_SWATCHES.forEach { (label, c) ->
                AccentSwatch(color = c, label = label, selected = accent == c) { push(presetId, c) }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** A preset swatch mock-card: mini topbar + poster block in the preset's own colours. */
@Composable
private fun PresetCard(preset: ThemePreset, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(152.dp)
            .clip(RoundedCornerShape(12.dp))
            .focusRing(focused, 12.dp)
            .background(preset.background, RoundedCornerShape(12.dp))
            .border(
                if (selected) 2.dp else 1.dp,
                if (selected) RenzoColors.Primary else RenzoColors.Border,
                RoundedCornerShape(12.dp),
            )
            .tvClickable(onFocused = { focused = it }, onClick = onClick)
            .padding(10.dp),
    ) {
        // mini topbar
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(preset.primary))
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .height(6.dp)
                    .width(46.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(preset.mutedForeground.copy(alpha = 0.5f)),
            )
        }
        Spacer(Modifier.height(8.dp))
        // mini poster row
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(preset.card),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                preset.label,
                color = preset.foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            if (selected) Text("✓", color = RenzoColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AccentSwatch(color: Color, label: String, selected: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .focusRing(focused, 999.dp)
                .background(color, CircleShape)
                .border(
                    if (selected) 3.dp else 1.dp,
                    if (selected) RenzoColors.Foreground else RenzoColors.Border,
                    CircleShape,
                )
                .tvClickable(onFocused = { focused = it }, onClick = onClick),
        )
        Text(
            label,
            color = if (selected) RenzoColors.Foreground else RenzoColors.MutedForeground,
            fontSize = 11.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
