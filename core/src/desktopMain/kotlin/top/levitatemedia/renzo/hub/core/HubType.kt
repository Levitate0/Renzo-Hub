package top.levitatemedia.renzo.hub.core

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.platform.Font

/**
 * Desktop side of [GeistFamily]: the same .ttf files, loaded from the module's
 * classpath resources (desktopMain/resources/font/).
 */
actual val GeistFamily: FontFamily = FontFamily(
    Font("font/geist_regular.ttf", FontWeight.Normal, FontStyle.Normal),
    Font("font/geist_medium.ttf", FontWeight.Medium, FontStyle.Normal),
    Font("font/geist_semibold.ttf", FontWeight.SemiBold, FontStyle.Normal),
    Font("font/geist_bold.ttf", FontWeight.Bold, FontStyle.Normal),
)
