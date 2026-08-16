package app.renzoshiori.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.renzoshiori.client.ShioriRuntime
import app.renzoshiori.client.data.network.ActiveDownload
import app.renzoshiori.client.data.network.ProgressHubClient
import app.renzoshiori.client.data.network.ProgressTokenApi
import app.renzoshiori.client.data.network.absoluteUrl
import app.renzoshiori.client.ui.theme.RenzoColors
import app.renzoshiori.client.ui.util.screenWidthDp
import coil3.compose.AsyncImage

/**
 * Activity Dock — 1:1 port of the web's activity-dock.tsx: the floating
 * bottom-right panel surfacing the current download(s), fed by the SAME
 * SignalR ProgressHub the web subscribes to (ProgressHubClient). Collapsed
 * shows the top active download; expanded lists up to 5 plus a link to the
 * queue. Dismissal is session-scoped per download id, so a NEW download
 * re-surfaces the dock. Suppressed on the Queue page itself.
 */
@Composable
fun ActivityDock(
    suppressed: Boolean,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = ShioriRuntime.app
    val serverUrl = app.tokenStore.serverUrl ?: return
    val client = remember(serverUrl) {
        ProgressHubClient(
            serverUrl = serverUrl,
            fetchToken = {
                app.network.currentServiceOf<ProgressTokenApi>()?.imageToken()?.token
            },
        )
    }
    LaunchedEffect(client) { client.run() }

    val downloads by client.downloads.collectAsState()
    var expanded by remember { mutableStateOf(false) }

    // Session-scoped dismissals (web: sessionStorage) — pruned to active ids
    // so a fresh download properly re-surfaces the dock.
    LaunchedEffect(downloads) {
        DockDismissed.ids.retainAll(downloads.keys)
    }

    if (suppressed) return
    val visible = downloads.values.filter { it.id !in DockDismissed.ids }
    if (visible.isEmpty()) return

    val top = visible.first()
    val list = visible.take(5)

    val dockWidth = minOf(360.dp, screenWidthDp() * 0.92f)
    Box(modifier.padding(end = 12.dp, bottom = 12.dp).navigationBarsPadding()) {
        Column(
            Modifier
                .width(dockWidth)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, RenzoColors.Border, RoundedCornerShape(12.dp))
                .background(RenzoColors.Background.copy(alpha = 0.97f)),
        ) {
            DockRow(
                item = top,
                extraCount = visible.size - 1,
                expanded = expanded,
                onToggleExpand = { expanded = !expanded },
                onDismiss = { visible.forEach { DockDismissed.ids.add(it.id) } },
            )
            if (expanded && list.size > 1) {
                HorizontalDivider(color = RenzoColors.Border)
                Column(Modifier.heightIn(max = 288.dp).verticalScroll(rememberScrollState())) {
                    list.drop(1).forEachIndexed { i, d ->
                        if (i > 0) HorizontalDivider(color = RenzoColors.Border.copy(alpha = 0.6f))
                        DockItem(d)
                    }
                }
                HorizontalDivider(color = RenzoColors.Border)
                Row(
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
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
    }
}

/** Web sessionStorage twin: survives navigation, resets with the process. */
private object DockDismissed {
    val ids = androidx.compose.runtime.mutableStateListOf<String>()
}

@Composable
private fun DockRow(
    item: ActiveDownload,
    extraCount: Int,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        DockCover(item.thumbnailUrl, width = 36.dp, height = 48.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                item.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = RenzoColors.Foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ProgressLine(item.percentage, barHeight = 4.dp)
            if (item.chapterName.isNotBlank()) {
                Text(
                    item.chapterName,
                    fontSize = 10.sp,
                    color = RenzoColors.MutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        if (extraCount > 0) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleExpand),
            ) {
                Icon(
                    if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ExpandLess,
                    contentDescription = if (expanded) "Collapse queue" else "Show all downloads",
                    tint = RenzoColors.MutedForeground,
                    modifier = Modifier.size(16.dp),
                )
                if (!expanded) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset { IntOffset(4, -2) }
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(RenzoColors.Primary),
                    ) {
                        Text(
                            "+$extraCount",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = RenzoColors.PrimaryForeground,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .padding(start = 4.dp)
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Hide download dock",
                tint = RenzoColors.MutedForeground,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun DockItem(item: ActiveDownload) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        DockCover(item.thumbnailUrl, width = 30.dp, height = 40.dp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                item.title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = RenzoColors.Foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ProgressLine(item.percentage, barHeight = 2.dp)
        }
    }
}

@Composable
private fun ProgressLine(percentage: Int, barHeight: androidx.compose.ui.unit.Dp) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
        Box(
            Modifier
                .weight(1f)
                .height(barHeight)
                .clip(RoundedCornerShape(50))
                .background(RenzoColors.Muted),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((percentage / 100f).coerceIn(0f, 1f))
                    .height(barHeight)
                    .background(RenzoColors.Primary),
            )
        }
        Text(
            "$percentage%",
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            color = RenzoColors.MutedForeground,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun DockCover(thumbnailUrl: String?, width: androidx.compose.ui.unit.Dp, height: androidx.compose.ui.unit.Dp) {
    val baseUrl = ShioriRuntime.app.tokenStore.serverUrl ?: ""
    Box(
        Modifier
            .width(width)
            .height(height)
            .clip(RoundedCornerShape(6.dp))
            .background(RenzoColors.Muted),
    ) {
        if (!thumbnailUrl.isNullOrBlank()) {
            AsyncImage(
                model = absoluteUrl(baseUrl, thumbnailUrl),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
