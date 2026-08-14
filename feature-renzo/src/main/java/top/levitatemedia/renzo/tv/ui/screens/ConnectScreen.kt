package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Text
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/**
 * First-run gate: ask for the self-hosted server's address, probe it, save it.
 * Keeps its own copy; the card/field/button styling matches the web auth gates
 * (gate-shell.tsx — see the shared Gate* pieces in LoginScreen.kt).
 */
@Composable
fun ConnectScreen(app: AppServices, onConnected: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    fun submit() {
        if (busy) return
        val url = normalizeServerUrl(address)
        if (url == null) {
            error = "Enter your server's address."
            return
        }
        pending = url
        attempt++
    }

    LaunchedEffect(attempt) {
        if (attempt == 0) return@LaunchedEffect
        val url = pending ?: return@LaunchedEffect
        busy = true
        error = null
        val working = try {
            when {
                app.client.probe(url) -> url
                url.startsWith("https://") -> {
                    // LAN servers rarely have certs — quietly retry plain http.
                    val plain = "http://" + url.removePrefix("https://")
                    if (app.client.probe(plain)) plain else null
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
        busy = false
        if (working != null) {
            app.prefs.serverUrl = working
            onConnected()
        } else {
            error = "Couldn't reach a Renzo server at that address."
        }
    }

    GateCard {
        GateBanner()
        Text(
            "Connect to your Renzo server",
            color = RenzoColors.Foreground,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "Renzo plays from a server you host. Enter its address to get started.",
            color = RenzoColors.MutedForeground,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 20.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        GateTextField(
            value = address,
            onValueChange = { address = it; error = null },
            placeholder = "e.g. 192.168.1.10:8787",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        GateButton(
            label = if (busy) "Connecting…" else "Connect",
            enabled = !busy,
        ) { submit() }
        // Reserved status line — mirrors the login gate's GateError slot.
        Box(Modifier.fillMaxWidth().heightIn(min = 16.dp)) {
            when {
                busy -> Text(
                    "Checking ${pending ?: ""}…",
                    color = RenzoColors.MutedForeground,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
                error != null -> Text(
                    error ?: "",
                    color = RenzoColors.Destructive,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                )
            }
        }
    }
}

/** trim → strip trailing slashes → default to https:// when no scheme. */
private fun normalizeServerUrl(input: String): String? {
    var s = input.trim().trimEnd('/')
    if (s.isEmpty()) return null
    if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
        s = "https://$s"
    }
    return s.trimEnd('/')
}
