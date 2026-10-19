package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Class
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MilitaryTech
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.ApiKeyInfo
import top.levitatemedia.renzo.tv.api.FolderInfo
import top.levitatemedia.renzo.tv.api.HealthResponse
import top.levitatemedia.renzo.tv.api.PublicUser
import top.levitatemedia.renzo.tv.ui.components.UserAvatar
import top.levitatemedia.renzo.tv.ui.components.focusRing
import top.levitatemedia.renzo.tv.ui.components.tvClickable
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors
import top.levitatemedia.renzo.tv.ui.kit.LocalIsTv
import top.levitatemedia.renzo.hub.core.notify.ReleaseAlerts
import top.levitatemedia.renzo.tv.ui.kit.CardDivider
import top.levitatemedia.renzo.tv.ui.kit.ErrorBox
import top.levitatemedia.renzo.tv.ui.kit.FieldLabel
import top.levitatemedia.renzo.tv.ui.kit.FieldPair
import top.levitatemedia.renzo.tv.ui.kit.LabelledField
import top.levitatemedia.renzo.tv.ui.kit.SettingsCard
import top.levitatemedia.renzo.tv.ui.kit.Hint as KitHint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.automirrored.outlined.Logout
import top.levitatemedia.renzo.tv.ui.kit.PageHeading
import top.levitatemedia.renzo.tv.ui.kit.RenzoBadge
import top.levitatemedia.renzo.tv.ui.kit.RenzoButton
import top.levitatemedia.renzo.tv.ui.kit.RenzoTextField
import top.levitatemedia.renzo.tv.ui.kit.SettingsNavSection
import top.levitatemedia.renzo.tv.ui.kit.SettingsScaffold
import top.levitatemedia.renzo.tv.ui.kit.SettingsSectionNav
import top.levitatemedia.renzo.tv.ui.kit.kitClickable
import top.levitatemedia.renzo.tv.ui.kit.screenWidthDp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState

// Web spec: frontend/src/app/account/account-view.tsx SECTIONS (id, label, icon).
private data class SectionDef(val id: String, val label: String, val icon: ImageVector)

private val SECTIONS = listOf(
    SectionDef("account", "Account", Icons.Outlined.Person),
    SectionDef("credentials", "Required credentials", Icons.Outlined.Key),
    SectionDef("defaults", "Library", Icons.Outlined.Folder),
    SectionDef("apikey", "API key", Icons.Outlined.Power),
)

/**
 * /account/ — laid out exactly like Shiori's Account page (feature-shiori
 * ui/settings/AccountScreen.kt): the persistent command bar above (wide) or a
 * back-arrow bar (narrow) via SettingsScaffold, an 896dp column with the page
 * heading, then the section nav as a 200dp sidebar at lg or Shiori's drawer
 * trigger below it, beside one section's cards. Renzo's own sections and panes
 * are unchanged in what they do.
 */
