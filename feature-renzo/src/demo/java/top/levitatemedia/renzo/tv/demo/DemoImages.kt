package top.levitatemedia.renzo.tv.demo

import android.content.Context
import android.net.Uri
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.intercept.Interceptor
import coil3.request.ImageRequest
import coil3.request.ImageResult
import coil3.request.crossfade
import coil3.size.Dimension

/**
 * Demo build only: every image fetched over the network is swapped for the
 * bundled Renzo app-icon plate before Coil loads it.
 *
 * The app still talks to the real server and shows the real library — only the
 * artwork is replaced, so a Play listing screenshot never carries a publisher's
 * cover, season art, banner, episode still or scrub frame.
 *
 * Installed as Coil's singleton loader, so it covers every AsyncImage in the
 * app with no call-site changes.
 */
internal object DemoImages {
    fun install(ctx: Context) {
        // Coil 3 replaced Coil.setImageLoader with a singleton *factory*.
        // setSafe is a no-op once the loader has been created, so this has to
        // run before the first AsyncImage composes — MainActivity.onCreate does.
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(PlaceholderInterceptor) }
                // Crossfade would catch a half-faded frame in a screencap.
                .crossfade(false)
                .build()
        }
    }
}

private object PlaceholderInterceptor : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val url = when (val data = chain.request.data) {
            is String -> data
            is Uri -> data.toString()
            else -> null
        }
        val swap = url?.let { placeholderFor(it, chain.px(true), chain.px(false)) }
            ?: return chain.proceed()
        return chain.withRequest(
            ImageRequest.Builder(chain.request).data(swap).build(),
        ).proceed()
    }

    /** Resolved target size in pixels, or 0 when Coil hasn't measured it. */
    private fun Interceptor.Chain.px(width: Boolean): Int =
        ((if (width) size.width else size.height) as? Dimension.Pixels)?.px ?: 0
}

private const val ASSETS = "file:///android_asset/placeholder"

/**
 * Which plate replaces this URL.
 *
 * Shape comes from the TARGET BOX rather than the URL, because the same cover
 * URL is drawn as a 2:3 poster in one place and cropped into the 16:9 hero in
 * another (TitleScreen falls back to `poster` when a title has no banner).
 * The URL is only consulted when Coil hasn't resolved a size yet.
 */
private fun placeholderFor(url: String, w: Int, h: Int): String? {
    // Local art (the wordmark, launcher icons, an already-swapped asset) stays.
    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return null

    // Scrub-bar frames come straight out of the user's own video file.
    if (url.contains("/preview/", true)) return "$ASSETS/preview.jpg"

    val landscape = if (w > 0 && h > 0) w.toFloat() / h > 1.2f else url.contains("/banner", true)
    return when {
        // A wide, LARGE box is the detail-page hero; a wide small one is an
        // episode still or a scrub thumbnail.
        landscape && w >= 700 -> "$ASSETS/banner.jpg"
        landscape -> "$ASSETS/thumb.jpg"
        else -> "$ASSETS/poster.png"
    }
}
