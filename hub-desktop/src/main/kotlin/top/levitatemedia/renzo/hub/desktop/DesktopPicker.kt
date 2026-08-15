package top.levitatemedia.renzo.hub.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.loadImageBitmap
import androidx.compose.ui.res.useResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.renzoshiori.client.resources.Res as ShioriRes
import app.renzoshiori.client.resources.renzo_login_banner
import org.jetbrains.compose.resources.painterResource
import top.levitatemedia.renzo.hub.core.HubTarget
import top.levitatemedia.renzo.tv.resources.Res as RenzoRes
import top.levitatemedia.renzo.tv.resources.renzo_wordmark

// The same dark-only values app/PickerScreen.kt uses — identical across both
// halves by design.
private val Background = Color(0xFF0B0C10)
private val Card = Color(0xFF141519)
private val Border = Color(0xFF2A2C33)
private val Muted = Color(0xFFA1A1AA)
private val Accent = Color(0xFFE11D48)
private val Hovered = Color(0xFF1C1D23)

/**
 * The APK's launch picker, recreated for the desktop window (the Android
 * original lives in :app, which the desktop can't depend on). Same rules:
 * shown on every cold start, choice deliberately not persisted, tiles carry
 * each half's own wordmark from the feature modules' resources. Focus ring
 * becomes a hover state — the desktop's equivalent of "which one am I about
 * to open".
 */
@Composable
fun DesktopPicker(onPick: (HubTarget) -> Unit) {
    val hubWordmark = remember {
        runCatching {
            BitmapPainter(useResource("renzo_hub_wordmark.png", ::loadImageBitmap))
        }.getOrNull()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(Background)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            Modifier.widthIn(max = 560.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (hubWordmark != null) {
                Image(
                    painter = hubWordmark,
                    contentDescription = "Renzo Hub",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(108.dp),
                )
            }
            Text(
                "You can switch at any time from the menu.",
                color = Muted,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
            )

            PickerTile(
                wordmark = painterResource(RenzoRes.drawable.renzo_wordmark),
                caption = "Anime",
                tileArtHeight = 60.dp,
                onClick = { onPick(HubTarget.Renzo) },
            )
            PickerTile(
                wordmark = painterResource(ShioriRes.drawable.renzo_login_banner),
                caption = "Manga",
                tileArtHeight = 60.dp,
                onClick = { onPick(HubTarget.Shiori) },
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}

@Composable
private fun PickerTile(
    wordmark: Painter,
    caption: String,
    tileArtHeight: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (hovered) Hovered else Card)
            .border(
                if (hovered) 3.dp else 1.dp,
                if (hovered) Accent else Border,
                RoundedCornerShape(14.dp),
            )
            .hoverable(interaction)
            .clickable(onClick = onClick)
            .padding(vertical = 26.dp, horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Sized by HEIGHT, not width — the two wordmarks have very different
        // aspect ratios, and matching heights makes them read at the same
        // optical size (same reasoning as the Android picker).
        Image(
            painter = wordmark,
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
