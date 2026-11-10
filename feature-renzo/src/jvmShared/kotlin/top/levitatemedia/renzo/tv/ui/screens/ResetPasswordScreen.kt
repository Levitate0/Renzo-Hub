package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.hub.core.tokenFromLink
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.RenzoBackHandler
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.PublicUser
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/**
 * Password reset — renzo/frontend components/gates/reset-gate.tsx, laid out
 * like the Shiori half's ResetPasswordScreen.kt and built from the Gate*
 * pieces in LoginScreen.kt.
 *
 * The web gate is reached from the emailed `/?reset=<token>` link. That link
 * opens a browser, never the Hub, so the token is a pasted field here; the
 * whole link is accepted and [tokenFromLink] lifts the token out of it. Once
 * the paste settles the token is validated exactly as the web does first
 * (GET /api/auth/reset/:token), the "Set a new password for <username>" line
 * fills in, and submit stays disabled until it has (reset-gate.tsx:112).
 *
 * One deviation: an invalid link shows its error in place instead of toasting
 * and bouncing to the login gate — the user typed it, so a typo should be
 * fixable without starting over.
 *
 * Success signs straight in: the server sets the session cookie and returns
 * the user (the web's completeAuth(data.user)), so [onLoggedIn] is the same
 * callback the login form uses.
 */
@Composable
fun ResetPasswordScreen(app: AppServices, onBackToLogin: () -> Unit, onLoggedIn: (PublicUser) -> Unit) {
    RenzoBackHandler(enabled = true) { onBackToLogin() }
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var username by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // The emailed link is `${publicUrl}/?reset=<token>` (auth.ts forgot).
    val token = tokenFromLink(link, listOf("reset", "token"))

    LaunchedEffect(token) {
        username = null
        if (token.isBlank()) return@LaunchedEffect
        delay(400) // let a paste / typing settle before asking the server
        try {
            val info = app.repo.resetInfo(token)
            if (!info.valid) {
                error = info.error ?: "This reset link is invalid or has expired"
                return@LaunchedEffect
            }
            username = info.username ?: "your account"
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            error = if (e.status == 0) "Can't reach the server. Check your connection."
            else e.message ?: "This reset link is invalid or has expired"
        } catch (_: Exception) {
            error = "Could not open reset link"
        }
    }

    fun submit() {
        if (busy || username == null) return
        error = null
        if (password.length < 8) {
            error = "Password must be at least 8 characters"
            return
        }
        if (password != confirm) {
            error = "Passwords don't match"
            return
        }
        busy = true
        scope.launch {
            try {
                val user = app.repo.resetPassword(token, password)
                busy = false
                onLoggedIn(user)
            } catch (e: CancellationException) {
                throw e
            } catch (e: ApiError) {
                error = if (e.status == 0) "Can't reach the server. Check your connection." else e.message ?: "failed"
                busy = false
            } catch (e: Exception) {
                error = e.message ?: "failed"
                busy = false
            }
        }
    }

    GateCard(isTv = app.isTv) {
        GateBanner()
        Text(
            if (token.isBlank()) {
                buildAnnotatedString { append("Paste the link from your reset email to set a new password.") }
            } else {
                buildAnnotatedString {
                    append("Set a new password for ")
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = RenzoColors.Foreground)) {
                        append(username ?: "…")
                    }
                    append(".")
                }
            },
            color = RenzoColors.MutedForeground,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(Modifier.fillMaxWidth()) {
            GateLabel("Reset link or token")
            Spacer(Modifier.height(8.dp))
            GateTextField(
                value = link,
                onValueChange = { link = it; error = null },
                placeholder = "Paste the link from your reset email",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions.Default,
            )
        }
        GateTextField(
            value = password,
            onValueChange = { password = it; error = null },
            placeholder = "New password (min 8)",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions.Default,
            visualTransformation = PasswordVisualTransformation(),
        )
        GateTextField(
            value = confirm,
            onValueChange = { confirm = it; error = null },
            placeholder = "Confirm new password",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { submit() }),
            visualTransformation = PasswordVisualTransformation(),
        )
        GateButton(label = "Set password & sign in", enabled = !busy && username != null) { submit() }
        error?.let { GateErrorBox(it) }
        GateLinkRow("Back to login", enabled = !busy, onClick = onBackToLogin)
    }
}