@Composable
fun AccountScreen(
    app: AppServices,
    onLogout: () -> Unit,
    onChangeServer: () -> Unit,
    section: String = "account",
) {
    var current by remember { mutableStateOf(section) }
    val user = app.user.value
    val snackbar = remember { SnackbarHostState() }
    // Android phones only: the web has no notifications.
    val notificationsHere = ReleaseAlerts.supported && !LocalIsTv.current
    val sections = (SECTIONS + listOfNotNull(
        if (notificationsHere) SectionDef("notifications", "Notifications", Icons.Outlined.Notifications) else null,
    )).map { SettingsNavSection(it.id, it.label, it.icon) }

    SettingsScaffold(title = "Account", onBack = { app.nav.back() }, snackbar = snackbar) { padding ->
        val wide = !LocalIsTv.current && screenWidthDp() >= 1024.dp
        val sectionNav: @Composable () -> Unit = {
            SettingsSectionNav(
                sections = sections,
                activeId = current,
                onChange = { current = it },
                drawerTitle = "Account",
            )
        }
        val sectionContent: @Composable () -> Unit = {
            Column(Modifier.fillMaxWidth()) {
                when (current) {
                    "credentials" -> CredentialsPane(app)
                    "defaults" -> DefaultsPane(app)
                    "apikey" -> ApiKeyPane(app)
                    "notifications" -> NotificationsPane()
                    else -> AccountPane(app, onLogout, onChangeServer)
                }
            }
        }
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // mx-auto max-w-4xl
            Column(Modifier.widthIn(max = 896.dp).fillMaxWidth()) {
                PageHeading(
                    "Account",
                    "Personal settings for ${user?.username ?: "your account"} — private to you.",
                )
                Spacer(Modifier.height(16.dp))
                if (wide) {
                    // lg:grid-cols-[200px_1fr] lg:items-start, gap-5
                    Row(Modifier.fillMaxWidth()) {
                        Column(Modifier.width(200.dp)) { sectionNav() }
                        Spacer(Modifier.width(20.dp))
                        Column(Modifier.weight(1f)) { sectionContent() }
                    }
                } else {
                    sectionNav()
                    Spacer(Modifier.height(16.dp))
                    sectionContent()
                }
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

// --- Account pane (account-pane.tsx + inline AvatarEditor) -------------------

@Composable
private fun AccountPane(app: AppServices, onLogout: () -> Unit, onChangeServer: () -> Unit) {
    val user = app.user.value
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var gravatarEmail by remember { mutableStateOf(user?.email ?: "") }
    var pendingAvatar by remember { mutableStateOf<Pair<String, String>?>(null) } // base64 to contentType
    var health by remember { mutableStateOf<HealthResponse?>(null) }

    LaunchedEffect(Unit) { health = try { app.repo.health() } catch (_: Exception) { null } }

    fun refreshUser(u: PublicUser) { app.user.value = u }
    fun fail(e: Exception?, fallback: String) {
        val m = e?.message?.takeIf { it.isNotBlank() } ?: fallback
        error = m; msg = m
    }

    val pickImage = top.levitatemedia.renzo.tv.rememberRenzoAvatarPicker(
        onPicked = { b64, type -> error = null; pendingAvatar = b64 to type },
        onError = { m -> error = m; msg = m },
    )

    // /health names the resolved provider in `.debrid` — reading only
    // realdebrid showed an AllDebrid-only account as "Not connected" here
    // (the exact defect the web already fixed).
    val debridIsAd = health?.debrid == "alldebrid"
    val (rdState, rdLabel) = debridPill(if (debridIsAd) health?.alldebrid else health?.realdebrid)
    val ani = health?.trackers?.anilist == true
    val mal = health?.trackers?.mal == true

    // Shiori's Account section, card for card (feature-shiori AccountScreen
    // "account"): Profile picture, Security, then Renzo's Connections and the
    // session card where Shiori keeps Sign out.
    Column {
        SettingsCard(
            title = "Profile picture",
            description = "Shown on the account menu and the users list.",
        ) {
            if (error != null) ErrorBox(error ?: "")

            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                // Pending pick beats the saved avatar beats initials.
                val pendingBmp = remember(pendingAvatar) {
                    pendingAvatar?.let {
                        runCatching {
                            val bytes = java.util.Base64.getMimeDecoder().decode(it.first)
                            top.levitatemedia.renzo.hub.core.decodeImageBytes(bytes)
                        }.getOrNull()
                    }
                }
                if (pendingBmp != null) {
                    Image(
                        pendingBmp,
                        contentDescription = "New avatar preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(CircleShape),
                    )
                } else {
                    UserAvatar(user, 64.dp)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RenzoButton(
                            text = "Upload image",
                            icon = Icons.Outlined.Upload,
                            variant = "outline",
                            small = true,
                            enabled = !busy,
                            onClick = { pickImage() },
                        )
                        if (user?.avatarBase64 != null) {
                            Spacer(Modifier.width(8.dp))
                            RenzoButton(
                                text = "Remove",
                                variant = "ghost",
                                small = true,
                                busy = busy,
                                onClick = {
                                    scope.launch {
                                        busy = true; error = null
                                        try {
                                            app.repo.setAvatar(null, null)
                                            refreshUser(user.copy(avatarBase64 = null, avatarContentType = null))
                                            pendingAvatar = null
                                            msg = "Avatar removed"
                                        } catch (e: Exception) { fail(e, "Couldn't remove avatar") } finally { busy = false }
                                    }
                                },
                            )
                        }
                    }
                    KitHint("Any image works — it's center-cropped and resized before upload.")
                }
            }

            Spacer(Modifier.height(14.dp))
            FieldLabel("Use Gravatar")
            val gravatarField: @Composable () -> Unit = {
                RenzoTextField(
                    value = gravatarEmail,
                    onValueChange = { gravatarEmail = it },
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email,
                )
            }
            val fetchButton: @Composable () -> Unit = {
                RenzoButton(
                    text = "Fetch",
                    variant = "secondary",
                    small = true,
                    enabled = gravatarEmail.isNotBlank() && !busy,
                    onClick = {
                        scope.launch {
                            busy = true; error = null
                            try {
                                val g = app.repo.gravatar(gravatarEmail.trim())
                                pendingAvatar = g.avatarBase64 to g.avatarContentType
                            } catch (e: Exception) { fail(e, "Gravatar lookup failed") } finally { busy = false }
                        }
                    },
                )
            }
            if (!LocalIsTv.current && screenWidthDp() >= 640.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { gravatarField() }
                    Spacer(Modifier.width(8.dp))
                    fetchButton()
                }
            } else {
                gravatarField()
                Spacer(Modifier.height(8.dp))
                fetchButton()
            }
            KitHint(
                "Looks up that address's Gravatar (via the server) as a preview — nothing changes " +
                    "until you save.",
            )

            Spacer(Modifier.height(12.dp))
            RenzoButton(
                text = if (busy) "Saving…" else "Save avatar",
                busy = busy,
                enabled = pendingAvatar != null,
                onClick = {
                    scope.launch {
                        busy = true; error = null
                        try {
                            val (b64, ct) = pendingAvatar ?: return@launch
                            val saved = app.repo.setAvatar(b64, ct)
                            user?.let { refreshUser(it.copy(avatarBase64 = saved.avatarBase64, avatarContentType = saved.avatarContentType)) }
                            pendingAvatar = null
                            msg = "Avatar updated"
                        } catch (e: Exception) { fail(e, "Couldn't save avatar") } finally { busy = false }
                    }
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Security ────────────────────────────────────────────────────
        SettingsCard(
            title = "Security",
            description = "Changing your password signs out all your other sessions.",
        ) {
            var email by remember(user?.email) { mutableStateOf(user?.email ?: "") }
            var savingEmail by remember { mutableStateOf(false) }
            var curPass by remember { mutableStateOf("") }
            var newPass by remember { mutableStateOf("") }
            var confirmPass by remember { mutableStateOf("") }
            var passError by remember { mutableStateOf("") }
            var savingPass by remember { mutableStateOf(false) }

            FieldLabel("Email", Icons.Outlined.Mail)
            val emailField: @Composable () -> Unit = {
                RenzoTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = "Optional — used for password reset",
                    keyboardType = KeyboardType.Email,
                )
            }
            val emailSave: @Composable () -> Unit = {
                RenzoButton(
                    text = if (savingEmail) "Saving…" else "Save",
                    busy = savingEmail,
                    enabled = email.trim() != (user?.email ?: ""),
                    onClick = {
                        savingEmail = true
                        scope.launch {
                            try {
                                val u = app.repo.setEmail(email.trim())
                                refreshUser(u)
                                msg = if (!u.email.isNullOrEmpty()) "Email updated" else "Email cleared"
                            } catch (e: Exception) { msg = e.message ?: "Failed to update email" }
                            savingEmail = false
                        }
                    },
                )
            }
            if (!LocalIsTv.current && screenWidthDp() >= 640.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Box(Modifier.weight(1f)) { emailField() }
                    Spacer(Modifier.width(8.dp))
                    emailSave()
                }
            } else {
                emailField()
                Spacer(Modifier.height(8.dp))
                emailSave()
            }
            KitHint("Password-reset links are sent here. Leave empty to disable email reset for this account.")

            CardDivider(top = 20, bottom = 12)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 10.dp)) {
                Icon(
                    Icons.Outlined.Lock, contentDescription = null,
                    tint = RenzoColors.Foreground,
                    modifier = Modifier.padding(end = 6.dp).size(14.dp),
                )
                Text("Password", style = MaterialTheme.typography.titleSmall, color = RenzoColors.Foreground)
            }
            if (passError.isNotEmpty()) ErrorBox(passError)
            FieldPair(
                breakpoint = 640.dp,
                left = { LabelledField("Current password", curPass, { curPass = it }, password = true) },
                right = {
                    LabelledField(
                        label = "New password",
                        value = newPass,
                        onValueChange = { newPass = it },
                        placeholder = "At least 8 characters",
                        password = true,
                    )
                },
            )
            FieldPair(
                breakpoint = 640.dp,
                left = { LabelledField("Confirm new password", confirmPass, { confirmPass = it }, password = true) },
            )
            RenzoButton(
                text = if (savingPass) "Changing…" else "Update password",
                icon = Icons.Outlined.Check,
                busy = savingPass,
                onClick = {
                    passError = ""
                    when {
                        newPass.length < 8 -> passError = "New password must be at least 8 characters"
                        newPass != confirmPass -> passError = "Passwords do not match"
                        else -> {
                            savingPass = true
                            scope.launch {
                                try {
                                    app.repo.changePassword(curPass, newPass)
                                    curPass = ""; newPass = ""; confirmPass = ""
                                    msg = "Password changed"
                                } catch (e: Exception) {
                                    passError = e.message ?: "Failed to change password"
                                }
                                savingPass = false
                            }
                        }
                    }
                },
            )
        }

        Spacer(Modifier.height(16.dp))

        // ── Connections (Renzo: what streaming and scrobbling run on) ───
        SettingsCard(
            title = "Connections",
            description = "Your debrid service streams and downloads; AniList and MyAnimeList " +
                "import lists and track what you watch. Set them up under Required credentials.",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ConnRow(
                    Icons.Outlined.Link,
                    if (debridIsAd) "AllDebrid" else "Real-Debrid",
                    "Required to stream & download",
                    pill = { StatePill(rdState, rdLabel) },
                )
                ConnRow(Icons.Outlined.MenuBook, "AniList", "List import & scrobbling", pill = {
                    StatePill(
                        if (health == null) PillState.Muted else if (ani) PillState.Ok else PillState.Warn,
                        if (health == null) "checking…" else if (ani) "Connected" else "Not connected",
                    )
                })
                ConnRow(Icons.Outlined.Class, "MyAnimeList", "List import & scrobbling", pill = {
                    StatePill(
                        if (health == null) PillState.Muted else if (mal) PillState.Ok else PillState.Warn,
                        if (health == null) "checking…" else if (mal) "Connected" else "Not connected",
                    )
                })
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── Session — Shiori ends its Account section with Sign out ──────
        SettingsCard(
            title = "Session",
            description = "Signed in as ${user?.username ?: "—"}" +
                (user?.role?.let { " (${roleLabel(it)})" } ?: "") + " on this device.",
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RenzoButton(
                    text = "Sign out",
                    icon = Icons.AutoMirrored.Outlined.Logout,
                    variant = "destructive",
                    onClick = onLogout,
                )
                Spacer(Modifier.width(8.dp))
                RenzoButton(text = "Change server", variant = "outline", onClick = onChangeServer)
            }
        }

        StatusText(msg)
    }
}

