package top.levitatemedia.renzo.hub.core.notify

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * The servers' timestamps, read and written without java.time: the Android
 * app supports API 24, and java.time needs 26 or desugaring.
 *
 * Both servers speak UTC. Shiori's database hands its dates back without a
 * zone suffix, and they are UTC all the same, so the suffix is ignored.
 */
object IsoTime {
    private fun format(pattern: String) = SimpleDateFormat(pattern, Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /** Epoch milliseconds, or null when [iso] is not a timestamp. */
    fun parse(iso: String?): Long? {
        if (iso == null || iso.length < 19) return null
        return runCatching { format("yyyy-MM-dd'T'HH:mm:ss").parse(iso.substring(0, 19))?.time }.getOrNull()
    }

    fun format(epochMs: Long): String = format("yyyy-MM-dd'T'HH:mm:ss'Z'").format(Date(epochMs))
}
