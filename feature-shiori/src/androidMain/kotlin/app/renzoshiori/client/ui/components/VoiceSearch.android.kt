package app.renzoshiori.client.ui.components

import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import top.levitatemedia.renzo.hub.core.HubForeground

/**
 * Voice search, resolved defensively.
 *
 * Not every television ships a speech recogniser — plenty of cheap sets and
 * most sideloaded/AOSP boxes have none — so this resolves the intent up front
 * and returns `null` when nothing handles it. Callers hide the mic entirely in
 * that case rather than offering a button that throws.
 *
 * Requires the `<queries>` entry for `android.speech.action.RECOGNIZE_SPEECH`
 * in the manifest, or package-visibility filtering makes `resolveActivity`
 * return null on API 30+ and the mic never appears at all.
 *
 * The transcript is handed back as a *query*, never a selection: romanised
 * Japanese titles come back mangled far more often than English, so the caller
 * must land the user on results they confirm with the D-pad, and must leave the
 * transcript editable in the field so it can be corrected without starting over.
 */
@Composable
actual fun rememberVoiceSearch(prompt: String, onTranscript: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val intent = remember(prompt) {
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        }
    }
    val available = remember(intent) {
        runCatching { context.packageManager.resolveActivity(intent, 0) != null }.getOrDefault(false)
    }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(onTranscript)
    }
    if (!available) return null
    return {
        // The recogniser is another app's activity, so launching it stops ours.
        // Without this the Hub treats that as "the user left" and returns to the
        // app picker — mid-search, losing the query. This is the Hub-side half
        // of the note left in the standalone client.
        try {
            HubForeground.leavingApp()
            launcher.launch(intent)
        } catch (_: ActivityNotFoundException) {
            // Resolved a moment ago, uninstalled since — nothing useful to do.
        }
    }
}
