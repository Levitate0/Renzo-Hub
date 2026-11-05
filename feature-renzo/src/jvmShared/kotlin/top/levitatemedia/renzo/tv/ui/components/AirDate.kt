package top.levitatemedia.renzo.tv.ui.components

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.GregorianCalendar
import java.util.Locale

private val DAY = Regex("""^(\d{4})-(\d{2})-(\d{2})$""")

/**
 * An episode's air date ("YYYY-MM-DD", the Japanese broadcast date from MAL /
 * AniList) for the episode lists: "Sep 29, 2023", or "Airs Oct 13" while it
 * hasn't aired. Web twin: renzo/frontend lib/utils.ts airDateLabel. Built in
 * the device's own zone from the parts, and with java.text rather than
 * java.time, which Android 7 (the Hub's minSdk 24) doesn't have.
 */
fun airDateLabel(day: String?, unaired: Boolean = false): String? {
    val (y, m, d) = day?.let { DAY.matchEntire(it) }?.destructured ?: return null
    val date = GregorianCalendar(y.toInt(), m.toInt() - 1, d.toInt()).time
    val locale = Locale.getDefault()
    return if (unaired) {
        "Airs " + SimpleDateFormat("MMM d", locale).format(date)
    } else {
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(date)
    }
}
