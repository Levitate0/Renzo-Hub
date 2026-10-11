package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import top.levitatemedia.renzo.tv.player.Cue
import top.levitatemedia.renzo.tv.player.TrackDescription
import top.levitatemedia.renzo.tv.player.VlcVideoPlayer
import top.levitatemedia.renzo.tv.player.WebVtt
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/*
 * The player stage: the video and everything drawn over it. Layout is
 * Crunchyroll's player — a thin seek bar across the bottom and one row of flat
 * icons under it (skip back / play / skip forward / volume / time on the left,
 * subtitles / speed / settings / fullscreen on the right) — in Renzo's colours.
 * Pieces the web watch page already has keep the web's look: the "‹ Back" pill,
 * the centre play button while paused, the caption boxes, the up-next card
 * (renzo/frontend/src/components/player, and the .pc- / .cc- rules in globals.css).
 */

/** What the stage shows that the libVLC player doesn't own. */
internal class StageState(
    val player: VlcVideoPlayer?,
    val resolving: Boolean,
    val error: String?,
    /** Behind the resolving spinner: the episode's own still. */
    val backdrop: String?,
    val cues: List<Cue>,
    val controlsVisible: Boolean,
    val fullscreen: Boolean,
    /** Series name and episode, shown over the video in fullscreen. */
    val seriesTitle: String,
    val episodeTitle: String,
    val subtitleLabels: List<String>,
    val ccIndex: Int,
    val rate: Float,
    val autoplay: Boolean,
    val volume: Int,
    val muted: Boolean,
    val upNext: UpNextInfo?,
    val countdown: Int,
    val previewUrl: ((Long) -> String)?,
    val cookie: String?,
)

internal class StageActions(
    val poke: () -> Unit,
    val back: () -> Unit,
    val toggleFullscreen: () -> Unit,
    val selectSubtitles: (Int) -> Unit,
    val setRate: (Float) -> Unit,
    val setAutoplay: (Boolean) -> Unit,
    val setVolume: (Int) -> Unit,
    val toggleMute: () -> Unit,
    val openExternal: () -> Unit,
    val retry: () -> Unit,
    val playUpNext: () -> Unit,
    val cancelUpNext: () -> Unit,
)

/** The auto-next proposal (web AutoNextInfo). */
internal data class UpNextInfo(val title: String, val thumb: String?)

private enum class StageMenu { Subtitles, Speed, Settings }

private val RATES = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

/** A blank cursor (web .hide-controls { cursor: none }); the default where AWT can't make one. */
private val HiddenCursor: PointerIcon by lazy {
    runCatching {
        PointerIcon(
            java.awt.Toolkit.getDefaultToolkit().createCustomCursor(
                java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_ARGB),
                java.awt.Point(0, 0),
                "none",
            ),
        )
    }.getOrDefault(PointerIcon.Default)
}

