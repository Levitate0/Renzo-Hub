package top.levitatemedia.renzo.hub.core

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * The wide command bar's skeleton, shared by both halves: [left] hugs the
 * start, [right] hugs the end, and [center] (the section pills) sits at the
 * bar's TRUE horizontal centre — capped at 60% of the bar (web max-w-[60vw])
 * and at the free span between the edge clusters. When even a full-span
 * centred block would underlap an edge cluster, the pills give up exact
 * centring rather than overlap: they slide just far enough to stay clear,
 * scrolling internally.
 */
@Composable
fun CenterClampedBar(
    left: @Composable () -> Unit,
    center: @Composable () -> Unit,
    right: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            Box { left() }
            Box { center() }
            Box { right() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = Constraints(maxHeight = height)
        val leftBar = measurables[0].measure(loose)
        val rightBar = measurables[2].measure(loose)
        // Web gap-3: the minimum air between the pills and either cluster.
        val gap = 12.dp.roundToPx()
        val free = (width - leftBar.width - rightBar.width - 2 * gap).coerceAtLeast(0)
        val centerMax = minOf((width * 0.6f).roundToInt(), free)
        val pills = measurables[1].measure(Constraints(maxWidth = centerMax, maxHeight = height))
        val minX = leftBar.width + gap
        val maxX = width - rightBar.width - gap - pills.width
        val pillsX = ((width - pills.width) / 2).coerceIn(minX, maxOf(minX, maxX))
        layout(width, height) {
            leftBar.placeRelative(0, (height - leftBar.height) / 2)
            rightBar.placeRelative(width - rightBar.width, (height - rightBar.height) / 2)
            pills.placeRelative(pillsX, (height - pills.height) / 2)
        }
    }
}
