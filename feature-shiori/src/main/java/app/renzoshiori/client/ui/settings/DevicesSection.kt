package app.renzoshiori.client.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Monitor
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.renzoshiori.client.RenzoApp
import app.renzoshiori.client.data.model.RememberedDeviceDto
import app.renzoshiori.client.data.network.AuthExtraApi
import app.renzoshiori.client.data.network.serverErrorMessage
import app.renzoshiori.client.ui.queue.parseUtcMillis
import app.renzoshiori.client.ui.theme.RenzoColors
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Account → Devices — transliterated from
 * RenzoFrontend/src/components/comp/settings/devices-section.tsx.
 *
 * Every "Remember me" sign-in (and every approved TV) is a separate long-lived
 * session — 90 days by default, and a TV in a shared room is meant to stay
 * signed in indefinitely. That's only safe if you can see the list and end any
 * one of them from here, which is what this is.
 */
@Composable
fun DevicesSection(snackbar: SnackbarHostState, onLogout: () -> Unit) {
    val app = LocalContext.current.applicationContext as RenzoApp
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<RememberedDeviceDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var confirming by remember { mutableStateOf<RememberedDeviceDto?>(null) }
    var revoking by remember { mutableStateOf(false) }

    suspend fun refresh() {
        runCatching { app.network.currentServiceOf<AuthExtraApi>()?.listDevices() }
            .onSuccess { devices = it ?: emptyList() }
            .onFailure { snackbar.showSnackbar(it.serverErrorMessage("Failed to load devices")) }
        loading = false
    }
    LaunchedEffect(Unit) { refresh() }

    // Web: intro copy above the list.
    Text(
        buildString {
            append("Each time you sign in with Remember me — or approve a television — that ")
            append("device gets its own long-lived session (90 days), renewed every time ")
            append("it's used. Sign one out here and the rest stay signed in.")
        },
        style = MaterialTheme.typography.bodySmall,
        color = RenzoColors.MutedForeground,
    )
    Spacer(Modifier.height(16.dp))

    when {
        loading -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 20.dp)) {
            CircularProgressIndicator(color = RenzoColors.Primary, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("Loading…", style = MaterialTheme.typography.bodySmall, color = RenzoColors.MutedForeground)
        }

        devices.isEmpty() -> EmptyNote(
            "No remembered devices. Tick \"Remember me\" when you sign in — or approve a TV — and it'll show up here.",
        )

        else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            devices.forEach { d ->
                DeviceRow(device = d, onRevoke = { confirming = d })
            }
        }
    }

    // Web: confirm dialog. Revoking the CURRENT session finishes the job with
    // a full sign-out rather than leaving a half-dead session on screen.
    confirming?.let { target ->
        RenzoDialog(
            onDismiss = { if (!revoking) confirming = null },
            title = "Sign out ${target.deviceName}?",
            description = if (target.isCurrent) {
                "This is the device you're using right now — signing it out logs you out " +
                    "here immediately, and you'll need to sign in again. Your other devices " +
                    "stay signed in."
            } else {
                "${target.deviceName} will have to sign in again to reach your library. " +
                    "Your other devices are unaffected."
            },
        ) {
            Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                RenzoButton("Cancel", variant = "outline", onClick = { confirming = null })
                Spacer(Modifier.width(8.dp))
                RenzoButton(
                    text = when {
                        revoking -> "Signing out…"
                        target.isCurrent -> "Sign out & log me out"
                        else -> "Sign out"
                    },
                    variant = "destructive",
                    busy = revoking,
                    onClick = {
                        revoking = true
                        scope.launch {
                            runCatching {
                                app.network.currentServiceOf<AuthExtraApi>()?.revokeDevice(target.id)
                                    ?: error("Not connected to a server.")
                            }.onSuccess {
                                confirming = null
                                revoking = false
                                if (target.isCurrent) {
                                    onLogout()
                                } else {
                                    snackbar.showSnackbar("${target.deviceName} signed out")
                                    refresh()
                                }
                            }.onFailure {
                                revoking = false
                                snackbar.showSnackbar(it.serverErrorMessage("Failed to revoke device"))
                            }
                        }
                    },
                )
            }
        }
    }
}

/** One remembered-device row — `rounded-lg border bg-card/50 px-3 py-2.5`. */
@Composable
private fun DeviceRow(device: RememberedDeviceDto, onRevoke: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(RenzoColors.Card.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .border(1.dp, RenzoColors.Border.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            if (device.isTvPairing) Icons.Filled.Tv else Icons.Filled.Monitor,
            contentDescription = null,
            tint = if (device.isTvPairing) RenzoColors.Primary else RenzoColors.MutedForeground,
            modifier = Modifier.padding(top = 2.dp).size(16.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.deviceName,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                    color = RenzoColors.Foreground,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (device.isTvPairing) {
                    Spacer(Modifier.width(6.dp))
                    TinyPill("TV", RenzoColors.Primary)
                }
                if (device.isCurrent) {
                    Spacer(Modifier.width(6.dp))
                    TinyPill("This device", RenzoColors.Green)
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "Last used ${relativeAgo(device.lastSeenAt)} · " +
                    "${if (device.isTvPairing) "Paired" else "Signed in"} ${relativeAgo(device.createdAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = RenzoColors.MutedForeground,
            )
            Text(
                (device.createdIp?.trim()?.takeIf { it.isNotEmpty() }?.let { "From $it" } ?: "Address unknown") +
                    " · ${expiresIn(device.expiresAt)}",
                style = MaterialTheme.typography.labelSmall,
                color = RenzoColors.MutedForeground,
            )
        }
        Spacer(Modifier.width(8.dp))
        IconGhostButton(
            icon = Icons.Filled.Delete,
            contentDescription = "Sign this device out",
            tint = RenzoColors.Red,
            onClick = onRevoke,
        )
    }
}

/** Web's `rounded-full px-1.5 py-0.5 text-[9px] uppercase` badge. */
@Composable
private fun TinyPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(999.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 0.5.sp),
            color = color,
        )
    }
}

/** "3 days ago" — verbatim thresholds from devices-section.tsx `relative()`. */
private fun relativeAgo(iso: String): String {
    val millis = parseUtcMillis(iso) ?: return "Unknown"
    val seconds = Math.round((System.currentTimeMillis() - millis) / 1000.0)
    if (seconds < 60) return "Just now"
    val minutes = Math.round(seconds / 60.0)
    if (minutes < 60) return "$minutes minute${if (minutes == 1L) "" else "s"} ago"
    val hours = Math.round(minutes / 60.0)
    if (hours < 24) return "$hours hour${if (hours == 1L) "" else "s"} ago"
    val days = Math.round(hours / 24.0)
    if (days < 30) return "$days day${if (days == 1L) "" else "s"} ago"
    return DateTimeFormatter.ofPattern("M/d/yyyy")
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
}

/** Forward-facing counterpart of [relativeAgo] — expiry is always ahead. */
private fun expiresIn(iso: String): String {
    val millis = parseUtcMillis(iso) ?: return "Expiry unknown"
    val days = Math.round((millis - System.currentTimeMillis()) / 86_400_000.0)
    return when {
        days < 0 -> "Expired"
        days == 0L -> "Expires today"
        else -> "Expires in $days day${if (days == 1L) "" else "s"}"
    }
}
