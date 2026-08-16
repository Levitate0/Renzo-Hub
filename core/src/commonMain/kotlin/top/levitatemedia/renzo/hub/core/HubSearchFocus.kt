package top.levitatemedia.renzo.hub.core

import androidx.compose.ui.focus.FocusRequester

/**
 * ⌘K / Ctrl-K → focus the topbar search, from anywhere (web parity, both
 * halves). Whichever half's search field is currently composed registers its
 * requester here; the desktop window's key handler calls it. Null off
 * desktop or while no search field is on screen.
 */
object HubSearchFocus {
    var requester: FocusRequester? = null
}
