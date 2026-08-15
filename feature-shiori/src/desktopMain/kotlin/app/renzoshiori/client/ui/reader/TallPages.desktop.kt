package app.renzoshiori.client.ui.reader

import androidx.compose.ui.graphics.asComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode

actual fun decodePageSlices(bytes: ByteArray, sliceHeightPx: Int): PageSlices? {
    val full = runCatching { Image.makeFromEncoded(bytes) }.getOrNull() ?: return null
    return try {
        val w = full.width
        val h = full.height
        if (w <= 0 || h <= 0) return null
        val slices = ArrayList<androidx.compose.ui.graphics.ImageBitmap>()
        var y = 0
        while (y < h) {
            val sliceH = minOf(sliceHeightPx, h - y)
            val bitmap = Bitmap()
            if (!bitmap.allocN32Pixels(w, sliceH)) return null
            Canvas(bitmap).drawImageRect(
                full,
                Rect.makeXYWH(0f, y.toFloat(), w.toFloat(), sliceH.toFloat()),
                Rect.makeWH(w.toFloat(), sliceH.toFloat()),
                SamplingMode.DEFAULT,
                null,
                true,
            )
            bitmap.setImmutable()
            slices += bitmap.asComposeImageBitmap()
            y += sliceH
        }
        PageSlices(w, h, slices)
    } finally {
        full.close()
    }
}
