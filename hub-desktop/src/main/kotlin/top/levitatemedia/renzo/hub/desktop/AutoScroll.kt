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
import java.awt.event.AWTEventListener
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
 * Exit-click swallowing (Windows 11 behaviour, user direction 2026-08-19):
 * the click that LEAVES the mode must not also act on the UI — on Windows,
 * left-clicking out of a pan never opens what was under the cursor; the NEXT
 * click is the first normal one. An [AWTEventListener] cannot consume events,
 * so while the mode is active a glass pane sits over the root: every button
 * press ends the pan and both the press and its release die on the glass.
 * The synthetic wheel events are dispatched straight to the component under
 * the cursor, bypassing picking, so scrolling is unaffected by the glass.
 */
internal class MiddleClickAutoScroll(private val window: ComposeWindow) {

    /** Anchor in content-pane coordinates, non-null while panning (drives the overlay). */
    val anchor = mutableStateOf<Point?>(null)

    private var sticky = false
    private var moved = false
    private var current = Point()
    /** A button press ended the pan; its release still dies on the glass. */
    private var exiting = false
    private var originalGlass: java.awt.Component? = null

    private val timer = Timer(TICK_MS) { tick() }
    private val listener = AWTEventListener { event -> onEvent(event) }

    /** Covers the window while the mode is active so the exiting click (and
     *  everything else the mouse does) never reaches the app underneath. */
    private val glass = object : javax.swing.JComponent() {}.apply {
        isOpaque = false
        cursor = Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR)
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                // ANY button ends the pan — Windows 11: the press is consumed,
                // only the release remains to swallow.
                exiting = true
                endPan()
            }

            override fun mouseReleased(e: MouseEvent) {
                // The exiting click's release — the press above happened on
                // this glass, so the grab routes its release here too. Swallow
                // it and take the glass down: the NEXT click is a normal one.
                if (exiting) {
                    exiting = false
                    removeGlass()
                }
            }
        })
        // Registered so the glass intercepts these event types too; the pan
        // itself polls the global pointer, so nothing to do here.
        addMouseMotionListener(object : java.awt.event.MouseAdapter() {})
        addMouseWheelListener { /* physical wheel is inert during the mode */ }
    }

    fun install() {
        Toolkit.getDefaultToolkit().addAWTEventListener(
            listener,
            AWTEvent.MOUSE_EVENT_MASK,
        )
    }

    fun uninstall() {
        Toolkit.getDefaultToolkit().removeAWTEventListener(listener)
        stop()
    }

    private fun onEvent(event: AWTEvent) {
        val e = event as? MouseEvent ?: return
        if (e.button != MouseEvent.BUTTON2) return
        when (e.id) {
            // The ENTRY press. Everything after it lives on the glass — except
            // the release below.
            MOUSE_PRESSED_ID -> {
                if (anchor.value != null || exiting) return
                val component = e.component ?: return
                if (component !== window && SwingUtilities.getWindowAncestor(component) !== window) return
                start(SwingUtilities.convertPoint(component, e.point, window.contentPane))
            }
            // AWT's implicit mouse grab delivers this release to the component
            // that took the entry press — NOT the glass — so it is handled
            // here: press-drag-release pans once; press-and-release in place
            // arms sticky mode (both are how Windows behaves). A middle
            // release means nothing to any Compose control, so letting it
            // fall through is harmless.
            MOUSE_RELEASED_ID -> {
                if (anchor.value != null && !exiting && e.component !== glass) {
                    if (moved) stop() else sticky = true
                }
            }
        }
    }

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
        originalGlass = window.rootPane.glassPane
        window.rootPane.glassPane = glass
        glass.isVisible = true
        timer.start()
    }

    /** Ends the panning; the glass stays up until the exiting release. */
    private fun endPan() {
        anchor.value = null
        sticky = false
        timer.stop()
    }

    private fun removeGlass() {
        glass.isVisible = false
        originalGlass?.let { window.rootPane.glassPane = it }
        originalGlass = null
    }

    private fun stop() {
        endPan()
        exiting = false
        removeGlass()
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
        const val MOUSE_PRESSED_ID = MouseEvent.MOUSE_PRESSED
        const val MOUSE_RELEASED_ID = MouseEvent.MOUSE_RELEASED
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
