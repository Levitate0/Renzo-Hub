package app.renzoshiori.client.ui.home

import top.levitatemedia.renzo.hub.core.HubForeground
import app.renzoshiori.client.ui.util.HubBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DriveFolderUpload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Power
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlin.math.roundToInt
import androidx.lifecycle.viewmodel.compose.viewModel
import app.renzoshiori.client.resources.Res
import app.renzoshiori.client.resources.*
import app.renzoshiori.client.ShioriRuntime
import app.renzoshiori.client.data.model.DownloadsMetricsDto
import app.renzoshiori.client.data.model.UserDto
import app.renzoshiori.client.data.model.UserLevel
import app.renzoshiori.client.ui.browse.BrowseScreen
import app.renzoshiori.client.ui.downloads.DownloadsScreen
import app.renzoshiori.client.ui.library.LibraryContent
import app.renzoshiori.client.ui.library.LibraryViewModel
import app.renzoshiori.client.ui.library.OnlineOfflinePill
import app.renzoshiori.client.ui.onboarding.TourAnchors
import app.renzoshiori.client.ui.onboarding.WalkthroughOverlay
import app.renzoshiori.client.ui.onboarding.tourAnchor
import app.renzoshiori.client.ui.queue.QueueScreen
import app.renzoshiori.client.ui.sources.SourcesScreen
import app.renzoshiori.client.ui.status.StatusScreen
import app.renzoshiori.client.ui.theme.RenzoColors
import app.renzoshiori.client.ui.tv.LocalIsTv
import app.renzoshiori.client.ui.tv.TvSelectedMark
import app.renzoshiori.client.ui.tv.TvUseAComputerScreen
import app.renzoshiori.client.ui.tv.focusRing
import app.renzoshiori.client.ui.tv.rememberFocusState
import app.renzoshiori.client.ui.tv.tvClickable
import app.renzoshiori.client.ui.tv.tvContentColor
import app.renzoshiori.client.ui.updates.UpdatesScreen
import app.renzoshiori.client.ui.util.rememberHideAdult
import app.renzoshiori.client.ui.util.screenWidthDp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Everything the account menu can do. The shell raises these; MainActivity
 * routes them to the matching screen or dialog — 1:1 with the web
 * user-menu.tsx item set.
 */
sealed interface AccountAction {
    data object EditProfile : AccountAction
    data object ChangePassword : AccountAction
    data object Trackers : AccountAction
    data object ImportBackup : AccountAction
    data object Users : AccountAction
    data object Account : AccountAction
    data object ServerSettings : AccountAction
    data class ImportSeries(val titleOnly: Boolean) : AccountAction
    data object Appearance : AccountAction
    data object Tour : AccountAction
    data object SignOut : AccountAction
    /** Hub: hop to the anime half. */
    data object SwitchApp : AccountAction
}

/**
 * Nav sections — the web's useSections() list, in its order, including the
 * native-only Downloads entry (section-pills.tsx gates it on `useIsNative()`,
 * which is always true here).
 */
enum class Section(val label: String, val icon: ImageVector) {
    Library("Library", Icons.AutoMirrored.Filled.LibraryBooks),
    Updates("Updates", Icons.Filled.Notifications),
    Browse("Browse", Icons.Filled.AutoAwesome),
    Queue("Queue", Icons.AutoMirrored.Filled.List),
    Status("Status", Icons.Filled.MonitorHeart),
    Sources("Sources", Icons.Filled.Power),
    Downloads("Downloads", Icons.Filled.Download),
}

/**
 * The app chrome, transliterated from command-bar.tsx (the <lg branch):
 * a 56dp bar with hamburger, logo + wordmark, expanding search, the
 * Online/Offline pill and the avatar. The hamburger opens the section drawer
 * from the LEFT (the web's Sheet side="left"); the avatar opens the account
 * panel from the RIGHT.
 */
