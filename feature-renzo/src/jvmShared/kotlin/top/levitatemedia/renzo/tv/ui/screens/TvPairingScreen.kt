package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import top.levitatemedia.renzo.hub.core.tv.TvCodeResult
import top.levitatemedia.renzo.hub.core.tv.TvPairingClient
import top.levitatemedia.renzo.hub.core.tv.TvPollState
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.PublicUser
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/**
 * Renzo's TV sign-in by pairing code (HANDOFFrenzohub_tvcode.md): the TV shows
 * an 8-character code and the server's approval URL; the user approves from
 * any browser. The approval response carries the ordinary `fsa_session`
 * cookie — stored exactly where a password login would have put it, so
 * nothing downstream knows a TV was involved. Wrapped in the same gate card
 * as the login/connect screens so the auth flow reads as one surface.
 */
@Composable
fun RenzoTvPairingScreen(
    app: AppServices,
    onPaired: (PublicUser) -> Unit,
    onUsePassword: () -> Unit,
) {
    val serverUrl = app.prefs.serverUrl.orEmpty()
    val client = remember(serverUrl) { TvPairingClient(serverUrl) }
    var code by remember { mutableStateOf<String?>(null) }
    var verificationUrl by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        code = null
        val requested = when (val r = client.requestCode(top.levitatemedia.renzo.hub.core.HubPlatform.deviceName)) {
            is TvCodeResult.Granted -> r.code
            // Transient (503 pairing table full / 429 this caller throttled):
            // the server's own message + the Try-again button below.
            is TvCodeResult.Busy -> {
                error = r.message
                return@LaunchedEffect
            }
            TvCodeResult.Unsupported -> {
                error = "This server doesn't support sign-in by code."
                return@LaunchedEffect
            }
        }
        // Typed-friendly display (ABCD-2345); the server normalises input,
        // so the dash costs nothing to whoever copies it.
        code = requested.userCode.let {
            if (it.length == 8 && '-' !in it) "${it.take(4)}-${it.drop(4)}" else it
        }
        verificationUrl = requested.verificationUrl

        when (val result = client.awaitApproval(requested) { secondsLeft = it }) {
            is TvPollState.Approved -> {
                // The credential is the fsa_session cookie on the approval
                // response — persist it exactly like ApiClient.raw() does.
                val cookie = result.setCookies.firstNotNullOfOrNull { c ->
                    Regex("^fsa_session=([^;]*)").find(c)?.groupValues?.get(1)
                }?.takeIf { it.isNotEmpty() }
                val user = runCatching {
                    val root = Json { ignoreUnknownKeys = true; isLenient = true }
                        .parseToJsonElement(result.body).jsonObject
                    Json { ignoreUnknownKeys = true; isLenient = true }
                        .decodeFromString<PublicUser>(root["user"].toString())
                }.getOrNull()
                if (cookie == null || user == null) {
                    error = "The server approved this device but sent something unreadable."
                } else {
                    app.prefs.sessionCookie = cookie
                    onPaired(user)
                }
            }
            is TvPollState.Failed -> error = result.reason
            TvPollState.Pending -> Unit // awaitApproval only returns terminal states
        }
    }

    GateCard(isTv = app.isTv) {
        GateBanner()
        Text(
            "Sign in from your phone or computer",
            color = RenzoColors.MutedForeground,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(4.dp))

        when {
            error != null -> {
                GateErrorBox(error ?: "")
                GateButton(label = "Try again") { attempt++ }
            }

            code == null -> androidx.compose.foundation.layout.Box(
                Modifier.fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                CircularProgressIndicator(color = RenzoColors.Primary)
            }

            else -> {
                Text(
                    "Go to",
                    color = RenzoColors.MutedForeground,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    verificationUrl.orEmpty(),
                    color = RenzoColors.Foreground,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "and enter this code",
                    color = RenzoColors.MutedForeground,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                // Oversized on purpose: read off a screen from across a room.
                Text(
                    code!!,
                    color = RenzoColors.Primary,
                    fontSize = 44.sp,
                    letterSpacing = 5.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (secondsLeft > 0) {
                    Text(
                        "Expires in ${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}",
                        color = RenzoColors.MutedForeground,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        GateLinkRow("Type a username and password instead", onClick = onUsePassword)
    }
}
