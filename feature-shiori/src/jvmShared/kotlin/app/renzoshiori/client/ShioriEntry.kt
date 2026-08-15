package app.renzoshiori.client

import app.renzoshiori.client.ShioriRuntime
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

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeShell(
                user = user,
                onOpenSeries = { id -> nav.navigate("series/$id") },
                onOpenOfflineSeries = { id -> nav.navigate("offline-series/$id") },
                onAccountAction = { action ->
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
                        AccountAction.SwitchApp -> onSwitchApp?.invoke()
                    }
                },
                showTour = tourVisible,
                onTourFinish = { tourVisible = false },
            )
            AccountDialogHost(dialog = dialog, onDismiss = { dialog = null })
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