// --- Required credentials pane (credentials-pane.tsx) ------------------------

@Composable
private fun CredentialsPane(app: AppServices) {
    val scope = rememberCoroutineScope()
    val user = app.user.value
    var msg by remember { mutableStateOf<String?>(null) }
    var health by remember { mutableStateOf<HealthResponse?>(null) }
    var healthKey by remember { mutableStateOf(0) }

    LaunchedEffect(healthKey) { health = try { app.repo.health() } catch (_: Exception) { null } }

    fun refresh(u: PublicUser) { app.user.value = u }
    fun refetchHealth() { healthKey++ }
    val openUrl: (String) -> Unit = { url ->
        runCatching { top.levitatemedia.renzo.hub.core.HubPlatform.openExternal(url) }
    }

    var rdToken by remember { mutableStateOf("") }
    var adKey by remember { mutableStateOf("") }
    var jimakuKey by remember { mutableStateOf("") }
    var aniToken by remember { mutableStateOf("") }
    var malToken by remember { mutableStateOf("") }

    val (rdState, rdLabel) = debridPill(health?.realdebrid)
    val (adState, adLabel) = debridPill(health?.alldebrid)
    val rdOn = health != null && health?.realdebrid != "not-connected"
    val adOn = health != null && health?.alldebrid != "not-connected"
    val ani = health?.trackers?.anilist == true
    val mal = health?.trackers?.mal == true

    fun saveRd(token: String) {
        scope.launch {
            try {
                val r = app.repo.setRealDebrid(token)
                rdToken = ""
                refresh(r)
                msg = if (r.realDebridConnected) {
                    if (r.premium == true) "Real-Debrid connected"
                    else "Connected — but account is NOT premium; downloads need premium"
                } else "Real-Debrid disconnected"
                refetchHealth()
            } catch (e: Exception) { msg = e.message }
        }
    }
    fun saveAd(key: String) {
        scope.launch {
            try {
                val r = app.repo.setAllDebrid(key)
                adKey = ""
                refresh(r)
                msg = if (r.allDebridConnected) "AllDebrid connected" else "AllDebrid disconnected"
                refetchHealth()
            } catch (e: Exception) { msg = e.message }
        }
    }
    fun saveDebridPref(provider: String) {
        scope.launch {
            try {
                refresh(app.repo.setPreferredDebrid(provider))
                msg = "Using ${if (provider == "alldebrid") "AllDebrid" else "Real-Debrid"}"
                refetchHealth()
            } catch (e: Exception) { msg = e.message }
        }
    }
    fun saveJimaku(key: String) {
        scope.launch {
            try {
                val u = app.repo.setJimaku(key)
                jimakuKey = ""
                refresh(u)
                msg = if (u.jimakuConnected) "Jimaku connected — subtitles enabled" else "Jimaku key removed"
            } catch (e: Exception) { msg = e.message }
        }
    }
    fun saveTracker(anilist: String?, malV: String?) {
        scope.launch {
            try {
                refresh(app.repo.setTrackerToken(anilist = anilist, mal = malV))
                if (anilist != null) aniToken = "" else malToken = ""
                msg = if (!(anilist ?: malV).isNullOrEmpty()) "Saved" else "Token cleared"
                refetchHealth()
            } catch (e: Exception) { msg = e.message }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PaneSection(
            title = "Required credentials",
            sub = buildAnnotatedString {
                append("Each account uses its ")
                b("own")
                append(" credentials. Real-Debrid is required to stream & download; a Jimaku key enables anime subtitles; connect AniList ")
                i("or")
                append(" MyAnimeList for list import & scrobbling.")
            },
        ) {
            Field(
                "Real-Debrid API token",
                pill = { StatePill(rdState, rdLabel) },
                hint = {
                    Hint(
                        link("All torrent traffic routes through a debrid service, never your home IP. ", "Get your token →"),
                        onClick = { openUrl("https://real-debrid.com/apitoken") },
                    )
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WebInput(rdToken, { rdToken = it }, "Paste your Real-Debrid API token", Modifier.weight(1f), masked = true)
                    Spacer(Modifier.width(8.dp))
                    WebButton("Save") { saveRd(rdToken.trim()) }
                    if (rdOn) {
                        Spacer(Modifier.width(8.dp))
                        WebButton("Disconnect", variant = BtnVariant.Outline) { saveRd("") }
                    }
                }
            }

            Field(
                "AllDebrid API key",
                pill = { StatePill(adState, adLabel) },
                hint = {
                    Hint(
                        link("Alternative to Real-Debrid — connect either (or both). ", "Get your key →"),
                        onClick = { openUrl("https://alldebrid.com/apikeys/") },
                    )
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WebInput(adKey, { adKey = it }, "Paste your AllDebrid API key", Modifier.weight(1f), masked = true)
                    Spacer(Modifier.width(8.dp))
                    WebButton("Save") { saveAd(adKey.trim()) }
                    if (adOn) {
                        Spacer(Modifier.width(8.dp))
                        WebButton("Disconnect", variant = BtnVariant.Outline) { saveAd("") }
                    }
                }
            }

            if (rdOn && adOn) {
                Field(
                    "Preferred debrid service",
                    hint = { Hint(plain("Used for streaming + downloads when both are connected.")) },
                ) {
                    SelectBox(
                        value = health?.debrid ?: "realdebrid",
                        options = listOf("realdebrid" to "Real-Debrid", "alldebrid" to "AllDebrid"),
                        onSelect = { saveDebridPref(it) },
                        modifier = fieldWidth(256),
                    )
                }
            }

            Field(
                "Jimaku API key",
                pill = {
                    StatePill(
                        if (user?.jimakuConnected == true) PillState.Ok else PillState.Warn,
                        if (user?.jimakuConnected == true) "Connected" else "Not connected",
                    )
                },
                hint = {
                    Hint(
                        buildAnnotatedString {
                            b("Required for subtitles")
                            append(" — enables anime captions and saves them with each download. ")
                            u("Sign in at jimaku.cc")
                            append(" → Account → API key.")
                        },
                        onClick = { openUrl("https://jimaku.cc/login") },
                    )
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WebInput(jimakuKey, { jimakuKey = it }, "Paste your Jimaku API key", Modifier.weight(1f), masked = true)
                    Spacer(Modifier.width(8.dp))
                    WebButton("Save") { saveJimaku(jimakuKey.trim()) }
                    if (user?.jimakuConnected == true) {
                        Spacer(Modifier.width(8.dp))
                        WebButton("Disconnect", variant = BtnVariant.Outline) { saveJimaku("") }
                    }
                }
            }
        }

        PaneSection(
            title = "AniList / MyAnimeList",
            sub = buildAnnotatedString {
                b("Connect just one")
                append(" — enough to import your list and auto-scrobble. Connecting opens the auth site in a popup; sign in once and it manages your tokens.")
            },
            actions = {
                WebButton("Refresh", variant = BtnVariant.Ghost, small = true, icon = Icons.Outlined.Refresh) {
                    refetchHealth(); msg = "Status refreshed"
                }
            },
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TrackerConnRow(
                    app, provider = "anilist", name = "AniList", icon = Icons.Outlined.MenuBook,
                    connected = ani, healthKnown = health != null,
                    onUser = { refresh(it) }, onMsg = { msg = it }, onConnected = { refetchHealth() },
                    openBrowser = openUrl,
                )
                TrackerConnRow(
                    app, provider = "mal", name = "MyAnimeList", icon = Icons.Outlined.Class,
                    connected = mal, healthKnown = health != null,
                    onUser = { refresh(it) }, onMsg = { msg = it }, onConnected = { refetchHealth() },
                    openBrowser = openUrl,
                )
            }

            Details("Advanced · paste a token manually") {
                Column(
                    Modifier.padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Hint(plain("Optional. Overrides the auth-site token for your account only."))
                    Field("AniList access token") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WebInput(aniToken, { aniToken = it }, "AniList token", Modifier.weight(1f), masked = true)
                            Spacer(Modifier.width(8.dp))
                            WebButton("Save", variant = BtnVariant.Outline) { saveTracker(aniToken.trim(), null) }
                            Spacer(Modifier.width(8.dp))
                            WebButton("Clear", variant = BtnVariant.Ghost) { saveTracker("", null) }
                        }
                    }
                    Field("MyAnimeList access token") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WebInput(malToken, { malToken = it }, "MAL token", Modifier.weight(1f), masked = true)
                            Spacer(Modifier.width(8.dp))
                            WebButton("Save", variant = BtnVariant.Outline) { saveTracker(null, malToken.trim()) }
                            Spacer(Modifier.width(8.dp))
                            WebButton("Clear", variant = BtnVariant.Ghost) { saveTracker(null, "") }
                        }
                    }
                }
            }
        }

        StatusText(msg)
    }
}

