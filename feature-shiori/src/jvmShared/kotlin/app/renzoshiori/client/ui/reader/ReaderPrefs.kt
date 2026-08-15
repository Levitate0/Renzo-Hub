package app.renzoshiori.client.ui.reader

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.utf16CodePoint
import top.levitatemedia.renzo.hub.core.HubPlatform
import top.levitatemedia.renzo.hub.core.keyValuePrefs

/**
 * Reader settings — a 1:1 transliteration of the web reader's `ReaderSettings`
 * (RenzoFrontend/src/app/reader/page.tsx). Same option set, same labels, same
 * defaults. Persisted in SharedPreferences, which is this client's localStorage:
 * the web stores the blob under "renzo_reader_settings" and the per-series mode
 * override under "renzo_reader_mode_<seriesId>" — both mirrored here.
 *
 * The web's `hotkeys` map is here too (it used to be omitted as touch-only —
 * the desktop exe ended that): the same 13 rebindable actions, the same
 * default keys, stored one pref per action so a partial map still gets
 * defaults for anything missing, exactly like the web's key-by-key merge.
 */
enum class ReaderMode(val value: String, val label: String) {
    AUTO("auto", "Auto (smart detect)"),
    PAGED("paged", "Paged — left to right"),
    PAGED_RTL("paged-rtl", "Paged — right to left"),
    DOUBLE("double", "Double page"),
    WEBTOON("webtoon", "Webtoon (no gaps)"),
    LONGSTRIP("longstrip", "Long strip (width-matched)"),
    VERTICAL("vertical", "Vertical (with gaps)");

    companion object {
        fun from(value: String?): ReaderMode = values().firstOrNull { it.value == value } ?: AUTO
    }
}

enum class FitMode(val value: String, val label: String) {
    WIDTH("width", "Fit width"),
    HEIGHT("height", "Fit height"),
    ORIGINAL("original", "Original size");

    companion object {
        fun from(value: String?): FitMode = values().firstOrNull { it.value == value } ?: WIDTH
    }
}

/** Web `BG` map: black #000, gray #18181b, white #fafafa. */
enum class ReaderBackground(val value: String, val label: String, val argb: Long) {
    BLACK("black", "Black", 0xFF000000L),
    GRAY("gray", "Dark gray", 0xFF18181BL),
    WHITE("white", "White", 0xFFFAFAFAL);

    companion object {
        fun from(value: String?): ReaderBackground = values().firstOrNull { it.value == value } ?: BLACK
    }
}

/** A reading mode with "auto" already resolved — the web's `resolvedMode`. */
enum class ResolvedMode {
    PAGED, PAGED_RTL, DOUBLE, WEBTOON, LONGSTRIP, VERTICAL;

    /** webtoon / longstrip / vertical — the infinite strip modes. */
    val continuous: Boolean get() = this == WEBTOON || this == LONGSTRIP || this == VERTICAL
    val rtl: Boolean get() = this == PAGED_RTL

    companion object {
        fun of(mode: ReaderMode): ResolvedMode = when (mode) {
            ReaderMode.PAGED_RTL -> PAGED_RTL
            ReaderMode.DOUBLE -> DOUBLE
            ReaderMode.WEBTOON -> WEBTOON
            ReaderMode.LONGSTRIP -> LONGSTRIP
            ReaderMode.VERTICAL -> VERTICAL
            else -> PAGED
        }
    }
}

/**
 * Rebindable reader actions — the web's `HotkeyAction`, same ids, labels,
 * defaults and display order. `nextPage`/`prevPage` navigate WITHIN a chapter
 * and never skip chapters — chapter skipping is its own pair.
 */
enum class HotkeyAction(val id: String, val label: String, val defaultKey: String) {
    NEXT_PAGE("nextPage", "Next page / scroll forward", "ArrowRight"),
    PREV_PAGE("prevPage", "Previous page / scroll back", "ArrowLeft"),
    SCROLL_DOWN("scrollDown", "Scroll down", "ArrowDown"),
    SCROLL_UP("scrollUp", "Scroll up", "ArrowUp"),
    NEXT_CHAPTER("nextChapter", "Next chapter", "]"),
    PREV_CHAPTER("prevChapter", "Previous chapter", "["),
    FIRST_PAGE("firstPage", "Jump to first page", "Home"),
    LAST_PAGE("lastPage", "Jump to last page", "End"),
    TOGGLE_CHROME("toggleChrome", "Show / hide controls", "Escape"),
    TOGGLE_CHAPTERS("toggleChapters", "Chapter list", "l"),
    TOGGLE_SETTINGS("toggleSettings", "Settings panel", "s"),
    BOOKMARK("bookmark", "Bookmark chapter", "b"),
    EXIT("exit", "Exit reader", "c"),
}

