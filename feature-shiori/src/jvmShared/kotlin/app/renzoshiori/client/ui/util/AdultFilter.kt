package app.renzoshiori.client.ui.util

import top.levitatemedia.renzo.hub.core.keyValuePrefs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Adult (18+) view filter — the native twin of the web's
 * lib/utils/adult-filter.ts. It filters Library/Browse rendering only; the
 * server-side `nsfwVisibility` setting is a different thing entirely (that one
 * filters the Sources list).
 *
 * The flag is snapshot state, not just a preference read: the web broadcasts a
 * `renzo-hide-adult-changed` event so every mounted view re-filters the moment
 * the menu item is clicked. Reading a plain SharedPreferences boolean inside a
 * composable gives no such signal — the grids would keep showing the old set
 * until the app was restarted.
 */
object AdultFilter {
    private const val PREFS = "renzo_prefs"
    private const val KEY = "renzo_hide_adult"

    /** Tag set the web classifies on, verbatim. */
    private val ADULT_TAGS = setOf(
        "hentai", "erotica", "adult", "smut", "pornographic", "porn",
        "18+", "r18", "r-18", "r18+", "r-18g", "nsfw",
    )

    private var hiddenState by mutableStateOf(false)

    /**
     * Loads the persisted flag. Called once from each platform's bootstrap.
     *
     * Defaults to HIDDEN. A fresh install shows nothing adult until the user
     * asks for it — the catalogue a source returns is not under our control,
     * so opt-in is the only defensible default (and it's what the store
     * questionnaire is really asking about).
     */
    fun init() {
        hiddenState = keyValuePrefs(PREFS).getBoolean(KEY, true)
    }

    /** Observable — a composable that reads this recomposes when it changes. */
    fun isHidden(): Boolean = hiddenState

    fun setHidden(hidden: Boolean) {
        hiddenState = hidden
        keyValuePrefs(PREFS).putBoolean(KEY, hidden)
    }

    /**
     * Web `isAdultItem`: the server flag OR the tags — never one instead of
     * the other. The flag is absent on older cached payloads and a series can
     * carry an adult tag from a source the flag computation didn't see, so an
     * `isNsfw == false` must still fall through to the tags (the web's
     * `item.isNsfw === true || isAdultSeries(item.genre)`).
     */
    fun isAdultItem(isNsfw: Boolean?, genres: List<String>): Boolean =
        isNsfw == true || genres.any { it.trim().lowercase() in ADULT_TAGS }

    /** Web `isAdultTag`: a single tag name is an explicit 18+ rating. */
    fun isAdultTag(tag: String): Boolean = tag.trim().lowercase() in ADULT_TAGS
}

/** Compose-friendly handle mirroring the web's `useHideAdult()` hook. */
class HideAdultState {
    /** Derived from the shared flag, so it tracks whoever last changed it. */
    val hidden: State<Boolean> = derivedStateOf { AdultFilter.isHidden() }

    fun toggle() = AdultFilter.setHidden(!AdultFilter.isHidden())
}

@Composable
fun rememberHideAdult(): HideAdultState = remember { HideAdultState() }
