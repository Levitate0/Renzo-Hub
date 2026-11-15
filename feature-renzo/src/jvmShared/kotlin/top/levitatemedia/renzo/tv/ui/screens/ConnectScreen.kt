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
import androidx.compose.material3.Text
import top.levitatemedia.renzo.hub.core.isCertificateFailure
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/**
 * First-run gate: ask for the self-hosted server's address, probe it, save it.
 * Keeps its own copy; the card/field/button styling matches the web auth gates
 * (gate-shell.tsx — see the shared Gate* pieces in LoginScreen.kt).
 */
@Composable
fun ConnectScreen(app: AppServices, onBackToPicker: (() -> Unit)? = null, onConnected: () -> Unit) {
    var address by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    // https failed but this http:// address answered: offered, never taken silently.
    var insecureOffer by remember { mutableStateOf<String?>(null) }
    var pendingTypedScheme by remember { mutableStateOf(false) }

    fun submit() {
        if (busy) return
        val url = normalizeServerUrl(address)
        if (url == null) {
            error = "Enter your server's address."
            return
        }
        insecureOffer = null
        pendingTypedScheme = schemeTyped(address)
        pending = url
        attempt++
    }

    fun accept(url: String) {
        app.prefs.serverUrl = url
        onConnected()
    }

    // No silent downgrade. This used to retry http:// after ANY https failure
    // (a TLS error included) and save whichever answered, so whoever could
    // break TLS on the path got the password and session in the clear. Now an
    // address typed with a scheme is used exactly as typed, a certificate
    // failure is reported rather than worked around, and a bare address that
    // only answers over http is offered behind a "Not secure" warning — LAN
    // servers by IP rarely have a certificate.
    LaunchedEffect(attempt) {
        if (attempt == 0) return@LaunchedEffect
        val url = pending ?: return@LaunchedEffect
        busy = true
        error = null
        val probe = app.client.probeDetailed(url)
        when {
            probe.getOrNull() == true -> { busy = false; accept(url); return@LaunchedEffect }
            isCertificateFailure(probe.exceptionOrNull()) -> error =
                "That server's HTTPS certificate isn't trusted (expired, self-signed, or something is " +
                    "intercepting the connection), so Renzo Hub won't sign in to it. Fix the certificate, " +
                    "or, for a server on your own network, enter its address starting with http://."
            pendingTypedScheme -> error = "Couldn't reach a Renzo server at that address."
            else -> {
                val plain = "http://" + url.removePrefix("https://")
                if (app.client.probe(plain)) insecureOffer = plain
                else error = "Couldn't reach a Renzo server at that address."
            }
        }
        busy = false
    }

    GateCard(isTv = app.isTv) {
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
            onValueChange = { address = it; error = null; insecureOffer = null },
            placeholder = "e.g. 192.168.1.10:8787",
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )
        val offer = insecureOffer
        if (offer != null) {
            Text(
                "Not secure: there's no HTTPS at this address, only $offer. Your password and session " +
                    "would travel unencrypted, readable by anyone on the network in between. Only " +
                    "continue for a server on a network you trust.",
                color = RenzoColors.Destructive,
                fontSize = 14.sp,
                lineHeight = 20.sp,
                modifier = Modifier.fillMaxWidth(),
            )
            GateButton(label = "Connect anyway (not secure)") { accept(offer) }
            GateLinkRow("Cancel") { insecureOffer = null }
        } else {
            GateButton(
                label = if (busy) "Connecting…" else "Connect",
                enabled = !busy,
            ) { submit() }
        }
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
        if (onBackToPicker != null) {
            GateLinkRow("Back to app picker", onClick = onBackToPicker)
        }
    }
}

private fun schemeTyped(input: String): Boolean = input.trim().let {
    it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true)
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
