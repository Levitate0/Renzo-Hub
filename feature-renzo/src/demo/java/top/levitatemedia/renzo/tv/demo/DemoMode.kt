package top.levitatemedia.renzo.tv.demo

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.Screen
import top.levitatemedia.renzo.tv.Tab

/**
 * The STORE-LISTING build (build type `demo`, run it from Android Studio's
 * Build Variants panel).
 *
 * It is the real app against the real server — same login, same library, same
 * playback — with one difference: every image that comes off the network is
 * replaced by a bundled Renzo placeholder, so nothing copyrighted (covers,
 * season art, banners, episode stills, scrub frames, the video itself) can end
 * up in a screenshot destined for Google Play.
 *
 * It also accepts capture extras so a page can be opened straight from adb or
 * an Android Studio run configuration's "Launch Flags", instead of driving the
 * UI by hand for every form factor:
 *
 *   -e page   discover | category | library | library-folder | updates |
 *             history | downloads | search | title | player | account |
 *             credentials | defaults | apikey | users | settings |
 *             appearance | drawer | accountmenu
 *   -e tv     1        → force the TV layout: landscape, no system bars
 *   -e theme  <preset> → renzo | midnight | … (ui/theme/Presets.kt)
 *   -e query  <text>   → seeds the search page
 *   -e id     <int>    → AniList id for the title / player pages
 *   -e ep     <int>    → episode for the player page
 */
object DemoMode {
    const val ENABLED = true

    private var tv = false
    private var page = "discover"
    private var section = "account"
    private var query = ""
    private var overlay: String? = null
    private var titleId = 0
    private var episode = 1

    val forceTv: Boolean get() = tv
    val overlayDrawer: Boolean get() = overlay == "drawer"
    val overlayAccount: Boolean get() = overlay == "account"

    /** Painted over the video surface — a decoded frame is content too. */
    val videoCoverAsset: String? get() = "file:///android_asset/placeholder/video-cover.jpg"

    /** Swap Coil's singleton loader for the placeholder one. */
    fun install(ctx: Context) = DemoImages.install(ctx)

    fun readIntent(intent: Intent?) {
        page = intent?.getStringExtra("page")?.lowercase()?.trim() ?: "discover"
        section = intent?.getStringExtra("section")?.lowercase()?.trim() ?: "account"
        query = intent?.getStringExtra("query")?.trim().orEmpty()
        titleId = intent?.getStringExtra("id")?.toIntOrNull() ?: 0
        episode = intent?.getStringExtra("ep")?.toIntOrNull() ?: 1
        tv = intent?.getStringExtra("tv") in setOf("1", "true", "yes")
        intent?.getStringExtra("theme")?.lowercase()?.trim()?.takeIf { it.isNotEmpty() }?.let {
            themeOverride = it
        }
        overlay = when (page) {
            "drawer", "menu" -> "drawer"
            "accountmenu", "account-menu", "avatar" -> "account"
            else -> null
        }
    }

    private var themeOverride: String? = null

    /**
     * Put the nav stack where `-e page` asked, before the first frame. Pages
     * that need a title id (`title`, `player`) are skipped unless `-e id` was
     * given, since the ids belong to the user's own library.
     */
    fun applyNav(app: AppServices) {
        themeOverride?.let { app.setTheme(it, null) }
        val nav = app.nav
        nav.stack.clear()
        nav.stack.add(Screen.Tabs)
        DemoScreens.reset()
        when (page) {
            "library" -> nav.tab.value = Tab.Library
            "library-folder", "folder" -> {
                nav.tab.value = Tab.Library
                DemoScreens.libraryFolder(section)
            }
            "updates" -> nav.tab.value = Tab.Updates
            "history" -> nav.tab.value = Tab.History
            "downloads" -> nav.tab.value = Tab.Downloads
            "search" -> {
                app.searchQuery.value = query
                nav.tab.value = Tab.Search
            }
            "category", "see-all", "trending" -> {
                nav.tab.value = Tab.Discover
                DemoScreens.discoverCategory(if (page == "category") "recommended" else "trending")
            }
            "title", "series", "detail" -> if (titleId > 0) nav.push(Screen.Title(titleId))
            "player", "watch" -> if (titleId > 0) {
                nav.push(Screen.Title(titleId))
                nav.push(Screen.Player(titleId, episode, ""))
            }
            "account" -> nav.push(Screen.Account(section))
            "credentials", "defaults", "apikey" -> nav.push(Screen.Account(page))
            "users" -> nav.push(Screen.Users)
            "settings" -> nav.push(Screen.ServerSettings)
            "appearance" -> nav.push(Screen.Appearance)
            else -> nav.tab.value = Tab.Discover
        }
    }

    /**
     * TV capture from a phone/tablet emulator image: lock landscape and hide
     * the status/navigation bars, because an Android TV panel has neither and
     * a Play TV screenshot must not show a phone clock.
     */
    fun applyWindow(activity: Activity) {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        val bars = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        if (tv) {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            bars.hide(WindowInsetsCompat.Type.systemBars())
            bars.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            // A later `-e tv 0` run must undo the TV window, not inherit it.
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            bars.show(WindowInsetsCompat.Type.systemBars())
        }
    }
}
