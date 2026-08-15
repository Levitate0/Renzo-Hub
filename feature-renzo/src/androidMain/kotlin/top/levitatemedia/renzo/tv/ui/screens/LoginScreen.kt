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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.R
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.PublicUser
import top.levitatemedia.renzo.tv.ui.components.focusRing
import top.levitatemedia.renzo.tv.ui.components.tvClickable
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/**
 * Sign-in gate, matched to the Shiori half's login page (AuthUi.kt /
 * LoginScreen.kt) so the two halves read as one app: a vertically-centred
 * 8dp-radius card, the banner in the header with "Enter your credentials to
 * log in" beneath it, LABELLED fields ("Username" / "Password" with
 * Enter-your-… placeholders), a "Remember me" checkbox, a full-width primary
 * "Log in" that says "Logging in..." while busy, and centred link rows. The
 * banner stays Renzo's own wordmark, and the Renzo-specific pieces survive:
 * first-run setup notice, generic forgot-password reply, server address line.
 */
@Composable
fun LoginScreen(app: AppServices, onBackToPicker: (() -> Unit)? = null, onLoggedIn: (PublicUser) -> Unit) {
    val scope = rememberCoroutineScope()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var rememberMe by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var setupRequired by remember { mutableStateOf(false) }
    var attempt by remember { mutableStateOf(0) }

    fun submit() {
        if (busy) return
        if (username.isBlank() || password.isEmpty()) {
            error = "Enter your username and password."
            return
        }
        attempt++
    }

    // Forgot password: username-gated; the response is ALWAYS generic (no
    // account/email enumeration) — web login-gate forgot().
    fun forgot() {
        if (busy) return
        if (username.trim().isEmpty()) {
            error = "Enter your username first"
            return
        }
        busy = true
        scope.launch {
            app.repo.forgot(username.trim())
            error = null
            notice = "If that account exists and has an email set, a reset link was sent."
            busy = false
        }
    }

    // Boot routed here on user==null too — surface first-run setup if that's why.
    LaunchedEffect(Unit) {
        try {
            setupRequired = app.repo.me().setupRequired == true
        } catch (_: Exception) {
            // 401 / unreachable — nothing to surface, the form handles it.
        }
    }

    LaunchedEffect(attempt) {
        if (attempt == 0) return@LaunchedEffect
        busy = true
        error = null
        notice = null
        try {
            val user = app.repo.login(username.trim(), password, rememberMe)
            busy = false
            onLoggedIn(user)
        } catch (e: ApiError) {
            busy = false
            error = when (e.status) {
                0 -> "Can't reach the server. Check your connection."
                401 -> "Invalid credentials"
                429 -> e.message ?: "Too many attempts. Try again later."
                else -> e.message ?: "Sign-in failed (HTTP ${e.status})."
            }
        } catch (_: Exception) {
            busy = false
            error = "Sign-in failed. Try again."
        }
    }

    GateCard {
        // ── header: banner + description (Shiori AuthCardHeader) ──
        GateBanner()
        Text(
            "Enter your credentials to log in",
            color = RenzoColors.MutedForeground,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))

        if (setupRequired) {
            GateNoticeBox(
                "This server has no accounts yet — open it in a web browser to finish setup first.",
            )
        }
        if (error != null) {
            GateErrorBox(error ?: "")
        } else if (notice != null) {
            GateNoticeBox(notice ?: "")
        }

        // ── labelled fields (Shiori AuthLabel + AuthInput) ──
        Column(Modifier.fillMaxWidth()) {
            GateLabel("Username")
            Spacer(Modifier.height(8.dp))
            GateTextField(
                value = username,
                onValueChange = { username = it; error = null },
                placeholder = "Enter your username",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next,
                ),
                keyboardActions = KeyboardActions.Default,
            )
        }
        Column(Modifier.fillMaxWidth()) {
            GateLabel("Password")
            Spacer(Modifier.height(8.dp))
            GateTextField(
                value = password,
                onValueChange = { password = it; error = null },
                placeholder = "Enter your password",
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                visualTransformation = PasswordVisualTransformation(),
            )
        }

        RememberRow(rememberMe) { rememberMe = !rememberMe }
        GateButton(label = if (busy) "Logging in…" else "Log in", enabled = !busy) { submit() }

        ForgotPasswordLink(enabled = !busy) { forgot() }
        if (onBackToPicker != null) {
            GateLinkRow("Back to app picker", onClick = onBackToPicker)
        }
        app.prefs.serverUrl?.let {
            Text(
                it,
                color = RenzoColors.MutedForeground,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** Centred link row (Shiori AuthLinkRow) — shared by both gates. */
@Composable
internal fun GateLinkRow(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            text,
            color = if (focused) RenzoColors.Foreground else RenzoColors.MutedForeground,
            fontSize = 14.sp,
            textDecoration = if (focused) TextDecoration.Underline else null,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .focusRing(focused, 8.dp)
                .let { m -> if (enabled) m.tvClickable(onFocused = { f -> focused = f }, onClick = onClick) else m.alpha(0.5f) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

/** "Forgot password?" — Shiori AuthLinkRow: centred, muted, underline on focus. */
@Composable
private fun ForgotPasswordLink(enabled: Boolean, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(
            "Forgot password?",
            color = if (focused) RenzoColors.Foreground else RenzoColors.MutedForeground,
            fontSize = 14.sp,
            textDecoration = if (focused) TextDecoration.Underline else null,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .focusRing(focused, 8.dp)
                .let { m -> if (enabled) m.tvClickable(onFocused = { f -> focused = f }, onClick = onClick) else m.alpha(0.5f) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

/** "Remember me" — Shiori AuthCheckboxRow: 16dp rounded-4 primary box + text-sm. */
@Composable
private fun RememberRow(checked: Boolean, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .focusRing(focused, 8.dp)
            .tvClickable(onFocused = { focused = it }, onClick = onToggle)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(if (checked) RenzoColors.Primary else Color.Transparent, RoundedCornerShape(4.dp))
                .border(1.dp, if (checked) RenzoColors.Primary else RenzoColors.Border, RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    Icons.Outlined.Check,
                    contentDescription = null,
                    tint = RenzoColors.PrimaryForeground,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
        Text("Remember me", color = RenzoColors.MutedForeground, fontSize = 14.sp)
    }
}

// --- Gate pieces, matched to Shiori's AuthUi (shared with ConnectScreen) -----

/**
 * The auth-gate card — gate-shell.tsx verbatim (as of 2026-08-14): CENTRED on
 * both axes, scroll on the outer layer so a card taller than the viewport
 * keeps its top edge reachable; max-w-md rounded-xl bordered bg-card p-6
 * column with gap-3.
 */
@Composable
internal fun GateCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(RenzoColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 448.dp)
                .fillMaxWidth(),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(RenzoColors.Card, RoundedCornerShape(12.dp))
                    .border(1.dp, RenzoColors.Border, RoundedCornerShape(12.dp))
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
        }
    }
}

/** GateBanner — gate-shell.tsx: Renzo's wordmark at ITS OWN size, w-48 mx-auto mb-1. */
@Composable
internal fun GateBanner() {
    val painter = painterResource(R.drawable.renzo_wordmark)
    val ratio = painter.intrinsicSize.let { if (it.height > 0f) it.width / it.height else 3f }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Image(
            painter = painter,
            contentDescription = "Renzo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.width(192.dp).height(192.dp / ratio),
        )
    }
}

/** Label — Shiori AuthLabel: `text-sm font-medium leading-none`. */
@Composable
internal fun GateLabel(text: String) {
    Text(
        text,
        color = RenzoColors.Foreground,
        fontSize = 14.sp,
        fontWeight = FontWeight.Medium,
    )
}

/** Error well — gate-shell.tsx GateError: `rounded-md bg-red-950 p-3 text-sm text-red-500`. */
@Composable
internal fun GateErrorBox(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF450A0A)) // red-950
            .padding(12.dp),
    ) {
        Text(message, color = RenzoColors.Red500, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Notice well — Shiori AuthNoticeBox: rounded-md bg-muted, p-3. */
@Composable
internal fun GateNoticeBox(message: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(RenzoColors.Muted)
            .padding(12.dp),
    ) {
        Text(message, color = RenzoColors.MutedForeground, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

/** Gate input — Shiori AuthInput: min-h-36, rounded-8, transparent bg, 1dp border. */
@Composable
internal fun GateTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .focusRing(focused, 8.dp)
            .border(1.dp, if (focused) RenzoColors.Primary else RenzoColors.Border, RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            textStyle = TextStyle(color = RenzoColors.Foreground, fontSize = 14.sp, fontFamily = top.levitatemedia.renzo.hub.core.GeistFamily),
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            singleLine = true,
            visualTransformation = visualTransformation,
            cursorBrush = SolidColor(RenzoColors.Foreground),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, color = RenzoColors.MutedForeground, fontSize = 14.sp)
                    }
                    inner()
                }
            },
        )
    }
}

/** Gate submit — Shiori AuthPrimaryButton: min-h-36, rounded-8, text-sm Medium. */
@Composable
internal fun GateButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 36.dp)
            .clip(RoundedCornerShape(8.dp))
            .focusRing(focused, 8.dp)
            .background(
                if (focused) RenzoColors.Primary.copy(alpha = 0.9f) else RenzoColors.Primary,
                RoundedCornerShape(8.dp),
            )
            .let { m -> if (enabled) m.tvClickable(onFocused = { f -> focused = f }, onClick = onClick) else m.alpha(0.5f) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = RenzoColors.PrimaryForeground, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
