package app.renzoshiori.client.ui.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.renzoshiori.client.data.model.SourceHealthDto
import app.renzoshiori.client.data.network.SourcesApi
import app.renzoshiori.client.ui.queue.formatRelativeTime
import app.renzoshiori.client.ui.theme.RenzoColors
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.roundToInt

/*
 * Sources › Health — a transliteration of RenzoFrontend
 * src/components/comp/sources/health-tab.tsx: how each source the user's series
 * use is doing, worst first, so a dying source shows before chapters go
 * missing. Same rows, same wording; change one, change both.
 */

private data class HealthStatus(val label: String, val dot: androidx.compose.ui.graphics.Color)

private fun statusOf(status: String) = when (status) {
    "dead" -> HealthStatus("Not working", RenzoColors.Red)
    "failing" -> HealthStatus("Struggling", RenzoColors.Amber)
    "ok" -> HealthStatus("Working", RenzoColors.Emerald)
    else -> HealthStatus("No activity", RenzoColors.MutedForeground.copy(alpha = 0.4f))
}

/** Web twin: health-tab.tsx healthDetail. */
internal fun healthDetail(s: SourceHealthDto): String {
    val parts = mutableListOf("${s.enabledCount} of ${s.seriesCount} series on")
    if (s.steppedDownCount > 0)
        parts += "stepped down on ${s.steppedDownCount}" + (if (s.reasons.isNotEmpty()) " (${s.reasons.joinToString(", ")})" else "")
    if (s.failuresLastDay > 0) parts += "${s.failuresLastDay} failure${if (s.failuresLastDay == 1) "" else "s"} today"
    val last = s.lastSuccessUtc?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() }
    parts += if (last != null) "last worked ${formatRelativeTime(last)}" else "no success this week"
    return parts.joinToString(" · ")
}

/** Web twin: health-tab.tsx healthSpeed. */
internal fun healthSpeed(s: SourceHealthDto): String {
    if (s.speed.attempts == 0) return "No downloads this week"
    val rate = "${((s.speed.successRate ?: 0.0) * 100).roundToInt()}% of ${s.speed.attempts} downloads this week"
    val median = s.speed.medianSeconds ?: return rate
    return "$rate · ≈ ${median.roundToInt()} s a chapter"
}

@Composable
internal fun HealthTab(api: SourcesApi?, onMigrate: (String) -> Unit) {
    var rows by remember { mutableStateOf<List<SourceHealthDto>?>(null) }
    var failed by remember { mutableStateOf(false) }

    // Refreshed every minute while open, like the web's refetchInterval.
    LaunchedEffect(api) {
        while (true) {
            val result = runCatching { api?.sourceHealth() }
            result.getOrNull()?.let { rows = it; failed = false }
            if (result.isFailure && rows == null) failed = true
            delay(60_000)
        }
    }

    val list = rows
    when {
        failed -> Message("Couldn't load source health.", RenzoColors.Red)
        list == null -> Message("Checking sources…", RenzoColors.MutedForeground)
        list.isEmpty() -> Message("None of your series use a source yet.", RenzoColors.MutedForeground)
        else -> LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Text(
                    "How each source your series use has been doing. A source is Not working when it has failed " +
                        "on your series all day without serving a single chapter. Failure counts start over when the server restarts.",
                    style = MaterialTheme.typography.bodySmall,
                    color = RenzoColors.MutedForeground,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
            items(list, key = { it.provider }) { s -> HealthRow(s, onMigrate) }
        }
    }
}

@Composable
private fun HealthRow(s: SourceHealthDto, onMigrate: (String) -> Unit) {
    val st = statusOf(s.status)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, RenzoColors.Border.copy(alpha = 0.5f), RoundedCornerShape(0.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Box(Modifier.size(8.dp).background(st.dot, CircleShape))
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    s.provider,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = RenzoColors.Foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Text(
                    st.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = RenzoColors.MutedForeground,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Text(healthDetail(s), style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
            Text(healthSpeed(s), style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground.copy(alpha = 0.7f))
        }
        if ((s.status == "dead" || s.status == "failing") && s.enabledCount > 0) {
            OutlineActionButton(label = "Migrate", icon = null, enabled = true) { onMigrate(s.provider) }
        }
    }
}

@Composable
private fun Message(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.TopStart) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = color)
    }
}
