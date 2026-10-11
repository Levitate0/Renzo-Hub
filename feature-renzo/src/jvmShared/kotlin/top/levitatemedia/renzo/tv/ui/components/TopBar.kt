package top.levitatemedia.renzo.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import top.levitatemedia.renzo.tv.resources.Res
import top.levitatemedia.renzo.tv.resources.renzo_mark
import androidx.compose.material3.MaterialTheme
import top.levitatemedia.renzo.hub.core.CenterClampedBar
import top.levitatemedia.renzo.tv.Tab
import top.levitatemedia.renzo.tv.api.PublicUser
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/**
 * The topbar, mirroring the web exactly: [hamburger] [banner] [spacer]
 * [pill tabs on wide screens] [spacer] [search box] [avatar]. 56dp tall.
 * The hamburger is ALWAYS there (it opens the NavDrawer); pills appear only
 * when the window is wide enough (tablets landscape / TV), like the web's lg
 * break — narrow phones get hamburger + search + avatar, same as web mobile.
 */
@Composable
fun TopBar(
    app: top.levitatemedia.renzo.tv.AppServices,
    active: Tab?,
    onTab: (Tab) -> Unit,
    onAccount: () -> Unit,
    onMenu: () -> Unit,
    onSearch: (String) -> Unit,
    user: PublicUser?,
    updatesBadge: Int = 0,
    downloadsBadge: Int = 0,
    /** Inset for the bar's CONTENT; the bar itself always spans the screen
     *  (TV overscan lives here, not on the bar). */
    horizontalPadding: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    // Shiori's bar divider: the border colour at 60%.
    val borderColor = RenzoColors.Border.copy(alpha = 0.6f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Raw screen width, NOT the measured width: TV overscan padding was
        // pushing this under the breakpoint and hiding the tab pills.
        val wide = top.levitatemedia.renzo.tv.ui.theme.logicalScreenSize().first >= 800 &&
            maxWidth >= 700.dp
        // Desktop matches the web: the bar IS the nav, no hamburger while the
        // pills are visible. TV keeps the HAMBURGER instead (user direction
        // 2026-08-21, second pass): a full pill row was too tight on a 1080p
        // panel, so the sections live in the drawer there and the bar keeps
        // just brand + search + status + avatar.
        val showHamburger = !(top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop && wide)
        val barModifier = Modifier
            .fillMaxWidth()
            // TV: the bar rides the panel-resolution chrome scale (full-size
            // on 4K, slimmer on 1080p), floored so the 36dp search box fits.
            .height(
                if (app.isTv) {
                    (56 * top.levitatemedia.renzo.hub.core.tvChromeScale()).dp.coerceAtLeast(44.dp)
                } else {
                    56.dp
                },
            )
            .background(RenzoColors.Background)
            // A hairline under the bar so page content never reads as
            // overlapping it on a short/small screen (web: topbar border-b).
            .drawBehind {
                drawRect(
                    color = borderColor,
                    topLeft = Offset(0f, size.height - 1.dp.toPx()),
                    size = Size(size.width, 1.dp.toPx()),
                )
            }
            .padding(horizontal = horizontalPadding)
        if (wide) {
            // Shiori's bar skeleton, shared from :core for exactly this: the
            // pills sit at the TRUE centre of the window, clamped between the
            // two clusters and capped at 60% width, scrolling instead of
            // underlapping either side when squeezed. The in-flow twin-spacer
            // centring used here before drifted off-centre whenever the right
            // cluster was wider than the brand, so the two halves of the Hub
            // never lined up.
            CenterClampedBar(
                modifier = barModifier,
                left = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showHamburger) {
                            HamburgerButton(onClick = onMenu)
                            Spacer(Modifier.width(10.dp))
                        }
                        Brand()
                    }
                },
                center = {
                    if (!app.isTv) {
                        Row(
                            Modifier
                                .horizontalScroll(rememberScrollState())
                                // One D-pad region — matches the Shiori bar.
                                .focusGroup(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Tab.BAR_TABS.forEach { tab ->
                                TabPill(
                                    label = tab.label,
                                    icon = tabIcon(tab),
                                    active = tab == active,
                                    badge = when (tab) {
                                        Tab.Updates -> updatesBadge
                                        Tab.Downloads -> downloadsBadge
                                        else -> 0
                                    },
                                ) { onTab(tab) }
                            }
                        }
                    }
                },
                right = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        // One D-pad region — matches the Shiori bar.
                        modifier = Modifier.focusGroup(),
                    ) {
                        SearchBox(onSearch = onSearch, modifier = Modifier.width(224.dp))
                        if (!app.isTv) {
                            Spacer(Modifier.width(8.dp))
                            TypeSelect(app)
                        }
                        Spacer(Modifier.width(8.dp))
                        OnlinePill()
                        // Service-status line (HANDOFFrenzohub_topbarstatusline.md):
                        // display-only chrome — hidden on TV, and below the width
                        // where this cluster still fits beside the centred tabs.
                        // It sits where Shiori shows its download counters.
                        if (!app.isTv &&
                            top.levitatemedia.renzo.tv.ui.theme.logicalScreenSize().first >= 1280
                        ) {
                            Spacer(Modifier.width(4.dp))
                            StatusLine(app)
                        }
                        AvatarButton(
                            user = user,
                            onClick = onAccount,
                            modifier = Modifier.padding(start = 6.dp, end = 4.dp),
                        )
                    }
                },
            )
        } else {
            Row(barModifier, verticalAlignment = Alignment.CenterVertically) {
                if (showHamburger) {
                    HamburgerButton(onClick = onMenu)
                    Spacer(Modifier.width(10.dp))
                }
                Brand()
                Spacer(Modifier.width(8.dp))
                // Flexible: it gives up space so the pill and avatar keep theirs.
                SearchBox(onSearch = onSearch, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OnlinePill()
                AvatarButton(
                    user = user,
                    onClick = onAccount,
                    modifier = Modifier.padding(start = 6.dp, end = 4.dp),
                )
            }
        }
    }
}

