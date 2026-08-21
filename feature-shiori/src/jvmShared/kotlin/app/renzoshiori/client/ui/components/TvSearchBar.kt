package app.renzoshiori.client.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import app.renzoshiori.client.ui.theme.RenzoColors
import app.renzoshiori.client.ui.tv.focusRing
import app.renzoshiori.client.ui.tv.rememberFocusState

/**
 * The TV search field: a bordered box with the text field in its `decorationBox`,
 * submitting on the IME Search action — the shape the leanback IME expects, and
 * the one `tv-native` already uses. Optional mic button, present only when the
 * device actually has a recogniser.
 *
 * This is a TV affordance; the touch build keeps the shell's command-bar search
 * untouched, so callers gate it on `LocalIsTv`.
 */
@Composable
fun TvSearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    placeholder: String,
    voicePrompt: String,
    modifier: Modifier = Modifier,
) {
    val focus = rememberFocusState()
    val micFocus = rememberFocusState()

    // Voice fills the field and runs the search, but leaves the text editable —
    // a mangled transcript is corrected in place, not by starting over.
    val startVoice = rememberVoiceSearch(voicePrompt) { transcript ->
        onValueChange(transcript)
        onSubmit(transcript)
    }

    // Chrome scales with PANEL RESOLUTION (user direction 2026-08-21): the
    // 48dp field is fine on a 4K panel and oversized on 1080p, where the
    // chrome stack ate ~1/5 of the screen.
    val chrome = top.levitatemedia.renzo.hub.core.tvChromeScale()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = (6 * chrome).dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height((48 * chrome).dp)
                .clip(RoundedCornerShape(10.dp))
                .background(RenzoColors.Card)
                .focusRing(focus.focused, 10.dp)
                .padding(horizontal = 14.dp),
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = if (focus.focused) RenzoColors.Primary else RenzoColors.MutedForeground,
                modifier = Modifier.size(20.dp),
            )
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = RenzoColors.Foreground),
                cursorBrush = SolidColor(RenzoColors.Foreground),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(
                    // Deliberately no clearFocus(): dropping focus on a TV leaves
                    // the cursor nowhere, and the leanback IME is a full-screen
                    // editor that closes itself on the Search action anyway.
                    onSearch = { onSubmit(value) },
                ),
                decorationBox = { inner ->
                    Box(modifier = Modifier.padding(start = 12.dp)) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = MaterialTheme.typography.bodyLarge,
                                color = RenzoColors.MutedForeground,
                            )
                        }
                        inner()
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { focus.set(it.isFocused) },
            )
        }

        if (startVoice != null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .size(48.dp)
                    .tvFocusTarget(
                        focused = micFocus.focused,
                        onFocused = micFocus::set,
                        radius = 10.dp,
                        fill = RenzoColors.Card,
                        onClick = startVoice,
                    ),
            ) {
                Icon(
                    Icons.Filled.Mic,
                    contentDescription = "Search by voice",
                    tint = if (micFocus.focused) RenzoColors.Primary else RenzoColors.MutedForeground,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}
