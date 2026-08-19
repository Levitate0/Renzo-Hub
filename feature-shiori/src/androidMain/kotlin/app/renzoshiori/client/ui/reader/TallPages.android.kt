package app.renzoshiori.client.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap

/**
 * Android tall-page slicing (Mihon's webtoon approach, region-decoded).
 *
 * Coil 3 clamps every decode to its max bitmap size, so a 720×12000 webtoon
 * page comes back scaled to fit 4096px tall — 245px wide — and the strip
 * renders it visibly blurry. [BitmapRegionDecoder] has no such limit: each
 * slice is decoded straight from the encoded bytes as its own ≤2048px-tall
 * bitmap, which uploads as an ordinary texture.
 *
 * [targetWidthPx] picks the sample size: the largest power of two that keeps
 * the decoded width at or above the drawn column, so a 720px source stays
 * pixel-for-pixel while a phone never holds a 12k-px page at full source
 * resolution it can't display anyway.
 */
actual fun decodePageSlices(bytes: ByteArray, sliceHeightPx: Int, targetWidthPx: Int): PageSlices? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    val srcW = bounds.outWidth
    val srcH = bounds.outHeight
    if (srcW <= 0 || srcH <= 0) return null

    var sampleSize = 1
    if (targetWidthPx > 0) {
        while (srcW / (sampleSize * 2) >= targetWidthPx) sampleSize *= 2
    }

    // Throws on formats it can't handle (e.g. animated GIF) — the caller's
    // runCatching turns that into the ordinary failed-page state.
    val decoder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        BitmapRegionDecoder.newInstance(bytes, 0, bytes.size)
    } else {
        @Suppress("DEPRECATION")
        BitmapRegionDecoder.newInstance(bytes, 0, bytes.size, false)
    } ?: return null

    return try {
        // Heap pressure must degrade RESOLUTION, not drop the page: a decode
        // that OOMs retries at the next power-of-two sample size (¼ the
        // memory each step) — a softer page beats a "didn't load" hole in
        // the middle of the chapter being read.
        var attempt = sampleSize
        while (true) {
            try {
                return decodeAt(decoder, srcW, srcH, sliceHeightPx, attempt)
            } catch (e: OutOfMemoryError) {
                if (attempt >= 16) throw e
                attempt *= 2
            }
        }
        @Suppress("UNREACHABLE_CODE")
        null
    } finally {
        decoder.recycle()
    }
}

private fun decodeAt(
    decoder: BitmapRegionDecoder,
    srcW: Int,
    srcH: Int,
    sliceHeightPx: Int,
    sampleSize: Int,
): PageSlices? {
    val opts = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    // Regions are cut in SOURCE px; the decoder scales each by the sample
    // size, so a slice arrives already at drawing resolution.
    val sliceSrcH = sliceHeightPx * sampleSize
    val slices = ArrayList<androidx.compose.ui.graphics.ImageBitmap>()
    var outW = 0
    var outH = 0
    var y = 0
    while (y < srcH) {
        val h = minOf(sliceSrcH, srcH - y)
        val bmp = decoder.decodeRegion(Rect(0, y, srcW, y + h), opts) ?: return null
        outW = maxOf(outW, bmp.width)
        outH += bmp.height
        slices += bmp.asImageBitmap()
        y += h
    }
    return PageSlices(outW, outH, slices)
}
