package app.renzoshiori.client.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.renzoshiori.client.ShioriRuntime
import app.renzoshiori.client.data.model.FavoriteListDto
import top.levitatemedia.renzo.hub.core.HubPlatform
import app.renzoshiori.client.data.network.serverErrorMessage
import app.renzoshiori.client.data.model.LibraryRowDto
import app.renzoshiori.client.data.model.ScrobblerConfigLiteDto
import app.renzoshiori.client.data.model.SettingsLiteDto
import app.renzoshiori.client.data.model.UserLevel
import app.renzoshiori.client.data.network.LibraryExtrasApi
import app.renzoshiori.client.data.offline.OfflineRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val loading: Boolean = true,
    val error: String? = null,
    /** Browsing the on-device offline library instead of the live server one. */
    val offlineMode: Boolean = false,
    val series: List<LibraryRowDto> = emptyList(),
    val offlineSeries: List<OfflineRepository.OfflineSeries> = emptyList(),
    val searchTerm: String = "",
    /** Owner-only display preference — every user's library instead of just mine. */
    val viewAllLibraries: Boolean = false,
    val canOwner: Boolean = false,
    /** Manager+ — gates the Add Series wording ("Request Series" otherwise). */
    val canAddSeries: Boolean = false,
    val favoriteLists: List<FavoriteListDto> = emptyList(),
    /**
     * True when offline was entered because the device had no internet, rather
     * than because the user pressed the pill. Only the forced kind undoes
     * itself when the network comes back — a deliberate choice must stick.
     */
    val offlineForced: Boolean = false,
    val settings: SettingsLiteDto? = null,
    /** Connected trackers — the Track-all chip self-hides when empty. */
    val connectedTrackers: List<ScrobblerConfigLiteDto> = emptyList(),
    val trackingAll: Boolean = false,
    /** Transient banner, e.g. the Track-all confirmation/error toast text. */
    val toast: String? = null,
)

class LibraryViewModel : ViewModel() {
    private val app = ShioriRuntime.app

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()

    val baseUrl: String get() = app.tokenStore.serverUrl ?: ""

    private fun extras(): LibraryExtrasApi? = app.network.currentServiceOf<LibraryExtrasApi>()

    init {
        refresh()
        loadSideData()
    }

    fun refresh() {
        // Came back onto a network after being forced offline? Rejoin on our
        // own — the user never asked to be offline, so they should not have to
        // ask to stop being offline.
        if (_state.value.offlineMode && _state.value.offlineForced && HubPlatform.hasInternet()) {
            _state.value = _state.value.copy(offlineMode = false, offlineForced = false)
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            if (_state.value.offlineMode) {
                loadOffline()
            } else {
                val api = extras()
                if (api == null) {
                    // No server address stored. Distinct from "unreachable" —
                    // this means the connection was never made or was lost.
                    _state.value = _state.value.copy(
                        loading = false,
                        error = "Not connected to a server.",
                    )
                    return@launch
                }
                runCatching { api.library(_state.value.viewAllLibraries) }
                    .onSuccess { rows ->
                        // Deduplicate by id — the web page does the same before rendering.
                        val seen = HashSet<String>()
                        val unique = rows.filter { seen.add(it.id) }
                        _state.value = _state.value.copy(loading = false, series = unique, error = null)
                    }
                    .onFailure { e ->
                        // Offline is a MODE, not an error handler. You enter it
                        // by pressing the pill, or because the device genuinely
                        // has no internet — never because one request failed.
                        // A 500, a bad payload or a server that is up but
                        // unhappy used to switch the whole library to offline
                        // and render an empty grid, which reads as "my series
                        // are gone" rather than "that call failed".
                        if (!HubPlatform.hasInternet()) {
                            loadOffline(autoFellBack = true)
                        } else {
                            // Stay online, keep what is already on screen, and
                            // say what actually went wrong.
                            _state.value = _state.value.copy(
                                loading = false,
                                error = e.serverErrorMessage("Couldn't load your library."),
                            )
                        }
                    }
            }
        }
    }

    /** Permissions, favourites, settings and trackers — everything the ribbon shows. */
    private fun loadSideData() {
        viewModelScope.launch {
            runCatching { app.network.currentApi()?.me() }.getOrNull()?.let { me ->
                _state.value = _state.value.copy(
                    canOwner = me.level >= UserLevel.OWNER,
                    canAddSeries = me.level >= UserLevel.MANAGER,
                )
            }
            val api = extras() ?: return@launch
            runCatching { api.favorites() }.getOrNull()?.let {
                _state.value = _state.value.copy(favoriteLists = it)
            }
            runCatching { api.settings() }.getOrNull()?.let {
                _state.value = _state.value.copy(settings = it)
            }
            runCatching { api.scrobblerConfigs() }.getOrNull()?.let { configs ->
                _state.value = _state.value.copy(connectedTrackers = configs.filter { it.isConnected })
            }
        }
    }

    private suspend fun loadOffline(autoFellBack: Boolean = false) {
        val offline = withContext(Dispatchers.IO) { app.offline.listSeries() }
        _state.value = _state.value.copy(
            loading = false,
            offlineMode = true,
            offlineForced = autoFellBack,
            offlineSeries = offline,
            // Only reachable now by pressing the pill or by a genuine loss of
            // internet, so it can say exactly that.
            error = if (autoFellBack && offline.isEmpty()) {
                "No internet, and nothing is saved on this device."
            } else {
                null
            },
        )
    }

    fun setOfflineMode(offline: Boolean) {
        _state.value = _state.value.copy(offlineMode = offline, offlineForced = false)
        refresh()
    }

    fun setSearch(term: String) {
        _state.value = _state.value.copy(searchTerm = term)
    }

    fun setViewAllLibraries(viewAll: Boolean) {
        _state.value = _state.value.copy(viewAllLibraries = viewAll)
        refresh()
    }

    /** Track all — auto-matches the whole library on every connected tracker. */
    fun trackAll() {
        val connected = _state.value.connectedTrackers
        if (connected.isEmpty() || _state.value.trackingAll) return
        _state.value = _state.value.copy(trackingAll = true)
        viewModelScope.launch {
            val api = extras()
            // Report what actually went wrong: a non-2xx never throws through
            // Retrofit's Response<T>, so it has to be checked explicitly, and
            // a timeout/connection drop should not read the same as a refusal.
            var failure: String? = null
            runCatching {
                connected.forEach { tracker ->
                    val response = api?.autoMatchAll(tracker.provider)
                    if (response != null && !response.isSuccessful && failure == null) {
                        failure = "${tracker.displayName}: HTTP ${response.code()}"
                    }
                }
            }.onFailure { e ->
                failure = when (e) {
                    is java.net.SocketTimeoutException -> "the server is still working — give it a minute and check Trackers"
                    is java.io.IOException -> "can't reach the server"
                    else -> e.message?.take(120) ?: "unknown error"
                }
            }
            val ok = failure == null
            _state.value = _state.value.copy(
                trackingAll = false,
                toast = if (ok) "Tracking all your series…" else "Couldn't track all series — $failure",
            )
        }
    }

    fun clearToast() {
        _state.value = _state.value.copy(toast = null)
    }

    companion object {
        fun factory() = viewModelFactory {
            initializer { LibraryViewModel() }
        }
    }
}
