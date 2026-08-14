package top.levitatemedia.renzo.hub.core

import androidx.compose.ui.text.font.FontFamily

/**
 * Geist Sans (v1.7.2, OFL) — the typeface both web frontends actually use
 * (GeistSans in their layout.tsx files). Bundled ONCE per platform so every
 * Hub surface renders as the same app; weights map to the web's usage
 * (400 body, 500 medium, 600 semibold titles, 700 bold).
 *
 * `expect` because font loading is the one genuinely platform-specific part:
 * Android resolves R.font resources, the desktop JVM loads the same .ttf files
 * from the classpath (desktopMain/resources/font).
 */
expect val GeistFamily: FontFamily
