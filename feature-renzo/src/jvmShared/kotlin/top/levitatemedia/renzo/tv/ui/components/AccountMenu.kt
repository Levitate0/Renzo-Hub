package top.levitatemedia.renzo.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.focus.focusProperties
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import top.levitatemedia.renzo.tv.api.PublicUser
import top.levitatemedia.renzo.tv.RenzoBackHandler
import top.levitatemedia.renzo.tv.resources.Res
import top.levitatemedia.renzo.tv.resources.ic_github
import top.levitatemedia.renzo.tv.resources.ic_globe
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/** The user's avatar: their uploaded image when present, else initials. */
@Composable
fun UserAvatar(user: PublicUser?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val bmp = remember(user?.avatarBase64) {
        user?.avatarBase64?.let {
            runCatching {
                val bytes = java.util.Base64.getMimeDecoder().decode(it)
                top.levitatemedia.renzo.hub.core.decodeImageBytes(bytes)
            }.getOrNull()
        }
    }
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            // Initials: accent letters on a dark accent disc (Shiori's style).
            .background(if (bmp != null) RenzoColors.Secondary else RenzoColors.Primary.copy(alpha = 0.22f), CircleShape)
            .border(1.dp, RenzoColors.Border, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (bmp != null) {
            Image(bmp, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(
                (user?.username?.take(2) ?: "?").uppercase(),
                color = RenzoColors.Primary,
                fontSize = (size.value * 0.36f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The account menu, matched metric-for-metric to the Shiori half's
 * AccountPanel (HomeShell.kt): a 300dp full-height sheet pinned to the RIGHT
 * edge on the Popover surface, header row (28dp avatar + "Account" titleSmall
 * + circled Close icon), an identity row (bodyMedium username + small role
 * pill), and MenuRow-style items — 16dp filled icon, bodyMedium label,
 * 14/11 padding — in divider-separated groups. Scrim tap or Back closes it.
 */
@Composable
fun AccountMenu(
    user: PublicUser?,
    contentLevel: String,
    onOpenSection: (String) -> Unit,   // account | users | settings | appearance | …
    onCycleContentLevel: () -> Unit,
    onLogout: () -> Unit,
    onChangeServer: () -> Unit,
    onClose: () -> Unit,
    /** Desktop only: with the hamburger gone at wide, the app switch lives
     *  here (the drawer keeps it everywhere the hamburger still shows). */
    onSwitchApp: (() -> Unit)? = null,
) {
    RenzoBackHandler(enabled = true) { onClose() }
    // Shiori's wide layout anchors this under the avatar as a dropdown; the
    // sheet remains the narrow/TV shape. Same items either way.
    val wide = top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop &&
        top.levitatemedia.renzo.tv.renzoScreenWidthDp() >= 800
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(if (wide) Color.Transparent else Color.Black.copy(alpha = 0.55f))
                // Never a D-pad target — see ScrimDialog: a focusable scrim
                // swallows the cursor invisibly and centre closes the sheet.
                .focusProperties { canFocus = false }
                .clickable(onClick = onClose),
        )
        if (wide) {
            // Dropdown card matching Shiori's AccountDropdown: 300dp wide,
            // pinned under the bar's avatar (top-right), full natural height —
            // deliberately never scrolls.
            Column(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 58.dp, end = 12.dp)
                    .width(300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
                    .background(RenzoColors.Popover),
            ) {
                IdentityRow(user)
                SheetDivider()
                MenuItems(
                    user, contentLevel, onOpenSection, onCycleContentLevel,
                    onLogout, onChangeServer, onClose, onSwitchApp,
                )
                SheetDivider()
                ProjectLinksRow()
            }
        } else {
            Column(
                Modifier
                    .align(Alignment.CenterEnd)
                    .width(300.dp)
                    .fillMaxHeight()
                    .background(RenzoColors.Popover)
                    .windowInsetsPadding(WindowInsets.safeDrawing),
            ) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                // --- header: avatar + "Account" + circled close --------------
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(user, 28.dp)
                    Text(
                        "Account",
                        style = MaterialTheme.typography.titleSmall,
                        color = RenzoColors.Foreground,
                        modifier = Modifier.padding(start = 10.dp).weight(1f),
                    )
                    var closeFocused by remember { mutableStateOf(false) }
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .focusRing(closeFocused, 999.dp)
                            .background(if (closeFocused) RenzoColors.Card else Color.Transparent, CircleShape)
                            .tvClickable(onFocused = { closeFocused = it }, onClick = onClose),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Close, contentDescription = "Close",
                            tint = RenzoColors.MutedForeground,
                            modifier = Modifier.border(1.dp, RenzoColors.Border, CircleShape).padding(6.dp),
                        )
                    }
                }
                SheetDivider()
                IdentityRow(user)
                SheetDivider()
                MenuItems(
                    user, contentLevel, onOpenSection, onCycleContentLevel,
                    onLogout, onChangeServer, onClose, onSwitchApp,
                    showLogout = false,
                )
                Spacer(Modifier.height(16.dp))
            }
            // Log out PINNED at the sheet's foot (user feedback 2026-08-21:
            // "hidden") — thirteen rows put it below the fold of the scroll,
            // and a button you have to know to scroll to may as well not
            // exist. Always on screen, first reachable with D-pad UP.
            SheetDivider()
            SheetItem("Log out", Icons.AutoMirrored.Filled.Logout, destructive = true) { onLogout(); onClose() }
            SheetDivider()
            ProjectLinksRow()
            Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * Project links, matching the web's new menu foot
 * (HANDOFFrenzohub_accountmenulinks.md): RENZO's own repo — not Shiori's —
 * and the renzo-apps site; deliberately no Discord. Inert on TV, where
 * there is no browser to open them in.
 */
@Composable
private fun ProjectLinksRow() {
    val links = listOf(
        Triple("GitHub", "https://github.com/Levitate0/Renzo", Res.drawable.ic_github),
        Triple("Website", "https://renzo-apps.levitatemedia.top", Res.drawable.ic_globe),
    )
    val inert = top.levitatemedia.renzo.hub.core.HubPlatform.isTv
    Row(
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        links.forEach { (name, href, res) ->
            var focused by remember { mutableStateOf(false) }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .then(
                        if (inert) {
                            Modifier
                        } else {
                            Modifier
                                .focusRing(focused, 6.dp)
                                .tvClickable(onFocused = { focused = it }) {
                                    top.levitatemedia.renzo.hub.core.HubPlatform.openExternal(href)
                                }
                        },
                    ),
            ) {
                Icon(
                    org.jetbrains.compose.resources.painterResource(res),
                    contentDescription = name,
                    tint = if (focused) RenzoColors.Foreground else RenzoColors.MutedForeground,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

/** Username + role pill. */
@Composable
private fun IdentityRow(user: PublicUser?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            user?.username ?: "…",
            style = MaterialTheme.typography.bodyMedium,
            color = RenzoColors.Foreground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        RolePill(user?.role ?: "user")
    }
}

/** The item list, shared by the dropdown (wide) and the sheet (narrow/TV). */
@Composable
private fun MenuItems(
    user: PublicUser?,
    contentLevel: String,
    onOpenSection: (String) -> Unit,
    onCycleContentLevel: () -> Unit,
    onLogout: () -> Unit,
    onChangeServer: () -> Unit,
    onClose: () -> Unit,
    onSwitchApp: (() -> Unit)?,
    /** false: the caller pins its own Log out (the sheet's sticky footer). */
    showLogout: Boolean = true,
) {
    // --- account actions ---------------------------------------------------
    SheetItem("Edit avatar…", Icons.Filled.ImageIcon) { onOpenSection("account"); onClose() }
    SheetItem("Change password…", Icons.Filled.VpnKey) { onOpenSection("account"); onClose() }
    SheetDivider()

    // --- navigation --------------------------------------------------------
    if (user?.role == "owner" || user?.role == "manager") {
        SheetItem("Users", Icons.Filled.People) { onOpenSection("users"); onClose() }
    }
    SheetItem("Account", Icons.Filled.VpnKey) { onOpenSection("account"); onClose() }
    if (user?.role == "owner") {
        SheetItem("Settings", Icons.Filled.Settings) { onOpenSection("settings"); onClose() }
    }
    SheetItem("Change server", Icons.Filled.Dns) { onChangeServer(); onClose() }
    SheetDivider()

    // --- personalization ---------------------------------------------------
    SheetItem("Appearance", Icons.Filled.Palette) { onOpenSection("appearance"); onClose() }
    SheetItem(
        "Show up to: " + contentLevel.replaceFirstChar { it.uppercase() },
        Icons.Filled.Visibility,
        onClick = onCycleContentLevel,
    )
    SheetDivider()

    // TV included (2026-08-21): the nav drawer that used to carry the app
    // switch is unreachable there now that the bar is the nav.
    if (onSwitchApp != null &&
        (top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop || top.levitatemedia.renzo.hub.core.HubPlatform.isTv)
    ) {
        SheetItem("Switch to Renzo Shiori", Icons.Filled.SwapHoriz) { onSwitchApp(); onClose() }
        SheetDivider()
    }

    // Log out stays destructive — a deliberate Renzo choice.
    if (showLogout) {
        SheetItem("Log out", Icons.AutoMirrored.Filled.Logout, destructive = true) { onLogout(); onClose() }
    }
}

/**
 * Role chip on Shiori's LEVEL_BADGE palette: owner = primary, manager =
 * purple, user = blue; medal icon 12dp, labelSmall text, 8/2 padding.
 */
@Composable
private fun RolePill(role: String) {
    val color = when (role) {
        "owner" -> RenzoColors.Primary
        "manager" -> Color(0xFFD8B4FE)
        else -> Color(0xFF93C5FD)
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(Icons.Filled.MilitaryTech, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Text(
            role.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun SheetDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(RenzoColors.Border))
}

/** Shiori's MenuRow: 16dp filled icon, bodyMedium label, 14/11 padding. */
@Composable
private fun SheetItem(
    label: String,
    icon: ImageVector,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }
    val fg = if (destructive) Color(0xFFFF6B6B) else RenzoColors.Foreground
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .focusRing(focused, 8.dp)
            .background(if (focused) RenzoColors.Card else Color.Transparent, RoundedCornerShape(8.dp))
            .tvClickable(onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = fg,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}
