package top.levitatemedia.renzo.tv

import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import top.levitatemedia.renzo.tv.ui.theme.LocalUiScale
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors
import top.levitatemedia.renzo.tv.ui.theme.RenzoTvTheme

/**
 * The Renzo half's entry point into the Hub.
 *
 * This replaces what MainActivity used to do. MainActivity itself is left in
 * place but is no longer in any manifest — the Hub hosts both halves in ONE
 * activity so that switching apps is a navigation event rather than a task-stack
 * change, and so the TV leanback entry can bypass the picker entirely.
 *
 * Built lazily: a user who only ever opens the manga half never constructs
 * AppServices, never opens Renzo's prefs and never registers its signer.
 */
class RenzoHost(activity: ComponentActivity) {
    val services: AppServices

    init {
        // Demo build only (no-ops in debug/release): the capture extras must be
        // read BEFORE AppServices, which latches isTv in its constructor.
        top.levitatemedia.renzo.tv.demo.DemoMode.install(activity.applicationContext)
        top.levitatemedia.renzo.tv.demo.DemoMode.readIntent(activity.intent)
        top.levitatemedia.renzo.tv.demo.DemoMode.applyWindow(activity)
        services = AppServices(activity.applicationContext)
        top.levitatemedia.renzo.tv.demo.DemoMode.applyNav(services)
    }

    /** singleTask: a second `am start` reuses the activity — re-apply demo extras. */
    fun onNewIntent(activity: ComponentActivity, intent: Intent) {
        if (!top.levitatemedia.renzo.tv.demo.DemoMode.ENABLED) return
        top.levitatemedia.renzo.tv.demo.DemoMode.readIntent(intent)
        top.levitatemedia.renzo.tv.demo.DemoMode.applyWindow(activity)
        top.levitatemedia.renzo.tv.demo.DemoMode.applyNav(services)
    }
}

/**
 * @param onSwitchApp invoked by the hamburger menu's "Switch to Renzo Shiori".
 *   Null on TV, where the manga half is deliberately unreachable.
 */
@Composable
fun RenzoRoot(host: RenzoHost, onSwitchApp: (() -> Unit)? = null, onBackToPicker: (() -> Unit)? = null) {
    val app = host.services
    // TV: scale the whole UI down so a full row of tiles plus the page chrome
    // fits the panel's short layout height. One density change scales text,
    // spacing and tiles together.
    val cfg = LocalConfiguration.current
    val base = LocalDensity.current
    val scale = if (app.isTv) (cfg.screenHeightDp / 660f).coerceIn(0.75f, 1f) else 1f
    CompositionLocalProvider(
        LocalUiScale provides scale,
        LocalDensity provides Density(density = base.density * scale, fontScale = base.fontScale),
    ) {
        RenzoTvTheme {
            Box(Modifier.fillMaxSize().background(RenzoColors.Background)) {
                RenzoBoot(app, onSwitchApp, onBackToPicker)
            }
        }
    }
}