@Composable
private fun <T> StateFlow<T>?.collectOr(default: T): State<T> {
    val fallback = remember { MutableStateFlow(default) }
    return (this ?: fallback).collectAsState()
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun PlayerStage(s: StageState, a: StageActions, modifier: Modifier = Modifier) {
    val p = s.player
    val status by p?.status.collectOr(VlcVideoPlayer.Status.Idle)
    val buffering by p?.buffering.collectOr(false)
    var menu by remember { mutableStateOf<StageMenu?>(null) }
    val playing = status == VlcVideoPlayer.Status.Playing
    // The web hides the bar only while playing; any open menu keeps it up.
    val chrome = s.controlsVisible || menu != null || !playing || s.error != null

    Box(
        modifier
            .background(Color.Black)
            .clipToBounds()
            .onPointerEvent(PointerEventType.Move) { a.poke() }
            .pointerHoverIcon(if (chrome) PointerIcon.Default else HiddenCursor),
    ) {
        when {
            s.error != null -> StageMessage(
                backdrop = s.backdrop,
                title = "Playback problem",
                message = s.error,
                actions = listOf(
                    "Try again" to a.retry,
                    "Open in external player" to a.openExternal,
                    "Go back" to a.back,
                ),
            )
            s.resolving || p == null -> StageResolving(s.backdrop)
            else -> {
                VideoSurface(
                    p,
                    Modifier
                        .fillMaxSize()
                        .pointerInput(p) {
                            detectTapGestures(
                                onTap = {
                                    if (menu != null) menu = null else { p.togglePause(); a.poke() }
                                },
                                onDoubleTap = { a.toggleFullscreen() },
                            )
                        },
                )
                Captions(p, s.cues, raised = chrome, Modifier.align(Alignment.BottomCenter))
                if ((buffering || status == VlcVideoPlayer.Status.Opening) && s.upNext == null) {
                    CircularProgressIndicator(
                        color = RenzoColors.Primary,
                        strokeWidth = 3.dp,
                        modifier = Modifier.align(Alignment.Center).size(52.dp),
                    )
                }
                // Web .pc-big: the centre button fades in while paused.
                if (chrome && (status == VlcVideoPlayer.Status.Paused || status == VlcVideoPlayer.Status.Ended) && s.upNext == null) {
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .size(74.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable { p.togglePause(); a.poke() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (status == VlcVideoPlayer.Status.Ended) Icons.Rounded.Replay else Icons.Rounded.PlayArrow,
                            contentDescription = "Play",
                            tint = Color.White,
                            modifier = Modifier.size(38.dp),
                        )
                    }
                }
            }
        }

        // Chrome: back pill on top, seek bar + control row along the bottom.
        AnimatedVisibility(
            visible = chrome,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(250)),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                TopChrome(s, a, Modifier.align(Alignment.TopStart))
                if (p != null && s.error == null && !s.resolving) {
                    BottomChrome(p, s, a, menu, { menu = if (menu == it) null else it }, Modifier.align(Alignment.BottomCenter))
                }
            }
        }

        if (p != null && menu != null) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 76.dp),
            ) {
                when (menu) {
                    StageMenu.Subtitles -> MenuCard("Subtitles") {
                        MenuRow("Off", checked = s.ccIndex < 0) { a.selectSubtitles(-1); menu = null }
                        s.subtitleLabels.forEachIndexed { i, label ->
                            MenuRow(label, checked = s.ccIndex == i) { a.selectSubtitles(i); menu = null }
                        }
                    }
                    StageMenu.Speed -> MenuCard("Playback speed") {
                        RATES.forEach { r ->
                            MenuRow(if (r == 1f) "Normal" else rateLabel(r), checked = s.rate == r) { a.setRate(r); menu = null }
                        }
                    }
                    StageMenu.Settings -> SettingsMenu(p, s, a, close = { menu = null })
                    null -> Unit
                }
            }
        }

        s.upNext?.let { info ->
            UpNextCard(
                info = info,
                countdown = s.countdown,
                onPlay = a.playUpNext,
                onCancel = a.cancelUpNext,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 74.dp),
            )
        }
    }
}

// --- video + captions -------------------------------------------------------

@Composable
private fun VideoSurface(p: VlcVideoPlayer, modifier: Modifier) {
    val frame by p.frame.collectAsState()
    val tick = p.frameTick.collectAsState()
    val videoW by p.videoWidth.collectAsState()
    val videoH by p.videoHeight.collectAsState()
    // Move decoded frames into the bitmap once per display frame.
    LaunchedEffect(p) { while (isActive) withFrameNanos { p.pumpFrame() } }
    Canvas(modifier) {
        tick.value // read in draw: each new frame redraws without recomposing
        val img = frame ?: return@Canvas
        // Only the visible region — the buffer carries decoder padding.
        val vw = if (videoW in 1..img.width) videoW else img.width
        val vh = if (videoH in 1..img.height) videoH else img.height
        if (vw <= 0 || vh <= 0) return@Canvas
        val scale = minOf(size.width / vw, size.height / vh)
        val w = (vw * scale).toInt()
        val h = (vh * scale).toInt()
        drawImage(
            image = img,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(vw, vh),
            dstOffset = IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
            dstSize = IntSize(w, h),
            filterQuality = FilterQuality.Medium,
        )
    }
}

