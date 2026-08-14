package app.renzoshiori.client.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize

/**
 * The window's size in dp — the multiplatform stand-in for Android's
 * LocalConfiguration.screen{Width,Height}Dp, which does not exist on the
 * desktop. Derived from the window container, so a desktop resize recomposes
 * readers/layouts exactly like a phone rotation does.
 */
@Composable
fun screenSizeDp(): DpSize = with(LocalDensity.current) {
    val size = LocalWindowInfo.current.containerSize
    DpSize(size.width.toDp(), size.height.toDp())
}

@Composable
fun screenWidthDp(): Dp = screenSizeDp().width

@Composable
fun screenHeightDp(): Dp = screenSizeDp().height
