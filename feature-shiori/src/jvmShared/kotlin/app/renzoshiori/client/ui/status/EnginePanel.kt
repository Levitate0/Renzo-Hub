package app.renzoshiori.client.ui.status

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.TextUnitType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.renzoshiori.client.data.model.SystemHealthDto
import app.renzoshiori.client.ui.queue.parseUtcMillis
import app.renzoshiori.client.ui.theme.RenzoColors

/**
 * Owner-only Engine panel on the Status screen. Transliterated from
 * RenzoFrontend src/components/comp/status/engine-panel.tsx — change both.
 */

private val PanelWhite02 = Color.White.copy(alpha = 0.02f)
private val PanelWhite06 = Color.White.copy(alpha = 0.06f)
private val Emerald400 = Color(0xFF34D399)
private val PanelAmber400 = Color(0xFFFBBF24)
private val PanelRed400 = Color(0xFFF87171)

private fun ago(iso: String?): String? {
    val millis = iso?.let { parseUtcMillis(it) } ?: return null
    val s = maxOf(0L, (System.currentTimeMillis() - millis) / 1000)
    if (s < 60) return "${s}s ago"
    val m = Math.round(s / 60.0)
    if (m < 60) return "$m min ago"
    val h = Math.round(m / 60.0)
    if (h < 48) return "$h h ago"
    return "${Math.round(h / 24.0)} days ago"
}

private fun duration(minutes: Int): String {
    if (minutes < 60) return "$minutes minutes"
    val h = minutes / 60.0
    if (h < 48) return "${Math.round(h)} hours"
    return "${Math.round(h / 24)} days"
}

private enum class Tone { OK, WARN, BAD }

/** web: diagnose() */
private fun diagnose(h: SystemHealthDto): List<Pair<Tone, String>> {
    val out = mutableListOf<Pair<Tone, String>>()
    val e = h.engine
    val d = h.downloads
    if (e != null && !e.running) {
        out += Tone.BAD to "The extension engine is not running, so no source can be reached. It restarts on its own; if this persists, check the server logs."
    } else if (e != null && !e.responding) {
        out += Tone.BAD to "The extension engine is not answering. If it stays this way for a minute it is restarted automatically."
    } else if (e != null && e.workers != null && e.busyWorkers != null && e.busyWorkers >= e.workers && (e.lastCompletedSecondsAgo ?: 0) > 60) {
        out += Tone.WARN to "Every engine worker is busy and nothing has finished for ${e.lastCompletedSecondsAgo}s. Usually a slow or Cloudflare-protected source; it clears by itself."
    }
    if (!d.autoDownloadNewChapters) {
        out += Tone.WARN to "Auto-download of new chapters is off in Settings — chapter lists update, but nothing new is queued."
    }
    if (d.waitingDue > 0 && d.slotsInUse >= d.maxSlots) {
        val slots = if (d.maxSlots == 1) "slot is" else "slots are"
        val chapters = if (d.waitingDue == 1) "chapter" else "chapters"
        out += Tone.WARN to "All ${d.maxSlots} download $slots in use with ${"%,d".format(d.waitingDue)} $chapters waiting. Raising “Simultaneous downloads” in Settings drains this faster."
    }
    val giveUp = d.retries * d.retryEveryMinutes
    if (giveUp >= 24 * 60) {
        out += Tone.WARN to "A chapter that keeps failing on one source is retried there for about ${duration(giveUp)} (${d.retries} × ${d.retryEveryMinutes} min) before the next source is tried. Fewer retries means faster fallback."
    }
    if (out.isEmpty()) out += Tone.OK to "Everything is running normally."
    return out
}

@Composable
private fun PanelRow(label: String, value: String, valueColor: Color = RenzoColors.Foreground) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 12.sp, color = RenzoColors.MutedForeground, modifier = Modifier.weight(1f))
        Text(value, fontSize = 12.5.sp, fontFamily = FontFamily.Monospace, color = valueColor)
    }
}