/** Web .cc-box/.cc-line: one dark rounded box per line, raised above the bar. */
@Composable
private fun Captions(p: VlcVideoPlayer, cues: List<Cue>, raised: Boolean, modifier: Modifier) {
    if (cues.isEmpty()) return
    var clock by remember { mutableLongStateOf(0L) }
    // libVLC's own clock, ~10x a second — cue edges land within 100ms.
    LaunchedEffect(p) { while (isActive) { clock = p.currentTimeMs(); delay(100) } }
    val active = remember(clock, cues) { WebVtt.active(cues, clock) }
    if (active.isEmpty()) return
    val bottom by animateDpAsState(if (raised) 84.dp else 40.dp, tween(200))
    BoxWithConstraints(modifier.fillMaxWidth().padding(bottom = bottom, start = 32.dp, end = 32.dp)) {
        // clamp(15px, 2.5vw, 26px)
        val size = (maxWidth.value * 0.025f).coerceIn(15f, 26f).sp
        Column(Modifier.align(Alignment.BottomCenter), horizontalAlignment = Alignment.CenterHorizontally) {
            active.flatMap { it.text.split('\n') }.forEach { line ->
                Text(
                    cueText(line),
                    color = Color.White,
                    fontSize = size,
                    lineHeight = size * 1.35f,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 9.dp, vertical = 2.dp),
                )
            }
        }
    }
}

/** WebVTT's simple inline tags (<i>, <b>, <u>) as styling; anything else dropped. */
internal fun cueText(text: String): AnnotatedString = buildAnnotatedString {
    val tag = Regex("""<(/?)([ibu])(?:\.[^>]*)?>|<[^>]+>""")
    var italic = 0
    var bold = 0
    var under = 0
    var i = 0
    fun emit(chunk: String) {
        if (chunk.isEmpty()) return
        pushStyle(
            SpanStyle(
                fontStyle = if (italic > 0) FontStyle.Italic else null,
                fontWeight = if (bold > 0) FontWeight.Bold else null,
                textDecoration = if (under > 0) TextDecoration.Underline else null,
            ),
        )
        append(chunk.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " "))
        pop()
    }
    for (m in tag.findAll(text)) {
        emit(text.substring(i, m.range.first))
        val closing = m.groupValues[1] == "/"
        when (m.groupValues[2]) {
            "i" -> italic += if (closing) -1 else 1
            "b" -> bold += if (closing) -1 else 1
            "u" -> under += if (closing) -1 else 1
        }
        i = m.range.last + 1
    }
    emit(text.substring(i))
}

// --- top + bottom chrome ----------------------------------------------------

