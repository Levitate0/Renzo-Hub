package app.renzoshiori.client.ui.reader

import androidx.compose.ui.graphics.ImageBitmap
import app.renzoshiori.client.ShioriRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Tall-page slicing (reader-image-quality handoff, second correction).
 *
 * Webtoon sources serve single pages 10-12k px tall — over the GPU's maximum
 * texture dimension (8192, sometimes 4096). A texture that can't be uploaded
 * whole gets power-of-two downsampled by the renderer until it fits, which
 * HALVES the horizontal detail too (720 → 360) — the pixelation no decode
 * size or filter setting could touch, because it happens at texture upload.
 *
 * The fix is what Mihon does and what browsers effectively do: decode the
 * page ONCE on the CPU (no texture limit there), cut it into slices short
 * enough to upload as ordinary textures, and stack them in the strip.
 */

/** Stay far below every common limit; slices also relayout cheaply. */
const val TEXTURE_SAFE_HEIGHT_PX = 4096

/** Target slice height — small textures, few seams. */
const val PAGE_SLICE_HEIGHT_PX = 2048

class PageSlices(
    /** TRUE natural size, from the CPU-side decode — immune to the halving. */
    val width: Int,
    val height: Int,
    val slices: List<ImageBitmap>,
)

/**
 * CPU-decode [bytes] and cut into slices of at most [sliceHeightPx].
 *
 * [targetWidthPx] is the drawn column width in physical px: the desktop
 * ignores it (full-resolution decode, plenty of heap), Android uses it to
 * pick a power-of-two sample size — a phone must not hold a 12k-px page at
 * full source resolution just to draw a 1080px-wide column.
 */
expect fun decodePageSlices(bytes: ByteArray, sliceHeightPx: Int, targetWidthPx: Int): PageSlices?

/**
 * Fetch + decode + slice, with a small most-recently-used cache so scrolling
 * back through the strip doesn't re-decode 12k-px images. Fetches sign like
 * the shared Coil loader (Bearer token from the token store).
 */
object TallPageLoader {
    // Sliced pages are big (a 12k-px webtoon page is tens of MB of slices);
    // the desktop JVM can afford a deeper back-scroll cache than a phone.
    private val CACHE_ENTRIES = if (top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop) 6 else 2

    private val cache = object : LinkedHashMap<String, PageSlices>(CACHE_ENTRIES, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, PageSlices>): Boolean =
            size > CACHE_ENTRIES
    }

    private val client by lazy { okhttp3.OkHttpClient.Builder().build() }

    suspend fun load(cacheKey: String, model: Any?, targetWidthPx: Int): PageSlices? = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[cacheKey] }?.let { return@withContext it }
        val bytes = when (model) {
            is ByteArray -> model
            is String -> fetch(model)
            else -> null
        } ?: return@withContext null
        val sliced = runCatching { decodePageSlices(bytes, PAGE_SLICE_HEIGHT_PX, targetWidthPx) }.getOrNull()
            ?: return@withContext null
        synchronized(cache) { cache[cacheKey] = sliced }
        sliced
    }

    private fun fetch(url: String): ByteArray? = runCatching {
        val token = ShioriRuntime.app.tokenStore.accessToken
        val request = okhttp3.Request.Builder().url(url).apply {
            if (token != null) header("Authorization", "Bearer $token")
        }.build()
        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.bytes() else null
        }
    }.getOrNull()
}
