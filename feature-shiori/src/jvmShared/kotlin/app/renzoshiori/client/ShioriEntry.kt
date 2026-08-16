package app.renzoshiori.client

import app.renzoshiori.client.ShioriRuntime
import app.renzoshiori.client.data.network.encodeFilename
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import app.renzoshiori.client.ui.tv.LocalIsTv
import app.renzoshiori.client.ui.tv.TvUseAComputerScreen
import app.renzoshiori.client.ui.tv.rememberIsTvDevice
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.navigation.NavType
import androidx.savedstate.read
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import app.renzoshiori.client.ui.auth.AuthStep
import app.renzoshiori.client.ui.auth.AuthViewModel
import app.renzoshiori.client.ui.auth.ConnectScreen
import app.renzoshiori.client.ui.auth.LoginScreen
import app.renzoshiori.client.ui.home.AccountAction
import app.renzoshiori.client.ui.home.AccountDialog
import app.renzoshiori.client.ui.home.AccountDialogHost
import app.renzoshiori.client.ui.home.HomeShell
import app.renzoshiori.client.ui.home.Section
import app.renzoshiori.client.ui.home.ShioriCommandBar
import app.renzoshiori.client.ui.importwizard.ImportWizardScreen
import app.renzoshiori.client.ui.reader.ReaderScreen
import app.renzoshiori.client.ui.settings.AppearanceScreen
import app.renzoshiori.client.ui.settings.DEFAULT_CUSTOM_ACCENT
import app.renzoshiori.client.ui.settings.hslStrToColor
import app.renzoshiori.client.ui.settings.prefString
import app.renzoshiori.client.ui.settings.presetById
import app.renzoshiori.client.ui.theme.RenzoColors
import app.renzoshiori.client.ui.settings.ServerSettingsScreen
import app.renzoshiori.client.ui.settings.TrackersScreen
import app.renzoshiori.client.ui.settings.UsersScreen
import app.renzoshiori.client.ui.series.OfflineSeriesScreen
import app.renzoshiori.client.ui.series.SeriesDetailScreen
import app.renzoshiori.client.ui.settings.AccountScreen
import app.renzoshiori.client.ui.theme.RenzoTheme

/**
 * Native Compose rewrite — replaces the old WebView shell entirely (no more
 * window.__RenzoAndroid JS bridge; this app talks to RenzoBackend's REST API
 * directly). Auth gate (Connect → Login) wraps a NavHost with the signed-in
 * graph: Library → Series/OfflineSeries → Reader, plus Account.
 */
/**
 * The Shiori half's entry point into the Hub.
 *
 * The MainActivity that used to wrap this is gone — the Hub hosts both halves
 * in one activity so switching is a navigation event rather than a task-stack
 * change. Everything below is byte-for-byte the old `setContent` body; only its
 * container changed.
 *
 * @param onSwitchApp invoked by the account menu's "Switch to Renzo". Null on
 *   TV, where this half is deliberately unreachable.
 */
@Composable
fun ShioriRoot(onSwitchApp: (() -> Unit)? = null, onBackToPicker: (() -> Unit)? = null) {
    val crashFile = androidx.compose.runtime.remember {
        top.levitatemedia.renzo.hub.core.hubCrashFile()
    }

    val authViewModel: AuthViewModel = viewModel(factory = AuthViewModel.factory())

    // Provided once, here, so every screen below can branch on device class
    // without re-detecting it. There is deliberately no separate TV screen tree
    // — see ui/tv/TvFocus.kt.
    CompositionLocalProvider(LocalIsTv provides rememberIsTvDevice()) {
    RenzoTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            var crashText by androidx.compose.runtime.remember {
                androidx.compose.runtime.mutableStateOf(
                    if (crashFile.exists()) runCatching { crashFile.readText() }.getOrNull() else null,
                )
            }
            if (crashText != null) {
                CrashReportScreen(
                    trace = crashText!!,
                    onDismiss = { crashFile.delete(); crashText = null },
                )
                return@Surface
            }
            val state by authViewModel.state.collectAsState()
            when (val step = state.step) {
                is AuthStep.Connect -> ConnectScreen(
                    loading = state.loading,
                    error = state.error,
                    onConnect = authViewModel::connect,
                    onBackToPicker = onBackToPicker,
                )
                is AuthStep.Login -> LoginScreen(
                    step = step,
                    loading = state.loading,
                    error = state.error,
                    onLogin = authViewModel::login,
                    onSelectUser = authViewModel::selectUser,
                    onBackToPicker = onBackToPicker,
                    onChangeServer = authViewModel::changeServer,
                )
                is AuthStep.SignedIn -> SignedInNavHost(
                    user = step.user,
                    onLogout = authViewModel::logout,
                    onSwitchServer = authViewModel::switchServer,
                    onSwitchApp = onSwitchApp,
                )
            }
        }
    }
    }
}

/**
 * Shown once after a crash: the saved stack trace, copyable, so crashes can
 * be reported from the device itself. Dismiss deletes the record and boots
 * the app normally.
 */