@Composable
private fun TopChrome(s: StageState, a: StageActions, modifier: Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Web #watchBack: "‹ Back", rounded-full, black/60 + border.
        Text(
            "‹ Back",
            color = Color.White,
            fontSize = 14.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(Color.Black.copy(alpha = 0.6f))
                .border(1.dp, RenzoColors.Border, RoundedCornerShape(999.dp))
                .clickable(onClick = a.back)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        // In fullscreen the info below the stage is out of sight, so the stage
        // names what is playing (Crunchyroll does the same).
        if (s.fullscreen) {
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(s.seriesTitle, color = RenzoColors.Primary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.episodeTitle, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BottomChrome(
    p: VlcVideoPlayer,
    s: StageState,
    a: StageActions,
    menu: StageMenu?,
    toggleMenu: (StageMenu) -> Unit,
    modifier: Modifier,
) {
    val status by p.status.collectAsState()
    val position by p.positionMs.collectAsState()
    val duration by p.durationMs.collectAsState()
    var scrubMs by remember { mutableStateOf<Long?>(null) }
    val playing = status == VlcVideoPlayer.Status.Playing

    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))))
            .padding(start = 16.dp, end = 16.dp, top = 36.dp, bottom = 6.dp),
    ) {
        SeekBar(
            positionMs = scrubMs ?: position,
            durationMs = duration,
            onScrub = { scrubMs = it; a.poke() },
            onCommit = { scrubMs?.let(p::seekTo); scrubMs = null; a.poke() },
            previewUrl = s.previewUrl,
            cookie = s.cookie,
        )
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeIcon(Icons.Rounded.Replay10, "Back 10 seconds (J)") { p.seekBy(-10_000); a.poke() }
            ChromeIcon(
                if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (playing) "Pause (K)" else "Play (K)",
            ) { p.togglePause(); a.poke() }
            ChromeIcon(Icons.Rounded.Forward10, "Forward 10 seconds (L)") { p.seekBy(10_000); a.poke() }
            VolumeControl(s.volume, s.muted, a.setVolume, a.toggleMute)
            Text(
                "${fmtClock(scrubMs ?: position)} / ${fmtClock(duration)}",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.padding(start = 10.dp),
            )
            Spacer(Modifier.weight(1f))
            if (s.subtitleLabels.isNotEmpty()) {
                ChromeIcon(
                    Icons.Rounded.ClosedCaption,
                    "Subtitles (C)",
                    on = s.ccIndex >= 0 || menu == StageMenu.Subtitles,
                ) { toggleMenu(StageMenu.Subtitles) }
            }
            ChromeTextButton(rateLabel(s.rate), "Playback speed", on = menu == StageMenu.Speed) { toggleMenu(StageMenu.Speed) }
            ChromeIcon(Icons.Rounded.Settings, "Settings", on = menu == StageMenu.Settings) { toggleMenu(StageMenu.Settings) }
            ChromeIcon(
                if (s.fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                if (s.fullscreen) "Exit fullscreen (F)" else "Fullscreen (F)",
                onClick = a.toggleFullscreen,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Tip(text: String, content: @Composable () -> Unit) {
    TooltipArea(
        tooltip = {
            Text(
                text,
                color = Color.White,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xF0101014))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        },
        delayMillis = 500,
        tooltipPlacement = TooltipPlacement.ComponentRect(
            anchor = Alignment.TopCenter,
            alignment = Alignment.TopCenter,
            offset = DpOffset(0.dp, (-6).dp),
        ),
    ) { content() }
}

/** A flat white control icon; hover (or an open menu) turns it the accent colour. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChromeIcon(icon: ImageVector, label: String, on: Boolean = false, onClick: () -> Unit) {
    var hover by remember { mutableStateOf(false) }
    Tip(label) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .onPointerEvent(PointerEventType.Enter) { hover = true }
                .onPointerEvent(PointerEventType.Exit) { hover = false }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = if (hover || on) RenzoColors.Primary else Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ChromeTextButton(text: String, label: String, on: Boolean, onClick: () -> Unit) {
    var hover by remember { mutableStateOf(false) }
    Tip(label) {
        Box(
            Modifier
                .height(44.dp)
                .widthIn(min = 44.dp)
                .onPointerEvent(PointerEventType.Enter) { hover = true }
                .onPointerEvent(PointerEventType.Exit) { hover = false }
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(text, color = if (hover || on) RenzoColors.Primary else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Speaker icon; a horizontal slider slides out while hovered (Crunchyroll). */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun VolumeControl(volume: Int, muted: Boolean, setVolume: (Int) -> Unit, toggleMute: () -> Unit) {
    var hover by remember { mutableStateOf(false) }
    val shown = if (muted) 0 else volume
    Row(
        Modifier
            .onPointerEvent(PointerEventType.Enter) { hover = true }
            .onPointerEvent(PointerEventType.Exit) { hover = false },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChromeIcon(
            when {
                shown == 0 -> Icons.Rounded.VolumeOff
                shown < 50 -> Icons.Rounded.VolumeDown
                else -> Icons.Rounded.VolumeUp
            },
            if (muted) "Unmute (M)" else "Mute (M)",
            onClick = toggleMute,
        )
        AnimatedVisibility(visible = hover, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) {
            var widthPx by remember { mutableStateOf(1) }
            fun at(x: Float) = ((x / widthPx).coerceIn(0f, 1f) * 100).toInt()
            Box(
                Modifier
                    .padding(start = 2.dp, end = 6.dp)
                    .width(84.dp)
                    .height(28.dp)
                    .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                    .pointerInput(Unit) { detectTapGestures { setVolume(at(it.x)) } }
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { setVolume(at(it.x)) },
                            onDrag = { c, _ -> setVolume(at(c.position.x)) },
                        )
                    },
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxWidth().height(4.dp)) {
                    val r = CornerRadius(size.height / 2)
                    val f = shown / 100f
                    drawRoundRect(Color.White.copy(alpha = 0.3f), size = size, cornerRadius = r)
                    drawRoundRect(Color.White, size = Size(size.width * f, size.height), cornerRadius = r)
                    drawCircle(Color.White, radius = 6.dp.toPx(), center = Offset(size.width * f, size.height / 2))
                }
            }
        }
    }
}

