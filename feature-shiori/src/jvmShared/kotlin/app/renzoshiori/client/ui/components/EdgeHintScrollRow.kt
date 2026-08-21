package app.renzoshiori.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import app.renzoshiori.client.ui.theme.RenzoColors

/**
 * A horizontally scrolling row that SAYS it scrolls (user direction
 * 2026-08-21): when content continues past an edge, that edge shows a
 * background fade with a chevron. Without the hint, a cut-off ribbon on a TV
 * reads as a broken layout rather than a scrollable one — there is no
 * scrollbar and no touch affordance at couch distance.
 *
 * The row is a focusGroup, so a D-pad walks it as one region; focusable
 * children bring themselves into view as the cursor moves.
 */
@Composable
fun EdgeHintScrollRow(
    modifier: Modifier = Modifier,
    contentModifier: Modifier = Modifier,
    verticalAlignment: Alignment.Vertical = Alignment.CenterVertically,
    horizontalArrangement: androidx.compose.foundation.layout.Arrangement.Horizontal =
        androidx.compose.foundation.layout.Arrangement.Start,
    content: @Composable RowScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    Box(modifier) {
        Row(
            verticalAlignment = verticalAlignment,
            horizontalArrangement = horizontalArrangement,
            modifier = contentModifier
                .horizontalScroll(scroll)
                .focusGroup(),
            content = content,
        )
        if (scroll.canScrollBackward) {
            EdgeHint(left = true, modifier = Modifier.align(Alignment.CenterStart))
        }
        if (scroll.canScrollForward) {
            EdgeHint(left = false, modifier = Modifier.align(Alignment.CenterEnd))
        }
    }
}

@Composable
private fun EdgeHint(left: Boolean, modifier: Modifier) {
    val bg = RenzoColors.Background
    Box(
        contentAlignment = if (left) Alignment.CenterStart else Alignment.CenterEnd,
        modifier = modifier
            .fillMaxHeight()
            .width(36.dp)
            .background(
                Brush.horizontalGradient(
                    colors = if (left) {
                        listOf(bg, bg.copy(alpha = 0f))
                    } else {
                        listOf(bg.copy(alpha = 0f), bg)
                    },
                ),
            ),
    ) {
        Icon(
            if (left) Icons.Filled.ChevronLeft else Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = RenzoColors.MutedForeground,
            modifier = Modifier.width(18.dp),
        )
    }
}