@Composable
fun HomeShell(
    user: UserDto,
    onOpenSeries: (String) -> Unit,
    onOpenOfflineSeries: (String) -> Unit,
    onAccountAction: (AccountAction) -> Unit,
    /**
     * Hoisted to the nav host: the persistent wide command bar is rendered
     * THERE (above every route, like the web's CommandBar), so the current
     * section and the LibraryViewModel that carries search state must be
     * shared rather than owned here.
     */
    section: String,
    onSectionChange: (String) -> Unit,
    libraryVm: LibraryViewModel,
    /** Browse "Read": preview (mihonId, title) live from the source. */
    onPreviewRead: (String, String) -> Unit = { _, _ -> },
    showTour: Boolean = false,
    onTourFinish: () -> Unit = {},
) {
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val current = Section.valueOf(section)
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val keyboard = LocalSoftwareKeyboardController.current
    val app = ShioriRuntime.app

    // Queue metrics drive the drawer's Queue badge/live dot and the footer
    // download-status row (web: useDownloadsMetrics).
    var metrics by remember { mutableStateOf(DownloadsMetricsDto()) }
    LaunchedEffect(drawerState.isOpen, current) {
        while (true) {
            runCatching { app.network.currentApi()?.downloadMetrics() }
                .getOrNull()?.let { metrics = it }
            delay(10_000)
        }
    }

    // externalDomain for the full OPDS URL the account panel copies.
    var externalDomain by remember { mutableStateOf("") }
    var importFolder by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        runCatching { app.network.currentApi()?.shellSettings() }.getOrNull()?.let {
            externalDomain = it.externalDomain
            importFolder = it.importFolder
        }
    }

    val libraryState by libraryVm.state.collectAsState()

    // A drawer you must open before you can steer is hostile with a remote, so
    // TV gets a persistent left rail instead. Same sections, same state, same
    // composable below it — only the container differs (see ui/tv/TvFocus.kt on
    // why there is no second screen tree).
    val isTv = LocalIsTv.current
    // The web's lg breakpoint: a wide window gets the DESKTOP chrome — inline
    // section pills, an always-visible search input, download counters, and
    // the account menu as an anchored dropdown. No hamburger, no sheet.
    val wide = !isTv && screenWidthDp() >= 1024.dp

    // On a set-top box, Back from a section must land somewhere rather than
    // dropping out of the app; Library is home. Touch keeps its existing
    // behaviour untouched.
    HubBackHandler(enabled = isTv && current != Section.Library) {
        onSectionChange(Section.Library.name)
    }

    val body: @Composable () -> Unit = {
        Box(modifier = Modifier.fillMaxSize().background(RenzoColors.Background)) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                // ── 56dp command bar (narrow/TV only) ────────────────────
                // At wide the PERSISTENT bar lives in the nav host, above
                // every route — see ShioriCommandBar below. This shell only
                // draws the hamburger/mobile variant.
                val avatarCluster: @Composable () -> Unit = {
                    Box {
                        UserAvatar(
                            user = user,
                            size = 32.dp,
                            modifier = Modifier.padding(start = 6.dp, end = 4.dp).tourAnchor(TourAnchors.ACCOUNT),
                            onClick = { menuOpen = true },
                        )
                    }
                }
                if (!wide) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp),
                ) {
                    if (!isTv) {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.tourAnchor(TourAnchors.NAV),
                        ) {
                            Icon(
                                Icons.Filled.Menu,
                                contentDescription = "Open navigation menu",
                                tint = RenzoColors.Foreground,
                            )
                        }
                    }
                    Image(
                        painter = painterResource(Res.drawable.splash_icon),
                        contentDescription = "Renzo Shiori home",
                        modifier = Modifier.size(28.dp),
                    )
                    if (!searchOpen) {
                        Text(
                            "Renzo Shiori",
                            style = MaterialTheme.typography.titleSmall,
                            color = RenzoColors.Foreground,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    // TV has ONE search, and it is the in-screen TvSearchBar on
                    // library/browse — the only one with a microphone. Keeping
                    // this field too would put two near-identical boxes on the
                    // same screen where only one accepts speech, which on a
                    // remote is the difference between talking and spelling a
                    // title out with a D-pad.
                    if (searchOpen && !isTv) {
                        var searchFocused by remember { mutableStateOf(false) }
                        BasicTextField(
                            value = libraryState.searchTerm,
                            onValueChange = libraryVm::setSearch,
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = RenzoColors.Foreground),
                            cursorBrush = SolidColor(RenzoColors.Foreground),
                            // The leanback IME needs a declared action to submit
                            // with; without one the remote's keyboard has no
                            // "done" and the field can't be left cleanly.
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                            modifier = Modifier.onFocusChanged { searchFocused = it.isFocused },
                            decorationBox = { inner ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .width(176.dp)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(RenzoColors.Muted.copy(alpha = 0.7f))
                                        .border(1.dp, RenzoColors.Border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                        .focusRing(isTv && searchFocused, 8.dp)
                                        .padding(horizontal = 10.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.Search, contentDescription = null,
                                        tint = RenzoColors.MutedForeground, modifier = Modifier.size(16.dp),
                                    )
                                    Box(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                                        if (libraryState.searchTerm.isEmpty()) {
                                            Text(
                                                searchPlaceholder(current),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = RenzoColors.MutedForeground,
                                            )
                                        }
                                        inner()
                                    }
                                }
                            },
                        )
                    }
                    // …and the toggle goes with it, so the chrome has no dead
                    // affordance and D-pad traversal across the top bar stays short.
                    if (!isTv) {
                        DpadIconButton(
                            icon = if (searchOpen) Icons.Filled.Close else Icons.Filled.Search,
                            contentDescription = if (searchOpen) "Close search" else "Open search",
                            modifier = Modifier.tourAnchor(TourAnchors.SEARCH),
                            onClick = {
                                if (searchOpen && libraryState.searchTerm.isNotEmpty()) libraryVm.setSearch("")
                                searchOpen = !searchOpen
                            },
                        )
                    }
                    // The expanded search field needs the width: the pill
                    // steps aside (it's still in the drawer footer / rail), the
                    // avatar always stays.
                    if (!searchOpen) {
                        ShellOnlineOfflinePill(
                            offline = libraryState.offlineMode,
                            onToggle = { libraryVm.setOfflineMode(!libraryState.offlineMode) },
                        )
                    }
                    avatarCluster()
                }
                HorizontalDivider(color = RenzoColors.Border.copy(alpha = 0.6f))
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (current) {
                        Section.Library -> LibraryContent(
                            vm = libraryVm,
                            onOpenSeries = onOpenSeries,
                            onOpenOfflineSeries = onOpenOfflineSeries,
                        )
                        Section.Updates -> UpdatesScreen(onOpenSeries = onOpenSeries)
                        Section.Browse -> BrowseScreen(onPreviewRead = onPreviewRead)
                        Section.Queue -> QueueScreen()
                        Section.Status -> StatusScreen(onOpenSeries = onOpenSeries)
                        // Sources is 36 clicks over extension-repository URLs —
                        // deliberately not ported to the remote. It stays in the
                        // rail (people go looking for it) but shows where to do
                        // it instead of a form that can't be filled in.
                        Section.Sources -> if (isTv) {
                            TvUseAComputerScreen(
                                title = "Manage sources",
                                serverUrl = app.tokenStore.serverUrl,
                                path = "/sources",
                            )
                        } else {
                            SourcesScreen()
                        }
                        Section.Downloads -> DownloadsScreen()
                    }
                }
            }

            // ── Account panel — slides in from the RIGHT (narrow only) ──
            AccountPanel(
                visible = menuOpen && !wide,
                user = user,
                externalDomain = externalDomain,
                importFolderConfigured = importFolder.isNotBlank(),
                onDismiss = { menuOpen = false },
                onAction = { action ->
                    menuOpen = false
                    onAccountAction(action)
                },
            )

            // The walkthrough spotlights real chrome, so it lives over the
            // shell rather than on a route of its own.
            if (showTour) {
                WalkthroughOverlay(onFinish = onTourFinish)
            }
        }
    }

    if (isTv) {
        Row(modifier = Modifier.fillMaxSize().background(RenzoColors.Background)) {
            TvNavRail(
                current = current,
                metrics = metrics,
                offline = libraryState.offlineMode,
                onToggleOffline = { libraryVm.setOfflineMode(!libraryState.offlineMode) },
                onSelect = { s -> onSectionChange(s.name) },
                onSwitchApp = { onAccountAction(AccountAction.SwitchApp) },
                modifier = Modifier.tourAnchor(TourAnchors.NAV),
            )
            Box(modifier = Modifier.weight(1f)) { body() }
        }
    } else {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = RenzoColors.Background,
                    modifier = Modifier.width(288.dp),
                ) {
                    NavDrawerContent(
                        current = current,
                        metrics = metrics,
                        offline = libraryState.offlineMode,
                        onToggleOffline = { libraryVm.setOfflineMode(!libraryState.offlineMode) },
                        onSelect = { s ->
                            onSectionChange(s.name)
                            scope.launch { drawerState.close() }
                        },
                        onClose = { scope.launch { drawerState.close() } },
                        onSwitchApp = { onAccountAction(AccountAction.SwitchApp) },
                    )
                }
            },
            content = body,
        )
    }
}

