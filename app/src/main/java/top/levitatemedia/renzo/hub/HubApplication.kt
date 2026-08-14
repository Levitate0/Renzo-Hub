package top.levitatemedia.renzo.hub

import app.renzoshiori.client.RenzoApp
import coil3.ImageLoader
import coil3.PlatformContext
import top.levitatemedia.renzo.hub.core.HubImageLoader
import top.levitatemedia.renzo.tv.offline.RenzoOfflineWiring

/**
 * The Hub's Application.
 *
 * It extends the Shiori half's RenzoApp rather than replacing it, because 32
 * call sites across that feature do `applicationContext as RenzoApp` to reach
 * the shared TokenStore / NetworkModule / OfflineRepository. Subclassing
 * satisfies every one of them; the alternative was editing 32 files or dragging
 * Shiori's whole data layer up into :core. Neither is worth it.
 *
 * The one thing that must NOT be inherited is the image loader: RenzoApp
 * installs a Bearer-authenticated one, which would sign the Renzo half's
 * requests with the wrong scheme. See HubImageLoader.
 */
class HubApplication : RenzoApp() {

    override fun onCreate() {
        // RenzoApp.onCreate registers the manga half's signer and download
        // source; this adds the anime half's. Both must exist before the
        // downloader runs, and it can run with no UI at all.
        super.onCreate()
        RenzoOfflineWiring.install(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        HubImageLoader.build(context)
}