/**
 * Shiori's bar brand, metric for metric: the square mark at 28dp and the app
 * name beside it in titleSmall. This used to be the full wordmark squeezed to
 * 28dp tall, at which size its own "Renzo" lettering was a smudge.
 */
@Composable
private fun Brand() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Image(
            painter = painterResource(Res.drawable.renzo_mark),
            contentDescription = "Renzo home",
            modifier = Modifier.size(28.dp),
        )
        Text(
            "Renzo",
            style = MaterialTheme.typography.titleSmall,
            color = RenzoColors.Foreground,
            maxLines = 1,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

/** Web/Shiori parity: every nav pill leads with its section icon. */
private fun tabIcon(tab: Tab) = when (tab) {
    Tab.Discover -> Icons.Filled.AutoAwesome
    Tab.Library -> Icons.Filled.VideoLibrary
    Tab.Updates -> Icons.Filled.Notifications
    Tab.History -> Icons.Filled.History
    Tab.Downloads -> Icons.Filled.Download
    Tab.Search -> Icons.Filled.Search
}

/** The web topbar's search input (search-box.tsx): rounded-full, card bg,
 *  leading icon, "Search anime…" placeholder, the ⌘K kbd chip, and LIVE
 *  search — 320ms debounce like the web's search context, with the keyboard
 *  Search action still submitting immediately. */
@Composable
private fun SearchBox(onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val isDesktop = top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    if (isDesktop) {
        // ⌘K / Ctrl-K from anywhere focuses this box (hub-desktop's window
        // key handler calls the registered requester).
        androidx.compose.runtime.DisposableEffect(Unit) {
            top.levitatemedia.renzo.hub.core.HubSearchFocus.requester = focusRequester
            onDispose {
                if (top.levitatemedia.renzo.hub.core.HubSearchFocus.requester === focusRequester) {
                    top.levitatemedia.renzo.hub.core.HubSearchFocus.requester = null
                }
            }
        }
    }
    var hadText by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(text) {
        val q = text.trim()
        if (q.isEmpty()) {
            // Erasing a previously-typed query exits search immediately
            // (web: active flips false and the browse rows return).
            if (hadText) {
                hadText = false
                onSearch("")
            }
            return@LaunchedEffect
        }
        hadText = true
        kotlinx.coroutines.delay(320)
        onSearch(q)
    }
    // Shiori's DesktopSearchField well: h-9, 8dp corners, muted fill at 50%,
    // border at 60%. The focus ring stays for TV, where the D-pad needs it.
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .focusRing(focused, 8.dp)
            .background(RenzoColors.Muted.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, RenzoColors.Border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            tint = RenzoColors.MutedForeground,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = text,
            onValueChange = { text = it },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = RenzoColors.Foreground),
            cursorBrush = SolidColor(RenzoColors.Foreground),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (text.isNotBlank()) onSearch(text.trim())
            }),
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text(
                            "Search anime…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = RenzoColors.MutedForeground,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
        if (isDesktop && text.isEmpty()) {
            Box(
                Modifier
                    .padding(start = 6.dp)
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
}

/** Web #searchType: All / Series / Movies, a rounded-full card pill. */
@Composable
private fun TypeSelect(app: top.levitatemedia.renzo.tv.AppServices) {
    var open by remember { mutableStateOf(false) }
    val label = when (app.searchType.value) {
        "series" -> "Series"
        "movie" -> "Movies"
        else -> "All"
    }
    Box {
        var focused by remember { mutableStateOf(false) }
        Row(
            Modifier
                // Shiori has no type filter; this takes the search field's
                // well so the right cluster reads as one family, not a mix of
                // pill and box shapes.
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .focusRing(focused, 8.dp)
                .background(RenzoColors.Muted.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .border(1.dp, RenzoColors.Border.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .tvClickable(onFocused = { focused = it }, onClick = { open = true })
                .padding(start = 10.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = RenzoColors.MutedForeground)
            Icon(
                Icons.Filled.ArrowDropDown,
                contentDescription = "Type",
                tint = RenzoColors.MutedForeground,
                modifier = Modifier.size(18.dp),
            )
        }
        androidx.compose.material3.DropdownMenu(
            expanded = open,
            onDismissRequest = { open = false },
            modifier = Modifier.background(RenzoColors.Popover),
        ) {
            listOf("" to "All", "series" to "Series", "movie" to "Movies").forEach { (v, l) ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(l, color = RenzoColors.Foreground, fontSize = 14.sp) },
                    onClick = {
                        app.searchType.value = v
                        open = false
                    },
                )
            }
        }
    }
}

/**
 * Web topbar's StatusLine: one line of small muted text showing the debrid
 * provider and the linked trackers, e.g. `RD ✓  ·  ⇄ AniList+MAL`.
 * Re-fetched on an interval (the web uses a TanStack query) so a fixed token
 * doesn't keep reading "RD ✗" until restart.
 */
@Composable
private fun StatusLine(app: top.levitatemedia.renzo.tv.AppServices) {
    var text by remember { mutableStateOf("…") }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            text = try {
                statusLineText(app.repo.health())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: top.levitatemedia.renzo.tv.api.ApiError) {
                // A dead session is NOT "offline" — that word sent people
                // hunting for a network problem (web source, verbatim).
                if (e.status == 401) "signed out" else "offline"
            } catch (_: Exception) {
                "offline"
            }
            kotlinx.coroutines.delay(60_000)
        }
    }
    Text(
        text,
        color = RenzoColors.MutedForeground,
        fontSize = 12.sp,
        maxLines = 1,
    )
}

