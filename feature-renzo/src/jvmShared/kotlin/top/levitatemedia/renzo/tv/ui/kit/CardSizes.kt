package top.levitatemedia.renzo.tv.ui.kit

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shiori's library card sizes (feature-shiori ui/library/LibraryScreen.kt
 * CARD_SIZES), shared by every Renzo cover grid and row. One choice, made on
 * the Library ribbon, governs them all — as Shiori's governs Library and Browse.
 */
data class CardSize(
    val value: String,
    val label: String,
    val width: Dp,
    val title: TextUnit,
    val badge: TextUnit,
)

val CARD_SIZES = listOf(
    CardSize("w-20", "XS", 80.dp, 6.4.sp, 6.4.sp),
    CardSize("w-32", "S", 128.dp, 12.sp, 12.sp),
    CardSize("w-45", "M", 180.dp, 14.sp, 12.sp),
    CardSize("w-58", "L", 232.dp, 16.sp, 14.sp),
    CardSize("w-70", "XL", 280.dp, 18.sp, 16.sp),
)

/**
 * The persisted size. Unset falls back per platform, as Shiori does: S on a
 * phone, M on a desktop window, L on a television.
 */
object CardSizePref {
    private val prefs by lazy { top.levitatemedia.renzo.hub.core.keyValuePrefs("renzo_tv") }
    private val stored = mutableStateOf<String?>(null)
    private var loaded = false

    private fun load() {
        if (!loaded) { stored.value = prefs.getString("cardSize", null); loaded = true }
    }

    fun value(isTv: Boolean): String {
        load()
        return stored.value ?: when {
            isTv -> "w-58"
            top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop -> "w-45"
            else -> "w-32"
        }
    }

    fun set(value: String) {
        stored.value = value
        loaded = true
        prefs.putString("cardSize", value)
    }
}

/** The card size in force for this composition. */
@Composable
fun currentCardSize(): CardSize {
    val v = CardSizePref.value(LocalIsTv.current)
    return CARD_SIZES.firstOrNull { it.value == v } ?: CARD_SIZES[2]
}
