package top.levitatemedia.renzo.tv.ui.screens

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
import top.levitatemedia.renzo.tv.ui.kit.CardDivider
import top.levitatemedia.renzo.tv.ui.kit.ErrorBox
import top.levitatemedia.renzo.tv.ui.kit.Hint
import top.levitatemedia.renzo.tv.ui.kit.RenzoButton
import top.levitatemedia.renzo.tv.ui.kit.SettingsCard
import top.levitatemedia.renzo.tv.ui.kit.SwitchRow
import top.levitatemedia.renzo.tv.ui.kit.WarningBox

/**
 * Account → Notifications (Android phones only): the switch for every anime in
 * the library. Each title's own switch is the bell on its page. The Shiori
 * half's twin is NotificationsSection.
 */
@Composable
internal fun NotificationsPane() {
    val platform = ReleaseAlerts.platform ?: return
    var all by remember { mutableStateOf(ReleaseAlerts.allOn(HubTarget.Renzo)) }
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

    SettingsCard(
        title = "Notifications",
        description = "On this device only. Get a notification when an anime in your library " +
            "has a new episode.",
    ) {
        SwitchRow(
            checked = all,
            onCheckedChange = { on ->
                all = on
                ReleaseAlerts.setAllOn(HubTarget.Renzo, on)
                if (on) ReleaseAlerts.switchedOn()
            },
            label = "New episodes for every anime in my library",
            hint = "Titles you switch on or off with the bell on their page keep their own setting.",
        )
        Hint(
            "Only new episodes notify, about an hour after they air. A better release of an " +
                "episode you already had, or a re-download, never does. Renzo Hub checks about " +
                "every half hour.",
        )
        if (!permitted && ReleaseAlerts.anyOn(HubTarget.Renzo)) {
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
        if (permitted && ReleaseAlerts.anyOn(HubTarget.Renzo)) {
            BackgroundChecks(limited, platform, "episodes")
        }
    }
}

/** The Shiori half's NotificationsSection twin: whether Android lets the check run while the phone sleeps. */
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
