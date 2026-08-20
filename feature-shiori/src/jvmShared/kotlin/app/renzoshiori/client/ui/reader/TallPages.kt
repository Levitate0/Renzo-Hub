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
    private val isDesktop = top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop

    // Desktop keeps its original shape — a 6-entry LRU, concurrent decodes —
    // which never had a problem (deep heap, fast local network). Everything
    // below the desktop checks exists for MOBILE, where the real failure was:
    // decoded slices are huge (a 720-wide page is ~20MB, a 1440-wide one
    // ~70MB), the composables are disposed the moment a page scrolls
    // off-screen, and a cache miss went all the way back to the NETWORK —
    // pages visibly unloaded and took seconds to come back.
    private const val DESKTOP_CACHE_ENTRIES = 6

    // Mobile decoded-slice budget: a third of the real heap (largeHeap is on
    // for exactly this reason), floored so at least a few pages always fit.
    private val MAX_SLICE_BYTES: Long =
        (Runtime.getRuntime().maxMemory() / 3).coerceIn(96L * 1024 * 1024, 256L * 1024 * 1024)

    private val cache = LinkedHashMap<String, PageSlices>(16, 0.75f, true)
    private var cacheBytes = 0L

    private fun sizeOf(p: PageSlices): Long =
        p.slices.sumOf { it.width.toLong() * it.height * 4 }

    // Mobile: ENCODED bytes, keyed by source URL. The decoded cache can't
    // hold a whole chapter, but the compressed originals can (~1-3MB each) —
    // so an evicted page re-decodes from RAM instead of refetching over the
    // network, which was the "takes forever to grab them again" half.
    private const val MAX_ENCODED_BYTES = 48L * 1024 * 1024
    private val byteCache = LinkedHashMap<String, ByteArray>(32, 0.75f, true)
    private var byteCacheBytes = 0L

    // Mobile: one decode at a time — neighbouring tall pages decoding
    // concurrently is a heap spike a phone can't afford. Desktop decodes
    // concurrently, as it always did.
    private val decodeGate = kotlinx.coroutines.sync.Mutex()

    private val client by lazy { okhttp3.OkHttpClient.Builder().build() }

    suspend fun load(cacheKey: String, model: Any?, targetWidthPx: Int): PageSlices? = withContext(Dispatchers.IO) {
        synchronized(cache) { cache[cacheKey] }?.let { return@withContext it }
        val bytes = when (model) {
            is ByteArray -> model
            is String -> fetchCached(model)
            else -> null
        } ?: return@withContext null
        if (isDesktop) {
            decodeAndStore(cacheKey, bytes, targetWidthPx)
        } else {
            decodeGate.withLock {
                // A concurrent load for the same page may have finished while
                // we waited at the gate.
                synchronized(cache) { cache[cacheKey] }?.let { return@withLock it }
                decodeAndStore(cacheKey, bytes, targetWidthPx)
            }
        }
    }

    private fun decodeAndStore(cacheKey: String, bytes: ByteArray, targetWidthPx: Int): PageSlices? {
        val sliced = runCatching { decodePageSlices(bytes, PAGE_SLICE_HEIGHT_PX, targetWidthPx) }.getOrNull()
            ?: return null
        synchronized(cache) {
            cache.put(cacheKey, sliced)?.let { cacheBytes -= sizeOf(it) }
            cacheBytes += sizeOf(sliced)
            if (isDesktop) {
                while (cache.size > DESKTOP_CACHE_ENTRIES) {
                    val eldest = cache.entries.first()
                    cacheBytes -= sizeOf(eldest.value)
                    cache.remove(eldest.key)
                }
            } else {
                // Evict eldest-by-access until under budget — but never the
                // entry just added, even if it alone exceeds the budget.
                val it = cache.entries.iterator()
                while (cacheBytes > MAX_SLICE_BYTES && cache.size > 1 && it.hasNext()) {
                    val eldest = it.next()
                    if (eldest.key == cacheKey) continue
                    cacheBytes -= sizeOf(eldest.value)
                    it.remove()
                }
            }
        }
        return sliced
    }

    /** Mobile keeps the compressed originals; desktop fetches as before. */
    private fun fetchCached(url: String): ByteArray? {
        if (isDesktop) return fetch(url)
        synchronized(byteCache) { byteCache[url] }?.let { return it }
        val bytes = fetch(url) ?: return null
        synchronized(byteCache) {
            byteCache.put(url, bytes)?.let { byteCacheBytes -= it.size }
            byteCacheBytes += bytes.size
            val it = byteCache.entries.iterator()
            while (byteCacheBytes > MAX_ENCODED_BYTES && byteCache.size > 1 && it.hasNext()) {
                val eldest = it.next()
                if (eldest.key == url) continue
                byteCacheBytes -= eldest.value.size
                it.remove()
            }
        }
        return bytes
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
