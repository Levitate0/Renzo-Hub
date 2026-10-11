package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.Screen
import top.levitatemedia.renzo.tv.api.AutodlStatus
import top.levitatemedia.renzo.tv.api.CardItem
import top.levitatemedia.renzo.tv.api.JobItem
import top.levitatemedia.renzo.tv.ui.kit.kitClickable
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

// ---------------------------------------------------------------------------
// Downloads (app/downloads/page.tsx + autodl-bar.tsx + jobs-list.tsx): the
// auto-downloader status bar with self-check chips, "↻ Retry all failed", and
// the jobs list grouped by series with per-job progress bars, "↑ Download
// now" (prioritize) and "↻ Retry" — 4s poll like the web ["jobs"] query.
// ---------------------------------------------------------------------------

private fun canRetryJob(j: JobItem, downloadsDenied: Boolean): Boolean =
    j.status == "failed" && j.mine && !downloadsDenied

@Composable
fun DownloadsScreen(app: AppServices, @Suppress("UNUSED_PARAMETER") onOpen: (CardItem) -> Unit) {
    var jobs by remember { mutableStateOf<List<JobItem>?>(null) }
    var autodl by remember { mutableStateOf<AutodlStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var retryBusy by remember { mutableStateOf(false) }
    var runBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun toast(msg: String) = top.levitatemedia.renzo.tv.renzoToast(msg)
    fun refetchJobs() { refreshKey++ }

    // Shared 4s poll (web: ["jobs"] + ["autodl"] both refetch at 4000ms);
    // bumping refreshKey restarts the loop for an immediate refetch.
    LaunchedEffect(refreshKey) {
        while (true) {
            try {
                jobs = app.repo.jobs()
                app.activeJobs.value = jobs?.count { it.active } ?: 0
                error = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (jobs == null) error = e.message ?: "Couldn't load downloads"
            }
            try {
                autodl = app.repo.autodlStatus()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            delay(4000)
        }
    }

    val denied = app.user.value?.downloadsDenied == true
    val failed = (jobs ?: emptyList()).filter { canRetryJob(it, denied) }

    // Retry every failed download at once (old #retryAll handler).
    fun retryAll() {
        if (retryBusy) return
        retryBusy = true
        scope.launch {
            try {
                if (failed.isEmpty()) {
                    toast("Nothing to retry")
                    return@launch
                }
                var ok = 0
                for (j in failed) {
                    try {
                        app.repo.retryEpisode(j.titleId, j.episode)
                        ok++
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        // keep going; one bad job shouldn't stop the rest
                    }
                }
                toast("Retrying $ok download" + if (ok == 1) "" else "s")
            } finally {
                retryBusy = false
                refetchJobs()
            }
        }
    }

    // "Run now" (owner-only server-side; the button renders only when canRun).
    fun runNow() {
        if (runBusy) return
        runBusy = true
        toast("Auto-download pass started…")
        scope.launch {
            try {
                val r = app.repo.autodlRun()
                toast("Auto-download queued ${r.queued}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                toast(e.message ?: "Run failed")
            } finally {
                runBusy = false
                refetchJobs()
            }
        }
    }

    // Shiori's Queue page (feature-shiori ui/queue/QueueScreen.kt): a centred
    // 1100dp column, the filter pills, the "Downloads  N" header with its
    // actions, the auto-downloader line, then flat rows — status dot, title +
    // episode, the % or state on the right, icon actions, and a thin progress
    // bar along the bottom of a downloading row.
    var filterIndex by remember { mutableIntStateOf(0) }
    val jobList = jobs ?: emptyList()
    val visible = remember(jobList, filterIndex) {
        jobList
            .filter { j ->
                when (filterIndex) {
                    1 -> j.status == "downloading" || j.status == "searching"
                    2 -> j.status == "queued"
                    3 -> j.status == "downloaded"
                    4 -> j.status == "failed"
                    else -> true
                }
            }
            // Active first, then queued, failed, done — the queue's own order.
            .sortedWith(compareBy<JobItem>({ statusRank(it.status) }, { it.title.lowercase() }, { it.episode }))
    }
    val wide = top.levitatemedia.renzo.tv.ui.kit.screenWidthDp() >= 1024.dp

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.then(if (wide) Modifier.widthIn(max = 1100.dp) else Modifier).fillMaxSize()) {
            Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                top.levitatemedia.renzo.tv.ui.kit.SegmentedPills(
                    labels = DL_FILTERS,
                    selectedIndex = filterIndex,
                    onSelect = { filterIndex = it },
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp),
            ) {
                Text(
                    "Downloads",
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
                    color = RenzoColors.Foreground,
                )
                Text(
                    jobList.size.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = RenzoColors.MutedForeground.copy(alpha = 0.7f),
                    modifier = Modifier.padding(start = 12.dp).weight(1f),
                )
                if (failed.isNotEmpty()) {
                    HeaderAction(
                        label = if (retryBusy) "Retrying…" else "Retry all failed",
                        icon = Icons.Filled.Refresh,
                        color = RenzoColors.Red,
                        enabled = !retryBusy,
                        onClick = { retryAll() },
                    )
                    Spacer(Modifier.width(8.dp))
                }
                if (autodl?.canRun == true) {
                    // Shiori's "Update now" pill.
                    HeaderAction(
                        label = if (runBusy || autodl?.running == true) "Running…" else "Run now",
                        icon = Icons.Filled.PlayArrow,
                        color = RenzoColors.Primary,
                        enabled = !(runBusy || autodl?.running == true),
                        onClick = { runNow() },
                    )
                }
            }

            AutodlLine(autodl, onOpenCredentials = { app.nav.push(Screen.Account("credentials")) })

            when {
                jobs == null && error != null -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(error!!, style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
                }
                jobs == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Loading…", style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
                }
                visible.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (filterIndex == 0) "No downloads. Episodes you download, or auto-download picks up, appear here."
                        else "Nothing to show for \"${DL_FILTERS[filterIndex]}\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = RenzoColors.MutedForeground,
                        textAlign = TextAlign.Center,
                    )
                }
                else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = 8.dp, bottom = 64.dp)) {
                    items(visible, key = { it.id }) { job ->
                        if (job != visible.first()) {
                            Box(Modifier.fillMaxWidth().height(1.dp).background(RenzoColors.Foreground.copy(alpha = 0.04f)))
                        }
                        JobFeedRow(
                            job = job,
                            denied = denied,
                            onPrioritize = { done ->
                                scope.launch {
                                    try {
                                        app.repo.prioritizeJob(job.id)
                                        toast("Moved to the front")
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        toast(e.message ?: "Failed")
                                    } finally {
                                        done()
                                        refetchJobs()
                                    }
                                }
                            },
                            onRetry = { done ->
                                scope.launch {
                                    try {
                                        app.repo.retryEpisode(job.titleId, job.episode)
                                        toast("Retrying…")
                                    } catch (e: CancellationException) {
                                        throw e
                                    } catch (e: Exception) {
                                        toast(e.message ?: "Failed")
                                    } finally {
                                        done()
                                        refetchJobs()
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private val DL_FILTERS = listOf("All", "Downloading", "Queued", "Completed", "Failed")

private fun statusRank(s: String): Int = when (s) {
    "downloading", "searching" -> 0
    "queued" -> 1
    "failed" -> 2
    else -> 3
}

/** Shiori's queue status dot colours. */
private fun jobDot(s: String): Color = when (s) {
    "downloaded" -> Color(0xFF31C46B)
    "failed" -> Color(0xFFD84343)
    "downloading", "searching" -> Color(0xFF3B8FEE)
    else -> Color(0xFFEEA51F)
}

/** Shiori's tinted header pill ("Update now"). */
@Composable
private fun HeaderAction(label: String, icon: ImageVector, color: Color, enabled: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(32.dp)
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.10f))
            .kitClickable(enabled = enabled, radius = 16.dp, onClick = onClick)
            .padding(horizontal = 12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, modifier = Modifier.padding(start = 6.dp))
    }
}

/** `new Date(lastRun).toLocaleTimeString()` — locale time from the ISO stamp. */
private fun lastRunTime(iso: String): String = try {
    val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
    parser.timeZone = TimeZone.getTimeZone("UTC")
    val date = parser.parse(iso.take(19))
    if (date != null) DateFormat.getTimeInstance().format(date) else iso
} catch (_: Exception) {
    iso
}

/**
 * The auto-downloader's status (autodl-bar.tsx) as one muted line under the
 * header — where Shiori shows its scan progress — plus the amber self-check
 * warnings (the credentials ones open that Account section).
 */
@Composable
private fun AutodlLine(s: AutodlStatus?, onOpenCredentials: () -> Unit) {
    val you = s?.scope == "you"
    val text = buildAnnotatedString {
        when {
            s == null -> append("Auto-downloader…")
            s.enabled -> {
                withStyle(SpanStyle(color = RenzoColors.Emerald400)) { append("● ") }
                append("Auto-download on — every ${s.intervalMin}m, ${s.trackedTitles} ")
                append(if (you) "tracked for you" else "tracked")
                append(" · ")
                val lastRun = s.lastRun
                if (lastRun != null) {
                    append("last run ${lastRunTime(lastRun)} (queued ${s.lastQueued}${if (you) " for you" else ""})")
                } else {
                    append("first run pending")
                }
                if (s.lastError != null) {
                    withStyle(SpanStyle(color = RenzoColors.Amber400)) { append(" · ⚠ ${s.lastError}") }
                }
            }
            else -> append("○ Auto-download off — set AUTO_DOWNLOAD=true in .env")
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        Text(text, style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
        (s?.checks ?: emptyList()).forEach { c ->
            val clickable = c.action == "settings:credentials"
            Text(
                "⚠ ${c.message}",
                style = MaterialTheme.typography.bodySmall,
                color = RenzoColors.Amber300,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(RenzoColors.Amber400.copy(alpha = 0.1f))
                    .border(1.dp, RenzoColors.Amber400.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                    .then(if (clickable) Modifier.kitClickable(onClick = onOpenCredentials) else Modifier)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            )
        }
    }
}

/** Shiori's QueueRow, for one episode job. */
@Composable
private fun JobFeedRow(
    job: JobItem,
    denied: Boolean,
    onPrioritize: (done: () -> Unit) -> Unit,
    onRetry: (done: () -> Unit) -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    val pct = (job.progress * 100).roundToInt().coerceIn(0, 100)
    val downloading = job.status == "downloading" || job.status == "searching"
    Box(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
        ) {
            Box(Modifier.size(7.dp).clip(CircleShape).background(jobDot(job.status)))
            Column(Modifier.padding(start = 12.dp, end = 8.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        job.title,
                        style = MaterialTheme.typography.bodyMedium.copy(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
                        color = RenzoColors.Foreground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Text(
                        "E${job.episode}",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                        color = RenzoColors.MutedForeground,
                        maxLines = 1,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    if (!job.mine) {
                        Text(
                            "· another user",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = RenzoColors.MutedForeground.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                job.message?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                        color = RenzoColors.MutedForeground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Text(
                if (job.status == "downloading" && pct > 0) "$pct%" else job.status,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = if (downloading) Color(0xFF83B9F5) else RenzoColors.MutedForeground,
                maxLines = 1,
            )
            if (job.status == "queued" && job.mine) {
                RowIcon(Icons.Filled.ArrowUpward, "Download now", enabled = !busy) { busy = true; onPrioritize { busy = false } }
            }
            if (canRetryJob(job, denied)) {
                RowIcon(Icons.Filled.Refresh, "Retry download", enabled = !busy) { busy = true; onRetry { busy = false } }
            }
        }
        if (downloading) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(pct / 100f)
                    .height(2.dp)
                    .background(Color(0xFF3B8FEE)),
            )
        }
    }
}

@Composable
private fun RowIcon(icon: ImageVector, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(28.dp)
            .clip(RoundedCornerShape(6.dp))
            .kitClickable(enabled = enabled, radius = 6.dp, onClick = onClick),
    ) {
        Icon(icon, contentDescription = label, tint = RenzoColors.MutedForeground, modifier = Modifier.size(14.dp))
    }
}
