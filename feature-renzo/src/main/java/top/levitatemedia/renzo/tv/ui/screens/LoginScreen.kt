package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.platform.LocalConfiguration
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
 * Sign-in gate — literal translation of the web login gate (login-gate.tsx +
 * gate-shell.tsx): banner, "Sign in to your account.", username/password,
 * "Remember me on this device", Sign in, error line, "Forgot password?".
 * Server is already known (Prefs.serverUrl); cookie is captured by ApiClient.
 */
@Composable
fun LoginScreen(app: AppServices, onLoggedIn: (PublicUser) -> Unit) {
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
        GateBanner()
        Text(
            "Sign in to your account.",
            color = RenzoColors.MutedForeground,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        if (setupRequired) {
            Text(
                "This server has no accounts yet — open it in a web browser to finish setup first.",
                color = RenzoColors.MutedForeground,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        GateTextField(
            value = username,
            onValueChange = { username = it; error = null },
            placeholder = "Username",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
            keyboardActions = KeyboardActions.Default,
        )
        GateTextField(
            value = password,
            onValueChange = { password = it; error = null },
            placeholder = "Password",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            visualTransformation = PasswordVisualTransformation(),
        )
        RememberRow(rememberMe) { rememberMe = !rememberMe }
        // Web keeps the label while busy — just disabled:opacity-50.
        GateButton(label = "Sign in", enabled = !busy) { submit() }
        // GateError — min-h-4 reserved line, text-sm text-destructive.
        Box(Modifier.fillMaxWidth().heightIn(min = 16.dp)) {
            when {
                error != null -> Text(error ?: "", color = RenzoColors.Destructive, fontSize = 14.sp, lineHeight = 20.sp)
                notice != null -> Text(
                    notice ?: "",
                    color = RenzoColors.MutedForeground,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
        ForgotPasswordLink(enabled = !busy) { forgot() }
        app.prefs.serverUrl?.let {
            Text(
                it,
                color = RenzoColors.MutedForeground,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

/** "Forgot password?" — link-button, muted → underlined on focus. */
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
                .clip(RoundedCornerShape(4.dp))
                .focusRing(focused, 4.dp)
                .let { m -> if (enabled) m.tvClickable(onFocused = { f -> focused = f }, onClick = onClick) else m.alpha(0.5f) }
                .padding(horizontal = 4.dp, vertical = 2.dp),
        )
    }
}

/** "Remember me on this device" — the web gate's 16px accent-primary checkbox. */
@Composable
private fun RememberRow(checked: Boolean, onToggle: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .focusRing(focused, 4.dp)
            .tvClickable(onFocused = { focused = it }, onClick = onToggle)
            .padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(16.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(if (checked) RenzoColors.Primary else Color.Transparent, RoundedCornerShape(3.dp))
                .border(1.dp, if (checked) RenzoColors.Primary else RenzoColors.MutedForeground, RoundedCornerShape(3.dp)),
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
        Spacer(Modifier.width(8.dp))
        Text("Remember me on this device", color = RenzoColors.MutedForeground, fontSize = 14.sp)
    }
}

// --- GateShell pieces (gate-shell.tsx), shared by the auth gates -------------

/**
 * The auth-gate card: top-aligned overlay (py-[8dvh]) holding a max-w-sm
 * rounded-xl bordered bg-card p-6 column with gap-3.
 */
@Composable
internal fun GateCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val vPad = (LocalConfiguration.current.screenHeightDp * 0.08f).dp
    Column(
        Modifier
            .fillMaxSize()
            .background(RenzoColors.Background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = vPad),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier
                .widthIn(max = 384.dp)
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

/** GateBanner — Renzo's wordmark, w-48 centered (mb-1 via the card's gap). */
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

/** Gate input: rounded-md border border-input bg-background px-3 py-2 text-sm. */
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
            .height(38.dp)
            .clip(RoundedCornerShape(6.dp))
            .focusRing(focused, 6.dp)
            .background(RenzoColors.Background, RoundedCornerShape(6.dp))
            .border(1.dp, if (focused) RenzoColors.Primary else RenzoColors.Input, RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp),
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
            cursorBrush = SolidColor(RenzoColors.Primary),
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

/** Gate submit: w-full rounded-md bg-primary py-2 text-sm font-semibold —
 *  36dp (20px line + 2×8px pad, borderless; the inputs' extra 2dp is border). */
@Composable
internal fun GateButton(label: String, enabled: Boolean = true, onClick: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clip(RoundedCornerShape(6.dp))
            .focusRing(focused, 6.dp)
            .background(
                if (focused) RenzoColors.Primary.copy(alpha = 0.9f) else RenzoColors.Primary,
                RoundedCornerShape(6.dp),
            )
            .let { m -> if (enabled) m.tvClickable(onFocused = { f -> focused = f }, onClick = onClick) else m.alpha(0.5f) },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = RenzoColors.PrimaryForeground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}
