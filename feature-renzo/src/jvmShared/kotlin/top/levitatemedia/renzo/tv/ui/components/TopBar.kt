package top.levitatemedia.renzo.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
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
import top.levitatemedia.renzo.tv.resources.renzo_wordmark
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
    active: Tab,
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
    val borderColor = RenzoColors.Border
    BoxWithConstraints(modifier.fillMaxWidth()) {
        // Raw screen width, NOT the measured width: TV overscan padding was
        // pushing this under the breakpoint and hiding the tab pills.
        val wide = top.levitatemedia.renzo.tv.ui.theme.logicalScreenSize().first >= 800 &&
            maxWidth >= 700.dp
        // Desktop matches the web: the bar IS the nav, so no hamburger while
        // the pills are visible. It comes back if the window shrinks below
        // the pill break (otherwise a narrow window would have no nav at all).
        val showHamburger = !(top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop && wide)
        val barModifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
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
            // Shiori's command-bar skeleton, same proportions: logo cluster
            // hugs the start, the pills sit at the bar's TRUE centre (clamped
            // so they never underlap the edge clusters, scrolling internally),
            // search + Online + avatar hug the end.
            top.levitatemedia.renzo.hub.core.CenterClampedBar(
                modifier = barModifier,
                left = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (showHamburger) {
                            HamburgerButton(onClick = onMenu)
                            Spacer(Modifier.width(10.dp))
                        }
                        Image(
                            painter = painterResource(Res.drawable.renzo_wordmark),
                            contentDescription = "Renzo",
                            modifier = Modifier.height(30.dp),
                        )
                    }
                },
                center = {
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
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
                },
                right = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SearchBox(onSearch = onSearch, modifier = Modifier.width(224.dp))
                        Spacer(Modifier.width(8.dp))
                        OnlinePill()
                        // Service-status line (HANDOFFrenzohub_topbarstatusline.md):
                        // display-only chrome — hidden on TV, and below the
                        // width where this cluster still fits beside the
                        // centred tabs (the bar's own 1800px analogue).
                        if (!app.isTv &&
                            top.levitatemedia.renzo.tv.ui.theme.logicalScreenSize().first >= 1280
                        ) {
                            Spacer(Modifier.width(8.dp))
                            StatusLine(app)
                        }
                        Spacer(Modifier.width(8.dp))
                        AvatarButton(user = user, onClick = onAccount)
                    }
                },
            )
        } else {
            Row(barModifier, verticalAlignment = Alignment.CenterVertically) {
                if (showHamburger) {
                    HamburgerButton(onClick = onMenu)
                    Spacer(Modifier.width(10.dp))
                }
                Image(
                    painter = painterResource(Res.drawable.renzo_wordmark),
                    contentDescription = "Renzo",
                    modifier = Modifier.height(30.dp),
                )
                Spacer(Modifier.width(8.dp))
                // Flexible: it gives up space so the pill and avatar keep theirs.
                SearchBox(onSearch = onSearch, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OnlinePill()
                Spacer(Modifier.width(8.dp))
                AvatarButton(user = user, onClick = onAccount)
            }
        }
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

/** The web topbar's search input: rounded-full, card bg, leading icon; submit
 *  via the keyboard's Search action. */
@Composable
private fun SearchBox(onSearch: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(999.dp))
            .focusRing(focused, 999.dp)
            .background(RenzoColors.Card, RoundedCornerShape(999.dp))
            .border(1.dp, if (focused) RenzoColors.Primary else RenzoColors.Border, RoundedCornerShape(999.dp))
            .padding(horizontal = 12.dp),
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
            textStyle = TextStyle(color = RenzoColors.Foreground, fontSize = 14.sp, fontFamily = top.levitatemedia.renzo.hub.core.GeistFamily),
            cursorBrush = SolidColor(RenzoColors.Primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                if (text.isNotBlank()) onSearch(text.trim())
            }),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (text.isEmpty()) {
                        Text("Search…", color = RenzoColors.MutedForeground, fontSize = 14.sp)
                    }
                    inner()
                }
            },
        )
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
    Row(
        Modifier
            .widthIn(min = 0.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(RenzoColors.Secondary.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(7.dp).clip(CircleShape).background(RenzoColors.DownloadedGreen, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text("Online", color = RenzoColors.Foreground, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

/** Web pill badge: 16dp round counter, primary-tinted; caps at 99+. */
@Composable
fun PillBadge(n: Int, onActivePill: Boolean) {
    if (n <= 0) return
    Box(
        Modifier
            .padding(start = 4.dp)
            .height(16.dp)
            .widthIn(min = 16.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (onActivePill) RenzoColors.PrimaryForeground.copy(alpha = 0.2f)
                else RenzoColors.Primary.copy(alpha = 0.15f),
            )
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (n > 99) "99+" else "$n",
            color = if (onActivePill) RenzoColors.PrimaryForeground else RenzoColors.Primary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
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
    icon: androidx.compose.ui.graphics.vector.ImageVector,
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
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(16.dp))
        Text(
            label,
            color = fg,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.padding(start = 6.dp),
        )
        PillBadge(badge, onActivePill = active)
    }
}

@Composable
private fun AvatarButton(user: PublicUser?, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .size(36.dp)                       // never shrink: the web avatar is shrink-0
            .clip(CircleShape)
            .focusRing(focused, 999.dp)
            .border(1.dp, if (focused) RenzoColors.Primary else RenzoColors.Border, CircleShape)
            .tvClickable(onFocused = { focused = it }, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        UserAvatar(user, 36.dp)
    }
}