/**
 * `/health` names the resolved provider in `.debrid` — reading only
 * `realdebrid` left an AllDebrid-only account permanently on "RD ✗" next to
 * working playback (confirmed audit finding on the old app).
 */
private fun statusLineText(h: top.levitatemedia.renzo.tv.api.HealthResponse): String {
    val debrid = if (h.debrid == "alldebrid") {
        if (h.alldebrid == "invalid" || h.alldebrid == "not-connected") "AD ✗" else "AD ✓"
    } else when (h.realdebrid) {
        "premium", "connected" -> "RD ✓"
        "not-premium" -> "RD ⚠ (free)"
        else -> "RD ✗"
    }
    val trackers = listOfNotNull(
        "AniList".takeIf { h.trackers.anilist },
        "MAL".takeIf { h.trackers.mal },
    ).joinToString("+")
    return listOfNotNull(debrid, trackers.takeIf { it.isNotEmpty() }?.let { "⇄ $it" })
        .joinToString("  ·  ")
}

/** Web topbar's mode pill: "● Online" — display-only (native is always online). */
@Composable
private fun OnlinePill() {
    // Shiori's OnlineOfflinePill in its online state: a faint foreground wash
    // rather than a solid secondary fill, emerald dot, 70% foreground label.
    Row(
        Modifier
            .padding(end = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(RenzoColors.Foreground.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(androidx.compose.ui.graphics.Color(0xFF10B981)))
        Spacer(Modifier.width(6.dp))
        Text(
            "Online",
            style = MaterialTheme.typography.labelMedium,
            color = RenzoColors.Foreground.copy(alpha = 0.7f),
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Web pill badge: 16dp round counter, primary-tinted; caps at 99+. */
@Composable
fun PillBadge(n: Int, onActivePill: Boolean) {
    if (n <= 0) return
    // Shiori's pill count: plain labelSmall text in the accent, no disc.
    Text(
        if (n > 99) "99+" else "$n",
        style = MaterialTheme.typography.labelSmall,
        color = if (onActivePill) RenzoColors.PrimaryForeground else RenzoColors.Primary,
        modifier = Modifier.padding(start = 4.dp),
    )
}

@Composable
private fun HamburgerButton(onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .focusRing(focused, 8.dp)
            .background(if (focused) RenzoColors.Secondary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
            .tvClickable(onFocused = { focused = it }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.Menu,
            contentDescription = "Open menu",
            tint = if (focused) RenzoColors.Foreground else RenzoColors.MutedForeground,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** Shiori's SectionPillsRow pill, metric for metric: 16dp leading icon,
 *  14sp label 6dp after it, 12/7 padding, rounded-full. */
@Composable
private fun TabPill(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector?,
    active: Boolean,
    badge: Int = 0,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val bg = when {
        active -> RenzoColors.Primary
        focused -> RenzoColors.Secondary
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val fg = when {
        active -> RenzoColors.PrimaryForeground
        focused -> RenzoColors.Foreground
        else -> RenzoColors.MutedForeground
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .focusRing(focused, 999.dp)
            .background(bg, RoundedCornerShape(999.dp))
            .tvClickable(onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            maxLines = 1,
            modifier = if (icon != null) Modifier.padding(start = 6.dp) else Modifier,
        )
        PillBadge(badge, onActivePill = active)
    }
}

@Composable
private fun AvatarButton(user: PublicUser?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    // Shiori's bar avatar: 32dp, accent ring at 30% over the 20% accent disc.
    Box(
        modifier
            .size(32.dp)                       // never shrink: the web avatar is shrink-0
            .clip(CircleShape)
            .focusRing(focused, 999.dp)
            .tvClickable(onFocused = { focused = it }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        UserAvatar(
            user,
            32.dp,
            ring = if (focused) RenzoColors.Primary else RenzoColors.Primary.copy(alpha = 0.3f),
        )
    }
}
