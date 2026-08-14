package top.levitatemedia.renzo.tv.ui.screens

import android.widget.Toast
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.Screen
import top.levitatemedia.renzo.tv.api.AutodlStatus
import top.levitatemedia.renzo.tv.api.CardItem
import top.levitatemedia.renzo.tv.api.JobItem
import top.levitatemedia.renzo.tv.ui.components.OutlineButton
import top.levitatemedia.renzo.tv.ui.components.dashedBorder
import top.levitatemedia.renzo.tv.ui.components.tvClickable
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
    val context = LocalContext.current
    var jobs by remember { mutableStateOf<List<JobItem>?>(null) }
    var autodl by remember { mutableStateOf<AutodlStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var retryBusy by remember { mutableStateOf(false) }
    var runBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun toast(msg: String) = Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
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

    Column(Modifier.fillMaxSize()) {
        // `h2 text-xl font-semibold`
        Text("Downloads", color = RenzoColors.Foreground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)

        if (jobs == null && error != null) {
            Text(
                error!!,
                color = RenzoColors.MutedForeground,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 16.dp),
            )
            return@Column
        }

        // `mt-4 grid grid-cols-1 gap-4` [AutodlBar, JobsList(gap-3)]
        val jobList = jobs ?: emptyList()
        val groups: Map<Int, List<JobItem>> = jobList.groupBy { it.titleId }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "autodl") {
                AutodlBar(
                    s = autodl,
                    runBusy = runBusy,
                    showRetryAll = failed.isNotEmpty(),
                    retryBusy = retryBusy,
                    onRetryAll = { retryAll() },
                    onRun = { runNow() },
                    onOpenCredentials = { app.nav.push(Screen.Account("credentials")) },
                    modifier = Modifier.padding(bottom = 4.dp), // gap-4 vs the list's gap-3
                )
            }
            if (jobList.isEmpty()) {
                item(key = "empty") {
                    // `empty rounded-lg border border-dashed p-8 text-center text-sm`
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .dashedBorder(RenzoColors.Border, 8.dp)
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("No downloads.", color = RenzoColors.MutedForeground, fontSize = 14.sp)
                    }
                }
            } else {
                items(groups.entries.toList(), key = { it.key }) { (_, gjobs) ->
                    JobGroup(
                        gjobs = gjobs,
                        denied = denied,
                        onPrioritize = { job, done ->
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
                        onRetry = { job, done ->
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
                        onRetryGroup = { gj, done ->
                            scope.launch {
                                try {
                                    for (j in gj.filter { canRetryJob(it, denied) }) {
                                        try {
                                            app.repo.retryEpisode(j.titleId, j.episode)
                                        } catch (e: CancellationException) {
                                            throw e
                                        } catch (_: Exception) {
                                            // keep going; one bad job shouldn't stop the rest
                                        }
                                    }
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
 * Auto-downloader status bar + self-check warning chips (autodl-bar.tsx):
 * `autodl flex flex-wrap items-center gap-3 rounded-lg border bg-card/50
 * px-3.5 py-2.5`; the status line, the retry slot, owner-only "Run now" and
 * the amber `autodl-check` chips (credentials ones open that settings pane).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AutodlBar(
    s: AutodlStatus?,
    runBusy: Boolean,
    showRetryAll: Boolean,
    retryBusy: Boolean,
    onRetryAll: () -> Unit,
    onRun: () -> Unit,
    onOpenCredentials: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val you = s?.scope == "you"
    val checks = s?.checks ?: emptyList()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RenzoColors.Card.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        FlowRow(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val text = buildAnnotatedString {
                when {
                    s == null -> append("Auto-downloader…")
                    s.enabled -> {
                        withStyle(SpanStyle(color = RenzoColors.Emerald400)) { append("●") }
                        append(" Auto-downloader on — every ${s.intervalMin}m, ${s.trackedTitles} ")
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
                    else -> append("○ Auto-downloader off — set AUTO_DOWNLOAD=true in .env")
                }
            }
            Text(
                text,
                color = RenzoColors.MutedForeground,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterVertically).weight(1f),
            )
            if (showRetryAll) {
                OutlineButton(
                    label = "↻ Retry all failed",
                    onClick = onRetryAll,
                    enabled = !retryBusy,
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
            if (s?.canRun == true) {
                OutlineButton(
                    label = "Run now",
                    onClick = onRun,
                    enabled = !(s.running || runBusy),
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }
        if (checks.isNotEmpty()) {
            // `autodl-checks mt-1 flex basis-full flex-col gap-1`
            Column(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                checks.forEach { c ->
                    val clickable = c.action == "settings:credentials"
                    var focused by remember { mutableStateOf(false) }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                RenzoColors.Amber400.copy(alpha = if (clickable && focused) 0.2f else 0.1f),
                                RoundedCornerShape(8.dp),
                            )
                            .border(1.dp, RenzoColors.Amber400.copy(alpha = 0.25f), RoundedCornerShape(8.dp))
                            .let {
                                if (clickable) it.tvClickable(
                                    onFocused = { f -> focused = f },
                                    onClick = onOpenCredentials,
                                ) else it
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                    ) {
                        Text("⚠ ${c.message}", color = RenzoColors.Amber300, fontSize = 12.5.sp)
                    }
                }
            }
        }
    }
}

/** One series group (`job-group rounded-lg border bg-card/50`): head with the
 *  truncating title, the "N eps · x active · y failed" summary and "↻ Retry
 *  failed"; then the per-episode rows separated by `divide-border/60`. */
@Composable
private fun JobGroup(
    gjobs: List<JobItem>,
    denied: Boolean,
    onPrioritize: (JobItem, done: () -> Unit) -> Unit,
    onRetry: (JobItem, done: () -> Unit) -> Unit,
    onRetryGroup: (List<JobItem>, done: () -> Unit) -> Unit,
) {
    var groupBusy by remember { mutableStateOf(false) }
    val dl = gjobs.count { it.status == "downloading" || it.status == "searching" }
    val q = gjobs.count { it.status == "queued" }
    val failed = gjobs.count { it.status == "failed" }
    val done = gjobs.count { it.status == "downloaded" }
    val parts = listOfNotNull(
        dl.takeIf { it > 0 }?.let { "$it active" },
        q.takeIf { it > 0 }?.let { "$it queued" },
        failed.takeIf { it > 0 }?.let { "$it failed" },
        done.takeIf { it > 0 }?.let { "$it done" },
    )
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RenzoColors.Card.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp)),
    ) {
        // `job-group-head flex flex-wrap items-center gap-2 border-b px-3 py-2.5`
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                gjobs.first().title,
                color = RenzoColors.Foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${gjobs.size} ep" + (if (gjobs.size == 1) "" else "s") +
                    (if (parts.isNotEmpty()) " · " + parts.joinToString(" · ") else ""),
                color = RenzoColors.MutedForeground,
                fontSize = 12.sp,
            )
            if (gjobs.any { canRetryJob(it, denied) }) {
                OutlineButton(
                    label = "↻ Retry failed",
                    onClick = {
                        if (!groupBusy) {
                            groupBusy = true
                            onRetryGroup(gjobs) { groupBusy = false }
                        }
                    },
                    enabled = !groupBusy,
                    height = 28.dp,
                    horizontalPadding = 8.dp,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(RenzoColors.Border))
        gjobs.sortedBy { it.episode }.forEachIndexed { i, job ->
            if (i > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(RenzoColors.Border.copy(alpha = 0.6f)))
            }
            JobRow(
                job = job,
                denied = denied,
                onPrioritize = { done -> onPrioritize(job, done) },
                onRetry = { done -> onRetry(job, done) },
            )
        }
    }
}

/** One episode row (`job px-3 py-2`): "E{n}" + status/percent line, optional
 *  message, the progress track and the queued/failed action buttons. */
@Composable
private fun JobRow(
    job: JobItem,
    denied: Boolean,
    onPrioritize: (done: () -> Unit) -> Unit,
    onRetry: (done: () -> Unit) -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    val pct = ((job.progress) * 100).roundToInt()
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "E${job.episode}",
                color = RenzoColors.Foreground,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            // `st text-xs capitalize text-muted-foreground`
            Text(
                job.status.replaceFirstChar { it.uppercase() } +
                    (if (job.status == "downloading") " $pct%" else ""),
                color = RenzoColors.MutedForeground,
                fontSize = 12.sp,
            )
        }
        job.message?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = RenzoColors.MutedForeground, fontSize = 12.sp)
        }
        // `track mt-1.5 h-1.5 rounded-full bg-foreground/10` + status fill
        val fillPct = if (job.status == "downloaded") 100 else pct
        Box(
            Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(RenzoColors.Foreground.copy(alpha = 0.1f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((fillPct / 100f).coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(if (job.status == "failed") RenzoColors.Destructive else RenzoColors.Primary),
            )
        }
        val showPrioritize = job.status == "queued" && job.mine
        val showRetry = canRetryJob(job, denied)
        if (showPrioritize || showRetry) {
            // `job-actions mt-1.5 flex flex-wrap gap-2` — h-7 px-2 text-xs buttons
            Row(
                Modifier.padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (showPrioritize) {
                    OutlineButton(
                        label = "↑ Download now",
                        onClick = { if (!busy) { busy = true; onPrioritize { busy = false } } },
                        enabled = !busy,
                        height = 28.dp,
                        horizontalPadding = 8.dp,
                    )
                }
                if (showRetry) {
                    OutlineButton(
                        label = "↻ Retry",
                        onClick = { if (!busy) { busy = true; onRetry { busy = false } } },
                        enabled = !busy,
                        height = 28.dp,
                        horizontalPadding = 8.dp,
                    )
                }
            }
        }
    }
}
