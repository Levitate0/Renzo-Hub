package top.levitatemedia.renzo.hub.core.offline

import android.content.Context
import kotlinx.serialization.json.Json

/**
 * Persisted job queue, shared by both halves.
 *
 * Jobs live here rather than travelling in the Intent that starts the service:
 * a batch of a hundred chapters would blow the Binder transaction size limit,
 * and a persisted queue also survives the process being killed mid-download.
 * That reasoning is inherited from the standalone Shiori client and still holds.
 */
class DownloadQueue(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Synchronized
    fun enqueue(job: DownloadJob) {
        val all = peekAll().toMutableList()
        all.add(job)
        write(all)
    }

    @Synchronized
    fun take(): DownloadJob? {
        val all = peekAll().toMutableList()
        if (all.isEmpty()) return null
        val first = all.removeAt(0)
        write(all)
        return first
    }

    /** Put a job back at the head — used when the queue is paused mid-drain. */
    @Synchronized
    fun pushFront(job: DownloadJob) {
        write(listOf(job) + peekAll())
    }

    @Synchronized
    fun peekAll(): List<DownloadJob> {
        val raw = prefs.getString(KEY_JOBS, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<DownloadJob>>(raw) }.getOrDefault(emptyList())
    }

    @Synchronized
    fun clear() = prefs.edit().remove(KEY_JOBS).apply()

    fun isEmpty(): Boolean = peekAll().isEmpty()

    private fun write(jobs: List<DownloadJob>) {
        prefs.edit().putString(KEY_JOBS, json.encodeToString(jobs)).apply()
    }

    private companion object {
        const val PREFS_NAME = "renzo_offline"
        /**
         * A new key, not the old `download_jobs`. The payload shape changed and
         * anything still queued from the standalone client carries a token that
         * has long since expired — dropping it is correct, not lossy.
         */
        const val KEY_JOBS = "hub_download_jobs"
    }
}
