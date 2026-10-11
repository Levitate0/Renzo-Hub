package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.CardItem
import top.levitatemedia.renzo.tv.api.FolderInfo
import top.levitatemedia.renzo.tv.ui.components.ErrorBox
import top.levitatemedia.renzo.tv.ui.components.MediaGrid
import top.levitatemedia.renzo.tv.ui.components.isHidden
import top.levitatemedia.renzo.tv.ui.kit.CARD_SIZES
import top.levitatemedia.renzo.tv.ui.kit.CardSizePref
import top.levitatemedia.renzo.tv.ui.kit.ContentLevelSelect
import top.levitatemedia.renzo.tv.ui.kit.FieldLabel
import top.levitatemedia.renzo.tv.ui.kit.LocalIsTv
import top.levitatemedia.renzo.tv.ui.kit.RenzoButton
import top.levitatemedia.renzo.tv.ui.kit.RenzoDialog
import top.levitatemedia.renzo.tv.ui.kit.RenzoTextField
import top.levitatemedia.renzo.tv.ui.kit.RibbonSelect
import top.levitatemedia.renzo.tv.ui.kit.RibbonToggleChip
import top.levitatemedia.renzo.tv.ui.kit.SelectOption
import top.levitatemedia.renzo.tv.ui.kit.currentCardSize
import top.levitatemedia.renzo.tv.ui.kit.kitClickable
import top.levitatemedia.renzo.tv.ui.kit.screenWidthDp
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

// ---------------------------------------------------------------------------
// Library, laid out as Shiori's library (feature-shiori ui/library
// LibraryScreen.kt): one contextual ribbon of compact selects — folder, list,
// 18+ — with the action cluster pushed right (sort, card size, New folder,
// Import), then Shiori's cover grid. Renzo's own filters and actions are all
// still here; only the chip rows became ribbon selects.
// ---------------------------------------------------------------------------