fun defaultHotkeys(): Map<HotkeyAction, String> =
    HotkeyAction.entries.associateWith { it.defaultKey }

/** The web's keyLabel: pretty-print a stored key token for the editor. */
fun hotkeyLabel(token: String): String = when (token) {
    "ArrowRight" -> "→"
    "ArrowLeft" -> "←"
    "ArrowUp" -> "↑"
    "ArrowDown" -> "↓"
    "Space" -> "Space"
    "Escape" -> "Esc"
    "" -> "—"
    else -> if (token.length == 1) token.uppercase() else token
}

/**
 * The web's eventKeyToken: normalize a key event to the stored/compared token.
 * Named keys keep their DOM `e.key` names so a map written by either client
 * reads identically; printable keys store their lowercase character.
 */
fun hotkeyToken(event: KeyEvent): String? {
    return when (event.key) {
        Key.DirectionRight -> "ArrowRight"
        Key.DirectionLeft -> "ArrowLeft"
        Key.DirectionUp -> "ArrowUp"
        Key.DirectionDown -> "ArrowDown"
        Key.MoveHome -> "Home"
        Key.MoveEnd -> "End"
        Key.Escape -> "Escape"
        Key.Spacebar -> "Space"
        Key.PageDown -> "PageDown"
        Key.PageUp -> "PageUp"
        else -> {
            val cp = event.utf16CodePoint
            if (cp in 33..126) cp.toChar().lowercaseChar().toString() else null
        }
    }
}

data class ReaderSettings(
    val mode: ReaderMode = ReaderMode.AUTO,
    val fit: FitMode = FitMode.WIDTH,
    /** % of viewport width cap in continuous modes. */
    val maxWidthPct: Int = 60,
    /**
     * Absolute page magnification, % (100 = as before).
     *
     * `fit` and `maxWidthPct` are both viewport-relative, so neither can make a
     * page bigger than the screen — which is exactly what viewing distance
     * needs. This multiplies on top of them, and above 100% the page is
     * pannable (touch: drag; D-pad: the arrows pan to the edge, then turn).
     * Defaults higher on a television — see [ReaderPrefs.load].
     */
    val scalePct: Int = 100,
    val background: ReaderBackground = ReaderBackground.BLACK,
    val preload: Int = 4,
    /** Vertical-mode gap, in px on the web → dp here. */
    val gapPx: Int = 12,
    val showPageNumber: Boolean = true,
    val tapNavigation: Boolean = true,
    /** Continuous tap-to-scroll step, as % of viewport height. */
    val tapAdvancePct: Int = 80,
    /** Continuous: append the next chapter at the bottom. */
    val infiniteScroll: Boolean = true,
    /** Show a "finished / up next" screen between chapters (paged: its own page). */
    val chapterTransition: Boolean = true,
    val autoMarkRead: Boolean = true,
    /** Clear the streamed-page cache when leaving the reader. */
    val autoClearCache: Boolean = true,
    /** Rebindable key map — action → stored key token ("" = unbound). */
    val hotkeys: Map<HotkeyAction, String> = defaultHotkeys(),
)

/** KeyValuePrefs-backed store for [ReaderSettings] + the per-series mode override. */
class ReaderPrefs {
    private val prefs = keyValuePrefs("renzo_reader_settings")

    /**
     * Reader settings are device-local (never server-synced), so a television
     * keeps its own values and can simply start from different defaults — no
     * per-device namespacing needed. Only the DEFAULTS differ: once a value has
     * been written it is honoured on every device class.
     */
    private val isTv = HubPlatform.isTv

