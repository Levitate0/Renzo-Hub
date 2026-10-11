package top.levitatemedia.renzo.tv.player

/**
 * WebVTT, parsed once per track and drawn by the app (RENZO-DESKTOP-NATIVE-HANDOFF.md
 * §6.4). The server converts every subtitle — embedded MKV tracks and Jimaku
 * alike — to WebVTT before the player sees it, so this is the only format needed.
 */
internal data class Cue(val startMs: Long, val endMs: Long, val text: String)

internal object WebVtt {
    private val TIMING = Regex("""((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})\s*-->\s*((?:\d+:)?\d{1,2}:\d{2}[.,]\d{1,3})""")

    fun parse(source: String): List<Cue> {
        val lines = source.replace("\r\n", "\n").replace('\r', '\n').split('\n')
        val cues = ArrayList<Cue>()
        var i = 0
        while (i < lines.size) {
            val m = TIMING.find(lines[i])
            if (m == null) { i++; continue }
            val start = toMs(m.groupValues[1])
            val end = toMs(m.groupValues[2])
            i++
            val text = StringBuilder()
            while (i < lines.size && lines[i].isNotBlank()) {
                if (text.isNotEmpty()) text.append('\n')
                text.append(lines[i])
                i++
            }
            if (end > start && text.isNotBlank()) cues.add(Cue(start, end, text.toString()))
        }
        cues.sortBy { it.startMs }
        return cues
    }

    /** The cues showing at [atMs] — binary search, never a scan per frame. */
    fun active(cues: List<Cue>, atMs: Long): List<Cue> {
        if (cues.isEmpty()) return emptyList()
        var lo = 0
        var hi = cues.size - 1
        var last = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (cues[mid].startMs <= atMs) { last = mid; lo = mid + 1 } else hi = mid - 1
        }
        if (last < 0) return emptyList()
        // Overlapping cues (two speakers) are rare and short; a small look-back is enough.
        val out = ArrayList<Cue>(2)
        var k = last
        while (k >= 0 && k > last - 8) {
            val c = cues[k]
            if (c.endMs > atMs) out.add(0, c)
            k--
        }
        return out
    }

    private fun toMs(t: String): Long {
        val parts = t.replace(',', '.').split(':')
        val (h, m, s) = when (parts.size) {
            3 -> Triple(parts[0].toLong(), parts[1].toLong(), parts[2])
            else -> Triple(0L, parts[0].toLong(), parts[1])
        }
        val secs = s.substringBefore('.').toLong()
        val millis = s.substringAfter('.', "0").padEnd(3, '0').take(3).toLong()
        return ((h * 60 + m) * 60 + secs) * 1000 + millis
    }
}