@Composable
fun LibraryScreen(app: AppServices, onOpen: (CardItem) -> Unit) {
    var folders by remember { mutableStateOf<List<FolderInfo>>(emptyList()) }
    var listCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    // Process-scoped (see LibraryState): coming back from a title returns to
    // the folder/list you were browsing, not "All".
    var activeFolder by LibraryState.folder             // "" = All
    var activeList by LibraryState.list                 // "" = All
    var orderBy by rememberSaveable { mutableStateOf("library") }
    var items by remember { mutableStateOf<List<CardItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var newFolderOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun toast(msg: String) = top.levitatemedia.renzo.tv.renzoToast(msg)

    LaunchedEffect(retryKey) {
        try {
            folders = app.repo.folders()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The ribbon is optional; the grid error surface covers real outages.
        }
        try {
            listCounts = app.repo.lists()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(activeFolder, activeList, retryKey) {
        loading = true
        error = null
        try {
            items = app.repo.library(activeFolder.ifEmpty { null }, activeList.ifEmpty { null })
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = libraryErrorMessage(e)
        }
        loading = false
    }

    // Old #importBtn: toast, POST /trackers/import, toast the total, reload.
    fun runImport() {
        if (importing) return
        importing = true
        toast("Importing from trackers…")
        scope.launch {
            try {
                val r = app.repo.importTrackers()
                toast("Imported ${r.anilist + r.mal} titles")
                retryKey++
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!(e is ApiError && (e.status == 401 || e.status == 402))) {
                    toast("Import failed: " + (e.message ?: "unknown error"))
                }
            } finally {
                importing = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        LibraryRibbon(
            folders = folders,
            activeFolder = activeFolder,
            onFolder = { activeFolder = it },
            listCounts = listCounts,
            activeList = activeList,
            onList = { activeList = it },
            contentLevel = app.contentLevel.value,
            onContentLevel = { app.setContentLevel(it) },
            orderBy = orderBy,
            onOrderBy = { orderBy = it },
            importing = importing,
            onImport = { runImport() },
            onNewFolder = { newFolderOpen = true },
        )

        when {
            error != null -> ErrorBox(message = error!!, onRetry = { retryKey++ }, autoFocus = app.isTv)
            else -> {
                val level = app.contentLevel.value
                val shown = remember(items, level, orderBy) {
                    items.filter { !isHidden(it, level) }.let { list ->
                        if (orderBy == "title") list.sortedBy { it.title.lowercase() } else list
                    }
                }
                MediaGrid(
                    items = shown,
                    onOpen = onOpen,
                    loading = loading,
                    empty = if (activeFolder.isEmpty() && activeList.isEmpty()) {
                        "Your library is empty. Add titles from Discover or Search, or import your AniList / MAL list."
                    } else {
                        "Nothing in this view yet."
                    },
                )
            }
        }
    }

    if (newFolderOpen) {
        NewFolderDialog(
            onCancel = { newFolderOpen = false },
            onCreate = { name, done ->
                scope.launch {
                    try {
                        app.repo.createFolder(name)
                        newFolderOpen = false
                        activeFolder = name // old: jump straight into the new folder
                        retryKey++
                        toast("Folder created")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        toast(e.message ?: "Folder create failed")
                    } finally {
                        done()
                    }
                }
            },
        )
    }
}

/**
 * Shiori's ribbon container: on a wide window the row fits and the action
 * cluster is pushed to the right edge; on phones and TV the row scrolls.
 */
@Composable
private fun RibbonRow(wide: Boolean, content: @Composable RowScope.() -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(if (wide) Modifier else Modifier.horizontalScroll(rememberScrollState()))
            .padding(vertical = 6.dp)
            .focusGroup(),
        content = content,
    )
}

@Composable
private fun LibraryRibbon(
    folders: List<FolderInfo>,
    activeFolder: String,
    onFolder: (String) -> Unit,
    listCounts: Map<String, Int>,
    activeList: String,
    onList: (String) -> Unit,
    contentLevel: String,
    onContentLevel: (String) -> Unit,
    orderBy: String,
    onOrderBy: (String) -> Unit,
    importing: Boolean,
    onImport: () -> Unit,
    onNewFolder: () -> Unit,
) {
    val isTv = LocalIsTv.current
    val sm = screenWidthDp() >= 640.dp
    val wide = !isTv && screenWidthDp() >= 1024.dp

    RibbonRow(wide = wide) {
        // Folder — Shiori's status select slot.
        RibbonSelect(
            options = listOf(SelectOption("", "All folders")) +
                folders.map { SelectOption(it.name, it.name, count = it.count) },
            value = activeFolder,
            onChange = onFolder,
            triggerWidth = if (sm) 176.dp else 144.dp,
        )
        // Lists — built-ins first, then any custom lists (old renderChips).
        val names = (listOf("watchlist", "favorites") + listCounts.keys).distinct()
        RibbonSelect(
            options = listOf(SelectOption("", "All lists")) +
                names.map { SelectOption(it, listLabel(it), count = listCounts[it] ?: 0) },
            value = activeList,
            onChange = onList,
            triggerWidth = if (sm) 160.dp else 128.dp,
        )
        ContentLevelSelect(contentLevel, onContentLevel)

        // Right cluster, pushed right on a wide window (the web's ml-auto).
        if (wide) Spacer(Modifier.weight(1f))
        RibbonSelect(
            options = listOf(SelectOption("library", "Recently added"), SelectOption("title", "Alphabetical")),
            value = orderBy,
            onChange = onOrderBy,
            triggerWidth = if (sm) 144.dp else 128.dp,
        )
        RibbonSelect(
            options = CARD_SIZES.map { SelectOption(it.value, it.label) },
            value = currentCardSize().value,
            onChange = { CardSizePref.set(it) },
            placeholder = "Card Size",
            maxTriggerWidth = 32.dp,
            triggerWidth = if (sm) 64.dp else 56.dp,
        )
        RibbonToggleChip(
            label = "New folder",
            active = false,
            onClick = onNewFolder,
            icon = Icons.Filled.CreateNewFolder,
        )
        // Shiori's primary "Add Series" slot.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(if (isTv) 40.dp else 32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RenzoColors.Primary)
                .kitClickable(enabled = !importing, onClick = onImport)
                .padding(horizontal = 12.dp),
        ) {
            Icon(Icons.Filled.Sync, contentDescription = null, tint = RenzoColors.PrimaryForeground, modifier = Modifier.size(16.dp))
            Text(
                if (importing) "Importing…" else "Import AniList / MAL",
                style = MaterialTheme.typography.labelMedium,
                color = RenzoColors.PrimaryForeground,
                maxLines = 1,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** "watchlist" → "Watchlist"; custom list names keep their own case. */
private fun listLabel(name: String): String = when (name) {
    "watchlist" -> "Watchlist"
    "favorites" -> "Favourites"
    else -> name
}

/** The "New folder" dialog (library-chips.tsx), on Shiori's dialog. */
@Composable
private fun NewFolderDialog(
    onCancel: () -> Unit,
    onCreate: (name: String, done: () -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    fun submit() {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || saving) return // old: empty prompt result -> no-op
        saving = true
        onCreate(trimmed) { saving = false }
    }

    RenzoDialog(
        onDismiss = onCancel,
        title = "New folder",
        description = "Folders group your library — a title lives in exactly one.",
        maxWidth = 512.dp,
    ) {
        FieldLabel("Folder name")
        RenzoTextField(value = name, onValueChange = { name = it }, placeholder = "Folder name")
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            RenzoButton(text = "Cancel", variant = "outline", onClick = onCancel)
            RenzoButton(
                text = if (saving) "Creating…" else "Create",
                busy = saving,
                enabled = name.trim().isNotEmpty(),
                onClick = { submit() },
            )
        }
    }
}

private fun libraryErrorMessage(e: Exception): String = when {
    e is ApiError && e.status == 0 -> "Can't reach your Renzo server — check the connection."
    e is ApiError && e.status == 401 -> "Session expired — sign in again from the account menu."
    else -> e.message ?: "Something went wrong."
}

/** Library's page state, kept for the process so Back restores your view. */
internal object LibraryState {
    val folder = androidx.compose.runtime.mutableStateOf("")
    val list = androidx.compose.runtime.mutableStateOf("")
}
