package app.renzoshiori.client.ui.reader

import androidx.compose.ui.graphics.ImageBitmap
import app.renzoshiori.client.ShioriRuntime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
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
    // BYTE-bounded, not entry-bounded. A fixed entry count was the wrong
    // shape: in a chapter where EVERY page is tall (most webtoons), a 2-entry
    // cache thrashed — each page scrolled back into view refetched and
    // re-decoded from scratch, which reads as the chapter dropping its own
    // pages. Slices vary hugely (a 720-wide page is ~20MB of slices, a
    // 1440-wide one ~100MB), so the budget counts actual pixels.
    private val MAX_CACHE_BYTES: Long =
        if (top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop) 256L * 1024 * 1024
        else 96L * 1024 * 1024

    private val cache = LinkedHashMap<String, PageSlices>(16, 0.75f, true)
    private var cacheBytes = 0L

    private fun sizeOf(p: PageSlices): Long =
        p.slices.sumOf { it.width.toLong() * it.height * 4 }

    // One decode at a time: neighbouring tall pages composing together must
    // not each hold a full page's slices mid-decode — that concurrent spike
    // is what pushed a phone's heap over the edge.
    private val decodeGate = kotlinx.coroutines.sync.Mutex()

    private val client by lazy { okhttp3.OkHttpClient.Builder().build() }

    suspend fun load(cacheKey: String, model: Any?, targetWidthPx: Int): PageSlices? = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[cacheKey] }?.let { return@withContext it }
        // Network can overlap; only the decode is serialized.
        val bytes = when (model) {
            is ByteArray -> model
            is String -> fetch(model)
            else -> null
        } ?: return@withContext null
        decodeGate.withLock {
            // A concurrent load for the same page may have finished while we
            // waited at the gate.
            synchronized(cache) { cache[cacheKey] }?.let { return@withLock it }
            val sliced = runCatching { decodePageSlices(bytes, PAGE_SLICE_HEIGHT_PX, targetWidthPx) }.getOrNull()
                ?: return@withLock null
            synchronized(cache) {
                cache.put(cacheKey, sliced)?.let { cacheBytes -= sizeOf(it) }
                cacheBytes += sizeOf(sliced)
                // Evict eldest-by-access until under budget — but never the
                // entry just added, even if it alone exceeds the budget.
                val it = cache.entries.iterator()
                while (cacheBytes > MAX_CACHE_BYTES && cache.size > 1 && it.hasNext()) {
                    val eldest = it.next()
                    if (eldest.key == cacheKey) continue
                    cacheBytes -= sizeOf(eldest.value)
                    it.remove()
                }
            }
            sliced
        }
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