    fun load(): ReaderSettings {
        val d = ReaderSettings()
        return ReaderSettings(
            mode = ReaderMode.from(prefs.getString(KEY_MODE, d.mode.value)),
            // Fit-width is a phone default: on a 16:9 panel it crops the page
            // badly, where fit-height shows the whole thing.
            fit = FitMode.from(prefs.getString(KEY_FIT, (if (isTv) FitMode.HEIGHT else d.fit).value)),
            maxWidthPct = prefs.getInt(KEY_MAX_WIDTH, d.maxWidthPct),
            scalePct = prefs.getInt(KEY_SCALE, if (isTv) TV_DEFAULT_SCALE_PCT else d.scalePct),
            background = ReaderBackground.from(prefs.getString(KEY_BACKGROUND, d.background.value)),
            preload = prefs.getInt(KEY_PRELOAD, d.preload),
            gapPx = prefs.getInt(KEY_GAP, d.gapPx),
            showPageNumber = prefs.getBoolean(KEY_SHOW_PAGE_NUMBER, d.showPageNumber),
            tapNavigation = prefs.getBoolean(KEY_TAP_NAVIGATION, d.tapNavigation),
            tapAdvancePct = prefs.getInt(KEY_TAP_ADVANCE, d.tapAdvancePct),
            infiniteScroll = prefs.getBoolean(KEY_INFINITE_SCROLL, d.infiniteScroll),
            chapterTransition = prefs.getBoolean(KEY_CHAPTER_TRANSITION, d.chapterTransition),
            autoMarkRead = prefs.getBoolean(KEY_AUTO_MARK_READ, d.autoMarkRead),
            autoClearCache = prefs.getBoolean(KEY_AUTO_CLEAR_CACHE, d.autoClearCache),
            // One pref per action: anything never written falls back to its
            // default, which is the web's key-by-key merge behaviour.
            hotkeys = HotkeyAction.entries.associateWith { a ->
                prefs.getString(HOTKEY_PREFIX + a.id, null) ?: a.defaultKey
            },
        )
    }

    fun save(s: ReaderSettings) {
        prefs.putString(KEY_MODE, s.mode.value)
        prefs.putString(KEY_FIT, s.fit.value)
        prefs.putInt(KEY_MAX_WIDTH, s.maxWidthPct)
        prefs.putInt(KEY_SCALE, s.scalePct)
        prefs.putString(KEY_BACKGROUND, s.background.value)
        prefs.putInt(KEY_PRELOAD, s.preload)
        prefs.putInt(KEY_GAP, s.gapPx)
        prefs.putBoolean(KEY_SHOW_PAGE_NUMBER, s.showPageNumber)
        prefs.putBoolean(KEY_TAP_NAVIGATION, s.tapNavigation)
        prefs.putInt(KEY_TAP_ADVANCE, s.tapAdvancePct)
        prefs.putBoolean(KEY_INFINITE_SCROLL, s.infiniteScroll)
        prefs.putBoolean(KEY_CHAPTER_TRANSITION, s.chapterTransition)
        prefs.putBoolean(KEY_AUTO_MARK_READ, s.autoMarkRead)
        prefs.putBoolean(KEY_AUTO_CLEAR_CACHE, s.autoClearCache)
        HotkeyAction.entries.forEach { a ->
            prefs.putString(HOTKEY_PREFIX + a.id, s.hotkeys[a] ?: a.defaultKey)
        }
    }

    /** Per-series mode override ("auto" is stored as absence, exactly like the web). */
    fun seriesMode(seriesId: String): ReaderMode? =
        prefs.getString(seriesModeKey(seriesId), null)?.let { ReaderMode.from(it) }

    fun setSeriesMode(seriesId: String, mode: ReaderMode?) {
        if (mode == null || mode == ReaderMode.AUTO) prefs.remove(seriesModeKey(seriesId))
        else prefs.putString(seriesModeKey(seriesId), mode.value)
    }

    private fun seriesModeKey(seriesId: String) = "renzo_reader_mode_$seriesId"

    companion object {
        /** Scale bounds, shared by the touch slider and the TV stepper. */
        const val SCALE_MIN = 50
        const val SCALE_MAX = 300
        const val SCALE_STEP = 10

        /** A page at 100% is legible at arm's length, not across a room. */
        private const val TV_DEFAULT_SCALE_PCT = 130

        private const val KEY_MODE = "mode"
        private const val KEY_FIT = "fit"
        private const val KEY_MAX_WIDTH = "maxWidthPct"
        private const val KEY_SCALE = "scalePct"
        private const val KEY_BACKGROUND = "background"
        private const val KEY_PRELOAD = "preload"
        private const val KEY_GAP = "gapPx"
        private const val KEY_SHOW_PAGE_NUMBER = "showPageNumber"
        private const val KEY_TAP_NAVIGATION = "tapNavigation"
        private const val KEY_TAP_ADVANCE = "tapAdvancePct"
        private const val KEY_INFINITE_SCROLL = "infiniteScroll"
        private const val KEY_CHAPTER_TRANSITION = "chapterTransition"
        private const val KEY_AUTO_MARK_READ = "autoMarkRead"
        private const val KEY_AUTO_CLEAR_CACHE = "autoClearCache"
        private const val HOTKEY_PREFIX = "hotkey_"
    }
}
