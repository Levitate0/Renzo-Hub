package top.levitatemedia.renzo.hub.core

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Shrink a screen until it fits, instead of scrolling it.
 *
 * A television never scrolls a form. There is no touch to flick with, the
 * scrollbar is invisible across a room, and content that runs off the top is
 * simply gone — which is what happened to the picker: the panel crops the edges
 * and the top of the page went with it.
 *
 * So the whole subtree is laid out at a smaller density when the viewport is
 * shorter than the design height. Everything shrinks together — text, spacing,
 * art, focus rings — which keeps proportions and focus targets intact. It is
 * the same trick the anime half already uses for its TV layout, lifted here so
 * the shell and the auth screens behave the same way.
 *
 * Only ever shrinks: a large panel renders at 1:1 rather than blowing the UI up.
 *
 * @param designHeightDp the height this content needs at normal scale.
 * @param minScale a floor, so a very short panel degrades rather than becoming
 *   unreadable. Below this, clipping is the lesser evil.
 */
@Composable
fun TvFit(
    designHeightDp: Int,
    minScale: Float = 0.6f,
    content: @Composable () -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val base = LocalDensity.current
        // Lowering density means MORE dp fit in the same pixels, so the content
        // shrinks to fit rather than overflowing.
        val scale = (maxHeight.value / designHeightDp.toFloat())
            .coerceAtMost(1f)
            .coerceAtLeast(minScale)
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = base.density * scale,
                // Font scale is deliberately NOT touched: the user's
                // accessibility setting should still apply on top.
                fontScale = base.fontScale,
            ),
        ) {
            content()
        }
    }
}
