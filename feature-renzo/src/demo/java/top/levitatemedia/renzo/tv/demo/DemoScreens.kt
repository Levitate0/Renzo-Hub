package top.levitatemedia.renzo.tv.demo

import top.levitatemedia.renzo.tv.ui.screens.BrowseState
import top.levitatemedia.renzo.tv.ui.screens.LibraryState

/**
 * Reaches into the two process-scoped page states so `-e page category` /
 * `-e page library-folder` can open the *inner* views (Discover's full-grid
 * category, Library filtered to a folder) that are otherwise only reachable
 * by clicking.
 */
internal object DemoScreens {
    fun reset() {
        BrowseState.category.value = null
        LibraryState.folder.value = ""
        LibraryState.list.value = ""
    }

    /** trending | recommended | newSeason (HomeScreen.CAT_LABELS). */
    fun discoverCategory(id: String) { BrowseState.category.value = id }

    fun libraryFolder(name: String) { LibraryState.folder.value = name }
}
