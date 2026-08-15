package top.levitatemedia.renzo.hub.core

/**
 * Platform facts and actions the shared screens need without holding a
 * Context. Android answers from the app context ([HubContextHolder]); the
 * desktop answers from the JVM. Screens in commonMain call ONLY this —
 * never android.* — which is the whole seam.
 */
expect object HubPlatform {
    /** Best-effort "is there a network at all" — never a substitute for a request failing. */
    fun hasInternet(): Boolean

    /** Leanback/TV device. Always false on desktop. */
    val isTv: Boolean

    /** The desktop (JVM window) build. Memory-generous: full-size image
     *  decodes are fine here where a phone would OOM. */
    val isDesktop: Boolean

    /** A human-usable name for this device (TV pairing, device lists). */
    val deviceName: String

    /**
     * Open a URL in the system browser. On Android this also marks the Hub as
     * deliberately leaving the foreground so the picker does not re-trigger.
     */
    fun openExternal(url: String)
}
