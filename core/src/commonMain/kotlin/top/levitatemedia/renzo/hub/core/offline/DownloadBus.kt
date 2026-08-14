package top.levitatemedia.renzo.hub.core.offline

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import top.levitatemedia.renzo.hub.core.HubTarget

data class DownloadProgress(
    val active: Boolean = false,
    val target: HubTarget? = null,
    val parentId: String? = null,
    val parentTitle: String? = null,
    val itemKey: String? = null,
    /** Items finished in the current job. */
    val done: Int = 0,
    val total: Int = 0,
    /** Progress within the current item, 0..1. Meaningful for a single large file. */
    val itemFraction: Float = 0f,
)

/**
 * Progress from the downloader to whatever UI is listening.
 *
 * A StateFlow rather than the standalone client's sendBroadcast: the service
 * runs in the same process as the UI, so a broadcast was always a detour, and
 * this drops the receiver registration, the exported-flag handling and the
 * Intent extras marshalling from every screen that wants progress.
 */
object DownloadBus {
    private val _progress = MutableStateFlow(DownloadProgress())
    val progress: StateFlow<DownloadProgress> = _progress.asStateFlow()

    /**
     * Bumped whenever an item finishes or the queue drains, so a screen can
     * reload its list without diffing the manifest itself.
     */
    private val _revision = MutableStateFlow(0)
    val revision: StateFlow<Int> = _revision.asStateFlow()

    internal fun emit(p: DownloadProgress) {
        _progress.value = p
    }

    internal fun itemFinished() {
        _revision.update { it + 1 }
    }

    internal fun idle() {
        _progress.value = DownloadProgress()
        _revision.update { it + 1 }
    }
}