/**
 * The TV navigation rail — the drawer's SectionList, always on screen.
 *
 * Selection and focus are on separate channels (§2.1 of the TV spec): the
 * active section keeps its accent colour, left accent bar and check mark
 * wherever the cursor goes, while the ring and fill follow the cursor. Both can
 * be true on the same row and stay tellable apart.
 */
@Composable
private fun TvNavRail(
    current: Section,
    metrics: DownloadsMetricsDto,
    offline: Boolean,
    onToggleOffline: () -> Unit,
    onSelect: (Section) -> Unit,
    onSwitchApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(232.dp)
            .fillMaxHeight()
            .background(RenzoColors.Popover)
            .statusBarsPadding()
            .padding(horizontal = 10.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Image(
            painter = painterResource(Res.drawable.renzo_login_banner),
            contentDescription = "Renzo Shiori",
            modifier = Modifier.height(30.dp).padding(start = 8.dp, bottom = 14.dp),
        )
        Section.entries.forEach { s ->
            val active = s == current
            val focus = rememberFocusState()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        when {
                            active -> RenzoColors.Primary.copy(alpha = 0.10f)
                            focus.focused -> RenzoColors.Card
                            else -> Color.Transparent
                        },
                        RoundedCornerShape(10.dp),
                    )
                    .focusRing(focus.focused, 10.dp)
                    .tvClickable(onFocused = focus::set) { onSelect(s) },
            ) {
                if (active) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(vertical = 10.dp)
                            .width(3.dp)
                            .height(26.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(RenzoColors.Primary),
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                ) {
                    Icon(
                        s.icon,
                        contentDescription = null,
                        tint = tvContentColor(active, focus.focused),
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        s.label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = tvContentColor(active, focus.focused),
                        modifier = Modifier.padding(start = 12.dp).weight(1f),
                    )
                    if (s == Section.Queue) {
                        val badge = metrics.downloads + metrics.failed
                        if (badge > 0) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .padding(end = 4.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(RenzoColors.Primary)
                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    if (badge > 99) "99+" else badge.toString(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = RenzoColors.PrimaryForeground,
                                )
                            }
                        }
                    }
                    TvSelectedMark(active)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        // The rail is the TV's drawer, so the app switch lives here too —
        // same placement contract as the phone drawer and the Renzo half.
        run {
            val focus = rememberFocusState()
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (focus.focused) RenzoColors.Card else Color.Transparent,
                        RoundedCornerShape(10.dp),
                    )
                    .focusRing(focus.focused, 10.dp)
                    .tvClickable(onFocused = focus::set, onClick = onSwitchApp)
                    .padding(horizontal = 12.dp, vertical = 11.dp),
            ) {
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = tvContentColor(false, focus.focused),
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Switch to Renzo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tvContentColor(false, focus.focused),
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }
        ShellOnlineOfflinePill(offline = offline, onToggle = onToggleOffline)
    }
}

