package app.renzoshiori.client.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.hub.core.notify.ReleaseAlerts

/**
 * Account → Notifications (Android phones only): the switch for every series
 * in the library. Each series' own switch is the bell on its page.
 */
@Composable
fun NotificationsSection() {
    val platform = ReleaseAlerts.platform ?: return
    var all by remember { mutableStateOf(ReleaseAlerts.allOn(HubTarget.Shiori)) }
    var permitted by remember { mutableStateOf(platform.permitted()) }
    var limited by remember { mutableStateOf(platform.backgroundLimited()) }
    // Re-read while shown: the answers change in the system dialogs or in
    // Android's settings, and nothing tells the page.
    LaunchedEffect(Unit) {
        while (true) {
            permitted = platform.permitted()
            limited = platform.backgroundLimited()
            delay(1_000)
        }
    }

    SwitchRow(
        checked = all,
        onCheckedChange = { on ->
            all = on
            ReleaseAlerts.setAllOn(HubTarget.Shiori, on)
            if (on) ReleaseAlerts.switchedOn()
        },
        label = "New chapters for every series in my library",
        hint = "Series you switch on or off with the bell on their page keep their own setting.",
    )
    Hint(
        "Only new chapters notify. A chapter you already had, added again or replaced by another " +
            "source, or an older one filled in by a backup source, never does. Renzo Hub checks " +
            "about every half hour.",
    )
    if (!permitted && ReleaseAlerts.anyOn(HubTarget.Shiori)) {
        CardDivider()
        ErrorBox("Android isn't letting Renzo Hub show notifications, so none will appear.")
        Spacer(Modifier.height(8.dp))
        RenzoButton(
            text = "Allow notifications",
            variant = "outline",
            small = true,
            onClick = { platform.requestPermission() },
        )
    }
    if (permitted && ReleaseAlerts.anyOn(HubTarget.Shiori)) {
        BackgroundChecks(limited, platform, "chapters")
    }
}

/**
 * Whether Android lets the check run while the phone sleeps. Held back, new
 * releases only turn up around the times the app is open. Shared with the
 * anime half's Notifications pane, word for word.
 */
@Composable
private fun BackgroundChecks(limited: Boolean, platform: ReleaseAlerts.Platform, what: String) {
    if (limited) {
        CardDivider()
        WarningBox(
            "Android pauses Renzo Hub's checks while your phone sleeps, so new $what can show up " +
                "hours late, or only once you open the app.",
        )
        RenzoButton(
            text = "Allow background checks",
            variant = "outline",
            small = true,
            onClick = { platform.allowBackground() },
        )
    }
    if (platform.hasSleepingAppsList) {
        Spacer(Modifier.height(8.dp))
        Hint(
            "On a Samsung phone, also keep Renzo Hub out of Sleeping apps and Deep sleeping apps " +
                "(Settings → Battery → Background usage limits). Samsung stops apps on those lists " +
                "whatever the setting above says.",
        )
    }
}
