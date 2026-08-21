package top.levitatemedia.renzo.hub

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import app.renzoshiori.client.ShioriRoot
import top.levitatemedia.renzo.hub.core.HubForeground
import top.levitatemedia.renzo.hub.core.HubSession
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.tv.RenzoHost
import top.levitatemedia.renzo.tv.RenzoRoot

/**
 * The Hub's single entry point, for phones and TV both.
 *
 * One activity hosts both halves, so "Switch to <app>" is a recomposition
 * rather than a task-stack change, and the TV leanback entry can bypass the
 * picker entirely.
 */
/**
 * How long the Hub must be in the background before returning to it counts as
 * a fresh start rather than a glance away. Uses elapsedRealtime, so it keeps
 * counting while the device is asleep.
 */
private const val SLEEP_THRESHOLD_MS = 5 * 60 * 1000L

class HubActivity : ComponentActivity() {
    private var renzoHost: RenzoHost? = null

    private fun renzoHost(): RenzoHost =
        renzoHost ?: RenzoHost(this).also { renzoHost = it }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Draw behind the transparent system bars; each half pads its own
        // content by the insets. Keeps the clock/notification bar visible with
        // the app's background showing through.
        WindowCompat.setDecorFitsSystemWindows(window, false)

        setContent {
            // The picker is shown every time the app comes to the foreground,
            // not just on a cold start. Deliberately `remember` rather than
            // `rememberSaveable`: the choice must not be restored from saved
            // instance state either, or a system-killed process would come back
            // straight into a half instead of the picker.
            var target by remember { mutableStateOf<HubTarget?>(null) }

            val choose: (HubTarget) -> Unit = { picked -> target = picked }

            // The picker comes back when the app has been AWAY for a while —
            // not every time it is brought forward. Glancing at another app and
            // returning should land you where you were; coming back to it the
            // next day should not silently resume a stale session.
            //
            // Driven off ON_STOP/ON_START, because being brought forward has no
            // event of its own that distinguishes it from a first launch. A
            // killed process shows the picker regardless, since `target` is
            // plain `remember`.
            val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            DisposableEffect(owner) {
                var stoppedAt = 0L
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    when (event) {
                        androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                            // A rotation, or a launched SAF picker / OAuth page /
                            // speech recogniser, is not the user leaving.
                            stoppedAt = when {
                                isChangingConfigurations -> 0L
                                HubForeground.consumeSuppression() -> 0L
                                else -> android.os.SystemClock.elapsedRealtime()
                            }
                        }
                        androidx.lifecycle.Lifecycle.Event.ON_START -> {
                            if (stoppedAt == 0L) return@LifecycleEventObserver
                            val away = android.os.SystemClock.elapsedRealtime() - stoppedAt
                            stoppedAt = 0L
                            if (away >= SLEEP_THRESHOLD_MS) target = null
                        }
                        else -> Unit
                    }
                }
                owner.lifecycle.addObserver(observer)
                onDispose { owner.lifecycle.removeObserver(observer) }
            }

            LaunchedEffect(target) { target?.let { HubSession.setActive(it) } }

            TvResolutionNormalized {
                when (target) {
                    null -> HubPicker(onPick = choose)

                    HubTarget.Renzo -> RenzoRoot(
                        host = renzoHost(),
                        onSwitchApp = { choose(HubTarget.Shiori) },
                        // The URL and login gates keep an escape hatch: a wrong
                        // address or a one-service household must never be stuck
                        // in the other half's sign-in.
                        onBackToPicker = { target = null },
                    )

                    HubTarget.Shiori -> ShioriRoot(
                        onSwitchApp = { choose(HubTarget.Renzo) },
                        onBackToPicker = { target = null },
                    )
                }
            }
        }
    }

    /** singleTask: a second `am start` reuses this instance. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        renzoHost?.onNewIntent(this, intent)
    }
}

@Composable
private fun HubPicker(onPick: (HubTarget) -> Unit) {
    PickerScreen(onPick = onPick)
}

/**
 * One TV layout for every panel resolution (user direction 2026-08-21).
 *
 * The whole TV app was designed against the ~540dp of height a 1080p set
 * reports at xhdpi. Panels and boxes disagree on density: a 4K set that
 * reports a LOWER density hands the same layout 1080dp and everything renders
 * half-size and dense. GROW-ONLY scaling by `panelHeightDp / 540` makes a 2K
 * or 4K panel draw the identical apparent UI a 1080p set gets — and a normal
 * 540dp panel passes through at exactly 1:1 (minScale = 1 means phones and
 * standard TVs are untouched).
 */
@Composable
private fun TvResolutionNormalized(content: @Composable () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val isTv = remember(context) { top.levitatemedia.renzo.hub.core.isTvDevice(context) }
    if (isTv) {
        top.levitatemedia.renzo.hub.core.TvScale(
            designHeightDp = 540,
            minScale = 1f,
            content = content,
        )
    } else {
        content()
    }
}