@Composable
private fun Meter(used: Int, max: Int) {
    val pct = if (max > 0) (used.toFloat() / max).coerceIn(0f, 1f) else 0f
    Box(
        Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp)
            .clip(RoundedCornerShape(50)).background(PanelWhite06),
    ) {
        Box(
            Modifier.fillMaxHeight().fillMaxWidth(pct).clip(RoundedCornerShape(50))
                .background(if (pct >= 1f) PanelAmber400 else RenzoColors.Primary),
        )
    }
}

@Composable
private fun PanelCard(title: String, icon: ImageVector, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.fillMaxWidth().clip(shape).background(PanelWhite02)
            .border(1.dp, PanelWhite06, shape).padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Icon(icon, contentDescription = null, tint = RenzoColors.MutedForeground, modifier = Modifier.size(16.dp))
            Text(
                title.uppercase(),
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = TextUnit(1.7f, TextUnitType.Sp),
                color = RenzoColors.MutedForeground,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        content()
    }
}

@Composable
fun EnginePanel(health: SystemHealthDto, wide: Boolean) {
    val e = health.engine
    val d = health.downloads

    val engineCard: @Composable (Modifier) -> Unit = { m ->
        PanelCard("Extension engine", Icons.Filled.Memory, m) {
            if (e == null) {
                Text("Not available on this server.", fontSize = 12.sp, color = RenzoColors.MutedForeground)
            } else {
                val state = if (!e.running) "Stopped" else if (e.responding) "Running" else "Not answering"
                PanelRow("State", state, if (!e.running || !e.responding) PanelRed400 else Emerald400)
                if (e.workers != null && e.busyWorkers != null) {
                    PanelRow("Busy workers", "${e.busyWorkers} / ${e.workers}")
                    Meter(e.busyWorkers, e.workers)
                }
                if (e.heapUsedMb != null && e.heapMaxMb != null) PanelRow("Memory", "${e.heapUsedMb} / ${e.heapMaxMb} MB")
                PanelRow("Browser helpers", "${e.browserHelpers} / ${e.browserHelperLimit}")
                ago(e.startedAt)?.let { PanelRow("Up since", it) }
            }
        }
    }
    val downloadsCard: @Composable (Modifier) -> Unit = { m ->
        PanelCard("Downloads", Icons.Filled.Download, m) {
            PanelRow("Slots in use", "${d.slotsInUse} / ${d.maxSlots}")
            Meter(d.slotsInUse, d.maxSlots)
            PanelRow("Waiting now", "%,d".format(d.waitingDue))
            PanelRow("Scheduled later", "%,d".format(d.waitingLater))
            PanelRow("Failed", "%,d".format(d.failed))
            PanelRow("Per source", d.perSource.toString())
        }
    }
    val restartsCard: @Composable (Modifier) -> Unit = { m ->
        PanelCard("Restarts", Icons.Filled.Refresh, m) {
            if (e != null) {
                PanelRow("Since server start", e.restartsSinceAppStart.toString())
                PanelRow("Last", ago(e.lastRestartAt) ?: "never")
                e.lastRestartReason?.let {
                    Text(it, fontSize = 11.5.sp, color = RenzoColors.MutedForeground, modifier = Modifier.padding(top = 4.dp))
                }
            }
            PanelRow("Background jobs", "${health.background.slotsInUse} / ${health.background.maxSlots}")
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (wide) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                engineCard(Modifier.weight(1f))
                downloadsCard(Modifier.weight(1f))
                restartsCard(Modifier.weight(1f))
            }
        } else {
            engineCard(Modifier)
            downloadsCard(Modifier)
            restartsCard(Modifier)
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((tone, text) in diagnose(health)) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = when (tone) { Tone.OK -> Emerald400; Tone.WARN -> PanelAmber400; Tone.BAD -> PanelRed400 },
                        modifier = Modifier.padding(top = 2.dp).size(14.dp),
                    )
                    Spacer(Modifier.size(8.dp))
                    Text(text, fontSize = 12.5.sp, color = RenzoColors.Foreground.copy(alpha = 0.9f))
                }
            }
        }
    }
}
