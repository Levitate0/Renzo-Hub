package app.renzoshiori.client.ui.reader

/**
 * Android never takes the slicing path (the reader gates it on
 * HubPlatform.isDesktop): Coil decodes to layout bounds there, so pages never
 * reach the GPU as over-limit textures the way the desktop's full-resolution
 * decodes do.
 */
actual fun decodePageSlices(bytes: ByteArray, sliceHeightPx: Int): PageSlices? = null
