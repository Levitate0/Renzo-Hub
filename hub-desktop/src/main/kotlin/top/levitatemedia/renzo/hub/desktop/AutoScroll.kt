package top.levitatemedia.renzo.hub.desktop

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.awt.AWTEvent
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.event.MouseEvent
import java.awt.event.MouseWheelEvent
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Windows-style middle-click autoscroll for the whole app, as a compat layer
 * over AWT: Compose Desktop has no native equivalent, so a middle press (or
 * middle click for sticky mode) anchors the pointer and the offset from the
 * anchor is turned into synthetic [MouseWheelEvent]s dispatched at the
 * pointer's position — whatever list, grid or reader strip is under the
 * cursor scrolls, with zero per-screen wiring.
 *
 * Smoothness: Compose's desktop event layer reads `preciseWheelRotation`
 * (verified against ComposeSceneMediator's bytecode — the int click count is
 * ignored), so tiny fractional rotations at 100Hz glide instead of stepping
 * whole wheel lines. `isShiftDown` routes a rotation to the horizontal axis,
 * which is how sideways panning rides along.
 *
 * Exit-click swallowing (Windows 11 behaviour, user direction 2026-08-19/20):
 * ONE click of ANY button — left or another middle — cancels the pan, and
 * that click is CONSUMED: it never opens what sat under the cursor. The next
 * click is the first normal one. Neither an [AWTEventListener] (can't
 * consume) nor a Swing glass pane (lightweight — can't block the heavyweight
 * surface Compose renders into on Windows) can do this reliably, so the
 * swallowing lives in a pushed [java.awt.EventQueue]: it sees every event
 * BEFORE dispatch and simply drops the ones that belong to the mode. The
 * synthetic wheel events are dispatched directly to the component under the
 * cursor and never pass through the queue.
 */
internal class MiddleClickAutoScroll(private val window: ComposeWindow) {

    /** Anchor in content-pane coordinates, non-null while panning (drives the overlay). */
    val anchor = mutableStateOf<Point?>(null)

    private var sticky = false
    private var moved = false
    private var current = Point()
    /** A button press cancelled the pan; everything mouse-button-shaped is
     *  swallowed until every button is back up. */
    private var exiting = false

    private val timer = Timer(TICK_MS) { tick() }

    private val queue = object : java.awt.EventQueue() {
        override fun dispatchEvent(event: AWTEvent) {
            if (event is MouseEvent && handleAndSwallow(event)) return
            super.dispatchEvent(event)
        }
    }

    // Windows-native parity: clicking ANOTHER window (or alt-tabbing away)
    // ends the pan — without this, a pan whose cursor wandered outside the
    // app kept scrolling forever until the user came back and clicked.
    private val focusListener = object : java.awt.event.WindowAdapter() {
        override fun windowLostFocus(e: java.awt.event.WindowEvent?) {
            stop()
        }
    }

    fun install() {
        Toolkit.getDefaultToolkit().systemEventQueue.push(queue)
        window.addWindowFocusListener(focusListener)
    }

    fun uninstall() {
        // The pushed queue stays (pop is protected and the filter is inert
        // while the mode is off); just make sure the mode is off.
        window.removeWindowFocusListener(focusListener)
        stop()
    }

    /**
     * The whole mode lifecycle, run against every mouse event before AWT
     * dispatches it. Returns true to DROP the event.
     */
    private fun handleAndSwallow(e: MouseEvent): Boolean {
        val component = e.component ?: return false
        if (component !== window && SwingUtilities.getWindowAncestor(component) !== window) return false
        when (e.id) {
            MouseEvent.MOUSE_PRESSED -> {
                if (exiting) return true
                if (anchor.value == null) {
                    // ENTRY: a middle press starts the pan; consumed, like
                    // Windows — it must not also register as a press below.
                    if (e.button == MouseEvent.BUTTON2) {
                        start(SwingUtilities.convertPoint(component, e.point, window.contentPane))
                        return true
                    }
                    return false
                }
                // ANY button while panning cancels — left, right, and a
                // second middle alike — and the cancelling click is consumed.
                exiting = true
                endPan()
                return true
            }
            MouseEvent.MOUSE_RELEASED -> {
                if (exiting) {
                    // Swallow until every button is up again; the NEXT press
                    // is the first normal one.
                    if (noButtonsDown(e)) exiting = false
                    return true
                }
                if (anchor.value != null && e.button == MouseEvent.BUTTON2) {
                    // The entry press's release: press-drag-release pans once
                    // and ends here; press-and-release in place arms sticky
                    // mode (both are how Windows behaves).
                    if (moved) stop() else sticky = true
                    return true
                }
                return false
            }
            // CLICKED events pair with a press/release that was swallowed.
            MouseEvent.MOUSE_CLICKED -> return exiting || anchor.value != null
            else -> return false
        }
    }

    private fun noButtonsDown(e: MouseEvent): Boolean =
        e.modifiersEx and (
            MouseEvent.BUTTON1_DOWN_MASK or
                MouseEvent.BUTTON2_DOWN_MASK or
                MouseEvent.BUTTON3_DOWN_MASK
            ) == 0

    /** Eased per-axis velocity (px/s) — the actual speed glides toward the
     *  offset-derived target instead of snapping with every mouse move, which
     *  is what keeps the pan smooth rather than jumpy. */
    private var velX = 0.0
    private var velY = 0.0

