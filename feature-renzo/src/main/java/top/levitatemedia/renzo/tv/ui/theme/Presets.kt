package top.levitatemedia.renzo.tv.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Theme presets — a literal port of the web's `theme-preset.ts` list plus the
 * matching `html[data-theme="…"]` token blocks in globals.css. Each preset
 * overrides the surface tokens and the primary (accent) hue; everything else
 * comes from the base dark theme.
 */
data class ThemePreset(
    val id: String,
    val label: String,
    val background: Color,
    val card: Color,
    val popover: Color,
    val secondary: Color,
    val muted: Color,
    val accent: Color,
    val border: Color,
    val foreground: Color,
    val mutedForeground: Color,
    /** Default primary for the preset (the user can override it per-device). */
    val primary: Color,
)

/** hsl(h, s%, l%) → sRGB, matching the CSS the tokens are written in. */
fun hsl(h: Double, s: Double, l: Double): Color {
    val c = (1 - kotlin.math.abs(2 * l - 1)) * s
    val hp = h / 60.0
    val x = c * (1 - kotlin.math.abs(hp.mod(2.0) - 1))
    val (r1, g1, b1) = when {
        hp < 1 -> Triple(c, x, 0.0)
        hp < 2 -> Triple(x, c, 0.0)
        hp < 3 -> Triple(0.0, c, x)
        hp < 4 -> Triple(0.0, x, c)
        hp < 5 -> Triple(x, 0.0, c)
        else -> Triple(c, 0.0, x)
    }
    val m = l - c / 2
    return Color((r1 + m).toFloat(), (g1 + m).toFloat(), (b1 + m).toFloat())
}

private fun p(
    id: String,
    label: String,
    bg: Triple<Double, Double, Double>,
    card: Triple<Double, Double, Double>,
    popover: Triple<Double, Double, Double>,
    secondary: Triple<Double, Double, Double>,
    muted: Triple<Double, Double, Double>,
    border: Triple<Double, Double, Double>,
    primary: Triple<Double, Double, Double>,
    fg: Triple<Double, Double, Double> = Triple(0.0, 0.0, 0.95),
    mutedFg: Triple<Double, Double, Double> = Triple(240.0, 0.05, 0.649),
) = ThemePreset(
    id = id,
    label = label,
    background = hsl(bg.first, bg.second, bg.third),
    card = hsl(card.first, card.second, card.third),
    popover = hsl(popover.first, popover.second, popover.third),
    secondary = hsl(secondary.first, secondary.second, secondary.third),
    muted = hsl(muted.first, muted.second, muted.third),
    accent = hsl(secondary.first, secondary.second, secondary.third),
    border = hsl(border.first, border.second, border.third),
    foreground = hsl(fg.first, fg.second, fg.third),
    mutedForeground = hsl(mutedFg.first, mutedFg.second, mutedFg.third),
    primary = hsl(primary.first, primary.second, primary.third),
)

val THEME_PRESETS: List<ThemePreset> = listOf(
    p("renzo", "Renzo",
        bg = Triple(20.0, 0.143, 0.041), card = Triple(24.0, 0.098, 0.10),
        popover = Triple(0.0, 0.0, 0.09), secondary = Triple(240.0, 0.037, 0.159),
        muted = Triple(0.0, 0.0, 0.15), border = Triple(240.0, 0.037, 0.159),
        primary = Triple(346.8, 0.772, 0.498)),
    p("amoled", "AMOLED",
        bg = Triple(0.0, 0.0, 0.0), card = Triple(0.0, 0.0, 0.06),
        popover = Triple(0.0, 0.0, 0.05), secondary = Triple(0.0, 0.0, 0.11),
        muted = Triple(0.0, 0.0, 0.10), border = Triple(0.0, 0.0, 0.14),
        primary = Triple(346.8, 0.772, 0.498)),
    p("midnight", "Midnight",
        bg = Triple(222.0, 0.47, 0.08), card = Triple(222.0, 0.40, 0.13),
        popover = Triple(222.0, 0.44, 0.11), secondary = Triple(222.0, 0.30, 0.19),
        muted = Triple(222.0, 0.26, 0.17), border = Triple(222.0, 0.26, 0.21),
        primary = Triple(217.2, 0.912, 0.598),
        fg = Triple(210.0, 0.40, 0.96), mutedFg = Triple(215.0, 0.20, 0.65)),
    p("sakura", "Sakura",
        bg = Triple(330.0, 0.22, 0.07), card = Triple(330.0, 0.18, 0.12),
        popover = Triple(330.0, 0.20, 0.10), secondary = Triple(330.0, 0.15, 0.18),
        muted = Triple(330.0, 0.12, 0.16), border = Triple(330.0, 0.12, 0.20),
        primary = Triple(340.0, 0.82, 0.66)),
    p("matcha", "Matcha",
        bg = Triple(140.0, 0.15, 0.06), card = Triple(140.0, 0.12, 0.11),
        popover = Triple(140.0, 0.14, 0.09), secondary = Triple(140.0, 0.10, 0.17),
        muted = Triple(140.0, 0.08, 0.15), border = Triple(140.0, 0.08, 0.19),
        primary = Triple(142.1, 0.706, 0.453)),
    p("ember", "Ember",
        bg = Triple(20.0, 0.22, 0.06), card = Triple(20.0, 0.18, 0.11),
        popover = Triple(20.0, 0.20, 0.09), secondary = Triple(20.0, 0.14, 0.17),
        muted = Triple(20.0, 0.12, 0.15), border = Triple(20.0, 0.12, 0.19),
        primary = Triple(24.6, 0.95, 0.531)),
    p("ocean", "Ocean",
        bg = Triple(195.0, 0.40, 0.07), card = Triple(195.0, 0.34, 0.12),
        popover = Triple(195.0, 0.38, 0.10), secondary = Triple(195.0, 0.28, 0.18),
        muted = Triple(195.0, 0.24, 0.16), border = Triple(195.0, 0.24, 0.20),
        primary = Triple(172.0, 0.66, 0.45)),
)

fun presetById(id: String): ThemePreset =
    THEME_PRESETS.firstOrNull { it.id == id } ?: THEME_PRESETS.first()

/** The web's accent swatches (appearance-pane.tsx). */
val ACCENT_SWATCHES: List<Pair<String, Color>> = listOf(
    "Rose" to hsl(346.8, 0.772, 0.498),
    "Blue" to hsl(217.2, 0.912, 0.598),
    "Violet" to hsl(263.4, 0.70, 0.505),
    "Emerald" to hsl(142.1, 0.706, 0.453),
    "Amber" to hsl(37.7, 0.92, 0.503),
    "Orange" to hsl(24.6, 0.95, 0.531),
    "Teal" to hsl(172.0, 0.66, 0.45),
    "Pink" to hsl(340.0, 0.82, 0.66),
)
