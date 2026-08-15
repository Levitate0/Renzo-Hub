package top.levitatemedia.renzo.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import top.levitatemedia.renzo.hub.core.GeistFamily

// Renzo's dark theme — the web frontend's default `renzo` preset, resolved to
// sRGB (frontend/src/styles/globals.css :root/.dark values, dark-only app).
object RenzoColors {
    // Preset-driven tokens: State-backed getters, so selecting a theme in
    // Appearance recomposes every screen that reads them (web: swapping the
    // html[data-theme] block). Non-preset tokens below stay constant.
    private val preset = androidx.compose.runtime.mutableStateOf(presetById("renzo"))
    private val accentOverride = androidx.compose.runtime.mutableStateOf<Color?>(null)

    /** Apply a preset id + optional custom accent (both persisted by AppServices). */
    fun apply(presetId: String, accent: Color?) {
        preset.value = presetById(presetId)
        accentOverride.value = accent
    }

    val presetId: String get() = preset.value.id
    val accent: Color? get() = accentOverride.value

    val Background: Color get() = preset.value.background
    val Card: Color get() = preset.value.card
    val Popover: Color get() = preset.value.popover
    val Foreground: Color get() = preset.value.foreground
    val Muted: Color get() = preset.value.muted
    val MutedForeground: Color get() = preset.value.mutedForeground
    val Secondary: Color get() = preset.value.secondary
    val Accent: Color get() = preset.value.accent
    val AccentForeground = Color(0xFFFAFAFA) // hsl(0 0% 98%)
    val Border: Color get() = preset.value.border
    /** Rose by default — focus ring, active pill, Play (web --primary). */
    val Primary: Color get() = accentOverride.value ?: preset.value.primary
    val PrimaryForeground = Color(0xFFFFF1F2)
    val Destructive = Color(0xFF7F1D1D)
    val DestructiveForeground = Color(0xFFFEF2F2)
    val DownloadedGreen = Color(0xFF34D399)
    val EpThumbWell = Color(0xFF05060A)
    val OverlayBlack = Color(0xB3000000)    // black @70% — ribbons/arrows
    val Input: Color get() = preset.value.border // hsl(240 3.7% 15.9%) — form-control borders
    // Tailwind literals the settings pills/badges use (shared.tsx PILL_CLS/RoleBadge).
    val Emerald400 = Color(0xFF34D399)
    val Emerald500 = Color(0xFF10B981)
    val Amber300 = Color(0xFFFCD34D)        // autodl self-check text (text-amber-300)
    val Amber400 = Color(0xFFFBBF24)
    val Amber500 = Color(0xFFF59E0B)
    val Red400 = Color(0xFFF87171)
    val Red500 = Color(0xFFEF4444)
    val Sky400 = Color(0xFF38BDF8)
    val Sky500 = Color(0xFF0EA5E9)
    // Library folder-chip active gradient (library-chips.tsx from-indigo-500 to-indigo-400).
    val Indigo500 = Color(0xFF6366F1)
    val Indigo400 = Color(0xFF818CF8)
    // PosterCard downloaded-dot halo: shadow-[0_0_0_3px_rgba(61,220,132,0.2)].
    val DownloadedGreenHalo = Color(0x333DDC84)
}

/**
 * Global UI scale (1f everywhere except TV). Android TVs report a short
 * layout height (~540dp at 1080p/2.0), so a phone-sized UI doesn't fit and
 * content gets cut off. Scaling the DENSITY shrinks every dp and sp together,
 * which is what "the whole UI scales with the TV" means — one knob instead of
 * per-component tweaks.
 */
val LocalUiScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

/** Screen size in LOGICAL dp (i.e. after [LocalUiScale]). */
@Composable
fun logicalScreenSize(): Pair<Float, Float> {
    val scale = LocalUiScale.current
    val size = androidx.compose.ui.platform.LocalWindowInfo.current.containerSize
    return with(androidx.compose.ui.platform.LocalDensity.current) {
        (size.width.toDp().value / scale) to (size.height.toDp().value / scale)
    }
}

/**
 * Geist across every text style. Renzo's screens set size/weight per call and
 * inherit the face from LocalTextStyle, so carrying the family in the theme
 * typography is what flips the whole half from Roboto to the web's actual
 * font — the same face the Shiori half has always bundled.
 */
private val base = Typography()
private val RenzoTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = GeistFamily),
    displayMedium = base.displayMedium.copy(fontFamily = GeistFamily),
    displaySmall = base.displaySmall.copy(fontFamily = GeistFamily),
    headlineLarge = base.headlineLarge.copy(fontFamily = GeistFamily),
    headlineMedium = base.headlineMedium.copy(fontFamily = GeistFamily),
    headlineSmall = base.headlineSmall.copy(fontFamily = GeistFamily),
    titleLarge = base.titleLarge.copy(fontFamily = GeistFamily),
    titleMedium = base.titleMedium.copy(fontFamily = GeistFamily),
    titleSmall = base.titleSmall.copy(fontFamily = GeistFamily),
    bodyLarge = base.bodyLarge.copy(fontFamily = GeistFamily),
    bodyMedium = base.bodyMedium.copy(fontFamily = GeistFamily),
    bodySmall = base.bodySmall.copy(fontFamily = GeistFamily),
    labelLarge = base.labelLarge.copy(fontFamily = GeistFamily),
    labelMedium = base.labelMedium.copy(fontFamily = GeistFamily),
    labelSmall = base.labelSmall.copy(fontFamily = GeistFamily),
)

@Composable
fun RenzoTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = RenzoTypography,
        colorScheme = darkColorScheme(
            primary = RenzoColors.Primary,
            onPrimary = RenzoColors.PrimaryForeground,
            background = RenzoColors.Background,
            onBackground = RenzoColors.Foreground,
            surface = RenzoColors.Card,
            onSurface = RenzoColors.Foreground,
            surfaceVariant = RenzoColors.Secondary,
            onSurfaceVariant = RenzoColors.MutedForeground,
            outline = RenzoColors.Border,
            error = RenzoColors.Destructive,
            onError = RenzoColors.DestructiveForeground,
        ),
        content = content,
    )
}
