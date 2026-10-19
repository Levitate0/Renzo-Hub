package top.levitatemedia.renzo.hub.core.notify

import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.hub.core.KeyValuePrefs
import top.levitatemedia.renzo.hub.core.keyValuePrefs

/**
 * One new-release notification: a series (or anime) and the chapters (or
 * episodes) that are new in it since the last check.
 */
data class ReleaseNotice(
    val target: HubTarget,
    /** Shiori's series id, or Renzo's AniList id. */
    val id: String,
    val title: String,
    /** Absolute cover URL, or null. */
    val coverUrl: String?,
    /** The new numbers, ascending. */
    val numbers: List<Double>,
    /** The chapter's own name when exactly one is new. */
    val name: String? = null,
    val movie: Boolean = false,
)

/** The notification's line: "Chapter 52 · The Return", "3 new chapters · 50–52", "Episode 5 is out", "Out now". */
fun ReleaseNotice.describe(): String {
    val first = numbers.firstOrNull() ?: return ""
    val last = numbers.last()
    return when {
        target == HubTarget.Renzo && movie -> "Out now"
        target == HubTarget.Renzo && numbers.size == 1 -> "Episode ${num(first)} is out"
        target == HubTarget.Renzo -> "Episodes ${num(first)}–${num(last)} are out"
        numbers.size == 1 -> "Chapter ${num(first)}" + (ownName(name)?.let { " · $it" } ?: "")
        else -> "${numbers.size} new chapters · ${num(first)}–${num(last)}"
    }
}

/** 52.0 → "52", 22.5 → "22.5". */
private fun num(d: Double): String = if (d % 1.0 == 0.0) d.toLong().toString() else d.toString()

/** What a chapter name says beyond "Chapter 52" again, or null. */
private fun ownName(name: String?): String? = name
    ?.replace(Regex("^\\s*(chapter|chap\\.?|ch\\.?)\\s*[0-9.]+\\s*[:\\-–—.]?\\s*", RegexOption.IGNORE_CASE), "")
    ?.trim()
    ?.takeIf { it.isNotEmpty() }

/**
 * New-release notifications (Android phones only): what is switched on, and
 * what the background check has already seen.
 *
 * Device-local on purpose, like a notification permission: a phone and a
 * tablet on the same account can want different things, and the web has no
 * notifications at all.
 *
 * **What counts as new.** For every series in the library the check keeps the
 * highest chapter or episode number it has seen (its MARK) and announces only
 * numbers above it. That is the whole definition of a new release, and it is
 * why a chapter another source re-adds, a backup source filling in an older
 * chapter, a better release of an episode, or a re-download can never
 * announce itself: none of them is above the mark. The mark is kept for every
 * series whether its notifications are on or not, and it never goes down, so
 * switching a series on later does not dump its back-catalogue.
 */
object ReleaseAlerts {
    /** The platform side. Set by the Android app on phones and tablets; null elsewhere, which hides every toggle. */
    interface Platform {
        /** Notifications can actually be shown right now (permission granted, not blocked). */
        fun permitted(): Boolean

        /** Ask for the notification permission (Android 13+), or open settings where there is nothing to ask. */
        fun requestPermission()

        /** The app's notification settings, where a block is lifted. */
        fun openSettings()

        /** Schedule or cancel the background check to match the settings. */
        fun sync()
    }

    @Volatile
    var platform: Platform? = null

    val supported: Boolean get() = platform != null

    private val prefs: KeyValuePrefs by lazy { keyValuePrefs("hub_release_alerts") }

    // ── switches ─────────────────────────────────────────────────────────────

    /** The service-wide switch: every series in the library. */
    fun allOn(target: HubTarget): Boolean = prefs.getBoolean("all.${target.name}", false)

    fun setAllOn(target: HubTarget, on: Boolean) = changing(target) {
        prefs.putBoolean("all.${target.name}", on)
    }

    /** A series' own switch, or null when it follows the service-wide one. */
    fun seriesSetting(target: HubTarget, id: String): Boolean? =
        when (prefs.getString(seriesKey(target, id), null)) {
            "1" -> true
            "0" -> false
            else -> null
        }

    /** Whether this series notifies: its own switch, else the service-wide one. */
    fun isOn(target: HubTarget, id: String): Boolean = seriesSetting(target, id) ?: allOn(target)

    /**
     * The bell on a series page. Always stored as the series' own setting, so
     * the service-wide switch never overrides a choice made for one series.
     */
    fun setSeries(target: HubTarget, id: String, on: Boolean) = changing(target) {
        prefs.putString(seriesKey(target, id), if (on) "1" else "0")
    }

    /** True when anything at all would notify for this half. */
    fun anyOn(target: HubTarget): Boolean {
        if (allOn(target)) return true
        val prefix = "s.${target.name}."
        return prefs.keys().any { it.startsWith(prefix) && prefs.getString(it, null) == "1" }
    }

    /**
     * Applies a change, then has the platform catch up. A half that goes from
     * nothing switched on to something has to take a fresh baseline: its marks
     * stopped moving while nothing was polling, so the first check after this
     * only records where every series stands, and announces nothing.
     */
    private inline fun changing(target: HubTarget, change: () -> Unit) {
        val was = anyOn(target)
        change()
        if (!was && anyOn(target)) setPrimed(target, false)
        platform?.sync()
    }

    private fun seriesKey(target: HubTarget, id: String) = "s.${target.name}.$id"

    // ── what the check has seen ─────────────────────────────────────────────

    fun mark(target: HubTarget, id: String): Double? =
        prefs.getString("m.${target.name}.$id", null)?.toDoubleOrNull()

    /** Raises the mark; it never goes down. */
    fun raiseMark(target: HubTarget, id: String, to: Double) {
        val now = mark(target, id)
        if (now == null || to > now) prefs.putString("m.${target.name}.$id", to.toString())
    }

    /** Server time of the last check's answer, for Shiori's "found since". */
    fun since(target: HubTarget): String? = prefs.getString("since.${target.name}", null)

    fun setSince(target: HubTarget, value: String?) = prefs.putString("since.${target.name}", value)

    /** False until a check has taken the baseline it compares against. */
    fun primed(target: HubTarget): Boolean = prefs.getBoolean("primed.${target.name}", false)

    fun setPrimed(target: HubTarget, value: Boolean) {
        prefs.putBoolean("primed.${target.name}", value)
        if (!value) setSince(target, null)
    }
}
