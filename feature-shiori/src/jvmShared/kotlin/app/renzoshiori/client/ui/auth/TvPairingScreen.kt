package app.renzoshiori.client.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.renzoshiori.client.data.model.UserDto
import app.renzoshiori.client.ui.theme.RenzoColors
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import top.levitatemedia.renzo.hub.core.tv.TvCodeResult
import top.levitatemedia.renzo.hub.core.tv.TvPairingClient
import top.levitatemedia.renzo.hub.core.tv.TvPollState

/**
 * Sign in to a television without typing a password into it.
 *
 * The TV shows a short code and the address of its own server's approval page;
 * the user approves from any browser where typing is bearable. What comes back
 * is exactly what a "remember me" login returns, so nothing downstream knows a
 * TV was involved.
 *
 * This exists because the people who most need TV access are the ones with no
 * second device to "just set it up on" — and because the alternative was
 * turning off authentication for the whole server so profiles could be picked
 * from a list, which removes passwords for every account on the instance.
 */
@Composable
fun TvPairingScreen(
    serverUrl: String,
    deviceName: String,
    onPaired: (token: String, user: UserDto, username: String?) -> Unit,
    onUsePassword: () -> Unit,
) {
    val client = remember(serverUrl) { TvPairingClient(serverUrl) }
    var code by remember { mutableStateOf<String?>(null) }
    var verificationUrl by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }

    LaunchedEffect(attempt) {
        error = null
        code = null
        val requested = when (val r = client.requestCode(deviceName)) {
            is TvCodeResult.Granted -> r.code
            // Transient: the server's pairing table is full. Its own message
            // says so, and the Try-again button below is the recovery — this
            // must NOT present as a missing feature.
            is TvCodeResult.Busy -> {
                error = r.message
                return@LaunchedEffect
            }
            // The server predates pairing, or it is unreachable. Either way
            // the password form is the only way in.
            TvCodeResult.Unsupported -> {
                error = "This server doesn't support sign-in by code."
                return@LaunchedEffect
            }
        }
        code = requested.userCode
        verificationUrl = requested.verificationUrl

        when (val result = client.awaitApproval(requested) { secondsLeft = it }) {
            is TvPollState.Approved -> {
                val parsed = runCatching {
                    val root = Json { ignoreUnknownKeys = true; isLenient = true }
                        .parseToJsonElement(result.body).jsonObject
                    val token = root["token"]!!.toString().trim('"')
                    val user = Json { ignoreUnknownKeys = true; isLenient = true }
                        .decodeFromString<UserDto>(root["user"].toString())
                    token to user
                }.getOrNull()
                if (parsed == null) {
                    error = "The server approved this device but sent something unreadable."
                } else {
                    onPaired(parsed.first, parsed.second, parsed.second.username)
                }
            }
            is TvPollState.Failed -> error = result.reason
            TvPollState.Pending -> Unit // awaitApproval only returns terminal states
        }
    }

    AuthPageScaffold {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Sign in from your phone or computer",
                style = MaterialTheme.typography.titleMedium,
                color = RenzoColors.Foreground,
                textAlign = TextAlign.Center,
            )

            when {
                error != null -> {
                    Text(
                        error!!,
                        color = RenzoColors.MutedForeground,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Button(
                        onClick = { attempt++ },
                        colors = ButtonDefaults.buttonColors(containerColor = RenzoColors.Primary),
                    ) { Text("Try again") }
                }

                code == null -> CircularProgressIndicator(color = RenzoColors.Primary)

                else -> {
                    Text(
                        "Go to",
                        color = RenzoColors.MutedForeground,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        verificationUrl.orEmpty(),
                        color = RenzoColors.Foreground,
                        style = MaterialTheme.typography.titleSmall,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "and enter this code",
                        color = RenzoColors.MutedForeground,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    // Deliberately oversized: this is read off a screen from
                    // across a room, which is the entire point of the feature.
                    Text(
                        code!!,
                        color = RenzoColors.Primary,
                        fontSize = 48.sp,
                        letterSpacing = 6.sp,
                        textAlign = TextAlign.Center,
                    )
                    if (secondsLeft > 0) {
                        Text(
                            "Expires in ${secondsLeft / 60}:${(secondsLeft % 60).toString().padStart(2, '0')}",
                            color = RenzoColors.MutedForeground,
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }

            Button(
                onClick = onUsePassword,
                colors = ButtonDefaults.buttonColors(containerColor = RenzoColors.Muted),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                modifier = Modifier.padding(top = 10.dp),
            ) { Text("Type a username and password instead", color = RenzoColors.Foreground) }
        }
    }
}
