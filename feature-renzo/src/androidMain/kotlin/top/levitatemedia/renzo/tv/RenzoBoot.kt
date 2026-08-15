package top.levitatemedia.renzo.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import top.levitatemedia.renzo.hub.core.HubSession
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.CardItem
import top.levitatemedia.renzo.tv.api.PublicUser
import top.levitatemedia.renzo.tv.ui.components.AccountMenu
import top.levitatemedia.renzo.tv.ui.components.LoadingBox
import top.levitatemedia.renzo.tv.ui.components.NavDrawer
import top.levitatemedia.renzo.tv.ui.components.TopBar
import top.levitatemedia.renzo.tv.ui.screens.AccountScreen
import top.levitatemedia.renzo.tv.ui.screens.AppearanceScreen
import top.levitatemedia.renzo.tv.ui.screens.ServerSettingsScreen
import top.levitatemedia.renzo.tv.ui.screens.UsersScreen
import top.levitatemedia.renzo.tv.ui.screens.ConnectScreen
import top.levitatemedia.renzo.tv.ui.screens.DownloadsScreen
import top.levitatemedia.renzo.tv.ui.screens.HistoryScreen
import top.levitatemedia.renzo.tv.ui.screens.HomeScreen
import top.levitatemedia.renzo.tv.ui.screens.LibraryScreen
import top.levitatemedia.renzo.tv.ui.screens.LoginScreen
import top.levitatemedia.renzo.tv.ui.screens.PlayerScreen
import top.levitatemedia.renzo.tv.ui.screens.SearchScreen
import top.levitatemedia.renzo.tv.ui.screens.TitleScreen
import top.levitatemedia.renzo.tv.ui.screens.UpdatesScreen
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/** Boot phases: probe saved server -> session check -> gate or app. */
private enum class Boot { Checking, NeedServer, NeedLogin, Ready }

/**
 * Renzo's boot gate: probe the saved server, check the session, then show the
 * Connect screen, the Login screen or the app.
 *
 * The MainActivity that used to wrap this is gone — the Hub hosts both halves
 * in one activity (see RenzoEntry.kt). Window setup and the demo hooks moved to
 * [RenzoHost]; the theme and TV density scaling moved to [RenzoRoot].
 */
@Composable
internal fun RenzoBoot(app: AppServices, onSwitchApp: (() -> Unit)?, onBackToPicker: (() -> Unit)? = null) {
    var boot by remember { mutableStateOf(Boot.Checking) }

    // Fires on first composition, and again whenever Connect drops back to
    // Checking after saving a new server.
    LaunchedEffect(boot) {
        if (boot == Boot.Checking) boot = probeSession(app)
    }

    // Any rejected request anywhere in this half lands here — see ApiClient.
    // The user goes back to Login instead of staring at screens that quietly
    // render nothing.
    LaunchedEffect(Unit) {
        HubSession.unauthorized.collect { target ->
            if (target != HubTarget.Renzo) return@collect
            app.user.value = null
            app.nav.stack.clear()
            app.nav.stack.add(Screen.Tabs)
            boot = Boot.NeedLogin
        }
    }

    when (boot) {
        Boot.Checking -> LoadingBox(label = "Connecting to your Renzo server…")
        Boot.NeedServer -> ConnectScreen(app, onBackToPicker = onBackToPicker) { boot = Boot.Checking; /* re-probe */
            // Re-run the boot check against the newly saved server.
            app.user.value = null
        }
        Boot.NeedLogin -> LoginScreen(app, onBackToPicker = onBackToPicker) { u ->
            app.user.value = u
            app.prefs.ccLang = u.ccLang
            app.prefs.lastUsername = u.username
            boot = Boot.Ready
        }
        Boot.Ready -> AppRoot(app, onSwitchApp) {
            // Session died (401 mid-use) or user logged out.
            app.prefs.clearSession()
            app.user.value = null
            boot = Boot.NeedLogin
        }
    }
}

/**
 * Decide where boot lands, mirroring the Shiori half's rule exactly.
 *
 * The point is that only a DEFINITE rejection costs the user their saved setup.
 * A 401 means the session really is gone, so Login. Anything else — offline,
 * server restarting, DNS not up yet — means "can't tell", and must NOT dump the
 * user on the Connect screen to retype a server address that was never wrong.
 * In that case boot proceeds with the cached identity; the screens will show
 * their own errors, and Account → Change server is still there if the address
 * genuinely did change.
 *
 * The offline path requires a stored session, so a first run against an
 * unreachable address still lands on Connect where it can be corrected.
 */
