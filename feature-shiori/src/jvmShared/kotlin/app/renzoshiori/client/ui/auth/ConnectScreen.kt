package app.renzoshiori.client.ui.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.renzoshiori.client.resources.Res
import app.renzoshiori.client.resources.renzo_login_banner
import app.renzoshiori.client.ui.theme.RenzoColors
import app.renzoshiori.client.ui.tv.LocalIsTv
import org.jetbrains.compose.resources.painterResource

/**
 * The connect (server URL) gate, on the SAME card system as the login page
 * (AuthPageScaffold/AuthCard) so the whole pre-auth flow reads as one design:
 * Shiori's own banner, "Connect to your server" description, a labelled
 * Server address field with the example as a hint, and a full-width primary
 * Connect button.
 */
@Composable
fun ConnectScreen(
    loading: Boolean,
    error: String?,
    onConnect: (String) -> Unit,
    /** Hub only: escape back to the app picker — a wrong URL must never strand anyone here. */
    onBackToPicker: (() -> Unit)? = null,
) {
    var address by remember { mutableStateOf("") }
    val isTv = LocalIsTv.current
    val fieldRequester = remember { FocusRequester() }

    fun submit() {
        if (!loading && address.isNotBlank()) onConnect(address)
    }

    // On a remote there is no tap to place the cursor: land in the one field
    // this screen has, so the leanback IME is one Centre press away.
    LaunchedEffect(isTv) {
        if (isTv) runCatching { fieldRequester.requestFocus() }
    }

    AuthPageScaffold {
        AuthCard {
            AuthCardHeader(spacing = 12.dp) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Image(
                        painter = painterResource(Res.drawable.renzo_login_banner),
                        contentDescription = "Renzo Shiori",
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.widthIn(max = 256.dp).fillMaxWidth(),
                    )
                }
                AuthCardDescription("Connect to your server")
            }
            AuthCardContent {
                if (error != null) {
                    AuthErrorBox(error)
                    Spacer(Modifier.height(16.dp))
                }

                AuthLabel("Server address")
                Spacer(Modifier.height(8.dp))
                AuthInput(
                    value = address,
                    onValueChange = { address = it },
                    placeholder = "https://renzo-shiori.example.com",
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                    onImeAction = { submit() },
                    modifier = Modifier.focusRequester(fieldRequester),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "e.g. https://renzo-shiori.example.com or http://192.168.1.10:8080",
                    color = RenzoColors.MutedForeground,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(16.dp))

                AuthPrimaryButton(
                    text = if (loading) "Connecting…" else "Connect",
                    onClick = { submit() },
                    enabled = !loading && address.isNotBlank(),
                )
                if (loading) {
                    Spacer(Modifier.height(16.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            color = RenzoColors.Primary,
                            modifier = Modifier.size(28.dp),
                            strokeWidth = 3.dp,
                        )
                    }
                }
                if (onBackToPicker != null) {
                    Spacer(Modifier.height(16.dp))
                    AuthLinkRow("Back to app picker", onBackToPicker)
                }
            }
        }
    }
}
