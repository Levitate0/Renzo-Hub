package top.levitatemedia.renzo.tv.player

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import com.sun.jna.Memory
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.flow.MutableStateFlow
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import java.util.Locale

/**
 * One libVLC player rendering into a Skia bitmap that Compose draws.
 *
 * Callback rendering, not a native window (RENZO-DESKTOP-NATIVE-HANDOFF.md §6.3):
 * a heavyweight native video surface would sit ABOVE everything Compose draws, so
 * the controls, subtitles and overlays would all end up hidden behind the video.
 *
 * Frames arrive on libVLC's thread as RV32 (BGRX) in one native buffer. At display
 * time they are copied into one reused staging array; [pumpFrame], on the UI
 * thread, copies the newest frame into one reused Skia bitmap. Nothing is
 * allocated per frame, and the bitmap is only ever touched from the UI thread, so
 * a draw can never see a half-written frame.
 *
 * libVLC event callbacks must not call back into libVLC, so they only update the
 * state flows here; every command comes from the UI side.
 */
internal class VlcVideoPlayer(private val vlc: VlcRuntime.Handle) {
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
     * The picture's visible size. [frame] can be taller or wider: libVLC hands
     * over the decoder's padded buffer (720p H.264 arrives as 1280x738), so
     * draw only this top-left region of it.
     */
    val videoWidth = MutableStateFlow(0)
    val videoHeight = MutableStateFlow(0)
    /** Bumped when the stream's tracks change, so menus re-read [audioTracks]. */
    val tracksChanged = MutableStateFlow(0)

    private val lib = vlc.lib
    private val mp: Pointer = lib.libvlc_media_player_new(vlc.instance)
        ?: error(lib.libvlc_errmsg() ?: "libvlc_media_player_new failed")

    private val lock = Any()
    private var width = 0
    private var height = 0
    /** libVLC decodes into this; [bufferBase] owns the allocation behind the aligned view. */
    private var bufferBase: Memory? = null
    private var buffer: Pointer? = null
    private var staging: ByteArray? = null
    private var fresh = false
    private var bitmap: Bitmap? = null
    @Volatile private var released = false

    private var currentMrl: String? = null
    private var currentOptions: List<String> = emptyList()

    // Strong references: JNA frees a callback's native stub once its object is
    // collected, and libVLC would then call into freed memory.
    private val formatCb = LibVlc.FormatCallback { _, chroma, w, h, pitches, lines ->
        val fw = w.getInt(0)
        val fh = h.getInt(0)
        if (fw <= 0 || fh <= 0) return@FormatCallback 0
        chroma.write(0, "RV32".toByteArray(Charsets.US_ASCII), 0, 4)
        pitches.setInt(0, fw * 4)
        lines.setInt(0, fh)
        synchronized(lock) {
            width = fw
            height = fh
            // libVLC wants its planes aligned; over-allocate and align a view.
            val base = Memory(fw.toLong() * 4 * fh + 64)
            bufferBase = base
            buffer = base.align(32)
            staging = ByteArray(fw * 4 * fh)
            fresh = false
        }
        // Visible size is resolved on the UI thread once frames flow: asking
        // libVLC from inside its own vout setup can deadlock.
        videoWidth.value = 0
        videoHeight.value = 0
        1
    }
    private val cleanupCb = LibVlc.CleanupCallback {
        synchronized(lock) { buffer = null; bufferBase = null }
    }
    private val lockCb = LibVlc.LockCallback { _, planes ->
        planes.setPointer(0, buffer)
        null
    }
    private val displayCb = LibVlc.DisplayCallback { _, _ ->
        synchronized(lock) {
            val src = buffer ?: return@DisplayCallback
            val dst = staging ?: return@DisplayCallback
            src.read(0, dst, 0, dst.size)
            fresh = true
        }
    }
    private val eventCb = LibVlc.EventCallback { event, _ ->
        val u = LibVlc.EVENT_UNION_OFFSET
        when (event.getInt(0)) {
            LibVlc.MediaPlayerOpening -> status.value = Status.Opening
            LibVlc.MediaPlayerBuffering -> buffering.value = event.getFloat(u) < 100f
            LibVlc.MediaPlayerPlaying -> { status.value = Status.Playing; buffering.value = false }
            LibVlc.MediaPlayerPaused -> status.value = Status.Paused
            LibVlc.MediaPlayerStopped -> if (status.value != Status.Ended && status.value != Status.Error) status.value = Status.Idle
            LibVlc.MediaPlayerEndReached -> status.value = Status.Ended
            LibVlc.MediaPlayerEncounteredError -> status.value = Status.Error
            LibVlc.MediaPlayerTimeChanged -> positionMs.value = event.getLong(u)
            LibVlc.MediaPlayerLengthChanged -> durationMs.value = event.getLong(u)
            LibVlc.MediaPlayerESAdded -> tracksChanged.value = tracksChanged.value + 1
        }
    }

    init {
        lib.libvlc_video_set_format_callbacks(mp, formatCb, cleanupCb)
        lib.libvlc_video_set_callbacks(mp, lockCb, null, displayCb, null)
        val em = lib.libvlc_media_player_event_manager(mp)
        for (type in intArrayOf(
            LibVlc.MediaPlayerOpening, LibVlc.MediaPlayerBuffering, LibVlc.MediaPlayerPlaying,
            LibVlc.MediaPlayerPaused, LibVlc.MediaPlayerStopped, LibVlc.MediaPlayerEndReached,
            LibVlc.MediaPlayerEncounteredError, LibVlc.MediaPlayerTimeChanged,
            LibVlc.MediaPlayerLengthChanged, LibVlc.MediaPlayerESAdded,
        )) {
            lib.libvlc_event_attach(em, type, eventCb, null)
        }
    }

