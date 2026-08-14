package top.levitatemedia.renzo.hub.core

/**
 * Distinguishes "the user left the Hub" from "the Hub launched something".
 *
 * The picker reappears whenever the app is brought back to the foreground,
 * which is driven off the activity being stopped. But a SAF folder picker, an
 * OAuth authorisation page or an "open in browser" link stops the activity too
 * — and returning from one of those to the app picker, instead of to the screen
 * that sent you, is just a bug.
 *
 * Android gives no signal that separates the two, so the launching site says so
 * itself: call [leavingApp] immediately before starting an external activity.
 * The flag is consumed by the next stop, so a stale one can only ever swallow a
 * single reset.
 */
object HubForeground {
    @Volatile
    private var suppressNextReset = false

    /** Call right before handing control to another app. */
    fun leavingApp() {
        suppressNextReset = true
    }

    /** True if this stop was caused by us; clears the flag. */
    fun consumeSuppression(): Boolean {
        val suppressed = suppressNextReset
        suppressNextReset = false
        return suppressed
    }
}
