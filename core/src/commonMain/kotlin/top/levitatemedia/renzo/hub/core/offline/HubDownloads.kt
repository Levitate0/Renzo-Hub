package top.levitatemedia.renzo.hub.core.offline

/**
 * Enqueue-and-run facade over the shared [DownloadEngine]-based downloader.
 * Android runs the engine inside a foreground Service (notification, metered
 * gate); the desktop runs it on a background coroutine in-process.
 */
expect object HubDownloads {
    /** Persist a job on the shared queue (survives the process). */
    fun enqueue(job: DownloadJob)

    /** Start draining the queue, if not already running. */
    fun start()

    /** Stop and clear the queue. */
    fun stopAll()
}
