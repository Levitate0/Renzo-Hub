package top.levitatemedia.renzo.hub

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import top.levitatemedia.renzo.hub.core.TvFit
import top.levitatemedia.renzo.hub.core.isTvDevice
import top.levitatemedia.renzo.hub.core.HubTarget

// Dark-only, matching both halves. These are the same values both clients use
// for background/card/border, which are identical by design across the two.
private val Background = Color(0xFF0B0C10)
private val Card = Color(0xFF141519)
private val Border = Color(0xFF2A2C33)
private val Muted = Color(0xFFA1A1AA)
private val Accent = Color(0xFFE11D48)
private val Focused = Color(0xFF1C1D23)

/**
 * Launch picker, shown on every cold start. The choice is intentionally not
 * persisted — defaulting to one half is wrong for anyone who only uses the
 * other. Never shown on TV, where the Hub simply is Renzo.
 *
 * The tiles use each half's own wordmark, pulled from the feature modules'
 * resources so the picker always matches what the app itself shows on its
 * login screen.
 */
@Composable
fun PickerScreen(onPick: (HubTarget) -> Unit) {
    val isTv = rememberIsTv()
    // A clipped picker is unrecoverable — there is nothing left to press. On a
    // TV the whole page scales to the panel rather than scrolling.
    if (isTv) {
        TvFit(designHeightDp = 620) { PickerContent(isTv = true, onPick = onPick) }
    } else {
        PickerContent(isTv = false, onPick = onPick)
    }
}

@Composable
private fun PickerContent(isTv: Boolean, onPick: (HubTarget) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            // A TV has no system bars to avoid, but the panel crops roughly 5%
            // of every edge. Without this the top of the content is simply cut
            // off — which is exactly what happened.
            .then(
                if (isTv) Modifier.padding(horizontal = 48.dp, vertical = 27.dp)
                else Modifier.windowInsetsPadding(WindowInsets.safeDrawing)
            )
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            painter = painterResource(R.drawable.renzo_hub_wordmark),
            contentDescription = "Renzo Hub",
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(if (isTv) 92.dp else 108.dp),
        )
        Text(
            "You can switch at any time from the menu.",
            color = Muted,
            fontSize = 13.sp,
            modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
        )

        PickerTile(
            wordmark = top.levitatemedia.renzo.tv.R.drawable.renzo_wordmark,
            caption = "Anime",
            tileArtHeight = if (isTv) 52.dp else 60.dp,
            onClick = { onPick(HubTarget.Renzo) },
        )
        PickerTile(
            wordmark = app.renzoshiori.client.R.drawable.renzo_login_banner,
            caption = "Manga",
            tileArtHeight = if (isTv) 52.dp else 60.dp,
            onClick = { onPick(HubTarget.Shiori) },
            modifier = Modifier.padding(top = 16.dp),
        )
    }
}

@Composable
private fun PickerTile(
    wordmark: Int,
    caption: String,
    tileArtHeight: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // `clickable` is focusable by default, so a D-pad can already reach these —
    // but Material's focus indication is invisible across a room. Without a
    // ring you cannot tell which half you are about to open, which makes the
    // picker unusable on the one device that has no touchscreen.
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (focused) Focused else Card)
            .border(
                if (focused) 3.dp else 1.dp,
                if (focused) Accent else Border,
                RoundedCornerShape(14.dp),
            )
            .onFocusChanged { focused = it.isFocused }
            .clickable(onClick = onClick)
            .padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Sized by HEIGHT, not width. The two wordmarks have very different
        // aspect ratios (1.42 vs 2.13), so matching their widths made one mark
        // half again as tall as the other and left the tiles visibly unequal.
        // Matching heights makes them read at the same optical size.
        Image(
            painter = painterResource(wordmark),
            contentDescription = caption,
            contentScale = ContentScale.Fit,
            modifier = Modifier.height(tileArtHeight).fillMaxWidth(0.78f),
        )
        Text(
            caption,
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Leanback detection, remembered so it isn't re-queried on every recomposition. */
@Composable
private fun rememberIsTv(): Boolean {
    val context = LocalContext.current
    return remember(context) { isTvDevice(context) }
}
