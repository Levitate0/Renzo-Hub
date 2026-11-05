package app.renzoshiori.client.ui.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.renzoshiori.client.ui.theme.RenzoColors

/**
 * Drag-to-reorder for a short Column of rows of any height — the web's
 * SortableRows (shiori/RenzoFrontend/src/components/comp/sortable-rows.tsx).
 * Used by the categories in Settings and the series Category picker, and the
 * collections dialog. A row moves only by its grip, so a tap on the row still
 * picks it.
 *
 * Each row goes inside `key(rowKey)`, so a row that changes place keeps its
 * composition and its running gesture. The row carries [dragReorderItem]; its
 * grip carries [dragReorderHandle].
 */
@Stable
class DragReorderState<K : Any> internal constructor() {
    internal var dragging by mutableStateOf<K?>(null)
    internal var offset by mutableFloatStateOf(0f)
    internal val heights = HashMap<K, Int>()

    // The order as this drag has left it. Several steps can land between two
    // recompositions, so the gesture can't wait for the caller's list to catch up.
    internal var order: MutableList<K> = mutableListOf()
    internal var moved = false
}

@Composable
fun <K : Any> rememberDragReorderState(): DragReorderState<K> = remember { DragReorderState() }

/** On each row: the part that moves. It follows the finger while dragged, drawn above the rest. */
fun <K : Any> Modifier.dragReorderItem(state: DragReorderState<K>, key: K): Modifier =
    onSizeChanged { state.heights[key] = it.height }
        .zIndex(if (state.dragging == key) 1f else 0f)
        .graphicsLayer {
            val dragged = state.dragging == key
            translationY = if (dragged) state.offset else 0f
            alpha = if (dragged) 0.6f else 1f
        }

/**
 * On a row's grip. [keys] is the list's order now and [spacing] the Column's gap
 * between rows. [onReorder] gets the new order at each step, so the rows part as
 * the finger moves. [onDrop] gets it once when the finger lifts, and only when
 * something moved. That's where a list that saves on its own sends it.
 */
@Composable
fun <K : Any> Modifier.dragReorderHandle(
    state: DragReorderState<K>,
    key: K,
    keys: List<K>,
    spacing: Dp,
    onReorder: (List<K>) -> Unit,
    onDrop: (List<K>) -> Unit = {},
): Modifier {
    val latestKeys by rememberUpdatedState(keys)
    val reorder by rememberUpdatedState(onReorder)
    val drop by rememberUpdatedState(onDrop)
    val gap = with(LocalDensity.current) { spacing.toPx() }
    return pointerInput(state, key) {
        fun finish() {
            val changed = state.moved
            state.dragging = null
            state.offset = 0f
            state.moved = false
            if (changed) drop(state.order.toList())
        }
        detectDragGestures(
            onDragStart = {
                state.order = latestKeys.toMutableList()
                state.moved = false
                state.offset = 0f
                state.dragging = key
            },
            onDragEnd = { finish() },
            onDragCancel = { finish() },
        ) { change, amount ->
            change.consume()
            state.offset += amount.y
            val order = state.order
            var i = order.indexOf(key)
            if (i < 0) return@detectDragGestures
            // Past half of the neighbour's slot, the row takes its place.
            // The slot is the neighbour's own height, since rows differ: a
            // collection carries its sub-collections.
            while (i < order.lastIndex) {
                val slot = (state.heights[order[i + 1]] ?: break) + gap
                if (state.offset < slot / 2) break
                order.add(i + 1, order.removeAt(i))
                state.offset -= slot
                state.moved = true
                i++
                reorder(order.toList())
            }
            while (i > 0) {
                val slot = (state.heights[order[i - 1]] ?: break) + gap
                if (state.offset > -slot / 2) break
                order.add(i - 1, order.removeAt(i))
                state.offset += slot
                state.moved = true
                i--
                reorder(order.toList())
            }
        }
    }
}

/** The grip icon, with a touch target a thumb can find. */
@Composable
fun DragHandle(label: String, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier.size(28.dp)) {
        Icon(
            Icons.Filled.DragIndicator,
            contentDescription = label,
            tint = RenzoColors.MutedForeground.copy(alpha = 0.70f),
            modifier = Modifier.size(16.dp),
        )
    }
}
