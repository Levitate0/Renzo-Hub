package top.levitatemedia.renzo.hub.core

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Geist Sans (v1.7.2, OFL) — the typeface both web frontends actually use
 * (GeistSans in their layout.tsx files). Bundled ONCE here so the two halves
 * of the Hub render as the same app: the Shiori half has always shipped it,
 * and the Renzo half now shares it instead of falling back to Roboto — the
 * single most visible "these are two different apps" tell.
 *
 * Weights map to the web's usage: 400 body, 500 medium, 600 semibold titles,
 * 700 bold.
 */
val GeistFamily = FontFamily(
    Font(R.font.geist_regular, FontWeight.Normal),
    Font(R.font.geist_medium, FontWeight.Medium),
    Font(R.font.geist_semibold, FontWeight.SemiBold),
    Font(R.font.geist_bold, FontWeight.Bold),
)