    /**
     * Starts playback of [mrl] (a URL or a file path). [startMs] uses libVLC's own
     * start-time option rather than a seek after opening: duration is unknown until
     * the media is parsed, and an early seek can land nowhere.
     */
    fun open(mrl: String, startMs: Long = 0L, options: List<String> = emptyList()) {
        currentMrl = mrl
        currentOptions = options
        play(mrl, buildList {
            addAll(options)
            if (startMs > 0L) add(":start-time=" + String.format(Locale.ROOT, "%.3f", startMs / 1000.0))
        })
    }

    private fun play(mrl: String, options: List<String>) {
        if (released) return
        // A URL goes in as a location; anything else is a local path (which also
        // covers Windows' "C:\…", whose "C:" is not a scheme).
        val isUrl = Regex("^[A-Za-z][A-Za-z0-9+.-]+://").containsMatchIn(mrl)
        val media = (if (isUrl) lib.libvlc_media_new_location(vlc.instance, mrl) else lib.libvlc_media_new_path(vlc.instance, mrl))
        if (media == null) { status.value = Status.Error; return }
        // Debrid links are remote and sometimes slow to start; a larger network
        // cache trades a little startup for far fewer stalls.
        lib.libvlc_media_add_option(media, ":network-caching=2000")
        options.forEach { lib.libvlc_media_add_option(media, it) }
        lib.libvlc_media_player_set_media(mp, media)
        lib.libvlc_media_release(media) // the player holds its own reference
        status.value = Status.Opening
        if (lib.libvlc_media_player_play(mp) != 0) status.value = Status.Error
    }

    fun setPaused(paused: Boolean) = lib.libvlc_media_player_set_pause(mp, if (paused) 1 else 0)

    fun togglePause() {
        when (status.value) {
            // Replay after a cancelled auto-next: a fresh media WITHOUT the resume
            // start-time — reusing the old one would jump back to where it began.
            Status.Ended -> currentMrl?.let { play(it, currentOptions) }
            Status.Paused -> setPaused(false)
            else -> setPaused(true)
        }
    }

    fun seekTo(ms: Long) {
        val dur = durationMs.value
        lib.libvlc_media_player_set_time(mp, if (dur > 0) ms.coerceIn(0L, dur) else ms.coerceAtLeast(0L))
        positionMs.value = if (dur > 0) ms.coerceIn(0L, dur) else ms.coerceAtLeast(0L)
    }

    fun seekBy(deltaMs: Long) = seekTo(currentTimeMs() + deltaMs)

    /** 0..200 (libVLC's scale; 100 = unity). */
    fun setVolume(volume: Int) { lib.libvlc_audio_set_volume(mp, volume.coerceIn(0, 200)) }

    fun setRate(rate: Float) { lib.libvlc_media_player_set_rate(mp, rate) }

    fun currentTimeMs(): Long = lib.libvlc_media_player_get_time(mp).coerceAtLeast(0L)

    /** The stream's audio tracks (dual-audio releases carry two). "Disable" is left out. */
    fun audioTracks(): List<TrackDescription> {
        val head = lib.libvlc_audio_get_track_description(mp) ?: return emptyList()
        val out = mutableListOf<TrackDescription>()
        try {
            var node: Pointer? = head
            while (node != null) {
                val id = node.getInt(0)
                val name = node.getPointer(LibVlc.TRACK_NAME_OFFSET)?.getString(0, "UTF-8").orEmpty()
                if (id >= 0) out += TrackDescription(id, name.ifBlank { "Track ${out.size + 1}" })
                node = node.getPointer(LibVlc.TRACK_NEXT_OFFSET)
            }
        } finally {
            lib.libvlc_track_description_list_release(head)
        }
        return out
    }

    fun audioTrack(): Int = lib.libvlc_audio_get_track(mp)

    fun setAudioTrack(id: Int) { lib.libvlc_audio_set_track(mp, id) }

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
                val w = IntByReference()
                val h = IntByReference()
                val ok = lib.libvlc_video_get_size(mp, 0, w, h) == 0 &&
                    w.value in 1..width && h.value in 1..height
                videoWidth.value = if (ok) w.value else width
                videoHeight.value = if (ok) h.value else height
            }
        }
        frameTick.value = frameTick.value + 1
        return true
    }

    /**
     * Stops and frees the player. Stopping a network stream can block for a
     * moment while libVLC joins its threads, so that happens off the UI thread;
     * the callbacks stay referenced by the closure until it is done.
     */
    fun release() {
        if (released) return
        released = true
        synchronized(lock) {
            bitmap?.close()
            bitmap = null
            staging = null
        }
        frame.value = null
        Thread({
            runCatching { lib.libvlc_media_player_stop(mp) }
            runCatching { lib.libvlc_media_player_release(mp) }
            // Keep every callback reachable until libVLC can no longer call it.
            listOf(formatCb, cleanupCb, lockCb, displayCb, eventCb).hashCode()
        }, "libvlc-release").apply { isDaemon = true }.start()
    }
}