private suspend fun probeSession(app: AppServices): Boot {
    app.prefs.serverUrl ?: return Boot.NeedServer

    fun offline(): Boot {
        // No prior session: nothing to fall back to, let them fix the address.
        if (app.prefs.sessionCookie == null) return Boot.NeedServer
        app.user.value = PublicUser(
            id = "",
            username = app.prefs.lastUsername ?: "you",
            ccLang = app.prefs.ccLang,
        )
        return Boot.Ready
    }

    return try {
        val me = app.repo.me()
        if (me.user != null) {
            app.user.value = me.user
            app.prefs.ccLang = me.user.ccLang
            app.prefs.lastUsername = me.user.username
            Boot.Ready
        } else {
            // Covers setupRequired too — LoginScreen shows the message.
            Boot.NeedLogin
        }
    } catch (e: ApiError) {
        if (e.status == 401) {
            app.prefs.clearSession()
            Boot.NeedLogin
        } else {
            offline()
        }
    } catch (_: Exception) {
        offline()
    }
}

@Composable
private fun AppRoot(app: AppServices, onSwitchApp: (() -> Unit)?, onSessionLost: () -> Unit) {
    val nav = app.nav
    val screen = nav.stack.last()
    // Seeded so the demo build can shoot the drawer / account sheet open
    // (`-e page drawer`); both are plain `false` in debug and release.
    var drawerOpen by remember {
        mutableStateOf(top.levitatemedia.renzo.tv.demo.DemoMode.overlayDrawer)
    }
    var accountMenuOpen by remember {
        mutableStateOf(top.levitatemedia.renzo.tv.demo.DemoMode.overlayAccount)
    }

    val doLogout = {
        nav.stack.clear(); nav.stack.add(Screen.Tabs)
        onSessionLost()
    }
    val doChangeServer = {
        app.prefs.forgetServer()
        nav.stack.clear(); nav.stack.add(Screen.Tabs)
        onSessionLost()
    }

    // Remote Back walks the stack; at the root it leaves the app (system
    // default). The drawer's own BackHandler wins while it is open.
    BackHandler(enabled = nav.stack.size > 1) { nav.back() }

    // Tab badges, web-parity cadences: jobs every 4s, updates every 120s.
    LaunchedEffect(Unit) {
        while (true) {
            try { app.activeJobs.value = app.repo.jobs().count { it.active } } catch (_: Exception) {}
            kotlinx.coroutines.delay(4000)
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            try { app.updatesCount.value = app.repo.updates().size } catch (_: Exception) {}
            kotlinx.coroutines.delay(120_000)
        }
    }

    val openCard: (CardItem) -> Unit = openCard@{ card ->
        val id = card.id ?: return@openCard
        if (card.updKind == "episode" || card.updKind == "movie") {
            nav.push(Screen.Player(id, card.ep ?: 1, card.title))
        } else {
            nav.push(Screen.Title(id))
        }
    }

    // Every screen EXCEPT the player is padded clear of the status bar, the
    // navigation bar and any display cutout; the player stays edge-to-edge.
    val insetPad: Modifier = when {
        screen is Screen.Player -> Modifier
        // Android TV has NO status/navigation bar, so system-bar padding is
        // just wasted height there — consume the insets (so nested screens
        // don't re-apply them) and pad only for panel overscan, which crops
        // ~5% of every edge (web: body.tv-nav padding 27px 48px).
        app.isTv -> Modifier
            .consumeWindowInsets(WindowInsets.safeDrawing)
            .padding(horizontal = 48.dp, vertical = 27.dp)
        // Phones/tablets: clear of the status bar, navigation bar and cutouts.
        else -> Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
    }

    val contentHPad = if (app.isTv) 48.dp else 16.dp
    Box(Modifier.fillMaxSize().then(if (screen is Screen.Tabs) Modifier else insetPad)) {
    when (screen) {
        is Screen.Tabs -> {
            Column(
                Modifier
                    .fillMaxSize()
                    // TV: no system bars to avoid — consume the insets so the
                    // bar can truly span the panel. Phones: only the TOP inset
                    // here, so the bar clears the status bar but still fills
                    // the full width edge to edge.
                    .then(
                        if (app.isTv) {
                            Modifier.consumeWindowInsets(WindowInsets.safeDrawing)
                        } else {
                            Modifier.windowInsetsPadding(
                                WindowInsets.safeDrawing.only(WindowInsetsSides.Top),
                            )
                        },
                    ),
            ) {
                TopBar(
                    horizontalPadding = contentHPad,
                    active = nav.tab.value,
                    onTab = { nav.tab.value = it },
                    onAccount = { accountMenuOpen = true },
                    onMenu = { drawerOpen = true },
                    onSearch = { q ->
                        app.searchQuery.value = q
                        nav.tab.value = Tab.Search
                    },
                    user = app.user.value,
                    updatesBadge = app.updatesCount.value,
                    downloadsBadge = app.activeJobs.value,
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        // Page content: insets/overscan live here, not on the
                        // bar, plus a gap so nothing crowds the bar's edge.
                        .then(
                            if (app.isTv) {
                                Modifier.padding(
                                    start = contentHPad, end = contentHPad, bottom = 27.dp,
                                )
                            } else {
                                Modifier
                                    .windowInsetsPadding(
                                        WindowInsets.safeDrawing.only(
                                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                                        ),
                                    )
                                    .padding(horizontal = contentHPad)
                            },
                        )
                        .padding(top = 12.dp),
                ) {
                    when (nav.tab.value) {
                        Tab.Discover -> HomeScreen(app, openCard)
                        Tab.Library -> LibraryScreen(app, openCard)
                        Tab.Updates -> UpdatesScreen(app, openCard)
                        Tab.History -> HistoryScreen(app, openCard)
                        Tab.Downloads -> DownloadsScreen(app, openCard)
                        Tab.Search -> SearchScreen(app, openCard)
                    }
                }
            }
        }
        is Screen.Title -> TitleScreen(
            app = app,
            titleId = screen.id,
            onPlay = { detail, ep -> nav.push(Screen.Player(detail.id, ep, detail.displayTitle)) },
            // Season/franchise hops REPLACE the current title instead of
            // stacking, so one Back leaves the series entirely rather than
            // walking back through every season you looked at.
            onOpenTitle = { nav.replaceTop(Screen.Title(it)) },
            onBack = { nav.back() },
            onSessionLost = onSessionLost,
        )
        is Screen.Player -> PlayerScreen(
            app = app,
            titleId = screen.titleId,
            ep = screen.ep,
            titleName = screen.titleName,
            onExit = { nav.back() },
            onSwitchEpisode = { newEp -> nav.replaceTop(Screen.Player(screen.titleId, newEp, screen.titleName)) },
            onSwitchTitle = { newId, newEp, name -> nav.replaceTop(Screen.Player(newId, newEp, name)) },
        )
        is Screen.Appearance -> AppearanceScreen(app, onClose = { nav.back() })
        is Screen.Users -> UsersScreen(app, onClose = { nav.back() })
        is Screen.ServerSettings -> ServerSettingsScreen(app, onClose = { nav.back() })
        is Screen.Account -> AccountScreen(
            app = app,
            onLogout = doLogout,
            onChangeServer = doChangeServer,
            section = screen.section,
        )
    }
    }

    // Hamburger drawer — overlays whatever screen is up (only openable from
    // the Tabs topbar). Rendered last so it sits above everything.
    if (drawerOpen) {
        NavDrawer(
            active = nav.tab.value,
            username = app.user.value?.username,
            onTab = { nav.tab.value = it },
            onAccount = { nav.push(Screen.Account()) },
            onClose = { drawerOpen = false },
            updatesBadge = app.updatesCount.value,
            downloadsBadge = app.activeJobs.value,
            onSwitchApp = onSwitchApp,
        )
    }

    // Avatar popout (web AccountMenu parity) — above everything, incl. the drawer.
    if (accountMenuOpen) {
        AccountMenu(
            user = app.user.value,
            contentLevel = app.contentLevel.value,
            onOpenSection = { section ->
                when (section) {
                    "appearance" -> nav.push(Screen.Appearance)
                    "users" -> nav.push(Screen.Users)
                    "settings" -> nav.push(Screen.ServerSettings)
                    else -> nav.push(Screen.Account(section))
                }
            },
            onCycleContentLevel = {
                val order = listOf("none", "ecchi", "erotica", "hentai")
                app.setContentLevel(order[(order.indexOf(app.contentLevel.value) + 1) % order.size])
            },
            onLogout = doLogout,
            onChangeServer = doChangeServer,
            onClose = { accountMenuOpen = false },
        )
    }
}