/** ConnRow + connect action — old TrackerBlock's browser-OAuth poll wiring. */
@Composable
private fun TrackerConnRow(
    app: AppServices,
    provider: String,
    name: String,
    icon: ImageVector,
    connected: Boolean,
    healthKnown: Boolean,
    onUser: (PublicUser) -> Unit,
    onMsg: (String) -> Unit,
    onConnected: () -> Unit,
    openBrowser: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var polling by remember { mutableStateOf(false) }
    ConnRow(
        icon, name,
        desc = if (connected) "Connected — importing & scrobbling" else "Not linked yet",
        pill = {
            StatePill(
                if (!healthKnown) PillState.Muted else if (connected) PillState.Ok else PillState.Err,
                if (!healthKnown) "checking…" else if (connected) "Connected" else "Not linked",
            )
        },
        action = {
            WebButton(
                if (polling) "Waiting…" else if (connected) "Reconnect ↗" else "Connect ↗",
                variant = BtnVariant.Outline,
                small = true,
            ) {
                if (polling) return@WebButton
                scope.launch {
                    try {
                        val start = app.repo.oauthStart(provider)
                        openBrowser(start.authUrl)
                        polling = true
                        onMsg("Finish signing in in the browser, then come back")
                        repeat(60) {
                            delay(3000)
                            val u = app.repo.oauthPoll(provider, start.state)
                            if (u != null) {
                                onUser(u); onMsg("$name connected ✓"); polling = false
                                onConnected()
                                return@launch
                            }
                        }
                        polling = false
                        onMsg("Connection timed out — try again")
                    } catch (e: ApiError) {
                        polling = false
                        onMsg("Couldn't start the connection: " + (e.message ?: "error"))
                    }
                }
            }
        },
    )
}

