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
import top.levitatemedia.renzo.tv.ui.components.UserAvatar
import top.levitatemedia.renzo.tv.ui.components.focusRing
import top.levitatemedia.renzo.tv.ui.components.tvClickable
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors
import top.levitatemedia.renzo.tv.ui.kit.PageHeading
import top.levitatemedia.renzo.tv.ui.kit.RenzoBadge
import top.levitatemedia.renzo.tv.ui.kit.RenzoButton
import top.levitatemedia.renzo.tv.ui.kit.RenzoTextField
import top.levitatemedia.renzo.tv.ui.kit.SettingsCard
import top.levitatemedia.renzo.tv.ui.kit.SettingsScaffold

// ---------------------------------------------------------------------------
// /users/ and /settings/ — the staff/owner pages, on Shiori's settings shell
// (SettingsScaffold + PageHeading + SettingsCard from ui/kit), the same one
// the Account and Appearance pages use, so all four read as one family.
// ---------------------------------------------------------------------------

/**
 * Shiori's settings page: the command bar above at wide (back-arrow bar
 * below), then the heading and the cards. Shiori's Users page runs full
 * width; its Settings page keeps the settings-manager's max-w-4xl column.
 */
@Composable
private fun SettingsPage(
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    maxWidth: androidx.compose.ui.unit.Dp? = null,
    content: @Composable () -> Unit,
) {
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    SettingsScaffold(title = title, onBack = onClose, snackbar = snackbar) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.then(if (maxWidth != null) Modifier.widthIn(max = maxWidth) else Modifier).fillMaxWidth()) {
                PageHeading(title, subtitle)
                Spacer(Modifier.height(20.dp))
                content()
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

/** Shiori's SettingsCard, with the 16dp gap the pages stack them at. */
@Composable
private fun Section(title: String, sub: String? = null, content: @Composable () -> Unit) {
    Box(Modifier.padding(bottom = 16.dp)) {
        SettingsCard(title = title, description = sub) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { content() }
        }
    }
}

/** Shiori's RenzoTextField. */
@Composable
private fun AdminField(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    password: Boolean = false,
) {
    RenzoTextField(value = value, onValueChange = onChange, modifier = modifier, placeholder = placeholder, password = password)
}

/** Shiori's RenzoBadge in the role colour. */
@Composable
private fun RolePill(role: String) {
    val color = when (role) {
        "owner" -> RenzoColors.Primary
        "manager" -> Color(0xFFD8B4FE)
        else -> Color(0xFF93C5FD)
    }
    RenzoBadge(role.replaceFirstChar { it.uppercase() }, color, icon = Icons.Filled.MilitaryTech)
}

/** Shiori's small outline button (destructive actions in the destructive variant). */
@Composable
private fun SmallButton(label: String, destructive: Boolean = false, onClick: () -> Unit) {
    RenzoButton(text = label, onClick = onClick, variant = if (destructive) "destructive" else "outline", small = true)
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
                RenzoButton("Create invite", onClick = {
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

    SettingsPage("Settings", "Server-wide configuration — owner only.", onClose, maxWidth = 896.dp) {
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
            RenzoButton("Save email settings", onClick = {
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
            RenzoButton("Send test", variant = "outline", onClick = {
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
