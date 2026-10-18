package app.renzoshiori.client.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.renzoshiori.client.ShioriRuntime
import app.renzoshiori.client.data.model.DownloadsMetricsDto
import app.renzoshiori.client.data.network.ActiveDownload
import app.renzoshiori.client.data.network.ProgressHubClient
import app.renzoshiori.client.data.network.ProgressTokenApi
import app.renzoshiori.client.data.network.absoluteUrl
import app.renzoshiori.client.ui.theme.RenzoColors
import coil3.compose.AsyncImage
import java.text.NumberFormat

/*
 * The live downloads panel — port of the web's download-status.tsx, which
 * replaced the Activity Dock. The dock floated over the bottom-right corner of
 * every screen and, with a queue that rarely empties, was almost always there,
 * on top of Install buttons, list rows and Apply bars; closing it only lasted
 * until the next chapter started. The panel now drops down from the command
 * bar's download counters, like a browser's download button, and the download
 * icon carries a ring with the running downloads' progress.
 */

/**
 * The live download list from the server's ProgressHub, held for as long as
 * the caller stays composed. Desktop only (user direction 2026-08-17): phones
 * and TV never open the SignalR connection, and get null.
 */
@Composable
fun rememberLiveDownloads(): State<Map<String, ActiveDownload>>? {
    if (!top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop) return null
    val app = ShioriRuntime.app
    val serverUrl = app.tokenStore.serverUrl ?: return null
    val client = remember(serverUrl) {
        ProgressHubClient(
            serverUrl = serverUrl,
            fetchToken = {
                app.network.currentServiceOf<ProgressTokenApi>()?.imageToken()?.token
            },
        )
    }
    LaunchedEffect(client) { client.run() }
    return client.downloads.collectAsState()
}

/** Average progress of the live downloads, or null when there are none. */
fun overallProgress(downloads: Collection<ActiveDownload>): Int? =
    if (downloads.isEmpty()) null else downloads.sumOf { it.percentage } / downloads.size

/**
 * The download icon, ringed with [progress] (0–100) while downloads run —
 * download-status.tsx's DownloadGlyph. Same 20dp box either way, so the
 * counters beside it don't shift when downloads start or stop.
 */
@Composable
fun DownloadGlyph(progress: Int?, tint: Color) {
    if (progress == null) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
            Icon(Icons.Filled.Download, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        }
        return
    }
    val sweep by animateFloatAsState(
        targetValue = progress.coerceIn(0, 100) / 100f,
        animationSpec = tween(300),
        label = "downloadRing",
    )
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(20.dp)) {
        // Web: an SVG circle r=9 in a 22-unit box, stroke 2.
        Canvas(Modifier.fillMaxSize()) {
            val unit = size.minDimension / 22f
            val radius = 9f * unit
            val topLeft = Offset(center.x - radius, center.y - radius)
            val box = Size(radius * 2, radius * 2)
            drawArc(
                color = tint.copy(alpha = 0.25f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = box,
                style = Stroke(width = 2f * unit),
            )
            drawArc(
                color = tint,
                startAngle = -90f,
                sweepAngle = 360f * sweep,
                useCenter = false,
                topLeft = topLeft,
                size = box,
                style = Stroke(width = 2f * unit, cap = StrokeCap.Round),
            )
        }
        Icon(Icons.Filled.Download, contentDescription = null, tint = tint, modifier = Modifier.size(10.dp))
    }
}

/**
 * The panel body (download-status.tsx DownloadsPanel): counts header, the
 * live downloads — downloading first — and the queue link. Hosted by the
 * caller's dropdown.
 */
@Composable
fun DownloadsPanelContent(
    downloads: Collection<ActiveDownload>,
    metrics: DownloadsMetricsDto,
    onOpenSeries: (String) -> Unit,
    onOpenQueue: () -> Unit,
) {
    val numbers = remember { NumberFormat.getIntegerInstance() }
    val sorted = downloads.sortedBy { if (it.starting) 1 else 0 }
    Column(Modifier.width(380.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Text(
                "Downloads",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = RenzoColors.Foreground,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PanelCount(Icons.Filled.Download, numbers.format(metrics.downloads), RenzoColors.Blue)
                PanelCount(Icons.Filled.Schedule, numbers.format(metrics.queued), RenzoColors.Yellow)
                PanelCount(Icons.Filled.Warning, numbers.format(metrics.failed), RenzoColors.Red)
            }
        }
        HorizontalDivider(color = RenzoColors.Border)

        if (sorted.isEmpty()) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 24.dp),
            ) {
                Text("Nothing downloading right now.", fontSize = 14.sp, color = RenzoColors.MutedForeground)
                if (metrics.queued > 0) {
                    Text(
                        "${numbers.format(metrics.queued)} chapter${if (metrics.queued == 1) "" else "s"} waiting for their turn.",
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        color = RenzoColors.MutedForeground.copy(alpha = 0.8f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        } else {
            Column(
                Modifier
                    .heightIn(max = 416.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(4.dp),
            ) {
                sorted.forEach { d -> PanelRow(d, onOpenSeries) }
            }
        }

        HorizontalDivider(color = RenzoColors.Border)
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(RenzoColors.Muted.copy(alpha = 0.3f))
                .clickable(onClick = onOpenQueue)
                .padding(vertical = 8.dp),
        ) {
            Text(
                "View full queue",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = RenzoColors.MutedForeground,
            )
            Icon(
                Icons.Filled.OpenInNew,
                contentDescription = null,
                tint = RenzoColors.MutedForeground,
                modifier = Modifier.padding(start = 4.dp).size(12.dp),
            )
        }
    }
}

@Composable
private fun PanelCount(icon: ImageVector, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(
            value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = color,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun PanelRow(item: ActiveDownload, onOpenSeries: (String) -> Unit) {
    val seriesId = item.seriesId
    val detail = listOf(item.chapterName, item.provider).filter { it.isNotBlank() }.joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .then(if (seriesId != null) Modifier.clickable { onOpenSeries(seriesId) } else Modifier)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        PanelCover(item.thumbnailUrl, width = 36.dp, height = 48.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                item.title.ifBlank { "Download" },
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = RenzoColors.Foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(RenzoColors.Muted),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((item.percentage / 100f).coerceIn(0f, 1f))
                            .height(4.dp)
                            .background(RenzoColors.Primary),
                    )
                }
                Text(
                    if (item.starting) "Starting" else "${item.percentage}%",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = RenzoColors.MutedForeground,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (detail.isNotEmpty()) {
                Text(
                    detail,
                    fontSize = 10.sp,
                    color = RenzoColors.MutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun PanelCover(thumbnailUrl: String?, width: Dp, height: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(RenzoColors.Muted),
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = absoluteUrl(ShioriRuntime.app.tokenStore.serverUrl ?: "", thumbnailUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.Download,
                contentDescription = null,
                tint = RenzoColors.MutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