@Composable
private fun CrashReportScreen(trace: String, onDismiss: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("The app crashed last time", style = MaterialTheme.typography.titleMedium)
        Text(
            trace,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { clipboard.setText(AnnotatedString(trace)) }) {
                Text("Copy")
            }
            Button(onClick = onDismiss) {
                Text("Continue")
            }
        }
    }
}

@Composable
private fun SignedInNavHost(
    user: app.renzoshiori.client.data.model.UserDto,
    onLogout: () -> Unit,
    onSwitchServer: () -> Unit,
    onSwitchApp: (() -> Unit)?,
) {
    val nav = rememberNavController()

    // Paint the app in the signed-in user's saved theme (preset + accent from
    // the shared preferences blob) before anything renders.
    androidx.compose.runtime.LaunchedEffect(user.preferences) {
        val preset = presetById(prefString(user.preferences, "preset"))
        val custom = prefString(user.preferences, "accent") == "custom"
        val accentHsl = if (custom) {
            prefString(user.preferences, "accentCustom") ?: DEFAULT_CUSTOM_ACCENT
        } else {
            preset.accent
        }
        RenzoColors.applyTheme(
            background = hslStrToColor(preset.bg),
            card = hslStrToColor(preset.card),
            primary = hslStrToColor(accentHsl),
        )
    }

    var dialog by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableStateOf<AccountDialog?>(null)
    }
    var tourVisible by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }

    // Hoisted section + search state: the persistent command bar (below) and
    // HomeShell must agree on them across route changes.
    var section by androidx.compose.runtime.saveable.rememberSaveable {
        androidx.compose.runtime.mutableStateOf(Section.Library.name)
    }
    val libraryVm: app.renzoshiori.client.ui.library.LibraryViewModel =
        androidx.lifecycle.viewmodel.compose.viewModel(
            factory = app.renzoshiori.client.ui.library.LibraryViewModel.factory(),
        )

    // One handler for the account menu wherever it's opened from (the home
    // shell's narrow panel or the persistent bar's dropdown on any route).
    val handleAccountAction: (AccountAction) -> Unit = { action ->
        when (action) {
            AccountAction.Account -> nav.navigate("account")
            AccountAction.Appearance -> nav.navigate("appearance")
            AccountAction.Users -> nav.navigate("users")
            AccountAction.ServerSettings -> nav.navigate("server-settings")
            AccountAction.Trackers -> nav.navigate("trackers")
            AccountAction.Tour -> tourVisible = true
            is AccountAction.ImportSeries -> nav.navigate("import-wizard/${action.titleOnly}")
            AccountAction.EditProfile -> dialog = AccountDialog.EditProfile
            AccountAction.ChangePassword -> dialog = AccountDialog.ChangePassword
            AccountAction.ImportBackup -> dialog = AccountDialog.ImportBackup
            AccountAction.SignOut -> onLogout()
            AccountAction.SwitchServer -> onSwitchServer()
            AccountAction.SwitchApp -> onSwitchApp?.invoke()
        }
    }

    val isTv = LocalIsTv.current
    val wide = !isTv && app.renzoshiori.client.ui.util.screenWidthDp() >= 1024.dp
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    // The web keeps its CommandBar on every route EXCEPT the reader, which is
    // deliberately chromeless full-screen.
    val chromeless = route == null ||
        route.startsWith("reader/") ||
        route.startsWith("preview/")

    androidx.compose.foundation.layout.Column(
        modifier = androidx.compose.ui.Modifier.fillMaxSize(),
    ) {
        if (wide && !chromeless) {
            ShioriCommandBar(
                user = user,
                // Web pill highlighting: home shows its section; a series page
                // keeps Library lit (its pathname starts with /library);
                // settings-type routes light nothing.
                activeSection = when {
                    route == "home" -> Section.valueOf(section)
                    route.startsWith("series/") || route.startsWith("offline-series/") -> Section.Library
                    else -> null
                },
                onSelectSection = { s ->
                    section = s.name
                    if (route != "home") nav.popBackStack("home", inclusive = false)
                },
                libraryVm = libraryVm,
                onAccountAction = handleAccountAction,
                showSearch = route == "home",
            )
        }
        androidx.compose.foundation.layout.Box(
            modifier = androidx.compose.ui.Modifier.weight(1f),
        ) {
    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeShell(
                user = user,
                onOpenSeries = { id -> nav.navigate("series/$id") },
                onOpenOfflineSeries = { id -> nav.navigate("offline-series/$id") },
                section = section,
                onSectionChange = { section = it },
                libraryVm = libraryVm,
                // Browse "Read": preview live from the source, nothing stored.
                // The args ride the route; mihonIds and titles can contain
                // anything (slashes, %, spaces), and nav does its own URI
                // decoding — base64url (the encodeFilename trick) is the one
                // encoding that survives both untouched.
                onPreviewRead = { mihonId, title ->
                    nav.navigate(
                        "preview/${encodeFilename(mihonId)}/${encodeFilename(title.ifBlank { "Preview" })}",
                    )
                },
                onAccountAction = handleAccountAction,
                showTour = tourVisible,
                onTourFinish = { tourVisible = false },
            )
        }
        composable("appearance") {
            ConfigRoute("Appearance", "/appearance") { AppearanceScreen(onBack = { nav.popBackStack() }) }
        }
        composable("users") {
            ConfigRoute("Users", "/settings") { UsersScreen(onBack = { nav.popBackStack() }) }
        }
        composable("server-settings") {
            ConfigRoute("Settings & sources", "/settings") {
                ServerSettingsScreen(onBack = { nav.popBackStack() })
            }
        }
        composable("trackers") {
            ConfigRoute("Trackers", "/settings") { TrackersScreen(onBack = { nav.popBackStack() }) }
        }
        composable(
            "import-wizard/{titleOnly}",
            arguments = listOf(navArgument("titleOnly") { type = NavType.BoolType }),
        ) { entry ->
            ConfigRoute("Import series", "/library") {
                ImportWizardScreen(
                    titleOnly = entry.arguments!!.read { getBoolean("titleOnly") },
                    onClose = { nav.popBackStack() },
                )
            }
        }
        composable(
            "series/{seriesId}",
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
        ) { entry ->
            val seriesId = entry.arguments!!.read { getString("seriesId") }
            SeriesDetailScreen(
                seriesId = seriesId,
                onBack = { nav.popBackStack() },
                onReadChapter = { sid, ch -> nav.navigate("reader/$sid/$ch") },
            )
        }
        composable(
            "offline-series/{seriesId}",
            arguments = listOf(navArgument("seriesId") { type = NavType.StringType }),
        ) { entry ->
            val seriesId = entry.arguments!!.read { getString("seriesId") }
            OfflineSeriesScreen(
                seriesId = seriesId,
                onBack = { nav.popBackStack() },
                onReadChapter = { sid, ch -> nav.navigate("reader/$sid/$ch") },
            )
        }
        composable(
            "preview/{mihonId}/{title}",
            arguments = listOf(
                navArgument("mihonId") { type = NavType.StringType },
                navArgument("title") { type = NavType.StringType },
            ),
        ) { entry ->
            val decode = { s: String ->
                String(java.util.Base64.getUrlDecoder().decode(s), Charsets.UTF_8)
            }
            val mihonId = decode(entry.arguments!!.read { getString("mihonId") })
            val title = decode(entry.arguments!!.read { getString("title") })
            ReaderScreen(
                seriesId = "",
                // -1 = "the first chapter" — the browse dialog can't know the
                // source's chapter list; the reader resolves it after fetching.
                chapterNumber = -1.0,
                previewMihonId = mihonId,
                previewTitle = title,
                onExit = { nav.popBackStack() },
            )
        }
        composable(
            "reader/{seriesId}/{chapter}",
            arguments = listOf(
                navArgument("seriesId") { type = NavType.StringType },
                navArgument("chapter") { type = NavType.FloatType },
            ),
        ) { entry ->
            val seriesId = entry.arguments!!.read { getString("seriesId") }
            val chapter = entry.arguments!!.read { getFloat("chapter") }.toDouble()
            ReaderScreen(
                seriesId = seriesId,
                chapterNumber = chapter,
                onExit = { nav.popBackStack() },
            )
        }
        composable("account") {
            AccountScreen(
                username = user.username,
                onBack = { nav.popBackStack() },
                onLogout = onLogout,
            )
        }
    }
        // Activity Dock (web activity-dock.tsx): floats bottom-right over
        // every route, suppressed on the Queue page itself — the page is the
        // source of truth there.
        app.renzoshiori.client.ui.components.ActivityDock(
            suppressed = route == "home" && section == Section.Queue.name,
            onOpenQueue = {
                section = Section.Queue.name
                if (route != "home") nav.popBackStack("home", inclusive = false)
            },
            modifier = androidx.compose.ui.Modifier.align(androidx.compose.ui.Alignment.BottomEnd),
        )
        }
    }

    // Account dialogs are window-level overlays: the persistent bar can open
    // them from ANY route, so they host outside the NavHost.
    AccountDialogHost(dialog = dialog, onDismiss = { dialog = null })
}

/**
 * Configuration screens are phone/desktop work: long typing, extension-repo
 * URLs, password fields. On a TV they are replaced by the instance's own web
 * address rather than left reachable and unusable — any household computer or
 * tablet does the job, which unlike "do it on your phone" doesn't assume the
 * user owns one.
 *
 * The shell hides the matching nav and account entries too; this is the
 * backstop for anything that still navigates here.
 */
@Composable
private fun ConfigRoute(title: String, path: String, content: @Composable () -> Unit) {
    if (!LocalIsTv.current) {
        content()
        return
    }
    val store = ShioriRuntime.app.tokenStore
    TvUseAComputerScreen(title = title, serverUrl = store.serverUrl, path = path)
}
