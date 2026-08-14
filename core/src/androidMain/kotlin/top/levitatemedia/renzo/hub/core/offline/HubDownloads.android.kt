package top.levitatemedia.renzo.hub.core.offline

import top.levitatemedia.renzo.hub.core.HubContextHolder

actual object HubDownloads {
    actual fun enqueue(job: DownloadJob) {
        DownloadQueue().enqueue(job)
    }

    actual fun start() {
        HubDownloadService.start(HubContextHolder.context)
    }

    actual fun stopAll() {
        HubDownloadService.stop(HubContextHolder.context)
    }
}