// --- Library defaults pane (defaults-pane.tsx) -------------------------------

// Old #defTrack options.
private val TRACK_OPTIONS = listOf(
    "" to "— Don't set —",
    "watching" to "Watching",
    "planning" to "Plan to watch",
    "completed" to "Completed",
    "paused" to "Paused",
    "dropped" to "Dropped",
    "rewatching" to "Rewatching",
)

// Old #defCc options (index.html:438).
private val CC_LANGS = listOf(
    "off" to "Off",
    "en" to "English",
    "ja" to "Japanese",
    "es" to "Spanish",
    "es-la" to "Spanish (Latin America)",
    "pt-br" to "Portuguese (Brazil)",
    "fr" to "French",
    "de" to "German",
    "it" to "Italian",
    "ru" to "Russian",
    "ar" to "Arabic",
    "zh" to "Chinese",
    "ko" to "Korean",
)

@Composable
private fun DefaultsPane(app: AppServices) {
    val scope = rememberCoroutineScope()
    var msg by remember { mutableStateOf<String?>(null) }
    val user = app.user.value

    var track by remember { mutableStateOf(user?.addDefaults?.track ?: "") }
    var autoStatus by remember { mutableStateOf(user?.autoStatus != false) }
    var cc by remember { mutableStateOf(user?.ccLang ?: "en") }
    var folder by remember { mutableStateOf(user?.addDefaults?.folder ?: "") }
    var autoDl by remember { mutableStateOf(user?.addDefaults?.autoDownload == true) }

    var folders by remember { mutableStateOf<List<FolderInfo>>(emptyList()) }
    LaunchedEffect(Unit) { folders = try { app.repo.folders() } catch (_: Exception) { emptyList() } }
    // Keep a stale saved folder selectable even if it's not in the list anymore.
    val folderNames = folders.map { it.name }.toMutableList()
    if (folder.isNotEmpty() && folder !in folderNames) folderNames.add(folder)

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PaneSection(
            title = "Library defaults",
            sub = plain(
                "Applied automatically the first time a series enters your library — when you add it or put it on a list. Leave blank / off to do nothing.",
            ),
        ) {
            Field("Set tracking status", hint = { Hint(plain("Synced to your AniList / MyAnimeList when connected.")) }) {
                SelectBox(track, TRACK_OPTIONS, { track = it }, fieldWidth(288))
            }

            Field(
                "Auto-update tracking status",
                hint = { Hint(plain("Keeps your AniList / MyAnimeList status in sync as you watch.")) },
            ) {
                SelectBox(
                    value = if (autoStatus) "on" else "off",
                    options = listOf(
                        "on" to "On — Watching as I watch, Completed when finished",
                        "off" to "Off — I'll set the status manually",
                    ),
                    onSelect = { autoStatus = it != "off" },
                    modifier = fieldWidth(384),
                )
            }

            Field(
                "Preferred subtitles",
                hint = { Hint(plain("Auto-selected in the player when that language is available.")) },
            ) {
                SelectBox(cc, CC_LANGS, { cc = it }, fieldWidth(288))
            }

            Field("Add to folder") {
                SelectBox(
                    value = folder,
                    options = listOf("" to "— Default folder —") + folderNames.map { it to it },
                    onSelect = { folder = it },
                    modifier = fieldWidth(288),
                )
            }

            // Auto-download opt-in — hidden entirely when downloads are denied.
            if (user?.downloadsDenied != true) {
                CheckboxRow(autoDl, "Auto-download new episodes to your own Real-Debrid") { autoDl = !autoDl }
            }

            Row {
                WebButton("Save defaults") {
                    scope.launch {
                        try {
                            val u = app.repo.saveAddDefaults(
                                track.ifBlank { null },
                                autoDl,
                                folder.trim().ifBlank { null },
                                ccLang = cc,
                                autoStatus = autoStatus,
                            )
                            app.user.value = u
                            app.prefs.ccLang = u.ccLang
                            msg = "Library defaults saved"
                        } catch (e: Exception) { msg = e.message }
                    }
                }
            }
        }
        StatusText(msg)

        // Desktop only, and stored on this computer — not a server default:
        // the choice depends on what is installed here.
        if (top.levitatemedia.renzo.hub.core.HubPlatform.isDesktop) {
            var player by remember { mutableStateOf(app.prefs.desktopPlayer) }
            PaneSection(
                title = "Playback on this computer",
                sub = plain("Which player opens when you press Play. Saved on this computer only."),
            ) {
                Field(
                    "Video player",
                    hint = {
                        Hint(plain("The external player uses mpv or VLC if one is installed. The built-in player can still hand any episode to it."))
                    },
                ) {
                    SelectBox(
                        value = player,
                        options = listOf(
                            "builtin" to "Built-in player",
                            "external" to "External player (mpv / VLC)",
                        ),
                        onSelect = { player = it; app.prefs.desktopPlayer = it },
                        modifier = fieldWidth(288),
                    )
                }
            }
        }
    }
}

