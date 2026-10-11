package top.levitatemedia.renzo.tv.ui.kit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/**
 * Shiori's series-page pieces (feature-shiori ui/series SeriesDetailComponents.kt
 * and SeriesHeroSection.kt), copied so Renzo's title page is built from the
 * same parts as Shiori's series page. Keep in step with those files.
 */

val Border40: Color get() = RenzoColors.Border.copy(alpha = 0.4f)
val Border60: Color get() = RenzoColors.Border.copy(alpha = 0.6f)
val ForegroundFaint: Color get() = RenzoColors.Foreground.copy(alpha = 0.04f)
val ForegroundFaint06: Color get() = RenzoColors.Foreground.copy(alpha = 0.06f)
val ForegroundFaint10: Color get() = RenzoColors.Foreground.copy(alpha = 0.10f)
val PillShape = RoundedCornerShape(50)

val Pink500 = Color(0xFFEC4899)
val Violet400 = Color(0xFFA78BFA)
val Emerald400 = Color(0xFF34D399)
val Emerald500 = Color(0xFF10B981)
val Amber500 = Color(0xFFF59E0B)
val Green400 = Color(0xFF4ADE80)
val Green500 = Color(0xFF22C55E)
val Blue400 = Color(0xFF60A5FA)
val Blue500 = Color(0xFF3B82F6)
val Yellow400 = Color(0xFFFACC15)
val Yellow500 = Color(0xFFEAB308)

/** The web's `h-1.5 w-1.5 rounded-full` status dot. */
@Composable
fun StatusDot(color: Color, size: Int = 6) {
    Box(Modifier.size(size.dp).clip(PillShape).background(color))
}

/** A 1dp rule at 60% border. */
@Composable
fun HairLine(color: Color = Border60) {
    Box(Modifier.fillMaxWidth().height(1.dp).background(color))
}

/** Shiori's RenzoChip: a pill with optional icon, label and "(count)". */
@Composable
fun RenzoChip(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    count: Int? = null,
    accent: Color? = null,
    active: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val tint = accent ?: if (active) RenzoColors.Primary else RenzoColors.MutedForeground
    val bg = when {
        active && accent != null -> accent.copy(alpha = 0.15f)
        active -> RenzoColors.Primary.copy(alpha = 0.15f)
        accent != null -> accent.copy(alpha = 0.10f)
        else -> ForegroundFaint
    }
    val borderColor = if (accent != null || active) tint.copy(alpha = 0.4f) else Border40
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
            .clip(PillShape)
            .background(bg)
            .border(1.dp, borderColor, PillShape)
            .kitClickable(enabled = enabled, radius = 999.dp, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 5.dp)
            .then(if (enabled) Modifier else Modifier.alpha(0.5f)),
    ) {
        if (icon != null) Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tint, maxLines = 1)
        if (count != null) {
            Text("($count)", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = tint.copy(alpha = 0.8f), maxLines = 1)
        }
    }
}

/** Shiori's 32dp square icon button (chapter-row actions). */
@Composable
fun SquareIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color = RenzoColors.MutedForeground,
    borderColor: Color = Border60,
    background: Color = RenzoColors.Foreground.copy(alpha = 0.03f),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .kitClickable(enabled = enabled, radius = 6.dp, onClick = onClick)
            .then(if (enabled) Modifier else Modifier.alpha(0.5f)),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(16.dp))
    }
}

/**
 * Shiori's hero toolbar button: 36dp, 8dp corners, icon-only on narrow and
 * icon + label on wide ([label] non-null).
 */
@Composable
fun HeroActionButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    label: String? = null,
    primary: Boolean = false,
    tint: Color = if (primary) RenzoColors.PrimaryForeground else RenzoColors.Foreground,
    borderColor: Color = Border60,
    background: Color = if (primary) RenzoColors.Primary else Color.Transparent,
) {
    val shell = Modifier
        .clip(MaterialTheme.shapes.small)
        .background(background)
        .border(1.dp, if (primary) Color.Transparent else borderColor, MaterialTheme.shapes.small)
        .kitClickable(onClick = onClick)
    if (label == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(36.dp).then(shell)) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(16.dp))
        }
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.height(36.dp).then(shell).padding(horizontal = 16.dp),
        ) {
            Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(16.dp))
            Text(
                label,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = tint,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}
