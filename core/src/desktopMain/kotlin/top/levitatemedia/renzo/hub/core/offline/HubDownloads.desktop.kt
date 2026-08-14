package top.levitatemedia.renzo.hub.core.offline

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

actual object HubDownloads {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val queue = DownloadQueue()

    /** Same engine as Android's service — no notification, no metered gate. */
    private val engine by lazy { DownloadEngine(store = DesktopOfflineFiles(), queue = queue) }

    actual fun enqueue(job: DownloadJob) {
        queue.enqueue(job)
    }

    actual fun start() {
        if (!engine.isRunning) scope.launch { engine.runQueue() }
    }

    actual fun stopAll() {
        queue.clear()
        engine.stop()
    }
}
