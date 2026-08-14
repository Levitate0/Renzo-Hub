package top.levitatemedia.renzo.tv.ui.components

import android.graphics.BitmapFactory
import android.util.Base64
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Image as ImageIcon
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.MilitaryTech
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VpnKey
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import top.levitatemedia.renzo.tv.api.PublicUser
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/** The user's avatar: their uploaded image when present, else initials. */
@Composable
fun UserAvatar(user: PublicUser?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    val bmp = remember(user?.avatarBase64) {
        user?.avatarBase64?.let {
            runCatching {
                val bytes = Base64.decode(it, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
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
 * The account menu, on Shiori's system: a FULL-HEIGHT SHEET pinned to the
 * RIGHT edge (not a dropdown card under the avatar), with a header row
 * (avatar + "Account" + circular ✕), an identity row carrying the role pill,
 * and icon-led rows in divider-separated groups. Scrim tap or Back closes it.
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
) {
    BackHandler(enabled = true) { onClose() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Shiori's sheet takes ~78% of a phone; capped so it stays a side panel
        // on tablets and TV instead of swallowing the screen.
        val sheetWidth = minOf(maxWidth * 0.78f, 400.dp)
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(onClick = onClose),
        )
        Column(
            Modifier
                .align(Alignment.CenterEnd)
                .width(sheetWidth)
                .fillMaxHeight()
                .background(RenzoColors.Popover)
                .border(1.dp, RenzoColors.Border)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState()),
        ) {
            // --- header: avatar + "Account" + circular close ------------------
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                UserAvatar(user, 40.dp)
                Spacer(Modifier.width(12.dp))
                Text(
                    "Account",
                    color = RenzoColors.Foreground,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                var closeFocused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .focusRing(closeFocused, 999.dp)
                        .background(if (closeFocused) RenzoColors.Secondary else Color.Transparent, CircleShape)
                        .border(1.dp, RenzoColors.Border, CircleShape)
                        .tvClickable(onFocused = { closeFocused = it }, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = RenzoColors.MutedForeground, fontSize = 17.sp)
                }
            }
            SheetDivider()

            // --- identity: username + role pill --------------------------------
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    user?.username ?: "…",
                    color = RenzoColors.Foreground,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                RolePill(user?.role ?: "user")
            }
            SheetDivider()

            // --- account actions ------------------------------------------------
            SheetItem("Edit avatar…", Icons.Outlined.ImageIcon) { onOpenSection("account"); onClose() }
            SheetItem("Change password…", Icons.Outlined.VpnKey) { onOpenSection("account"); onClose() }
            SheetDivider()

            // --- navigation -------------------------------------------------------
            if (user?.role == "owner" || user?.role == "manager") {
                SheetItem("Users", Icons.Outlined.People) { onOpenSection("users"); onClose() }
            }
            SheetItem("Account", Icons.Outlined.VpnKey) { onOpenSection("account"); onClose() }
            if (user?.role == "owner") {
                SheetItem("Settings", Icons.Outlined.Settings) { onOpenSection("settings"); onClose() }
            }
            SheetItem("Change server", Icons.Outlined.Dns) { onChangeServer(); onClose() }
            SheetDivider()

            // --- personalization ---------------------------------------------------
            SheetItem("Appearance", Icons.Outlined.Palette) { onOpenSection("appearance"); onClose() }
            SheetItem(
                "Show up to: " + contentLevel.replaceFirstChar { it.uppercase() },
                Icons.Outlined.Visibility,
                onClick = onCycleContentLevel,
            )
            SheetDivider()

            // Log out stays destructive — a deliberate Renzo choice.
            SheetItem("Log out", Icons.Outlined.Logout, destructive = true) { onLogout(); onClose() }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Role chip with Shiori's medal icon; owner = accent, manager = sky. */
@Composable
private fun RolePill(role: String) {
    val color = when (role) {
        "owner" -> RenzoColors.Primary
        "manager" -> RenzoColors.Sky400
        else -> RenzoColors.MutedForeground
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(Icons.Outlined.MilitaryTech, contentDescription = null, tint = color, modifier = Modifier.size(13.dp))
        Text(
            role.replaceFirstChar { it.uppercase() },
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SheetDivider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(RenzoColors.Border))
}

/** One icon-led sheet row (Shiori: leading icon, 16sp label, tall touch row). */
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
            .focusRing(focused, 0.dp)
            .background(if (focused) RenzoColors.Secondary else Color.Transparent)
            .tvClickable(onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (destructive) fg else RenzoColors.MutedForeground,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(label, color = fg, fontSize = 16.sp)
    }
}
