package top.levitatemedia.renzo.tv.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.sun.jna.Pointer
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat
import java.nio.ByteBuffer
import java.util.Locale

/**
 * One libvlc player rendering into a Skia bitmap that Compose draws.
 *
 * Callback rendering, not a native window (RENZO-DESKTOP-NATIVE-HANDOFF.md §6.3):
 * a heavyweight native video surface would sit ABOVE everything Compose draws, so
 * the controls, subtitles and overlays would all end up hidden behind the video.
 *
 * Frames arrive on libvlc's thread as RV32 (BGRX). They are copied into one reused
 * staging array there; [pumpFrame], on the UI thread, copies the newest frame into
 * one reused Skia bitmap. Nothing is allocated per frame, and the bitmap is only
 * ever touched from the UI thread, so a draw can never see a half-written frame.
 *
 * libvlc event callbacks must not call back into libvlc, so they only update the
 * state flows here; every command comes from the UI side.
 */
internal class VlcVideoPlayer(factory: MediaPlayerFactory) {
    enum class Status { Idle, Opening, Playing, Paused, Ended, Error }

    val status = MutableStateFlow(Status.Idle)
    val buffering = MutableStateFlow(false)
    val positionMs = MutableStateFlow(0L)
    val durationMs = MutableStateFlow(0L)
    /** Bumped on the UI thread each time a new frame lands in [frame]. */
    val frameTick = MutableStateFlow(0L)
    /** Replaced only when the video size changes; its pixels update in place. */
    val frame = MutableStateFlow<ImageBitmap?>(null)
    /**
     * The picture's visible size. [frame] can be taller or wider: libvlc hands
     * over the decoder's padded buffer (720p H.264 arrives as 1280x738), so
     * draw only this top-left region of it.
     */
    val videoWidth = MutableStateFlow(0)
    val videoHeight = MutableStateFlow(0)

    private val player: EmbeddedMediaPlayer = factory.mediaPlayers().newEmbeddedMediaPlayer()

    private val lock = Any()
    private var width = 0
    private var height = 0
    private var staging: ByteArray? = null
    private var fresh = false
    private var bitmap: Bitmap? = null
    @Volatile private var released = false

    init {
        val surface = factory.videoSurfaces().newVideoSurface(
            object : BufferFormatCallback {
                override fun getBufferFormat(sourceWidth: Int, sourceHeight: Int): BufferFormat {
                    synchronized(lock) {
                        width = sourceWidth
                        height = sourceHeight
                        staging = ByteArray(sourceWidth * sourceHeight * 4)
                        fresh = false
                    }
                    // Visible size is resolved on the UI thread once frames flow:
                    // asking libvlc from inside its own vout setup can deadlock.
                    videoWidth.value = 0
                    videoHeight.value = 0
                    return RV32BufferFormat(sourceWidth, sourceHeight)
                }

                override fun allocatedBuffers(buffers: Array<out ByteBuffer>) = Unit
            },
            object : RenderCallback {
                override fun display(mediaPlayer: MediaPlayer, nativeBuffers: Array<out ByteBuffer>, bufferFormat: BufferFormat) {
                    val src = nativeBuffers.firstOrNull() ?: return
                    synchronized(lock) {
                        val dst = staging ?: return
                        src.rewind()
                        src.get(dst, 0, minOf(dst.size, src.remaining()))
                        fresh = true
                    }
                }
            },
            true,
        )
        player.videoSurface().set(surface)

        player.events().addMediaPlayerEventListener(object : MediaPlayerEventAdapter() {
            override fun opening(mediaPlayer: MediaPlayer) { status.value = Status.Opening }
            override fun playing(mediaPlayer: MediaPlayer) { status.value = Status.Playing; buffering.value = false }
            override fun paused(mediaPlayer: MediaPlayer) { status.value = Status.Paused }
            override fun finished(mediaPlayer: MediaPlayer) { status.value = Status.Ended }
            override fun error(mediaPlayer: MediaPlayer) { status.value = Status.Error }
            override fun buffering(mediaPlayer: MediaPlayer, newCache: Float) { buffering.value = newCache < 100f }
            override fun timeChanged(mediaPlayer: MediaPlayer, newTime: Long) { positionMs.value = newTime }
            override fun lengthChanged(mediaPlayer: MediaPlayer, newLength: Long) { durationMs.value = newLength }
        })
    }

    /**
     * Starts playback of [mrl] (a URL or a file path). [startMs] uses libvlc's own
     * start-time option rather than a seek after opening: duration is unknown until
     * the media is parsed, and an early seek can land nowhere.
     */
    fun open(mrl: String, startMs: Long = 0L, options: List<String> = emptyList()) {
        val opts = buildList {
            addAll(options)
            // Debrid links are remote and sometimes slow to start; a larger network
            // cache trades a little startup for far fewer stalls.
            add(":network-caching=2000")
            if (startMs > 0L) add(":start-time=" + String.format(Locale.ROOT, "%.3f", startMs / 1000.0))
        }
        status.value = Status.Opening
        player.media().play(mrl, *opts.toTypedArray())
    }

    fun setPaused(paused: Boolean) = player.controls().setPause(paused)

    fun togglePause() {
        if (status.value == Status.Ended) {
            // Replay after a cancelled auto-next.
            player.controls().setTime(0)
            player.controls().play()
        } else {
            player.controls().pause()
        }
    }

    fun seekTo(ms: Long) {
        val dur = durationMs.value
        player.controls().setTime(if (dur > 0) ms.coerceIn(0L, dur) else ms.coerceAtLeast(0L))
    }

    fun seekBy(deltaMs: Long) = seekTo(player.status().time() + deltaMs)

    /** 0..200 (libvlc's scale; 100 = unity). */
    fun setVolume(volume: Int) { player.audio().setVolume(volume.coerceIn(0, 200)) }
    fun volume(): Int = player.audio().volume()

    fun currentTimeMs(): Long = player.status().time()

    /**
     * UI thread: move the newest decoded frame into [frame]. Returns true when the
     * picture changed. Call once per display frame.
     */
    fun pumpFrame(): Boolean {
        if (released) return false
        synchronized(lock) {
            if (!fresh) return false
            fresh = false
            val src = staging ?: return false
            var bmp = bitmap
            if (bmp == null || bmp.width != width || bmp.height != height) {
                bmp?.close()
                // Not `Bitmap().apply { … }`: inside it `width`/`height` would be the
                // new, still 0x0 bitmap's own, and nothing would be allocated.
                val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
                bmp = Bitmap()
                if (!bmp.allocPixels(info)) { bmp.close(); return false }
                bitmap = bmp
                frame.value = bmp.asComposeImageBitmap()
            }
            val pixmap = bmp.peekPixels() ?: return false
            Pointer(pixmap.addr).write(0, src, 0, minOf(src.size, pixmap.rowBytes * height))
            pixmap.close()
            bmp.notifyPixelsChanged()
            if (videoWidth.value == 0) {
                val d = runCatching { player.video().videoDimension() }.getOrNull()
                val ok = d != null && d.width in 1..width && d.height in 1..height
                videoWidth.value = if (ok) d!!.width else width
                videoHeight.value = if (ok) d!!.height else height
            }
        }
        frameTick.value = frameTick.value + 1
        return true
    }

    fun release() {
        if (released) return
        released = true
        runCatching { player.controls().stop() }
        runCatching { player.release() }
        synchronized(lock) {
            bitmap?.close()
            bitmap = null
            staging = null
        }
        frame.value = null
    }
}
