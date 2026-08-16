package top.levitatemedia.renzo.tv

import androidx.compose.runtime.mutableStateOf
import top.levitatemedia.renzo.hub.core.offline.HubOfflineFiles
import top.levitatemedia.renzo.tv.api.ApiClient
import top.levitatemedia.renzo.tv.api.PublicUser
import top.levitatemedia.renzo.tv.api.Repo

/**
 * App-scoped services + session state, passed to every screen. Platform facts
 * arrive as constructor arguments: Android's RenzoHost passes leanback
 * detection (plus DemoMode's forceTv) and the Context-backed OfflineStore;
 * the desktop passes false and its plain-directory store.
 */
class AppServices(
    /** Leanback device? Drives TV overscan padding (web: body.tv-nav). */
    val isTv: Boolean,
    /** Downloaded episodes on this device. Shared store, Renzo's slice of it. */
    val offlineStore: HubOfflineFiles,
    /** Demo-build screenshot seeds; plain false in debug/release/desktop. */
    val demoOverlayDrawer: Boolean = false,
    val demoOverlayAccount: Boolean = false,
) {
    val prefs = Prefs()
    val client = ApiClient(prefs)
    val repo = Repo(client)
    val nav = AppNav()

    val offline = top.levitatemedia.renzo.tv.offline.renzoOfflineLibrary(offlineStore)

    /** Logged-in user (null until /me or login succeeds). */
    val user = mutableStateOf<PublicUser?>(null)

    /** Device-local adult-content ladder (web parity: none/ecchi/erotica/hentai). */
    val contentLevel = mutableStateOf(prefs.contentLevel)

    /** Topbar search box → Search results screen (web parity). */
    val searchQuery = mutableStateOf("")

    /** Topbar type select (web #searchType): "" = All, "series", "movie". */
    val searchType = mutableStateOf("")

    /** Tab badges (web parity): updates-feed size + active download jobs. */
    val updatesCount = mutableStateOf(0)
    val activeJobs = mutableStateOf(0)

    init {
        // Restore the saved theme before the first frame (web: the inline
        // theme-seed script sets data-theme before paint).
        top.levitatemedia.renzo.tv.ui.theme.RenzoColors.apply(
            prefs.themePreset,
            prefs.themeAccent.takeIf { it != 0 }?.let { androidx.compose.ui.graphics.Color(it) },
        )
    }

    /** Appearance page: switch preset / accent and persist (also synced to the
     *  server so the web UI picks the same theme up). */
    fun setTheme(presetId: String, accent: androidx.compose.ui.graphics.Color?) {
        prefs.themePreset = presetId
        prefs.themeAccent = accent?.let { c ->
            (0xFF shl 24) or
                ((c.red * 255).toInt() shl 16) or
                ((c.green * 255).toInt() shl 8) or
                (c.blue * 255).toInt()
        } ?: 0
        top.levitatemedia.renzo.tv.ui.theme.RenzoColors.apply(presetId, accent)
    }

    fun setContentLevel(level: String) {
        prefs.contentLevel = level
        contentLevel.value = level
    }
}
