package app.renzoshiori.client.ui.downloads

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import app.renzoshiori.client.data.offline.OfflineSync
import app.renzoshiori.client.ui.series.DestructiveDialogButton
import app.renzoshiori.client.ui.series.OutlineDialogButton
import app.renzoshiori.client.ui.series.RenzoDialog
import app.renzoshiori.client.ui.util.hubToast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How often the watcher looks at the connection. Cheap: no request unless there is work. */
private const val WATCH_INTERVAL_MS = 15_000L

/**
 * RenzoFrontend comp/offline-watcher.tsx: syncs reading progress made offline
 * once the server answers again (useOfflineReadSync) and, with Auto-clean on,
 * asks before removing downloads (useReconnectPurge). Mounted once for the
 * signed-in Shiori half, above the routes, so it keeps running in the reader.
 *
 * The web's prompt is a card pinned to the bottom of the screen; here it is a
 * dialog, so nothing floats over the content for longer than it takes to
 * answer. It only offers chapters already read — see [OfflineSync].
 */
@Composable
fun OfflineWatcher() {
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { OfflineSync.tick() }
            delay(WATCH_INTERVAL_MS)
        }
    }
    val count by OfflineSync.purgePrompt.collectAsState()
    val n = count ?: return
    RenzoDialog(
        title = "Back online",
        onDismiss = { OfflineSync.dismissPurge() },
        description = "Remove $n downloaded chapter${if (n == 1) "" else "s"} you've already read? " +
            "Unread chapters and the one you're reading are kept.",
        footer = {
            OutlineDialogButton("Keep") { OfflineSync.dismissPurge() }
            DestructiveDialogButton("Remove") {
                scope.launch {
                    val removed = OfflineSync.confirmPurge()
                    hubToast("Cleared — $removed chapter${if (removed == 1) "" else "s"} removed.")
                }
            }
        },
    )
}
