package top.levitatemedia.renzo.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import top.levitatemedia.renzo.hub.core.offline.DesktopOfflineFiles
import top.levitatemedia.renzo.tv.offline.RenzoOfflineWiring
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors
import top.levitatemedia.renzo.tv.ui.theme.RenzoTvTheme

/**
 * The Renzo half's desktop composition root — RenzoHost's twin without an
 * Activity. install() is called from hub-desktop's main() (like Android's
 * Application) so the download signer exists even if the anime half is never
 * opened this run; AppServices itself stays lazy, matching RenzoHost's
 * "a manga-only user never constructs it" behaviour.
 */
object RenzoDesktop {
    fun install() {
        if (installed) return
        installed = true
        RenzoOfflineWiring.install()
    }

    private var installed = false

    val services: AppServices by lazy {
        install()
        AppServices(isTv = false, offlineStore = DesktopOfflineFiles())
    }
}

/** RenzoRoot minus the TV density scale — the desktop is never a TV. */
@Composable
fun RenzoDesktopRoot(
    onSwitchApp: (() -> Unit)? = null,
    onBackToPicker: (() -> Unit)? = null,
) {
    val app = RenzoDesktop.services
    RenzoTvTheme {
        Box(Modifier.fillMaxSize().background(RenzoColors.Background)) {
            RenzoBoot(app, onSwitchApp, onBackToPicker)
        }
    }
}