/**
 * Crunchyroll's seek bar in Renzo's accent (web .pc-seek: 5px track at white/28,
 * 14px primary thumb). Hovering shows the time — and the server's preview frame
 * when the episode is in the library — above the cursor; click or drag to seek.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onScrub: (Long) -> Unit,
    onCommit: () -> Unit,
    previewUrl: ((Long) -> String)?,
    cookie: String?,
) {
    var widthPx by remember { mutableStateOf(1) }
    var hoverX by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    fun msAt(x: Float): Long = if (durationMs <= 0) 0L else ((x / widthPx).coerceIn(0f, 1f) * durationMs).toLong()
    val frac = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val hot = hoverX != null || dragging
    val track by animateDpAsState(if (hot) 6.dp else 4.dp, tween(120))

    Box(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
            .onPointerEvent(PointerEventType.Move) { e -> hoverX = e.changes.first().position.x }
            .onPointerEvent(PointerEventType.Exit) { hoverX = null }
            .pointerInput(durationMs) { detectTapGestures { o -> onScrub(msAt(o.x)); onCommit() } }
            .pointerInput(durationMs) {
                detectDragGestures(
                    onDragStart = { o -> dragging = true; onScrub(msAt(o.x)) },
                    onDragEnd = { dragging = false; onCommit() },
                    onDragCancel = { dragging = false; onCommit() },
                    onDrag = { c, _ -> hoverX = c.position.x; onScrub(msAt(c.position.x)) },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(track)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(Color.White.copy(alpha = 0.28f), size = size, cornerRadius = r)
            hoverX?.let { hx ->
                drawRoundRect(Color.White.copy(alpha = 0.22f), size = Size(hx.coerceIn(0f, size.width), size.height), cornerRadius = r)
            }
            drawRoundRect(RenzoColors.Primary, size = Size(size.width * frac, size.height), cornerRadius = r)
            drawCircle(
                RenzoColors.Primary,
                radius = (if (hot) 8.dp else 7.dp).toPx(),
                center = Offset(size.width * frac, size.height / 2),
            )
        }

        // Hover bubble: drawn above the bar without taking layout space.
        val hx = hoverX
        if (hx != null && durationMs > 0) {
            val ms = msAt(hx)
            val bubbleW = 176.dp
            val bubbleWpx = with(density) { bubbleW.toPx() }
            val left = (hx - bubbleWpx / 2).coerceIn(0f, (widthPx - bubbleWpx).coerceAtLeast(0f))
            var previewOk by remember(ms / 10_000) { mutableStateOf(previewUrl != null) }
            Column(
                Modifier
                    .align(Alignment.TopStart)
                    // Zero-size in layout, placed unconstrained just above the bar.
                    .layout { measurable, _ ->
                        val pl = measurable.measure(Constraints())
                        layout(0, 0) { pl.place(left.toInt(), -pl.height - 6.dp.roundToPx()) }
                    }
                    .width(bubbleW),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (previewOk && previewUrl != null) {
                    val ctx = LocalPlatformContext.current
                    AsyncImage(
                        model = ImageRequest.Builder(ctx)
                            .data(previewUrl(ms))
                            .apply { if (cookie != null) httpHeaders(NetworkHeaders.Builder().set("Cookie", cookie).build()) }
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        // Debrid-only episodes have no preview frames: 404 → time only.
                        onError = { previewOk = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.Black)
                            .border(2.dp, Color.White.copy(alpha = 0.85f), RoundedCornerShape(6.dp)),
                    )
                }
                Text(
                    fmtClock(ms),
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.75f))
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                )
            }
        }
    }
}

// --- menus ------------------------------------------------------------------

/** Web .cc-menu: popover card, uppercase muted heading, accent = selected. */
@Composable
private fun MenuCard(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .width(240.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(RenzoColors.Popover.copy(alpha = 0.96f))
            .border(1.dp, RenzoColors.Border, RoundedCornerShape(10.dp))
            .padding(6.dp),
    ) {
        MenuHeading(title)
        content()
    }
}

