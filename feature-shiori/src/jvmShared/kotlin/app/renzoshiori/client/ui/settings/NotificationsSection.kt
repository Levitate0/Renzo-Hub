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
    // Re-read while shown: the answer changes in the system dialog or in
    // Android's settings, and nothing tells the page.
    LaunchedEffect(Unit) {
        while (true) {
            permitted = platform.permitted()
            delay(1_000)
        }
    }

    SwitchRow(
        checked = all,
        onCheckedChange = { on ->
            all = on
            ReleaseAlerts.setAllOn(HubTarget.Shiori, on)
            if (on && !platform.permitted()) platform.requestPermission()
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
}