// --- API key pane (apikey-pane.tsx) ------------------------------------------

@Composable
private fun ApiKeyPane(app: AppServices) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    var info by remember { mutableStateOf<ApiKeyInfo?>(null) }
    var loading by remember { mutableStateOf(true) }
    var show by remember { mutableStateOf(false) }
    var confirmRotate by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try { info = app.repo.apiKey() } catch (e: Exception) { msg = e.message }
        loading = false
    }

    val apiKey = info?.apiKey ?: ""
    val manifest = info?.manifestUrl ?: ""

    fun copyText(text: String, ok: String) {
        if (text.isEmpty()) { msg = "Nothing to copy"; return }
        clipboard.setText(AnnotatedString(text))
        msg = ok
    }
    fun rotate() {
        scope.launch {
            try {
                val r = app.repo.rotateApiKey()
                info = info?.copy(apiKey = r.apiKey) ?: r
                msg = "New API key generated — update it in Jellyfin"
            } catch (e: Exception) { msg = e.message }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PaneSection(
            title = "API key",
            sub = buildAnnotatedString {
                append("Watch your Renzo library inside Jellyfin. Your ")
                b("personal API key")
                append(" links the Renzo plugin to ")
                i("your")
                append(" account — playback streams through your own Real-Debrid and your own library, never anyone else's.")
            },
        ) {
            Field(
                "Your API key",
                hint = {
                    Hint(
                        buildAnnotatedString {
                            append("Keep this secret — anyone with it can stream through your Real-Debrid. ")
                            u("Regenerate")
                            append(" if it leaks (updates needed anywhere you used it).")
                        },
                        onClick = { confirmRotate = true },
                    )
                },
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WebInput(
                        apiKey, {}, if (loading) "Loading…" else "",
                        Modifier.weight(1f),
                        masked = !show, readOnly = true, mono = true,
                    )
                    Spacer(Modifier.width(8.dp))
                    WebButton(if (show) "Hide" else "Show", variant = BtnVariant.Outline) { show = !show }
                    Spacer(Modifier.width(8.dp))
                    WebButton("Copy") { copyText(apiKey, "API key copied") }
                }
            }

            if (confirmRotate) {
                // window.confirm translation — inline confirm block.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(RenzoColors.Background.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Regenerate your API key? Anything using the old key (e.g. Jellyfin) will stop working until you paste the new one.",
                        color = RenzoColors.Foreground,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                    Row {
                        WebButton("Regenerate") { confirmRotate = false; rotate() }
                        Spacer(Modifier.width(8.dp))
                        WebButton("Cancel", variant = BtnVariant.Outline) { confirmRotate = false }
                    }
                }
            }

            Field("Plugin repository URL") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WebInput(manifest, {}, "", Modifier.weight(1f), readOnly = true, mono = true)
                    Spacer(Modifier.width(8.dp))
                    WebButton("Copy") { copyText(manifest, "Repository URL copied") }
                }
            }

            Details("How to install", initiallyOpen = true) {
                Column(
                    Modifier.padding(top = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InstallStep(1, buildAnnotatedString {
                        append("In Jellyfin: ")
                        b("Dashboard → Plugins → Repositories → ＋")
                        append(", paste the repository URL above, save.")
                    })
                    InstallStep(2, buildAnnotatedString {
                        b("Catalog")
                        append(" → install ")
                        b("Renzo")
                        append(" → restart Jellyfin.")
                    })
                    InstallStep(3, buildAnnotatedString {
                        b("Dashboard → Plugins → Renzo")
                        append(" → set the Renzo server URL and paste ")
                        b("your API key")
                        append(" from above.")
                    })
                    InstallStep(4, buildAnnotatedString {
                        append("Open the ")
                        b("Renzo")
                        append(" channel (Trending · This Season · Recommended); titles you browse become searchable in Jellyfin.")
                    })
                    Text(
                        "Note: Jellyfin 10.11 has no live channel search, so Renzo titles show up in Jellyfin's global search after the channel has been browsed or refreshed — not typed live against Renzo.",
                        color = RenzoColors.MutedForeground,
                        fontSize = 12.sp,
                        lineHeight = 19.sp,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
        StatusText(msg)
    }
}

@Composable
private fun InstallStep(n: Int, text: AnnotatedString) {
    Row {
        Text("$n. ", color = RenzoColors.MutedForeground, fontSize = 12.sp, lineHeight = 19.sp)
        Text(text, color = RenzoColors.MutedForeground, fontSize = 12.sp, lineHeight = 19.sp)
    }
}

// --- shared pieces (settings/shared.tsx ports) -------------------------------

/** Old setPill states: "pill-state ok|warn|err" (+ muted while checking). */
private enum class PillState { Ok, Warn, Err, Muted }

/** Old RD_MAP (app.js:2373) — health value → [pill state, label]. */
private fun debridPill(v: String?): Pair<PillState, String> = when (v) {
    null -> PillState.Muted to "checking…"
    "premium" -> PillState.Ok to "Premium"
    "not-premium" -> PillState.Warn to "Connected · not premium"
    "connected" -> PillState.Ok to "Connected"
    "invalid" -> PillState.Err to "Invalid token"
    else -> PillState.Err to "Not connected"
}

/** Connection state pill — Shiori's RenzoBadge in the matching status colour. */
@Composable
private fun StatePill(state: PillState, label: String) {
    val color = when (state) {
        PillState.Ok -> RenzoColors.Emerald
        PillState.Warn -> RenzoColors.Amber
        PillState.Err -> RenzoColors.Red
        PillState.Muted -> RenzoColors.MutedForeground
    }
    RenzoBadge(label, color)
}

private fun roleLabel(r: String): String =
    if (r == "owner") "Owner" else if (r == "manager") "Manager" else "User"

@Composable
private fun RoleBadge(role: String) {
    val color = when (role) {
        "owner" -> RenzoColors.Primary
        "manager" -> Color(0xFFD8B4FE)
        else -> Color(0xFF93C5FD)
    }
    RenzoBadge(roleLabel(role), color, icon = Icons.Filled.MilitaryTech)
}

/**
 * Shiori's SettingsCard (rounded-xl border bg-card, titleMedium + bodySmall
 * description), with the two things Renzo's panes need on top: a rich-text
 * description and header actions beside the title at sm.
 */
@Composable
private fun PaneSection(
    title: String? = null,
    sub: AnnotatedString? = null,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, RenzoColors.Border, RoundedCornerShape(12.dp))
            .background(RenzoColors.Card)
            .padding(16.dp),
    ) {
        val header: @Composable () -> Unit = {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = RenzoColors.Foreground)
            }
            if (sub != null) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = RenzoColors.MutedForeground,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
        if (title != null || sub != null || actions != null) {
            if (actions != null && screenWidthDp() >= 640.dp) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { header() }
                    Spacer(Modifier.width(12.dp))
                    Row { actions() }
                }
            } else {
                header()
                if (actions != null) Row(Modifier.padding(top = 12.dp)) { actions() }
            }
            Spacer(Modifier.height(14.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), content = content)
    }
}

