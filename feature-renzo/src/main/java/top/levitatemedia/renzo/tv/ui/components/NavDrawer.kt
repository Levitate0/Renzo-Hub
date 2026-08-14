package top.levitatemedia.renzo.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import top.levitatemedia.renzo.tv.R
import top.levitatemedia.renzo.tv.Tab
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

private val TAB_ICONS: Map<Tab, ImageVector> = mapOf(
    Tab.Discover to Icons.Outlined.AutoAwesome,
    Tab.Library to Icons.Outlined.VideoLibrary,
    Tab.Updates to Icons.Outlined.Notifications,
    Tab.History to Icons.Outlined.History,
    Tab.Downloads to Icons.Outlined.CloudDownload,
    Tab.Search to Icons.Outlined.Search,
)

/**
 * The hamburger drawer — the web app's nav drawer, natively: left panel with
 * the banner, the tab list, and an Account row. Works with both touch (tap
 * the scrim to close) and D-pad (focus starts on the active tab; Back closes).
 */
@Composable
fun NavDrawer(
    active: Tab,
    username: String?,
    onTab: (Tab) -> Unit,
    onAccount: () -> Unit,
    onClose: () -> Unit,
    updatesBadge: Int = 0,
    downloadsBadge: Int = 0,
    onSwitchApp: (() -> Unit)? = null,
) {
    BackHandler(enabled = true) { onClose() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        // Scrim — tap/click anywhere outside the panel closes.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                .clickable(onClick = onClose),
        )
        Column(
            Modifier
                .width(300.dp)
                .fillMaxHeight()
                .background(RenzoColors.Background)
                .border(1.dp, RenzoColors.Border)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(12.dp),
        ) {
            // Header: small logo left, X close right (web drawer ground truth).
            Row(
                Modifier.fillMaxWidth().padding(start = 8.dp, top = 8.dp, bottom = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.renzo_wordmark),
                    contentDescription = "Renzo",
                    modifier = Modifier.height(28.dp),
                )
                Spacer(Modifier.weight(1f))
                var closeFocused by remember { mutableStateOf(false) }
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .focusRing(closeFocused, 999.dp)
                        .background(if (closeFocused) RenzoColors.Secondary else Color.Transparent, CircleShape)
                        .tvClickable(onFocused = { closeFocused = it }, onClick = onClose),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = RenzoColors.MutedForeground, fontSize = 15.sp)
                }
            }
            Tab.BAR_TABS.forEach { tab ->
                DrawerItem(
                    label = tab.label,
                    icon = TAB_ICONS[tab],
                    selected = tab == active,
                    badge = when (tab) {
                        Tab.Updates -> updatesBadge
                        Tab.Downloads -> downloadsBadge
                        else -> 0
                    },
                    modifier = if (tab == active) Modifier.focusRequester(firstFocus) else Modifier,
                    onClick = { onTab(tab); onClose() },
                )
            }

            // Hub: hop to the other half. Null on TV, where the manga reader is
            // deliberately unreachable, so nothing renders there.
            onSwitchApp?.let { switch ->
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(RenzoColors.Border))
                Spacer(Modifier.height(8.dp))
                DrawerItem(
                    label = "Switch to Renzo Shiori",
                    icon = Icons.Outlined.SwapHoriz,
                    selected = false,
                    onClick = { onClose(); switch() },
                )
            }
        }
    }
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: Int = 0,
) {
    var focused by remember { mutableStateOf(false) }
    // Web drawer ground truth: NO filled pill — the active item is rose text +
    // rose icon on a transparent row; hover/focus gets a faint surface.
    val fg = when {
        selected -> RenzoColors.Primary
        focused -> RenzoColors.Foreground
        else -> RenzoColors.MutedForeground
    }
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .focusRing(focused, 10.dp)
            .background(if (focused) RenzoColors.Secondary else Color.Transparent, RoundedCornerShape(10.dp))
            .tvClickable(onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(14.dp))
        }
        Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        // Rose filled count badge at the row END (web: Updates "1").
        if (badge > 0) {
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(RenzoColors.Primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (badge > 99) "99" else "$badge",
                    color = RenzoColors.PrimaryForeground,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}
