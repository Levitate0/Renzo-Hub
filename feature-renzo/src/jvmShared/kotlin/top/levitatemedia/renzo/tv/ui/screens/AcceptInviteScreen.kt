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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
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
 * Invite acceptance — renzo/frontend components/gates/invite-gate.tsx, laid
 * out like the Shiori half's SetPasswordScreen.kt and built from the Gate*
 * pieces in LoginScreen.kt.
 *
 * The web gate is reached from the invite link, which is a PATH
 * (`${publicUrl}/invite/<token>`, auth.ts inviteRoutes) — the same link
 * AdminScreens' "Invite a user" copies. It opens a browser, never the Hub, so
 * the link is pasted here and [tokenFromLink] takes the segment after
 * `/invite/` (or `?invite=` / `?token=`, or a bare token). When the paste
 * settles the invite is looked up exactly as the web does on mount: the
 * "You'll join as a <role>." note appears and a preset username is filled in
 * and locked (invite-gate.tsx:38-42).
 *
 * Success creates the account and signs in (Set-Cookie + {user}), so
 * [onLoggedIn] is the login form's own callback — the web's completeAuth.
 */
@Composable
fun AcceptInviteScreen(app: AppServices, onBackToLogin: () -> Unit, onLoggedIn: (PublicUser) -> Unit) {
    RenzoBackHandler(enabled = true) { onBackToLogin() }
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var usernameLocked by remember { mutableStateOf(false) }
    var roleNote by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var invalid by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val token = tokenFromLink(link, listOf("invite", "token"), pathPrefix = "/invite/")

    LaunchedEffect(token) {
        roleNote = ""
        invalid = false
        if (usernameLocked) { username = ""; usernameLocked = false }
        if (token.isBlank()) return@LaunchedEffect
        delay(400) // let a paste / typing settle before asking the server
        try {
            val info = app.repo.inviteInfo(token)
            if (!info.valid) {
                error = info.error ?: "Invalid invite"
                invalid = true
                return@LaunchedEffect
            }
            roleNote = "You'll join as a ${info.role}."
            if (info.presetUsername && info.username != null) {
                username = info.username
                usernameLocked = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiError) {
            // 404 = invalid/expired; the server's {error} rides on the ApiError.
            if (e.status == 0) {
                error = "Can't reach the server. Check your connection."
            } else {
                error = e.message ?: "Invalid invite"
                invalid = true
            }
        } catch (_: Exception) {
            error = "Could not load invite"
        }
    }

    fun submit() {
        if (busy || invalid) return
        error = null
        if (token.isBlank()) {
            error = "Paste your invite link (or the token from it) to continue."
            return
        }
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
                val user = app.repo.acceptInvite(token, username.trim(), password)
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
        // setup-title: text-center text-lg font-semibold
        Text(
            "You're invited to Renzo",
            color = RenzoColors.Foreground,
            fontSize = 18.sp,
            lineHeight = 28.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            if (roleNote.isEmpty()) "Create your account to join." else "Create your account to join. $roleNote",
            color = RenzoColors.MutedForeground,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        Column(Modifier.fillMaxWidth()) {
            GateLabel("Invite link or token")
            Spacer(Modifier.height(8.dp))
            GateTextField(
                value = link,
                onValueChange = { link = it; error = null },
                placeholder = "Paste the invite link you were sent",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions.Default,
            )
        }
        Column(Modifier.fillMaxWidth()) {
            GateLabel("Username")
            Spacer(Modifier.height(8.dp))
            GateTextField(
                value = username,
                onValueChange = { username = it; error = null },
                placeholder = "Choose a username",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions.Default,
                enabled = !usernameLocked,
            )
        }
        Column(Modifier.fillMaxWidth()) {
            GateLabel("Password")
            Spacer(Modifier.height(8.dp))
            GateTextField(
                value = password,
                onValueChange = { password = it; error = null },
                placeholder = "At least 8 characters",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions.Default,
                visualTransformation = PasswordVisualTransformation(),
            )
        }
        Column(Modifier.fillMaxWidth()) {
            GateLabel("Confirm password")
            Spacer(Modifier.height(8.dp))
            GateTextField(
                value = confirm,
                onValueChange = { confirm = it; error = null },
                placeholder = "Re-enter password",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                visualTransformation = PasswordVisualTransformation(),
            )
        }
        GateButton(label = "Create account & join", enabled = !busy && !invalid) { submit() }
        error?.let { GateErrorBox(it) }
        GateLinkRow("Back to login", enabled = !busy, onClick = onBackToLogin)
    }
}
