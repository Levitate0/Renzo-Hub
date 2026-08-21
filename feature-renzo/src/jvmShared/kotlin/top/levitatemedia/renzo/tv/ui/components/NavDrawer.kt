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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VideoLibrary
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
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import top.levitatemedia.renzo.tv.resources.Res
import top.levitatemedia.renzo.tv.resources.renzo_wordmark
import top.levitatemedia.renzo.tv.Tab
import top.levitatemedia.renzo.tv.RenzoBackHandler
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

// Filled, matching Shiori's drawer (its Section icons are all Icons.Filled).
private val TAB_ICONS: Map<Tab, ImageVector> = mapOf(
    Tab.Discover to Icons.Filled.AutoAwesome,
    Tab.Library to Icons.Filled.VideoLibrary,
    Tab.Updates to Icons.Filled.Notifications,
    Tab.History to Icons.Filled.History,
    Tab.Downloads to Icons.Filled.CloudDownload,
    Tab.Search to Icons.Filled.Search,
)

/**
 * The hamburger drawer, restyled to the Shiori half's NavDrawerContent
 * (HomeShell.kt) metric-for-metric: 288dp panel, 32dp banner header with a
 * circled Close icon, an 8dp-padded list of rounded-8 rows (icon 20dp, text
 * bodyMedium, 12/10 padding), active = primary-tinted row with a 2dp left
 * accent bar, count badges as small primary pills, and the app switch under
 * its own divider. Works with both touch (tap the scrim to close) and D-pad
 * (focus starts on the active tab; Back closes).
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
    RenzoBackHandler(enabled = true) { onClose() }
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }

    Box(Modifier.fillMaxSize()) {
        // Scrim — tap/click anywhere outside the panel closes.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0x99000000))
                // Never a D-pad target — a focusable scrim swallows the
                // cursor invisibly and centre closes the drawer.
                .focusProperties { canFocus = false }
                .clickable(onClick = onClose),
        )
        Column(
            Modifier
                .width(288.dp)
                .fillMaxHeight()
                .background(RenzoColors.Background)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            // Header: banner left, circled ✕ right (Shiori's drawer header).
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(Res.drawable.renzo_wordmark),
                    contentDescription = "Renzo",
                    modifier = Modifier.height(32.dp),
                )
                Spacer(Modifier.weight(1f))
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
            Box(Modifier.fillMaxWidth().height(1.dp).background(RenzoColors.Border))

            Column(
                Modifier.padding(8.dp).weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
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
            }

            // Hub: hop to the other half, under its own divider — the same
            // placement and row style as Shiori's "Switch to Renzo". Null on
            // TV builds without the manga half, so nothing renders there.
            onSwitchApp?.let { switch ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(RenzoColors.Border))
                Box(Modifier.padding(8.dp)) {
                    DrawerItem(
                        label = "Switch to Renzo Shiori",
                        icon = Icons.Filled.SwapHoriz,
                        selected = false,
                        onClick = { onClose(); switch() },
                    )
                }
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
    // Shiori's SectionList row: active = bg-primary/10 + primary content + a
    // 2px left accent bar; the D-pad cursor gets a Card fill + ring on its own
    // channel so selection stays readable wherever focus is.
    val fg = when {
        selected -> RenzoColors.Primary
        focused -> RenzoColors.Foreground
        else -> RenzoColors.MutedForeground
    }
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    selected -> RenzoColors.Primary.copy(alpha = 0.10f)
                    focused -> RenzoColors.Card
                    else -> Color.Transparent
                },
                RoundedCornerShape(8.dp),
            )
            .focusRing(focused, 8.dp)
            .tvClickable(onFocused = { focused = it }, onClick = onClick),
    ) {
        if (selected) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(vertical = 8.dp)
                    .width(2.dp)
                    .height(24.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(RenzoColors.Primary),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
            }
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = fg,
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            )
            // Primary count pill at the row END (Shiori's Queue badge).
            if (badge > 0) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(RenzoColors.Primary)
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                ) {
                    Text(
                        if (badge > 99) "99+" else "$badge",
                        style = MaterialTheme.typography.labelSmall,
                        color = RenzoColors.PrimaryForeground,
                    )
                }
            }
        }
    }
}
