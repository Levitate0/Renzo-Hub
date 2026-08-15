@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material3.Icon
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.InviteItem
import top.levitatemedia.renzo.tv.api.PublicUser
import top.levitatemedia.renzo.tv.ui.components.PillButton
import top.levitatemedia.renzo.tv.ui.components.UserAvatar
import top.levitatemedia.renzo.tv.ui.components.focusRing
import top.levitatemedia.renzo.tv.ui.components.tvClickable
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

// ---------------------------------------------------------------------------
// /users/ and /settings/ — the staff/owner pages, built on the SAME shell as
// the Account page (page header with ✕, card sections, shared field/row bits)
// so the three settings-family screens look identical.
// ---------------------------------------------------------------------------

/** Shared settings-family page shell (web SettingsRouteShell). */
@Composable
private fun SettingsPage(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp)
            .widthIn(max = 720.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text(title, color = RenzoColors.Foreground, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    subtitle,
                    color = RenzoColors.MutedForeground,
                    // Shiori PageHeading subtitle: bodySmall (12sp).
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            var xFocused by remember { mutableStateOf(false) }
            // Shiori's close affordance: the Close glyph in a 1dp circle.
            Box(
                Modifier
                    .clip(CircleShape)
                    .focusRing(xFocused, 999.dp)
                    .background(if (xFocused) RenzoColors.Card else Color.Transparent, CircleShape)
                    .tvClickable(onFocused = { xFocused = it }, onClick = onClose),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Close, contentDescription = "Close",
                    tint = RenzoColors.MutedForeground,
                    modifier = Modifier.border(1.dp, RenzoColors.Border, CircleShape).padding(6.dp),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        content()
        Spacer(Modifier.height(32.dp))
    }
}

/** Card section (web PaneSection: rounded-xl border bg-card p-4/5). */
@Composable
private fun Section(title: String, sub: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RenzoColors.Card, RoundedCornerShape(12.dp))
            .border(1.dp, RenzoColors.Border, RoundedCornerShape(12.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(title, color = RenzoColors.Foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (sub != null) Text(sub, color = RenzoColors.MutedForeground, fontSize = 12.sp, lineHeight = 18.sp)
        content()
    }
}

@Composable
private fun AdminField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .focusRing(focused, 8.dp)
            .border(1.dp, if (focused) RenzoColors.Primary else RenzoColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = TextStyle(color = RenzoColors.Foreground, fontSize = 14.sp, fontFamily = top.levitatemedia.renzo.hub.core.GeistFamily),
            cursorBrush = SolidColor(RenzoColors.Primary),
            visualTransformation = if (password && value.isNotEmpty()) PasswordVisualTransformation() else VisualTransformation.None,
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, color = RenzoColors.MutedForeground, fontSize = 14.sp)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun RolePill(role: String) {
    val color = when (role) {
        "owner" -> RenzoColors.Primary
        "manager" -> Color(0xFFD8B4FE)
        else -> Color(0xFF93C5FD)
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.MilitaryTech, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(
            role.replaceFirstChar { it.uppercase() },
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun SmallButton(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .focusRing(focused, 8.dp)
            .background(if (focused) RenzoColors.Secondary else Color.Transparent, RoundedCornerShape(8.dp))
            .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
            .tvClickable(onFocused = { focused = it }, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (destructive) Color(0xFFFF6B6B) else RenzoColors.Foreground,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// --- /users/ ----------------------------------------------------------------

/**
 * Users — the staff page: everyone on the server with role and download
 * permission controls, plus invite links (create / copy / revoke).
 */
@Composable
fun UsersScreen(app: AppServices, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var users by remember { mutableStateOf<List<PublicUser>>(emptyList()) }
    var invites by remember { mutableStateOf<List<InviteItem>>(emptyList()) }
    var msg by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val isOwner = app.user.value?.role == "owner"

    LaunchedEffect(reload) {
        try { users = app.repo.users() } catch (e: Exception) { msg = e.message }
        try { invites = app.repo.invites() } catch (_: Exception) {}
    }

    SettingsPage("Users", "Everyone with access to this server.", onClose) {
        Section("Accounts", "Roles decide who can manage users and server settings.") {
            users.forEach { u ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    UserAvatar(u, 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                u.username,
                                color = RenzoColors.Foreground,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            RolePill(u.role)
                        }
                        Text(
                            listOfNotNull(
                                u.email?.takeIf { it.isNotBlank() },
                                if (u.downloadsDenied) "downloads blocked" else null,
                            ).joinToString(" · ").ifBlank { "No email set" },
                            color = RenzoColors.MutedForeground,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                FlowRow(
                    Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (isOwner && u.role != "owner") {
                        SmallButton(if (u.role == "manager") "Make user" else "Make manager") {
                            scope.launch {
                                try {
                                    app.repo.setUserRole(u.id, if (u.role == "manager") "user" else "manager")
                                    reload++
                                } catch (e: Exception) { msg = e.message }
                            }
                        }
                    }
                    if (u.role != "owner") {
                        SmallButton(if (u.downloadsDenied) "Allow downloads" else "Block downloads") {
                            scope.launch {
                                try {
                                    app.repo.setUserDownloads(u.id, !u.downloadsDenied)
                                    reload++
                                } catch (e: Exception) { msg = e.message }
                            }
                        }
                        SmallButton("Delete", destructive = true) {
                            scope.launch {
                                try { app.repo.deleteUser(u.id); reload++ } catch (e: Exception) { msg = e.message }
                            }
                        }
                    }
                }
            }
            if (users.isEmpty()) {
                Text("Loading…", color = RenzoColors.MutedForeground, fontSize = 13.sp)
            }
        }

        Section("Invite a user", "Creates a one-time link that expires in 7 days.") {
            var inviteRole by remember { mutableStateOf("user") }
            var inviteName by remember { mutableStateOf("") }
            var inviteEmail by remember { mutableStateOf("") }
            AdminField(inviteName, { inviteName = it }, "Username (optional)", Modifier.fillMaxWidth())
            AdminField(inviteEmail, { inviteEmail = it }, "Email (optional — sends the link)", Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                SmallButton("Role: " + inviteRole.replaceFirstChar { it.uppercase() }) {
                    inviteRole = if (inviteRole == "user" && isOwner) "manager" else "user"
                }
                Spacer(Modifier.width(10.dp))
                PillButton("Create invite", onClick = {
                    scope.launch {
                        try {
                            val inv = app.repo.createInvite(inviteRole, inviteEmail, inviteName)
                            clipboard.setText(AnnotatedString(inv.url))
                            inviteName = ""; inviteEmail = ""
                            msg = "Invite created — link copied"
                            reload++
                        } catch (e: Exception) { msg = e.message }
                    }
                })
            }
        }

        if (invites.isNotEmpty()) {
            Section("Pending invites") {
                invites.forEach { inv ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                inv.username ?: inv.email ?: "Anyone with the link",
                                color = RenzoColors.Foreground,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                inv.role.replaceFirstChar { it.uppercase() } + " · expires " + inv.expiresAt.take(10),
                                color = RenzoColors.MutedForeground,
                                fontSize = 12.sp,
                            )
                        }
                        SmallButton("Copy") {
                            clipboard.setText(AnnotatedString(inv.url)); msg = "Invite link copied"
                        }
                        Spacer(Modifier.width(8.dp))
                        SmallButton("Revoke", destructive = true) {
                            scope.launch {
                                try { app.repo.revokeInvite(inv.token); reload++ } catch (e: Exception) { msg = e.message }
                            }
                        }
                    }
                }
            }
        }

        if (msg != null) {
            Text(msg!!, color = RenzoColors.Primary, fontSize = 13.sp)
        }
    }
}

// --- /settings/ (owner: SMTP) ------------------------------------------------

/** Settings — the owner page: outgoing email (SMTP) for invites and resets. */
@Composable
fun ServerSettingsScreen(app: AppServices, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("587") }
    var secure by remember { mutableStateOf(false) }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var from by remember { mutableStateOf("") }
    var hasPassword by remember { mutableStateOf(false) }
    var testTo by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val s = app.repo.smtp()
        if (s != null) {
            host = s.host; port = s.port.toString(); secure = s.secure
            user = s.user; from = s.from; hasPassword = s.hasPassword
        }
    }

    SettingsPage("Settings", "Server-wide configuration — owner only.", onClose) {
        Section(
            "Email (SMTP)",
            "Used to send invite links and password-reset emails. Leave the host empty to disable email.",
        ) {
            AdminField(host, { host = it }, "smtp.example.com", Modifier.fillMaxWidth())
            Row(verticalAlignment = Alignment.CenterVertically) {
                AdminField(port, { port = it.filter { c -> c.isDigit() } }, "587", Modifier.width(110.dp))
                Spacer(Modifier.width(10.dp))
                SmallButton(if (secure) "TLS: implicit (465)" else "TLS: STARTTLS") { secure = !secure }
            }
            AdminField(user, { user = it }, "Username", Modifier.fillMaxWidth())
            AdminField(
                pass, { pass = it },
                if (hasPassword) "Password (saved — leave blank to keep)" else "Password",
                Modifier.fillMaxWidth(), password = true,
            )
            AdminField(from, { from = it }, "From address, e.g. Renzo <no-reply@example.com>", Modifier.fillMaxWidth())
            PillButton("Save email settings", onClick = {
                scope.launch {
                    try {
                        app.repo.saveSmtp(host.trim(), port.toIntOrNull() ?: 587, secure, user.trim(), pass, from.trim())
                        pass = ""
                        hasPassword = hasPassword || pass.isNotBlank()
                        msg = if (host.isBlank()) "Email disabled" else "Email settings saved"
                    } catch (e: Exception) { msg = e.message }
                }
            })
        }

        Section("Send a test email", "Confirms the settings above actually deliver.") {
            AdminField(testTo, { testTo = it }, "you@example.com", Modifier.fillMaxWidth())
            PillButton("Send test", onClick = {
                scope.launch {
                    try { app.repo.testSmtp(testTo.trim()); msg = "Test email sent" } catch (e: Exception) { msg = e.message }
                }
            })
        }

        if (msg != null) {
            Text(msg!!, color = RenzoColors.Primary, fontSize = 13.sp)
        }
    }
}
