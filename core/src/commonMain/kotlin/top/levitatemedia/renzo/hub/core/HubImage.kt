package top.levitatemedia.renzo.hub.core

import androidx.compose.ui.graphics.ImageBitmap

/** Decode an encoded image (PNG/JPEG/WebP bytes) — BitmapFactory vs Skia. */
expect fun decodeImageBytes(bytes: ByteArray): ImageBitmap?
