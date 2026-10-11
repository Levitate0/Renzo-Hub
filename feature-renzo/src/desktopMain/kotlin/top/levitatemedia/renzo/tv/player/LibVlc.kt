package top.levitatemedia.renzo.tv.player

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.ptr.IntByReference

/**
 * The slice of libVLC 3's C API the built-in player uses, bound directly with
 * JNA. Signatures follow include/vlc/libvlc*.h of VLC 3.0.
 *
 * Our own binding rather than vlcj: libVLC is LGPL-2.1+, which any app may load,
 * while vlcj is GPLv3 and would put the Hub's own code under GPLv3 when shipped.
 * Everything here is a plain call into the LGPL library.
 *
 * Handles (instance, media, player, event manager) are opaque [Pointer]s. The
 * callback interfaces must stay strongly referenced for as long as libVLC can
 * call them — JNA frees a callback's native stub when its object is collected.
 */
internal interface LibVlc : Library {
    fun libvlc_get_version(): String?
    fun libvlc_errmsg(): String?

    fun libvlc_new(argc: Int, argv: Array<String>): Pointer?
    fun libvlc_release(instance: Pointer)

    fun libvlc_media_new_location(instance: Pointer, mrl: String): Pointer?
    fun libvlc_media_new_path(instance: Pointer, path: String): Pointer?
    fun libvlc_media_add_option(media: Pointer, option: String)
    fun libvlc_media_release(media: Pointer)

    fun libvlc_media_player_new(instance: Pointer): Pointer?
    fun libvlc_media_player_release(player: Pointer)
    fun libvlc_media_player_set_media(player: Pointer, media: Pointer)
    fun libvlc_media_player_play(player: Pointer): Int
    fun libvlc_media_player_set_pause(player: Pointer, pause: Int)
    fun libvlc_media_player_stop(player: Pointer)
    fun libvlc_media_player_get_time(player: Pointer): Long
    fun libvlc_media_player_set_time(player: Pointer, timeMs: Long)
    fun libvlc_media_player_get_length(player: Pointer): Long
    fun libvlc_media_player_set_rate(player: Pointer, rate: Float): Int
    fun libvlc_media_player_event_manager(player: Pointer): Pointer

    /** 0..200, 100 = unity. Returns -1 if out of range. */
    fun libvlc_audio_set_volume(player: Pointer, volume: Int): Int
    /** Linked list of [TrackDescription] nodes, or null; free with [libvlc_track_description_list_release]. */
    fun libvlc_audio_get_track_description(player: Pointer): Pointer?
    fun libvlc_audio_get_track(player: Pointer): Int
    fun libvlc_audio_set_track(player: Pointer, trackId: Int): Int
    fun libvlc_track_description_list_release(list: Pointer)

    /** The VISIBLE size of video [num] — the frame buffer itself can be padded. */
    fun libvlc_video_get_size(player: Pointer, num: Int, width: IntByReference, height: IntByReference): Int
    fun libvlc_video_set_callbacks(player: Pointer, lock: LockCallback, unlock: UnlockCallback?, display: DisplayCallback?, opaque: Pointer?)
    fun libvlc_video_set_format_callbacks(player: Pointer, setup: FormatCallback, cleanup: CleanupCallback?)

    fun libvlc_event_attach(eventManager: Pointer, eventType: Int, callback: EventCallback, userData: Pointer?): Int

    /** `void *(*lock)(void *opaque, void **planes)`: point planes[0] at the frame buffer. */
    fun interface LockCallback : Callback { fun invoke(opaque: Pointer?, planes: Pointer): Pointer? }

    /** `void (*unlock)(void *opaque, void *picture, void *const *planes)` */
    fun interface UnlockCallback : Callback { fun invoke(opaque: Pointer?, picture: Pointer?, planes: Pointer?) }

    /** `void (*display)(void *opaque, void *picture)`: the frame is due on screen now. */
    fun interface DisplayCallback : Callback { fun invoke(opaque: Pointer?, picture: Pointer?) }

    /**
     * `unsigned (*setup)(void **opaque, char *chroma, unsigned *width, unsigned *height,
     * unsigned *pitches, unsigned *lines)`: pick the pixel format and buffer layout;
     * returns the number of picture buffers, 0 to refuse.
     */
    fun interface FormatCallback : Callback {
        fun invoke(opaque: Pointer?, chroma: Pointer, width: Pointer, height: Pointer, pitches: Pointer, lines: Pointer): Int
    }

    /** `void (*cleanup)(void *opaque)`: the buffers from setup are no longer used. */
    fun interface CleanupCallback : Callback { fun invoke(opaque: Pointer?) }

    /** `void (*)(const libvlc_event_t *event, void *userData)` */
    fun interface EventCallback : Callback { fun invoke(event: Pointer, userData: Pointer?) }

    companion object {
        // libvlc_event_e (libvlc_events.h, VLC 3.0).
        const val MediaPlayerOpening = 258
        const val MediaPlayerBuffering = 259
        const val MediaPlayerPlaying = 260
        const val MediaPlayerPaused = 261
        const val MediaPlayerStopped = 262
        const val MediaPlayerEndReached = 265
        const val MediaPlayerEncounteredError = 266
        const val MediaPlayerTimeChanged = 267
        const val MediaPlayerLengthChanged = 273
        const val MediaPlayerESAdded = 276

        /**
         * libvlc_event_t is `{ int type; void *p_obj; union u; }`, so the union
         * starts after the int (padded) and the pointer, aligned for its int64
         * members: offset 16 on 64-bit, 8 on 32-bit.
         * Every event read here is a single int64 or float at the union's start.
         */
        val EVENT_UNION_OFFSET: Long = if (Native.POINTER_SIZE == 8) 16L else 8L

        // libvlc_track_description_t: { int i_id; char *psz_name; libvlc_track_description_t *p_next; }
        val TRACK_NAME_OFFSET: Long = if (Native.POINTER_SIZE == 4) 4L else 8L
        val TRACK_NEXT_OFFSET: Long = TRACK_NAME_OFFSET + Native.POINTER_SIZE
    }
}

/** One audio track as libVLC describes it. */
internal data class TrackDescription(val id: Int, val name: String)