/**
 * The Online/Offline pill. On touch it's the library's own composable,
 * unchanged; on TV the current mode is *selected* state, so it keeps its colour
 * and check while the cursor is elsewhere.
 */
@Composable
private fun ShellOnlineOfflinePill(offline: Boolean, onToggle: () -> Unit) {
    if (!LocalIsTv.current) {
        OnlineOfflinePill(offline = offline, onToggle = onToggle)
        return
    }
    val focus = rememberFocusState()
    val shape = RoundedCornerShape(50)
    val dot = if (offline) Color(0xFFF59E0B) else Color(0xFF10B981)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 4.dp)
            .background(
                when {
                    offline -> Color(0x26F59E0B)
                    focus.focused -> RenzoColors.Card
                    else -> RenzoColors.Foreground.copy(alpha = 0.06f)
                },
                shape,
            )
            .focusRing(focus.focused, 50.dp)
            .tvClickable(onFocused = focus::set, onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(8.dp))
        Text(
            if (offline) "Offline" else "Online",
            style = MaterialTheme.typography.labelMedium,
            color = if (offline) Color(0xFFFBBF24) else tvContentColor(false, focus.focused),
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun searchPlaceholder(section: Section): String = when (section) {
    Section.Library -> "Search series..."
    Section.Sources -> "Search sources..."
    Section.Queue -> "Search queue..."
    else -> "Search..."
}

/* ─── Nav drawer (web: Sheet side="left" + SectionList + footer) ────────── */

@Composable
private fun NavDrawerContent(
    current: Section,
    metrics: DownloadsMetricsDto,
    offline: Boolean,
    onToggleOffline: () -> Unit,
    onSelect: (Section) -> Unit,
    onClose: () -> Unit,
    onSwitchApp: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        ) {
            Image(
                painter = painterResource(Res.drawable.renzo_login_banner),
                contentDescription = "Renzo Shiori",
                modifier = Modifier.height(32.dp),
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(
                    Icons.Filled.Close, contentDescription = "Close",
                    tint = RenzoColors.MutedForeground,
                    modifier = Modifier.border(1.dp, RenzoColors.Border, CircleShape).padding(6.dp),
                )
            }
        }
        HorizontalDivider(color = RenzoColors.Border)

        // SectionList — rounded-lg rows, active = bg-primary/10 + primary text
        // + a 2px left accent bar, live dot and count badge on Queue.
        Column(
            modifier = Modifier.padding(8.dp).weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Section.entries.forEach { s ->
                val active = s == current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) RenzoColors.Primary.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { onSelect(s) },
                ) {
                    if (active) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(vertical = 8.dp)
                                .width(2.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(RenzoColors.Primary),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Icon(
                            s.icon, contentDescription = null,
                            tint = if (active) RenzoColors.Primary else RenzoColors.MutedForeground,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            s.label,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (active) RenzoColors.Primary else RenzoColors.MutedForeground,
                            modifier = Modifier.padding(start = 12.dp).weight(1f),
                        )
                        if (s == Section.Queue) {
                            if (metrics.downloads > 0) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 8.dp)
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(RenzoColors.Primary),
                                )
                            }
                            val badge = metrics.downloads + metrics.failed
                            if (badge > 0) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(50))
                                        .background(RenzoColors.Primary)
                                        .padding(horizontal = 7.dp, vertical = 2.dp),
                                ) {
                                    Text(
                                        if (badge > 99) "99+" else badge.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = RenzoColors.PrimaryForeground,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // "Switch to Renzo" lives here under the hamburger, mirroring where the
        // Renzo half keeps its own switch (NavDrawer.kt) — hopping halves is
        // navigation, not an account action, so it is NOT in the account panel.
        HorizontalDivider(color = RenzoColors.Border)
        Box(modifier = Modifier.padding(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSwitchApp)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Filled.SwapHoriz,
                    contentDescription = null,
                    tint = RenzoColors.MutedForeground,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    "Switch to Renzo",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RenzoColors.MutedForeground,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        // Drawer footer — view-mode pill, download status, project links.
        HorizontalDivider(color = RenzoColors.Border)
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OnlineOfflinePill(offline = offline, onToggle = onToggleOffline)
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, RenzoColors.Border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .background(RenzoColors.Muted.copy(alpha = 0.3f))
                    .padding(vertical = 8.dp),
            ) {
                StatChip(Icons.Filled.Download, metrics.downloads.toString(), RenzoColors.Blue)
                StatChip(Icons.Filled.Schedule, metrics.queued.toString(), RenzoColors.Yellow)
                StatChip(Icons.Filled.Warning, metrics.failed.toString(), RenzoColors.Red)
            }
            ExternalLinksRow()
        }
    }
}

