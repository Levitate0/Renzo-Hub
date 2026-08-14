package top.levitatemedia.renzo.hub.core.offline

import top.levitatemedia.renzo.hub.core.HubTarget

/**
 * The per-feature seam of the downloader.
 *
 * The service knows how to fetch, resume, write and record; it knows nothing
 * about chapters, episodes, debrid or page routes. Each half registers a source
 * that turns one queued item into a list of files to fetch.
 */
interface DownloadSource {
    /**
     * Called once before a job's items are fetched. Optional; the default does
     * nothing.
     *
     * Renzo uses it to warm the server's stream cache with one batch call, so
     * the per-item resolves that follow return immediately instead of blocking
     * ~45s each.
     */
    suspend fun prepare(job: DownloadJob) {}

    /**
     * Resolve the assets for [item], called immediately before it is fetched
     * rather than when it was queued.
     *
     * Shiori returns the page paths it already baked into the item. Renzo must
     * call the play endpoint here, because its URLs expire.
     *
     * Return an empty list to skip the item.
     */
    suspend fun resolveAssets(job: DownloadJob, item: PendingItem): List<PendingAsset>
}

object DownloadSources {
    private val sources = java.util.concurrent.ConcurrentHashMap<HubTarget, DownloadSource>()

    fun register(target: HubTarget, source: DownloadSource) {
        sources[target] = source
    }

    fun of(target: HubTarget): DownloadSource? = sources[target]
}

/** Passes through assets baked in at enqueue time. */
object StaticDownloadSource : DownloadSource {
    override suspend fun resolveAssets(job: DownloadJob, item: PendingItem): List<PendingAsset> =
        item.assets ?: emptyList()
}