/** One labelled form field — Shiori's FieldLabel + control + Hint. */
@Composable
private fun Field(
    label: String,
    pill: (@Composable () -> Unit)? = null,
    hint: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = RenzoColors.Foreground)
            if (pill != null) {
                Spacer(Modifier.width(8.dp))
                pill()
            }
        }
        content()
        hint?.invoke()
    }
}

/** Shiori's Hint (bodySmall, muted); optionally focusable/clickable (hint links). */
@Composable
private fun Hint(text: AnnotatedString, onClick: (() -> Unit)? = null) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        color = RenzoColors.MutedForeground,
        modifier = Modifier
            .padding(top = 4.dp)
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(4.dp)).kitClickable(radius = 4.dp, onClick = onClick) else Modifier),
    )
}

/** Connection row (shared.tsx ConnRow): icon · name/desc · pill · action. */
@Composable
private fun ConnRow(
    icon: ImageVector,
    name: String,
    desc: String,
    pill: @Composable () -> Unit,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RenzoColors.Muted),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = RenzoColors.MutedForeground, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.labelLarge, color = RenzoColors.Foreground, maxLines = 1)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground, maxLines = 1)
        }
        Spacer(Modifier.width(12.dp))
        pill()
        if (action != null) {
            Spacer(Modifier.width(12.dp))
            action()
        }
    }
}