@Composable
private fun StatChip(icon: ImageVector, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** external-links.tsx — GitHub / Discord / Website, verbatim hrefs. */
@Composable
private fun ExternalLinksRow() {
    val links = listOf(
        Triple("GitHub", "https://github.com/Levitate0/Renzo", Res.drawable.ic_github),
        Triple("Discord", "https://discord.gg/AvhtPPV8", Res.drawable.ic_discord),
        Triple("Website", "https://www.renzo.net", Res.drawable.ic_globe),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        links.forEach { (name, href, res) ->
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .dpadClickable(radius = 6.dp) {
                        top.levitatemedia.renzo.hub.core.HubPlatform.openExternal(href)
                    },
            ) {
                Icon(
                    painterResource(res), contentDescription = name,
                    tint = RenzoColors.MutedForeground, modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/* ─── Account panel (web: UserAvatarDropdown content) ───────────────────── */

@Composable
private fun UserAvatar(
    user: UserDto,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    /** False for the decorative copy in the account panel header — a D-pad stop
     *  that does nothing is worse than no stop at all. */
    interactive: Boolean = true,
    onClick: () -> Unit,
) {
    val avatar: Painter? = remember(user.avatarBase64) {
        user.avatarBase64?.takeIf { it.isNotBlank() }?.let { b64 ->
            runCatching {
                val bytes = java.util.Base64.getMimeDecoder().decode(b64.trim())
                val bmp = top.levitatemedia.renzo.hub.core.decodeImageBytes(bytes)!!
                androidx.compose.ui.graphics.painter.BitmapPainter(bmp)
            }.getOrNull()
        }
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(RenzoColors.Primary.copy(alpha = 0.20f))
            .border(1.dp, RenzoColors.Primary.copy(alpha = 0.30f), CircleShape)
            .then(
                if (interactive) {
                    Modifier.dpadClickable(radius = size / 2, fill = null, onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        if (avatar != null) {
            Image(
                painter = avatar,
                contentDescription = user.username,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                user.username.take(2).uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = RenzoColors.Primary,
            )
        }
    }
}

@Composable
private fun BoxScope.AccountPanel(
    visible: Boolean,
    user: UserDto,
    externalDomain: String,
    importFolderConfigured: Boolean,
    onDismiss: () -> Unit,
    onAction: (AccountAction) -> Unit,
) {
    val isTv = LocalIsTv.current
    val scrimInteraction = remember { MutableInteractionSource() }

    // Back closes the panel rather than the app — and on TV it is the only way
    // out, because the scrim below is deliberately not a focus stop.
    HubBackHandler(enabled = visible) { onDismiss() }

    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .then(
                    // A full-screen tap-to-dismiss scrim is a focusable element
                    // the size of the screen; on a D-pad it swallows the cursor.
                    if (isTv) {
                        Modifier
                    } else {
                        Modifier.clickable(
                            interactionSource = scrimInteraction,
                            indication = null,
                            onClick = onDismiss,
                        )
                    },
                ),
        )
    }
    AnimatedVisibility(
        visible = visible,
        enter = slideInHorizontally(initialOffsetX = { it }),
        exit = slideOutHorizontally(targetOffsetX = { it }),
        modifier = Modifier.align(Alignment.CenterEnd),
    ) {
        Column(
            modifier = Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(RenzoColors.Popover)
                .statusBarsPadding()
                // Keeps D-pad traversal inside the panel rather than wandering
                // back into the chrome behind it; Back is the way out.
                .focusGroup()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
            ) {
                UserAvatar(user = user, size = 28.dp, interactive = false, onClick = {})
                Text(
                    "Account",
                    style = MaterialTheme.typography.titleSmall,
                    color = RenzoColors.Foreground,
                    modifier = Modifier.padding(start = 10.dp).weight(1f),
                )
                DpadIconButton(
                    icon = Icons.Filled.Close,
                    contentDescription = "Close",
                    tint = RenzoColors.MutedForeground,
                    iconModifier = Modifier.border(1.dp, RenzoColors.Border, CircleShape).padding(6.dp),
                    onClick = onDismiss,
                )
            }
            HorizontalDivider(color = RenzoColors.Border)
            AccountMenuBody(
                user = user,
                externalDomain = externalDomain,
                importFolderConfigured = importFolderConfigured,
                onAction = onAction,
            )
        }
    }
}

/**
 * The account menu's contents — user-menu.tsx transliterated. One body, two
 * containers: the RIGHT-slide sheet on touch/TV, and the web's anchored
 * dropdown under the avatar on a wide desktop window.
 */
@Composable
private fun AccountMenuBody(
    user: UserDto,
    externalDomain: String,
    importFolderConfigured: Boolean,
    onAction: (AccountAction) -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val hideAdult = rememberHideAdult()
    val isTv = LocalIsTv.current
    var copied by remember { mutableStateOf(false) }
    var importPickerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (copied) { delay(2000); copied = false }
    }

    val domain = externalDomain.ifBlank { "http://localhost:9833" }
    val fullOpdsUrl = "${domain.trimEnd('/')}/${user.opdsPath}"

    Column(modifier = Modifier.fillMaxWidth()) {

            // Username + role badge (LEVEL_LABEL / LEVEL_BADGE).
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    user.username,
                    style = MaterialTheme.typography.bodyMedium,
                    color = RenzoColors.Foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                val (roleLabel, roleColor) = when (user.level) {
                    UserLevel.OWNER -> "Owner" to RenzoColors.Primary
                    UserLevel.ADMIN -> "Admin" to Color(0xFFFCD34D)
                    UserLevel.MANAGER -> "Manager" to Color(0xFFD8B4FE)
                    else -> "User" to Color(0xFF93C5FD)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(roleColor.copy(alpha = 0.15f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Icon(
                        Icons.Filled.MilitaryTech, contentDescription = null,
                        tint = roleColor, modifier = Modifier.size(12.dp),
                    )
                    Text(
                        roleLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = roleColor,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            HorizontalDivider(color = RenzoColors.Border)

            // OPDS path + copy-the-full-URL button.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Icon(
                    Icons.Filled.Route, contentDescription = null,
                    tint = RenzoColors.MutedForeground, modifier = Modifier.size(16.dp),
                )
                Text(
                    user.opdsPath,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = RenzoColors.MutedForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 8.dp).weight(1f),
                )
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .dpadClickable(radius = 6.dp) {
                            clipboard.setText(AnnotatedString(fullOpdsUrl))
                            copied = true
                        },
                ) {
                    Icon(
                        if (copied) Icons.Filled.Check else Icons.Filled.ContentCopy,
                        contentDescription = "Copy OPDS URL",
                        tint = if (copied) RenzoColors.Green else RenzoColors.MutedForeground,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            HorizontalDivider(color = RenzoColors.Border)

            // Configuration is deliberately not ported to the remote: 67 clicks
            // and 16 text fields across the settings area, plus the import
            // wizard. Rather than a column of rows that each dead-end (or, worse,
            // a column of disabled rows that invite repeated pressing), TV shows
            // ONE row that leads to the instance's own web address.
            if (isTv) {
                MenuRow(Icons.Filled.Settings, "Settings, sources & import…") {
                    onAction(AccountAction.ServerSettings)
                }
            } else {
                MenuRow(Icons.Filled.Edit, "Edit...") { onAction(AccountAction.EditProfile) }
                if (user.hasPassword) {
                    MenuRow(Icons.Filled.VpnKey, "Change password...") { onAction(AccountAction.ChangePassword) }
                }
                MenuRow(Icons.Filled.Sensors, "Trackers...") { onAction(AccountAction.Trackers) }
                // Web: FolderInput — Download here collided with Import Series.
                MenuRow(Icons.Filled.DriveFolderUpload, "Import Suwayomi Backup...") { onAction(AccountAction.ImportBackup) }

                HorizontalDivider(color = RenzoColors.Border)

                if (user.level >= UserLevel.ADMIN) {
                    MenuRow(Icons.Filled.People, "Users") { onAction(AccountAction.Users) }
                }
                MenuRow(Icons.Filled.VpnKey, "Account") { onAction(AccountAction.Account) }
                if (user.level >= UserLevel.OWNER) {
                    MenuRow(Icons.Filled.Settings, "Settings") { onAction(AccountAction.ServerSettings) }
                }
                if (user.level >= UserLevel.MANAGER) {
                    MenuRow(Icons.Filled.Download, "Import Series") { importPickerOpen = true }
                    HorizontalDivider(color = RenzoColors.Border)
                }

                MenuRow(Icons.Filled.Palette, "Appearance") { onAction(AccountAction.Appearance) }
            }
            // Web: Compass — Route here duplicated the OPDS-path row's icon.
            MenuRow(Icons.Filled.Explore, "Take a tour") { onAction(AccountAction.Tour) }
            MenuRow(
                if (hideAdult.hidden.value) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                "Adult (18+): ${if (hideAdult.hidden.value) "Hidden" else "Shown"}",
            ) { hideAdult.toggle() }

            HorizontalDivider(color = RenzoColors.Border)
            // "Switch to Renzo" is in the nav drawer/rail, not here — the
            // account panel matches the web's user-menu.tsx contents.
            MenuRow(Icons.AutoMirrored.Filled.Logout, "Sign out") { onAction(AccountAction.SignOut) }
            HorizontalDivider(color = RenzoColors.Border)
            Box(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
                ExternalLinksRow()
            }
    }

    if (importPickerOpen) {
        ImportSeriesPicker(
            importFolderConfigured = importFolderConfigured,
            onDismiss = { importPickerOpen = false },
            onPick = { titleOnly ->
                importPickerOpen = false
                onAction(AccountAction.ImportSeries(titleOnly))
            },
        )
    }
}

/**
 * The web's SectionPills (section-pills.tsx), desktop ≥lg only: rounded-full
 * 32dp pills, active = filled primary, Queue carries the live dot + count.
 */
@Composable
private fun SectionPillsRow(
    /** null = no active pill (a non-section route like settings). */
    current: Section?,
    metrics: DownloadsMetricsDto,
    onSelect: (Section) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        // Web: overflow-x-auto scrollbar-hide — when CenterClampedBar squeezes
        // the pills they scroll rather than clip or underlap the edge clusters.
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        Section.entries.forEach { s ->
            val active = s == current
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) RenzoColors.Primary else Color.Transparent)
                    .clickable { onSelect(s) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            ) {
                Icon(
                    s.icon, contentDescription = null,
                    tint = if (active) RenzoColors.PrimaryForeground else RenzoColors.MutedForeground,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    s.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (active) RenzoColors.PrimaryForeground else RenzoColors.MutedForeground,
                    maxLines = 1,
                    modifier = Modifier.padding(start = 6.dp),
                )
                if (s == Section.Queue) {
                    if (metrics.downloads > 0) {
                        Box(
                            Modifier
                                .padding(start = 4.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (active) RenzoColors.PrimaryForeground else RenzoColors.Primary),
                        )
                    }
                    val badge = metrics.downloads + metrics.failed
                    if (badge > 0) {
                        Text(
                            if (badge > 99) "99+" else badge.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) RenzoColors.PrimaryForeground else RenzoColors.Primary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The persistent wide command bar — the web's CommandBar, which every route
 * except the reader keeps (series pages, settings, account…). Rendered by the
 * nav host ABOVE the NavHost; self-contained: polls its own queue metrics and
 * shell settings so it works identically on every route.
 *
 * [activeSection] is null on routes that aren't a home section (settings,
 * account…) — the web highlights no pill there; series pages pass Library.
 */
@Composable
fun ShioriCommandBar(
    user: UserDto,
    activeSection: Section?,
    onSelectSection: (Section) -> Unit,
    libraryVm: LibraryViewModel,
    onAccountAction: (AccountAction) -> Unit,
    /** The search field + Online pill only mean something on the home sections. */
    showSearch: Boolean,
) {
    val app = ShioriRuntime.app
    var menuOpen by remember { mutableStateOf(false) }
    val libraryState by libraryVm.state.collectAsState()

    var metrics by remember { mutableStateOf(DownloadsMetricsDto()) }
    LaunchedEffect(Unit) {
        while (true) {
            runCatching { app.network.currentApi()?.downloadMetrics() }
                .getOrNull()?.let { metrics = it }
            delay(10_000)
        }
    }
    var externalDomain by remember { mutableStateOf("") }
    var importFolder by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        runCatching { app.network.currentApi()?.shellSettings() }.getOrNull()?.let {
            externalDomain = it.externalDomain
            importFolder = it.importFolder
        }
    }

    Column {
        CenterClampedBar(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 6.dp),
            left = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(Res.drawable.splash_icon),
                        contentDescription = "Renzo Shiori home",
                        modifier = Modifier.size(28.dp),
                    )
                    Text(
                        "Renzo Shiori",
                        style = MaterialTheme.typography.titleSmall,
                        color = RenzoColors.Foreground,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            },
            center = {
                SectionPillsRow(activeSection, metrics) { s -> onSelectSection(s) }
            },
            right = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showSearch) {
                        DesktopSearchField(
                            value = libraryState.searchTerm,
                            onValueChange = libraryVm::setSearch,
                            placeholder = searchPlaceholder(activeSection ?: Section.Library),
                        )
                        ShellOnlineOfflinePill(
                            offline = libraryState.offlineMode,
                            onToggle = { libraryVm.setOfflineMode(!libraryState.offlineMode) },
                        )
                    }
                    DownloadStatusBar(metrics) { onSelectSection(Section.Queue) }
                    Box {
                        UserAvatar(
                            user = user,
                            size = 32.dp,
                            modifier = Modifier.padding(start = 6.dp, end = 4.dp).tourAnchor(TourAnchors.ACCOUNT),
                            onClick = { menuOpen = true },
                        )
                        // Web ≥lg: the account menu is an anchored dropdown,
                        // not the mobile right-slide sheet.
                        AccountDropdown(
                            expanded = menuOpen,
                            user = user,
                            externalDomain = externalDomain,
                            importFolderConfigured = importFolder.isNotBlank(),
                            onDismiss = { menuOpen = false },
                            onAction = { action ->
                                menuOpen = false
                                onAccountAction(action)
                            },
                        )
                    }
                }
            },
        )
        HorizontalDivider(color = RenzoColors.Border.copy(alpha = 0.6f))
    }
}

/**
 * The wide command bar's skeleton: [left] hugs the start, [right] hugs the
 * end, and [center] (the section pills) sits at the bar's TRUE horizontal
 * centre — capped at 60% of the bar (web max-w-[60vw]) and at the free span
 * between the edge clusters. When even a full-span centred block would
 * underlap an edge cluster, the pills give up exact centring rather than
 * overlap: they slide just far enough to stay clear, scrolling internally.
 */
@Composable
private fun CenterClampedBar(
    left: @Composable () -> Unit,
    center: @Composable () -> Unit,
    right: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    Layout(
        content = {
            Box { left() }
            Box { center() }
            Box { right() }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val loose = Constraints(maxHeight = height)
        val leftBar = measurables[0].measure(loose)
        val rightBar = measurables[2].measure(loose)
        // Web gap-3: the minimum air between the pills and either cluster.
        val gap = 12.dp.roundToPx()
        val free = (width - leftBar.width - rightBar.width - 2 * gap).coerceAtLeast(0)
        val centerMax = minOf((width * 0.6f).roundToInt(), free)
        val pills = measurables[1].measure(Constraints(maxWidth = centerMax, maxHeight = height))
        val minX = leftBar.width + gap
        val maxX = width - rightBar.width - gap - pills.width
        val pillsX = ((width - pills.width) / 2).coerceIn(minX, maxOf(minX, maxX))
        layout(width, height) {
            leftBar.placeRelative(0, (height - leftBar.height) / 2)
            rightBar.placeRelative(width - rightBar.width, (height - rightBar.height) / 2)
            pills.placeRelative(pillsX, (height - pills.height) / 2)
        }
    }
}

/** The web's always-visible desktop search input (w-56, h-9, muted well). */
@Composable
private fun DesktopSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = RenzoColors.Foreground),
        cursorBrush = SolidColor(RenzoColors.Foreground),
        decorationBox = { inner ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .width(224.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(RenzoColors.Muted.copy(alpha = 0.5f))
                    .border(1.dp, RenzoColors.Border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp),
            ) {
                Icon(
                    Icons.Filled.Search, contentDescription = null,
                    tint = RenzoColors.MutedForeground, modifier = Modifier.size(16.dp),
                )
                Box(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                    if (value.isEmpty()) {
                        Text(
                            placeholder,
                            style = MaterialTheme.typography.bodyMedium,
                            color = RenzoColors.MutedForeground,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Clear search",
                        tint = RenzoColors.MutedForeground,
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .clickable { onValueChange("") },
                    )
                } else {
                    // The web's ⌘K hint chip (xl:inline-flex kbd).
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(RenzoColors.Muted.copy(alpha = 0.6f))
                            .border(1.dp, RenzoColors.Border.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            "⌘K",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = RenzoColors.MutedForeground,
                        )
                    }
                }
            }
        },
    )
}

/**
 * download-status.tsx `variant="bar"`: the at-a-glance active/queued/failed
 * counters, one click from the queue.
 */
@Composable
private fun DownloadStatusBar(metrics: DownloadsMetricsDto, onOpenQueue: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpenQueue)
            .padding(horizontal = 8.dp, vertical = 8.dp),
    ) {
        StatChip(Icons.Filled.Download, metrics.downloads.toString(), RenzoColors.Blue)
        StatChip(Icons.Filled.Schedule, metrics.queued.toString(), RenzoColors.Yellow)
        StatChip(Icons.Filled.Warning, metrics.failed.toString(), RenzoColors.Red)
    }
}