@Composable
private fun MenuHeading(title: String) {
    Text(
        title.uppercase(),
        color = RenzoColors.MutedForeground,
        fontSize = 11.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 0.8.sp,
        modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun MenuRow(label: String, checked: Boolean, trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    var hover by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (hover) RenzoColors.Accent else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hover = true }
            .onPointerEvent(PointerEventType.Exit) { hover = false }
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (checked) RenzoColors.Primary else RenzoColors.Foreground,
            fontSize = 14.sp,
            fontWeight = if (checked) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        when {
            trailing != null -> trailing()
            checked -> Icon(Icons.Rounded.Check, null, tint = RenzoColors.Primary, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun SettingsMenu(p: VlcVideoPlayer, s: StageState, a: StageActions, close: () -> Unit) {
    val tracksVersion by p.tracksChanged.collectAsState()
    var tracks by remember { mutableStateOf(emptyList<TrackDescription>()) }
    var current by remember { mutableStateOf(-1) }
    LaunchedEffect(tracksVersion) { tracks = p.audioTracks(); current = p.audioTrack() }
    MenuCard("Settings") {
        MenuRow("Autoplay", checked = false, trailing = { Switch(s.autoplay) }) { a.setAutoplay(!s.autoplay) }
        // Dual-audio releases carry a Japanese and an English track.
        if (tracks.size > 1) {
            Spacer(Modifier.height(4.dp))
            MenuHeading("Audio")
            tracks.forEach { t ->
                MenuRow(t.name, checked = t.id == current) { p.setAudioTrack(t.id); current = t.id }
            }
        }
        Box(Modifier.padding(vertical = 4.dp).fillMaxWidth().height(1.dp).background(RenzoColors.Border))
        MenuRow("Open in external player", checked = false) { close(); a.openExternal() }
    }
}

/** A small on/off pill switch in the accent colour. */
@Composable
private fun Switch(on: Boolean) {
    Box(
        Modifier
            .size(width = 34.dp, height = 20.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(if (on) RenzoColors.Primary else RenzoColors.Muted)
            .padding(3.dp),
        contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).background(Color.White))
    }
}

// --- up next + full-stage panes --------------------------------------------

/** Web AutoNextCard, verbatim layout: thumb, "UP NEXT", title, countdown, two buttons. */
@Composable
private fun UpNextCard(info: UpNextInfo, countdown: Int, onPlay: () -> Unit, onCancel: () -> Unit, modifier: Modifier) {
    Row(
        modifier
            .widthIn(max = 340.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(RenzoColors.Background.copy(alpha = 0.95f))
            .border(1.dp, RenzoColors.Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = info.thumb,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 104.dp, height = 60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(RenzoColors.Muted),
        )
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("UP NEXT", color = RenzoColors.MutedForeground, fontSize = 11.sp, letterSpacing = 0.8.sp)
            Text(info.title, color = RenzoColors.Foreground, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Next episode in ${countdown.coerceAtLeast(0)}s", color = RenzoColors.MutedForeground, fontSize = 12.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SmallButton("▶ Play now", filled = true, onClick = onPlay)
                SmallButton("Cancel", filled = false, onClick = onCancel)
            }
        }
    }
}

@Composable
internal fun SmallButton(label: String, filled: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .height(30.dp)
            .alpha(if (enabled) 1f else 0.6f)
            .clip(shape)
            .background(if (filled) RenzoColors.Primary else Color.Transparent)
            .then(if (filled) Modifier else Modifier.border(1.dp, RenzoColors.Border, shape))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (filled) RenzoColors.PrimaryForeground else RenzoColors.Foreground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun StageBackdrop(url: String?) {
    if (url == null) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize().alpha(0.28f),
    )
}

@Composable
private fun StageResolving(backdrop: String?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        StageBackdrop(backdrop)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 460.dp).padding(24.dp),
        ) {
            CircularProgressIndicator(color = RenzoColors.Primary, strokeWidth = 3.dp, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(4.dp))
            Text("Resolving stream…", color = RenzoColors.Primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Searching sources and preparing playback — this can take up to a minute.",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun StageMessage(backdrop: String?, title: String, message: String, actions: List<Pair<String, () -> Unit>>) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        StageBackdrop(backdrop)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 560.dp).padding(24.dp),
        ) {
            Text(title, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(message, color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, textAlign = TextAlign.Center)
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                actions.forEachIndexed { i, (label, act) -> SmallButton(label, filled = i == 0, onClick = act) }
            }
        }
    }
}

// --- formatting ---------------------------------------------------------------

private fun rateLabel(r: Float): String =
    (if (r == r.toInt().toFloat()) r.toInt().toString() else r.toString().trimEnd('0')) + "x"

/** Web fmtTime: m:ss / h:mm:ss. */
internal fun fmtClock(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val ss = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, ss) else "%d:%02d".format(m, ss)
}
