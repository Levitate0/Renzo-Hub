package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.CardItem
import top.levitatemedia.renzo.tv.api.FolderInfo
import top.levitatemedia.renzo.tv.ui.components.ContentChips
import top.levitatemedia.renzo.tv.ui.components.ErrorBox
import top.levitatemedia.renzo.tv.ui.components.MediaGrid
import top.levitatemedia.renzo.tv.ui.components.dashedBorder
import top.levitatemedia.renzo.tv.ui.components.focusRing
import top.levitatemedia.renzo.tv.ui.components.isHidden
import top.levitatemedia.renzo.tv.ui.components.tvClickable
import top.levitatemedia.renzo.tv.RenzoBackHandler
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

// ---------------------------------------------------------------------------
// Library (app/library/page.tsx + library-chips.tsx): "My Library" head with
// the Import AniList / MAL button, folder chips ("📁 All" + folders + "+ New
// folder" dialog), list chips (All / watchlist / favorites / custom), the
// content-filter ladder and the wrapping cover grid.
// ---------------------------------------------------------------------------

@Composable
fun LibraryScreen(app: AppServices, onOpen: (CardItem) -> Unit) {
    var folders by remember { mutableStateOf<List<FolderInfo>>(emptyList()) }
    var listCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    // Process-scoped (see LibraryState): coming back from a title returns to
    // the folder/list you were browsing, not "All".
    var activeFolder by LibraryState.folder             // "" = All
    var activeList by LibraryState.list                 // "" = All
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
            // Chip row is optional; the grid error surface covers real outages.
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
        LibraryHead(importing = importing, onImport = { runImport() })

        FolderChips(
            folders = folders,
            activeFolder = activeFolder,
            onSelect = { activeFolder = it },
            onNewFolder = { newFolderOpen = true },
        )
        ListChips(
            counts = listCounts,
            activeList = activeList,
            onSelect = { activeList = it },
        )
        ContentChips(app)

        when {
            error != null -> ErrorBox(message = error!!, onRetry = { retryKey++ })
            else -> {
                val level = app.contentLevel.value
                MediaGrid(
                    items = items.filter { !isHidden(it, level) },
                    onOpen = onOpen,
                    loading = loading,
                    empty = "Nothing here yet.",
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

/** `library-head flex flex-col gap-3 sm:flex-row sm:items-center
 *  sm:justify-between`: the 20sp/600 "My Library" and the outline-sm
 *  "Import AniList / MAL" button with the (spinning) refresh icon. */
@Composable
private fun LibraryHead(importing: Boolean, onImport: () -> Unit) {
    val sm = top.levitatemedia.renzo.tv.renzoScreenWidthDp() >= 640
    if (sm) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("My Library", color = RenzoColors.Foreground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            ImportButton(importing, onImport, Modifier)
        }
    } else {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("My Library", color = RenzoColors.Foreground, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            ImportButton(importing, onImport, Modifier.fillMaxWidth())
        }
    }
}

/** shadcn outline/sm with the gap-2 icon slot (web: h-8 px-3 text-xs). */
@Composable
private fun ImportButton(importing: Boolean, onImport: () -> Unit, modifier: Modifier) {
    var focused by remember { mutableStateOf(false) }
    // `animate-spin` on the RefreshCw icon while the import runs.
    val angle = if (importing) {
        rememberInfiniteTransition(label = "spin").animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing)),
            label = "spinAngle",
        ).value
    } else 0f
    Row(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(6.dp))
            .focusRing(focused, 6.dp)
            .background(if (focused) RenzoColors.Accent else RenzoColors.Background, RoundedCornerShape(6.dp))
            .border(1.dp, RenzoColors.Input, RoundedCornerShape(6.dp))
            .tvClickable(onFocused = { focused = it }, onClick = { if (!importing) onImport() })
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        val alpha = if (importing) 0.5f else 1f
        Icon(
            Icons.Outlined.Refresh,
            contentDescription = null,
            tint = RenzoColors.Foreground.copy(alpha = alpha),
            modifier = Modifier.size(16.dp).rotate(angle),
        )
        Text(
            "Import AniList / MAL",
            color = RenzoColors.Foreground.copy(alpha = alpha),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** One library chip (library-chips.tsx Chip): rounded-full border px-3.5
 *  py-1.5 text-[13px]; inactive = border-border bg-card text-muted. */
@Composable
private fun Chip(
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    count: Int? = null,
    /** Folder chips' active state is the indigo gradient; lists use primary. */
    folderStyle: Boolean = false,
    dashed: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(999.dp)
    val activeBg: Modifier.() -> Modifier = {
        if (folderStyle) background(
            Brush.linearGradient(listOf(RenzoColors.Indigo500, RenzoColors.Indigo400)),
            shape,
        ) else background(RenzoColors.Primary, shape)
    }
    val fg = when {
        active && folderStyle -> Color.White
        active -> RenzoColors.PrimaryForeground
        focused -> RenzoColors.Foreground
        else -> RenzoColors.MutedForeground
    }
    Row(
        Modifier
            .clip(shape)
            .focusRing(focused, 999.dp)
            .let { if (active) it.activeBg() else it.background(RenzoColors.Card, shape) }
            .let {
                when {
                    active -> it
                    dashed -> it.dashedBorder(if (focused) RenzoColors.Primary else RenzoColors.Border, 999.dp)
                    else -> it.border(1.dp, if (focused) RenzoColors.Primary else RenzoColors.Border, shape)
                }
            }
            .tvClickable(onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, fontSize = 13.sp)
        if (count != null && count > 0) {
            // `cnt ml-1.5 opacity-60`
            Text(
                count.toString(),
                color = fg.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
    }
}

/** `chips folders-row mt-3.5 flex flex-wrap gap-2`: "📁 All" + one chip per
 *  folder (with count) + the dashed "+ New folder" chip. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderChips(
    folders: List<FolderInfo>,
    activeFolder: String,
    onSelect: (String) -> Unit,
    onNewFolder: () -> Unit,
) {
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("📁 All", active = activeFolder == "", onClick = { onSelect("") }, folderStyle = true)
        folders.forEach { f ->
            Chip(
                f.name,
                active = activeFolder == f.name,
                onClick = { onSelect(f.name) },
                count = f.count,
                folderStyle = true,
            )
        }
        Chip("+ New folder", active = false, onClick = onNewFolder, dashed = true)
    }
}

/** `chips mt-3.5`: All + [watchlist, favorites, …custom lists] with counts. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListChips(
    counts: Map<String, Int>,
    activeList: String,
    onSelect: (String) -> Unit,
) {
    // Old renderChips: built-ins first, then any custom lists from the counts.
    val names = (listOf("watchlist", "favorites") + counts.keys).distinct()
    FlowRow(
        Modifier.fillMaxWidth().padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Chip("All", active = activeList == "", onClick = { onSelect("") })
        names.forEach { n ->
            Chip(n, active = activeList == n, onClick = { onSelect(n) }, count = counts[n] ?: 0)
        }
    }
}

/** The "New folder" dialog (library-chips.tsx): title, helper line, the
 *  "Folder name" input and Cancel / Create ("Creating…") actions. */
@Composable
private fun NewFolderDialog(
    onCancel: () -> Unit,
    onCreate: (name: String, done: () -> Unit) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    var fieldFocused by remember { mutableStateOf(false) }
    RenzoBackHandler(enabled = true) { onCancel() }

    fun submit() {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || saving) return // old: empty prompt result -> no-op
        saving = true
        onCreate(trimmed) { saving = false }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xCC000000))
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 512.dp)
                .fillMaxWidth(0.95f)
                .clip(RoundedCornerShape(12.dp))
                .background(RenzoColors.Background, RoundedCornerShape(12.dp))
                .border(1.dp, RenzoColors.Border, RoundedCornerShape(12.dp))
                .clickable(enabled = false) {}
                .padding(24.dp),
        ) {
            Text("New folder", color = RenzoColors.Foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Folders group your library — a title lives in exactly one.",
                color = RenzoColors.MutedForeground,
                fontSize = 14.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .focusRing(fieldFocused, 6.dp)
                    .background(RenzoColors.Background, RoundedCornerShape(6.dp))
                    .border(1.dp, RenzoColors.Input, RoundedCornerShape(6.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(color = RenzoColors.Foreground, fontSize = 14.sp, fontFamily = top.levitatemedia.renzo.hub.core.GeistFamily),
                    cursorBrush = SolidColor(RenzoColors.Primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { fieldFocused = it.isFocused },
                    decorationBox = { inner ->
                        Box {
                            if (name.isEmpty()) {
                                Text("Folder name", color = RenzoColors.MutedForeground, fontSize = 14.sp)
                            }
                            inner()
                        }
                    },
                )
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            ) {
                var cancelFocused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .focusRing(cancelFocused, 6.dp)
                        .background(
                            if (cancelFocused) RenzoColors.Accent else Color.Transparent,
                            RoundedCornerShape(6.dp),
                        )
                        .tvClickable(onFocused = { cancelFocused = it }, onClick = onCancel)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text("Cancel", color = RenzoColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
                var createFocused by remember { mutableStateOf(false) }
                val enabled = !saving && name.trim().isNotEmpty()
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .focusRing(createFocused, 6.dp)
                        .background(
                            RenzoColors.Primary.copy(alpha = if (enabled) 1f else 0.5f),
                            RoundedCornerShape(6.dp),
                        )
                        .tvClickable(onFocused = { createFocused = it }, onClick = { submit() })
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        if (saving) "Creating…" else "Create",
                        color = RenzoColors.PrimaryForeground,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
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
