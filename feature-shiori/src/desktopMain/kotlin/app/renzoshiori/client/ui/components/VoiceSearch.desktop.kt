package app.renzoshiori.client.ui.components

import androidx.compose.runtime.Composable

@Composable
actual fun rememberVoiceSearch(prompt: String, onTranscript: (String) -> Unit): (() -> Unit)? = null