    private fun start(p: Point) {
        anchor.value = p
        current = p
        sticky = false
        moved = false
        velX = 0.0
        velY = 0.0
        window.contentPane.cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        timer.start()
    }

    /** Ends the panning; [exiting] keeps swallowing until the buttons are up. */
    private fun endPan() {
        anchor.value = null
        sticky = false
        window.contentPane.cursor = Cursor.getDefaultCursor()
        timer.stop()
    }

    private fun stop() {
        endPan()
        exiting = false
    }

    private fun tick() {
        val a = anchor.value ?: return
        // Poll the GLOBAL pointer instead of trusting the last AWT event:
        // native Windows panning keeps going when the cursor leaves the
        // window (mouse events stop arriving there), and the offset must
        // keep growing with it.
        java.awt.MouseInfo.getPointerInfo()?.location?.let { loc ->
            SwingUtilities.convertPointFromScreen(loc, window.contentPane)
            current = loc
            if (a.distance(loc) > DRAG_THRESHOLD_PX) moved = true
        }
        velY += (targetVelocity((current.y - a.y).toDouble()) - velY) * EASING
        velX += (targetVelocity((current.x - a.x).toDouble()) - velX) * EASING
        dispatch(rotationFor(velY), horizontal = false)
        dispatch(rotationFor(velX), horizontal = true)
    }

    /**
     * Offset from the anchor → target speed in px/s, SIGNED so above/left of
     * the anchor pans up/left and below/right pans down/right. Windows'
     * native panning model (Explorer/Chromium autoscroll): zero inside the
     * anchor badge's dead zone, then LINEAR in distance — fine control close
     * to the anchor, whole pages flying by at arm's reach. ~380 px/s at 50px
     * past the dead zone, ~880 at 100px, ~2380 at 250px.
     */
    private fun targetVelocity(offsetPx: Double): Double {
        if (abs(offsetPx) <= DEAD_ZONE_PX) return 0.0
        val distance = abs(offsetPx) - DEAD_ZONE_PX
        val magnitude = (distance * SPEED_PX_PER_SEC_PER_PX).coerceAtMost(MAX_PX_PER_SECOND)
        return magnitude * sign(offsetPx)
    }

    private fun rotationFor(pxPerSecond: Double): Double {
        if (abs(pxPerSecond) < 1.0) return 0.0
        return pxPerSecond * (TICK_MS / 1000.0) / PX_PER_WHEEL_UNIT
    }

    private fun dispatch(rotation: Double, horizontal: Boolean) {
        if (rotation == 0.0) return
        val contentPane = window.contentPane
        // A cursor outside the window must still scroll SOMETHING: clamp the
        // hit point into the content pane so the wheel event always lands on
        // the scrollable nearest the exit edge.
        val hit = Point(
            current.x.coerceIn(0, (contentPane.width - 1).coerceAtLeast(0)),
            current.y.coerceIn(0, (contentPane.height - 1).coerceAtLeast(0)),
        )
        val target = SwingUtilities.getDeepestComponentAt(contentPane, hit.x, hit.y) ?: contentPane
        val pt = SwingUtilities.convertPoint(contentPane, hit, target)
        target.dispatchEvent(
            MouseWheelEvent(
                target,
                MouseEvent.MOUSE_WHEEL,
                System.currentTimeMillis(),
                if (horizontal) MouseEvent.SHIFT_DOWN_MASK else 0,
                pt.x, pt.y,
                0, 0,
                0,
                false,
                MouseWheelEvent.WHEEL_UNIT_SCROLL,
                1,
                // The int click count is ignored by Compose; precise carries it.
                rotation.roundToInt(),
                rotation,
            ),
        )
    }

    private companion object {
        const val TICK_MS = 10
        const val DEAD_ZONE_PX = 12.0
        const val DRAG_THRESHOLD_PX = 8.0
        /** Linear gain, the Windows/Chromium autoscroll model: each px of
         *  reach past the dead zone adds this many px/s. 16 (was 10, user
         *  direction 2026-08-19: steeper): ~800 px/s at 50px past the dead
         *  zone, ~1600 at 100px, ~4000 at 250px. */
        const val SPEED_PX_PER_SEC_PER_PX = 16.0
        /** High ceiling — native panning is effectively uncapped; this only
         *  guards against a cursor parked at the far edge of a big monitor. */
        const val MAX_PX_PER_SECOND = 10000.0
        /** Per-tick approach toward the target speed. Native panning reacts
         *  immediately; this is just enough smoothing to avoid 100Hz jitter. */
        const val EASING = 0.35
        /** One preciseWheelRotation unit scrolls roughly this many px in Compose lists. */
        const val PX_PER_WHEEL_UNIT = 64.0
    }
}

/** The anchor badge: a translucent ring with a centre dot at the pan origin. */
@Composable
internal fun AutoScrollAnchorBadge(anchor: Point) {
    val density = LocalDensity.current
    val sizeDp = 26.dp
    val sizePx = with(density) { sizeDp.toPx() }
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (anchor.x * density.density - sizePx / 2f).roundToInt(),
                    (anchor.y * density.density - sizePx / 2f).roundToInt(),
                )
            }
            .size(sizeDp),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color.Black.copy(alpha = 0.35f))
            drawCircle(Color.White.copy(alpha = 0.75f), style = Stroke(width = 1.5.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.9f), radius = 2.dp.toPx(), center = Offset(size.width / 2f, size.height / 2f))
        }
    }
}