/**
 * The web's UserAvatarDropdown: the account menu anchored under the avatar on
 * a wide desktop window, instead of the mobile right-slide sheet.
 */
@Composable
private fun AccountDropdown(
    expanded: Boolean,
    user: UserDto,
    externalDomain: String,
    importFolderConfigured: Boolean,
    onDismiss: () -> Unit,
    onAction: (AccountAction) -> Unit,
) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier
            .width(300.dp)
            .background(RenzoColors.Popover)
            .heightIn(max = 620.dp),
    ) {
        AccountMenuBody(
            user = user,
            externalDomain = externalDomain,
            importFolderConfigured = importFolderConfigured,
            onAction = onAction,
        )
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .dpadClickable(radius = 8.dp, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
    ) {
        Icon(icon, contentDescription = null, tint = RenzoColors.Foreground, modifier = Modifier.size(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = RenzoColors.Foreground,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * The web's "Import Series" ResponsiveModal — two option cards. The
 * titles-only option is only offered when the server actually has an import
 * folder mounted (the web disables it; we hide it instead of dimming).
 */
@Composable
private fun ImportSeriesPicker(
    importFolderConfigured: Boolean,
    onDismiss: () -> Unit,
    onPick: (Boolean) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(RenzoColors.Popover)
                .border(1.dp, RenzoColors.Border, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Import Series", style = MaterialTheme.typography.titleMedium, color = RenzoColors.Foreground)
            Text(
                "Choose how to scan your library folder.",
                style = MaterialTheme.typography.bodySmall,
                color = RenzoColors.MutedForeground,
            )
            ImportOption(
                icon = Icons.Filled.Download,
                title = "Regular Import",
                description = "Scan the library folder for existing archives (CBZ/CBR).",
                onClick = { onPick(false) },
            )
            // Web (user-menu.tsx): always shown, disabled with an explanatory
            // subtitle when no import folder is mounted — hiding it taught
            // nobody that the feature exists.
            ImportOption(
                icon = Icons.Filled.Download,
                title = "Import Titles Only (e.g. from Suwayomi)",
                description = if (importFolderConfigured) {
                    "Register bare titles from a folder with no archives yet " +
                        "(e.g. loose-image chapters), then auto-match them online."
                } else {
                    "Not configured — no import folder is mounted."
                },
                enabled = importFolderConfigured,
                onClick = { onPick(true) },
            )
        }
    }
}

@Composable
private fun ImportOption(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
            .then(
                if (enabled) {
                    Modifier.dpadClickable(radius = 8.dp, fill = null, onClick = onClick)
                } else {
                    Modifier.alpha(0.5f)
                },
            )
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = RenzoColors.Foreground, modifier = Modifier.size(16.dp))
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = RenzoColors.Foreground,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(description, style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
    }
}
