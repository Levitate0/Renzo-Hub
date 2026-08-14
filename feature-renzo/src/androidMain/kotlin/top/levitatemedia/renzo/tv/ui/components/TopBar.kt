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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.tv.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import top.levitatemedia.renzo.tv.R
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
        Row(
            Modifier
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
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HamburgerButton(onClick = onMenu)
            Spacer(Modifier.width(10.dp))
            Image(
                painter = painterResource(R.drawable.renzo_wordmark),
                contentDescription = "Renzo",
                modifier = Modifier.height(30.dp),
            )
            if (wide) {
                // The pills live in the FLEXIBLE slot and scroll if they don't
                // fit: fixed-width children (pills + search + pill + avatar)
                // used to add up past the screen and overflow off the right
                // edge on a TV once overscan padding was applied.
                Row(
                    Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Tab.BAR_TABS.forEach { tab ->
                        TabPill(
                            label = tab.label,
                            active = tab == active,
                            badge = when (tab) {
                                Tab.Updates -> updatesBadge
                                Tab.Downloads -> downloadsBadge
                                else -> 0
                            },
                        ) { onTab(tab) }
                    }
                }
                SearchBox(onSearch = onSearch, modifier = Modifier.width(168.dp))
            } else {
                Spacer(Modifier.width(8.dp))
                // Flexible: it gives up space so the pill and avatar keep theirs.
                SearchBox(onSearch = onSearch, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.width(8.dp))
            OnlinePill()
            Spacer(Modifier.width(8.dp))
            AvatarButton(user = user, onClick = onAccount)
        }
    }
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

@Composable
private fun TabPill(label: String, active: Boolean, badge: Int = 0, onClick: () -> Unit) {
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
            .padding(horizontal = 14.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = fg, fontSize = 14.sp, fontWeight = FontWeight.Medium)
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