/** shadcn button variants (ui/button.tsx) the account page uses. */
private enum class BtnVariant { Primary, Outline, Ghost, GhostDanger }

/** Shiori's RenzoButton, under the variant names these panes were written with. */
@Composable
private fun WebButton(
    label: String,
    variant: BtnVariant = BtnVariant.Primary,
    small: Boolean = false,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    onClick: () -> Unit,
) {
    RenzoButton(
        text = label,
        onClick = onClick,
        icon = icon,
        variant = when (variant) {
            BtnVariant.Primary -> "default"
            BtnVariant.Outline -> "outline"
            BtnVariant.Ghost -> "ghost"
            BtnVariant.GhostDanger -> "destructive"
        },
        small = small,
        enabled = enabled,
    )
}

/** Shiori's RenzoTextField (input.tsx), under the old parameter names. */
@Composable
internal fun WebInput(
    value: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    masked: Boolean = false,
    readOnly: Boolean = false,
    mono: Boolean = false,
    email: Boolean = false,
    numeric: Boolean = false,
) {
    RenzoTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier,
        placeholder = placeholder,
        password = masked,
        keyboardType = when {
            email -> KeyboardType.Email
            numeric -> KeyboardType.Number
            else -> KeyboardType.Text
        },
        readOnly = readOnly,
        monospace = mono,
    )
}

/** TvSelect translation: closed trigger + expanding option list. */
@Composable
internal fun SelectBox(
    value: String,
    options: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    var focused by remember { mutableStateOf(false) }
    Column(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .focusRing(focused, 8.dp)
                .background(if (focused) RenzoColors.Accent else Color.Transparent, RoundedCornerShape(8.dp))
                .border(1.dp, RenzoColors.Input, RoundedCornerShape(8.dp))
                .tvClickable(onFocused = { focused = it }, onClick = { open = !open })
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                options.firstOrNull { it.first == value }?.second ?: value,
                color = RenzoColors.Foreground,
                fontSize = 14.sp,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Icon(Icons.Outlined.UnfoldMore, contentDescription = null, tint = RenzoColors.MutedForeground, modifier = Modifier.size(16.dp))
        }
        if (open) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(RenzoColors.Popover, RoundedCornerShape(8.dp))
                    .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
                    .padding(4.dp),
            ) {
                options.forEach { (v, label) ->
                    key(v) {
                        var f by remember { mutableStateOf(false) }
                        val selected = v == value
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .focusRing(f, 4.dp)
                                .background(
                                    when {
                                        selected -> RenzoColors.Primary.copy(alpha = 0.10f)
                                        f -> RenzoColors.Accent
                                        else -> Color.Transparent
                                    },
                                    RoundedCornerShape(4.dp),
                                )
                                .tvClickable(onFocused = { f = it }, onClick = { onSelect(v); open = false })
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Text(
                                label,
                                color = if (selected) RenzoColors.Primary else RenzoColors.Foreground,
                                fontSize = 14.sp,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Radix Checkbox row (defaults pane): 16dp primary-bordered box + text-sm label. */
@Composable
private fun CheckboxRow(checked: Boolean, label: String, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .focusRing(focused, 8.dp)
            .tvClickable(onFocused = { focused = it }, onClick = onToggle)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) RenzoColors.Primary else Color.Transparent, RoundedCornerShape(4.dp))
                .border(1.dp, RenzoColors.Primary, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(Icons.Outlined.Check, contentDescription = null, tint = RenzoColors.PrimaryForeground, modifier = Modifier.size(12.dp))
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(label, color = RenzoColors.Foreground, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** <details> translation: bordered well + disclosure summary. */
@Composable
private fun Details(
    summary: String,
    initiallyOpen: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by remember { mutableStateOf(initiallyOpen) }
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RenzoColors.Background.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(4.dp))
                .focusRing(focused, 4.dp)
                .tvClickable(onFocused = { focused = it }, onClick = { open = !open })
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                (if (open) "▾  " else "▸  ") + summary,
                color = RenzoColors.MutedForeground,
                fontSize = 14.sp,
            )
        }
        if (open) content()
    }
}

/** Save confirmations — Shiori shows these as a snackbar; inline here, same style. */
@Composable
private fun StatusText(msg: String?) {
    if (msg != null) {
        Text(
            msg,
            style = MaterialTheme.typography.bodySmall,
            color = RenzoColors.MutedForeground,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
    Spacer(Modifier.height(28.dp))
}

/** w-full below the sm break, fixed width above (web `w-full sm:w-NN`). */
@Composable
private fun fieldWidth(maxDp: Int): Modifier =
    if (top.levitatemedia.renzo.tv.renzoScreenWidthDp() >= 640) Modifier.width(maxDp.dp)
    else Modifier.fillMaxWidth()

// --- AnnotatedString helpers (web <b>/<i>/underline + link text) -------------

private fun plain(text: String): AnnotatedString = AnnotatedString(text)

private fun AnnotatedString.Builder.b(t: String) {
    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(t) }
}

private fun AnnotatedString.Builder.i(t: String) {
    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(t) }
}

private fun AnnotatedString.Builder.u(t: String) {
    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(t) }
}

private fun link(prefix: String, linkText: String): AnnotatedString = buildAnnotatedString {
    append(prefix)
    u(linkText)
}
