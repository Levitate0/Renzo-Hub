package top.levitatemedia.renzo.hub.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * "Open this series" from outside the UI: a tapped release notification.
 *
 * Held until the half it names is signed in and showing, and then taken
 * exactly once. The activity only learns about it from an Intent, possibly
 * before either half has composed, and a half that is still at its sign-in
 * screen must not lose it.
 */
object HubLinks {
    data class Open(val target: HubTarget, val id: String)

    private val _pending = MutableStateFlow<Open?>(null)
    val pending: StateFlow<Open?> = _pending.asStateFlow()

    fun open(link: Open) {
        _pending.value = link
    }

    /** The pending link for [target], removed so it is acted on once; null when there is none. */
    fun take(target: HubTarget): Open? {
        val link = _pending.value ?: return null
        if (link.target != target) return null
        return if (_pending.compareAndSet(link, null)) link else null
    }
}
