package top.levitatemedia.renzo.tv

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf

/**
 * Hand-rolled navigation: a simple back stack of screens. (nav-compose adds
 * TV focus-restoration complexity we don't need; every screen is cheap to
 * rebuild and remembers its own state in the AppState holders.)
 */
sealed class Screen {
    /** One of the five tabs; which one is in AppNav.tab. */
    data object Tabs : Screen()
    data class Title(val id: Int) : Screen()
    data class Player(
        val titleId: Int,
        val ep: Int,
        /** Display context for the overlay + auto-next. */
        val titleName: String,
    ) : Screen()
    /** Settings hub; section: account | credentials | defaults | apikey. */
    data class Account(val section: String = "account") : Screen()
    data object Appearance : Screen()
    data object Users : Screen()
    data object ServerSettings : Screen()
}

enum class Tab(val label: String) {
    Discover("Discover"),
    Library("Library"),
    Updates("Updates"),
    History("History"),
    Downloads("Downloads"),
    /** Not in the tab bars — reached by submitting the topbar search box (web parity). */
    Search("Search"),
    ;

    companion object {
        /** The web app's tab set, shown in the pill bar and the drawer. */
        val BAR_TABS = listOf(Discover, Library, Updates, History, Downloads)
    }
}

class AppNav {
    val stack = mutableStateListOf<Screen>(Screen.Tabs)
    val tab = mutableStateOf(Tab.Discover)

    val current: Screen get() = stack.last()

    fun push(s: Screen) { stack.add(s) }

    /** @return true if consumed (popped); false when already at the root. */
    fun back(): Boolean {
        if (stack.size > 1) { stack.removeAt(stack.size - 1); return true }
        return false
    }

    fun replaceTop(s: Screen) { stack[stack.size - 1] = s }
}
