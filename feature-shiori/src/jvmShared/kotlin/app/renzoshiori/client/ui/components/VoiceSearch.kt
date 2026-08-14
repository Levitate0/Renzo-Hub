package app.renzoshiori.client.ui.components

import androidx.compose.runtime.Composable

/**
 * Voice search, resolved defensively — null when the platform has no speech
 * recogniser, and callers hide the mic entirely in that case. Android answers
 * with the system RecognizerIntent; the desktop has no recogniser and always
 * answers null.
 */
@Composable
expect fun rememberVoiceSearch(prompt: String, onTranscript: (String) -> Unit): (() -> Unit)?
