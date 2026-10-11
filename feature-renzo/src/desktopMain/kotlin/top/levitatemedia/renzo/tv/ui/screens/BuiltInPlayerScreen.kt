package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.FullscreenExit
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.levitatemedia.renzo.hub.core.offline.AssetRole
import top.levitatemedia.renzo.hub.core.offline.DesktopOfflineFiles
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.offline.episodeKey
import top.levitatemedia.renzo.tv.player.Cue
import top.levitatemedia.renzo.tv.player.VlcRuntime
import top.levitatemedia.renzo.tv.player.VlcVideoPlayer
import top.levitatemedia.renzo.tv.player.WebVtt
import top.levitatemedia.renzo.tv.ui.components.PillButton
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/*
 * The built-in desktop player — the Android PlayerScreen (Media3) ported to
 * libvlc, behind the same RenzoPlayerRoute seam (RENZO-DESKTOP-NATIVE-HANDOFF.md
 * §6). The resolve / resume / watched / up-next logic is the Android player's,
 * step for step, so both platforms behave the same; only the engine and the
 * desktop input (mouse, keyboard, fullscreen) differ.
 */

// Same thresholds as the Android player: a few seconds in is noise, and near
// the end the next launch should start the episode over, not land on credits.
private const val RESUME_MIN_MS = 15_000L
private const val RESUME_TAIL_MS = 60_000L

/** A subtitle track, with however its WebVTT text is fetched. */
private class SubTrack(val label: String, val lang: String, val load: suspend () -> String?)

@Composable
internal fun BuiltInPlayerScreen(
    app: AppServices,
    titleId: Int,
    ep: Int,
    titleName: String,
    onExit: () -> Unit,
    onSwitchEpisode: (Int) -> Unit,
    onUseExternal: () -> Unit,
) {
    // A full reset per episode: fresh resolve, fresh player, fresh overlay state.
    androidx.compose.runtime.key(titleId, ep) {
        BuiltInPlayerContent(app, titleId, ep, titleName, onExit, onSwitchEpisode, onUseExternal)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun BuiltInPlayerContent(
    app: AppServices,
    titleId: Int,
    ep: Int,
    titleName: String,
    onExit: () -> Unit,
    onSwitchEpisode: (Int) -> Unit,
    onUseExternal: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val factory = remember { VlcRuntime.factory() }

    // --- state (mirrors the Android player) ---------------------------------
    var attempt by remember { mutableStateOf(0) }
    var mrl by remember { mutableStateOf<String?>(null) }
    var filename by remember { mutableStateOf<String?>(null) }
    var fatalError by remember { mutableStateOf<String?>(null) }
    var subTracks by remember { mutableStateOf(listOf<SubTrack>()) }
    var ccIndex by remember { mutableStateOf(-1) }
    var cues by remember { mutableStateOf(listOf<Cue>()) }

    var player by remember { mutableStateOf<VlcVideoPlayer?>(null) }
    val resumeKey = remember(titleId, ep) { episodeKey(titleId, ep) }
    var resumeMs by remember { mutableStateOf(0L) }
    var resumeLoaded by remember { mutableStateOf(false) }
    val endedHandled = remember { mutableStateOf(false) }
    val prefetched = remember { mutableStateOf(false) }

    var scrubMs by remember { mutableStateOf<Long?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var pokeCount by remember { mutableStateOf(0) }
    var upNextEp by remember { mutableStateOf<Int?>(null) }
    var countdown by remember { mutableStateOf(8) }
    var volume by remember { mutableStateOf(100) }
    var muted by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(isFullscreen()) }
    var subClockMs by remember { mutableLongStateOf(0L) }

    val rootFocus = remember { FocusRequester() }

    fun poke() { pokeCount++; controlsVisible = true }

    // Downloaded to THIS computer? Then there is nothing to resolve — it plays
    // with no network and without spending a debrid link.
    val offlineItem = remember(titleId, ep) { app.offline.item(episodeKey(titleId, ep))?.takeIf { it.complete } }

    // Server-owned resume point, so the position follows you between devices;
    // the local copy is the offline fallback (Android player, verbatim).
    LaunchedEffect(titleId, ep) {
        val local = app.prefs.resumePositionMs(resumeKey)
        val remote = runCatching { app.repo.resumePoints(titleId)[ep.toString()] }.getOrNull()
        resumeMs = remote?.positionMs ?: local
        resumeLoaded = true
    }

    // --- resolve (server-side this can block for up to ~45s) ----------------
    LaunchedEffect(attempt) {
        fatalError = null
        mrl = null
        subTracks = emptyList()
        if (offlineItem != null) {
            val files = app.offlineStore as? DesktopOfflineFiles
            val video = app.offline.assetsOf(offlineItem.itemKey, AssetRole.VIDEO).firstOrNull()
            val file = video?.let { files?.localFile(it.relPath) }
            if (file == null) {
                fatalError = "This download is missing its video file."
                return@LaunchedEffect
            }
            subTracks = app.offline.assetsOf(offlineItem.itemKey, AssetRole.SUBTITLE).map { asset ->
                SubTrack(asset.label ?: "Subtitles", asset.label ?: "und") {
                    withContext(Dispatchers.IO) { app.offline.readAsset(asset)?.decodeToString() }
                }
            }
            filename = file.name
            mrl = file.absolutePath
            return@LaunchedEffect
        }
        try {
            val s = app.repo.play(titleId, ep)
            filename = s.filename
            // A file in the server's library is cookie-gated, and libvlc must never
            // be handed the session cookie (it would follow redirects to other
            // hosts with it). The server signs a per-file, 2-hour URL for exactly
            // this. Debrid URLs need no credentials at all.
            mrl = if (s.source == "local") {
                app.client.absolute(app.repo.offlineLinks(titleId, ep).url)
            } else {
                app.client.absolute(s.url)
            }
            val cookie = app.client.cookieHeader()
            subTracks = s.subtitles.map { sub ->
                SubTrack(sub.label.ifBlank { sub.lang.uppercase() }, sub.lang) {
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val req = okhttp3.Request.Builder().url(app.repo.captionUrl(sub.id))
                            cookie?.let { req.header("Cookie", it) }
                            app.client.http.newCall(req.build()).execute().use { r -> if (r.isSuccessful) r.body?.string() else null }
                        }.getOrNull()
                    }
                }
            }
        } catch (e: ApiError) {
            fatalError = when (e.status) {
                402 -> "Connect a debrid service in the web app first"
                401 -> "Session expired — sign in again from the home screen"
                0 -> "Can't reach your Renzo server"
                else -> e.message ?: "Playback failed"
            }
        } catch (e: Exception) {
            fatalError = e.message ?: "Playback failed"
        }
    }

    // --- initial subtitle pick: preferred language, else the first track ----
    LaunchedEffect(subTracks) {
        if (subTracks.isEmpty()) { ccIndex = -1; cues = emptyList(); return@LaunchedEffect }
        val pref = app.prefs.ccLang
        ccIndex = if (pref == "off") -1 else {
            val want = normLang(pref)
            subTracks.indexOfFirst { normLang(it.lang) == want }.let { if (it >= 0) it else 0 }
        }
    }
    LaunchedEffect(ccIndex, subTracks) {
        cues = emptyList()
        val t = subTracks.getOrNull(ccIndex) ?: return@LaunchedEffect
        cues = t.load()?.let(WebVtt::parse).orEmpty()
    }

    fun cycleSubtitles() {
        if (subTracks.isEmpty()) return
        ccIndex = if (ccIndex + 1 >= subTracks.size) -1 else ccIndex + 1
        app.prefs.ccLang = subTracks.getOrNull(ccIndex)?.lang?.let(::normLang) ?: "off"
        poke()
    }

    // --- build / release the player once there is something to play ---------
    DisposableEffect(mrl, resumeLoaded) {
        val target = mrl
        val f = factory
        var built: VlcVideoPlayer? = null
        if (target != null && resumeLoaded && f != null) {
            built = VlcVideoPlayer(f).also {
                it.setVolume(if (muted) 0 else volume)
                it.open(target, startMs = resumeMs)
                resumeMs = 0L
            }
            player = built
        }
        onDispose {
            built?.let { pl ->
                val pos = pl.currentTimeMs()
                val dur = pl.durationMs.value
                if (resumeLoaded && pos > RESUME_MIN_MS && (dur <= 0L || dur - pos > RESUME_TAIL_MS)) {
                    app.prefs.setResumePosition(resumeKey, pos)
                    scope.launch { runCatching { app.repo.saveResume(titleId, ep, pos, dur) } }
                } else if (resumeLoaded) {
                    app.prefs.clearResumePosition(resumeKey)
                }
                pl.release()
            }
            if (player === built) player = null
        }
    }

    val p = player
    val status by (p?.status ?: remember { kotlinx.coroutines.flow.MutableStateFlow(VlcVideoPlayer.Status.Idle) }).collectAsState()
    val buffering by (p?.buffering ?: remember { kotlinx.coroutines.flow.MutableStateFlow(false) }).collectAsState()
    val positionMs by (p?.positionMs ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0L) }).collectAsState()
    val durationMs by (p?.durationMs ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0L) }).collectAsState()
    val frame by (p?.frame ?: remember { kotlinx.coroutines.flow.MutableStateFlow(null) }).collectAsState()
    val frameTick by (p?.frameTick ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0L) }).collectAsState()
    val videoW by (p?.videoWidth ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsState()
    val videoH by (p?.videoHeight ?: remember { kotlinx.coroutines.flow.MutableStateFlow(0) }).collectAsState()
    val isPlaying = status == VlcVideoPlayer.Status.Playing

    // --- move decoded frames into the bitmap, once per display frame ---------
    LaunchedEffect(p) {
        val pl = p ?: return@LaunchedEffect
        var lastClock = 0L
        while (isActive) {
            withFrameNanos { now ->
                pl.pumpFrame()
                // The subtitle clock: libvlc's own time, read ~10x a second.
                if (now - lastClock > 100_000_000L) { lastClock = now; subClockMs = pl.currentTimeMs() }
            }
        }
    }

    // --- status-driven behaviour (Android's Player.Listener, ported) --------
    LaunchedEffect(p, status) {
        val pl = p ?: return@LaunchedEffect
        when (status) {
            VlcVideoPlayer.Status.Playing -> {
                // libvlc can drop a volume set before its audio output exists.
                pl.setVolume(if (muted) 0 else volume)
                if (offlineItem == null && !prefetched.value) {
                    // Warm the server's cache so auto-next is instant.
                    prefetched.value = true
                    scope.launch { runCatching { app.repo.play(titleId, ep + 1) } }
                }
            }
            VlcVideoPlayer.Status.Ended -> if (!endedHandled.value) {
                app.prefs.clearResumePosition(resumeKey)
                endedHandled.value = true
                val next = runCatching { app.repo.markWatched(titleId, ep).upNext }.getOrNull()
                if (next != null) { controlsVisible = false; countdown = 8; upNextEp = next } else onExit()
            }
            VlcVideoPlayer.Status.Error -> {
                resumeMs = pl.currentTimeMs()
                fatalError = if (offlineItem != null) {
                    "This download can't be played — it may be incomplete."
                } else {
                    // Debrid links expire — Try again re-resolves a fresh link.
                    "Playback failed — the stream link may have expired"
                }
            }
            else -> Unit
        }
    }

    // --- persist the resume point every 10s while playing -------------------
    LaunchedEffect(p, isPlaying, resumeLoaded) {
        val pl = p ?: return@LaunchedEffect
        if (!resumeLoaded) return@LaunchedEffect
        while (isPlaying) {
            delay(10_000)
            val pos = pl.currentTimeMs()
            val dur = pl.durationMs.value
            if (pos > RESUME_MIN_MS && (dur <= 0L || dur - pos > RESUME_TAIL_MS)) {
                app.prefs.setResumePosition(resumeKey, pos)
                runCatching { app.repo.saveResume(titleId, ep, pos, dur) }
            }
        }
    }

    // --- auto-hide 4s after the last interaction, only while playing --------
    LaunchedEffect(controlsVisible, pokeCount, isPlaying) {
        if (controlsVisible && isPlaying) { delay(4000); controlsVisible = false }
    }

    // --- auto-next countdown -------------------------------------------------
    LaunchedEffect(upNextEp) {
        val next = upNextEp ?: return@LaunchedEffect
        for (i in 8 downTo 1) { countdown = i; delay(1000) }
        onSwitchEpisode(next)
    }

    // Leaving the player always leaves fullscreen.
    DisposableEffect(Unit) { onDispose { if (isFullscreen()) setFullscreen(false) } }
    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    fun setVol(v: Int) {
        volume = v.coerceIn(0, 200); muted = false
        p?.setVolume(volume); poke()
    }

    // --- layout ---------------------------------------------------------------
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPointerEvent(PointerEventType.Move) { poke() }
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val pl = p
                when (e.key) {
                    Key.Spacebar, Key.K, Key.MediaPlayPause -> { pl?.togglePause(); poke(); true }
                    Key.DirectionLeft, Key.J -> { pl?.seekBy(-10_000); poke(); true }
                    Key.DirectionRight, Key.L -> { pl?.seekBy(10_000); poke(); true }
                    Key.DirectionUp -> { setVol(volume + 10); true }
                    Key.DirectionDown -> { setVol(volume - 10); true }
                    Key.M -> { muted = !muted; pl?.setVolume(if (muted) 0 else volume); poke(); true }
                    Key.F -> { setFullscreen(!isFullscreen()); fullscreen = isFullscreen(); true }
                    Key.C -> { cycleSubtitles(); true }
                    Key.N -> { onSwitchEpisode(ep + 1); true }
                    Key.Escape -> {
                        if (isFullscreen()) { setFullscreen(false); fullscreen = false } else onExit()
                        true
                    }
                    else -> false
                }
            },
    ) {
        when {
            fatalError != null -> BuiltInErrorPane(
                titleName = titleName,
                ep = ep,
                message = fatalError ?: "",
                onRetry = { attempt++ },
                onBack = onExit,
                onExternal = onUseExternal,
            )
            mrl == null -> BuiltInResolvingPane(titleName, ep)
            else -> {
                // Video, aspect-fit. Single click = play/pause, double = fullscreen.
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .pointerInput(p) {
                            detectTapGestures(
                                onTap = { p?.togglePause(); poke() },
                                onDoubleTap = { setFullscreen(!isFullscreen()); fullscreen = isFullscreen() },
                            )
                        },
                ) {
                    val img = frame
                    Canvas(Modifier.fillMaxSize()) {
                        @Suppress("UNUSED_EXPRESSION") frameTick // redraw on every new frame
                        // Only the visible region: the buffer carries decoder padding.
                        val vw = if (videoW in 1..(img?.width ?: 0)) videoW else img?.width ?: 0
                        val vh = if (videoH in 1..(img?.height ?: 0)) videoH else img?.height ?: 0
                        if (img != null && vw > 0 && vh > 0) {
                            val scale = minOf(size.width / vw, size.height / vh)
                            val w = vw * scale
                            val h = vh * scale
                            drawImage(
                                image = img,
                                srcOffset = androidx.compose.ui.unit.IntOffset.Zero,
                                srcSize = IntSize(vw, vh),
                                dstOffset = androidx.compose.ui.unit.IntOffset(((size.width - w) / 2).toInt(), ((size.height - h) / 2).toInt()),
                                dstSize = IntSize(w.toInt(), h.toInt()),
                                filterQuality = androidx.compose.ui.graphics.FilterQuality.Medium,
                            )
                        }
                    }
                    // Subtitles: drawn by the app, raised clear of the controls.
                    val active = remember(subClockMs, cues) { WebVtt.active(cues, subClockMs) }
                    if (active.isNotEmpty()) {
                        val fontSize = (maxHeight.value * 0.042f).coerceIn(16f, 44f).sp
                        Text(
                            text = cueText(active),
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                fontSize = fontSize,
                                fontWeight = FontWeight.SemiBold,
                                shadow = Shadow(Color.Black, Offset(0f, 2f), blurRadius = 6f),
                                lineHeight = fontSize * 1.2f,
                            ),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 48.dp)
                                .padding(bottom = if (controlsVisible) 150.dp else (maxHeight.value * 0.07f).dp),
                        )
                    }
                }

                if ((buffering || status == VlcVideoPlayer.Status.Opening || p == null) && upNextEp == null) {
                    CircularProgressIndicator(
                        color = RenzoColors.Primary,
                        modifier = Modifier.align(Alignment.Center).size(48.dp),
                    )
                }

                if (controlsVisible && upNextEp == null) {
                    // Top: back, title context, and the escape hatch to an external player.
                    Row(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                            .padding(horizontal = 24.dp, vertical = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ControlIcon(Icons.Rounded.ArrowBack, "Back", onExit)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(
                                "$titleName · E$ep",
                                color = RenzoColors.Foreground,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            filename?.let {
                                Text(
                                    it,
                                    color = RenzoColors.MutedForeground,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                        ControlIcon(Icons.Rounded.OpenInNew, "Open in external player", onUseExternal, label = "External player")
                    }

                    // Bottom: scrub bar + transport.
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))))
                            .padding(horizontal = 32.dp, vertical = 20.dp),
                    ) {
                        DesktopScrubBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            scrubMs = scrubMs,
                            onScrub = { scrubMs = it; poke() },
                            onCommit = { scrubMs?.let { t -> p?.seekTo(t) }; scrubMs = null; poke() },
                            previewUrl = { ms ->
                                val bucket = (ms / 1000 / 10) * 10
                                app.client.absolute("/api/titles/$titleId/preview/$ep?at=$bucket")
                            },
                            cookie = app.client.cookieHeader(),
                        )
                        Spacer(Modifier.height(14.dp))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${fmtTime(scrubMs ?: positionMs)} / ${fmtTime(durationMs)}",
                                color = RenzoColors.Foreground,
                                fontSize = 13.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                ControlIcon(Icons.Rounded.Replay10, "Back 10 seconds", { p?.seekBy(-10_000); poke() })
                                ControlIcon(
                                    if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    if (isPlaying) "Pause" else "Play",
                                    { p?.togglePause(); poke() },
                                    diameter = 56,
                                )
                                ControlIcon(Icons.Rounded.Forward10, "Forward 10 seconds", { p?.seekBy(10_000); poke() })
                            }
                            Row(
                                Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                ControlIcon(
                                    if (muted || volume == 0) Icons.Rounded.VolumeOff else Icons.Rounded.VolumeUp,
                                    if (muted) "Unmute" else "Mute",
                                    { muted = !muted; p?.setVolume(if (muted) 0 else volume); poke() },
                                    label = if (muted) "Muted" else "$volume%",
                                )
                                if (subTracks.isNotEmpty()) {
                                    ControlIcon(
                                        Icons.Rounded.ClosedCaption,
                                        "Subtitles",
                                        { cycleSubtitles() },
                                        label = subTracks.getOrNull(ccIndex)?.label ?: "Off",
                                    )
                                }
                                ControlIcon(Icons.Rounded.SkipNext, "Next episode", { onSwitchEpisode(ep + 1) }, label = "Next")
                                ControlIcon(
                                    if (fullscreen) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                                    if (fullscreen) "Exit fullscreen" else "Fullscreen",
                                    { setFullscreen(!isFullscreen()); fullscreen = isFullscreen() },
                                )
                            }
                        }
                    }
                }

                upNextEp?.let { next ->
                    BuiltInUpNext(
                        next = next,
                        countdown = countdown,
                        onPlayNow = { onSwitchEpisode(next) },
                        onCancel = { upNextEp = null; controlsVisible = true },
                    )
                }
            }
        }
    }
}

// --- helpers -----------------------------------------------------------------

/** WebVTT's simple inline tags (<i>, <b>, <u>) as styling; anything else dropped. */
private fun cueText(cues: List<Cue>): AnnotatedString = buildAnnotatedString {
    val tag = Regex("""<(/?)([ibu])(?:\.[^>]*)?>|<[^>]+>""")
    cues.forEachIndexed { n, cue ->
        if (n > 0) append('\n')
        var italic = 0; var bold = 0; var under = 0
        var i = 0
        for (m in tag.findAll(cue.text)) {
            if (m.range.first > i) {
                pushStyle(SpanStyle(
                    fontStyle = if (italic > 0) FontStyle.Italic else null,
                    fontWeight = if (bold > 0) FontWeight.Bold else null,
                    textDecoration = if (under > 0) TextDecoration.Underline else null,
                ))
                append(cue.text.substring(i, m.range.first))
                pop()
            }
            val closing = m.groupValues[1] == "/"
            when (m.groupValues[2]) {
                "i" -> italic += if (closing) -1 else 1
                "b" -> bold += if (closing) -1 else 1
                "u" -> under += if (closing) -1 else 1
            }
            i = m.range.last + 1
        }
        if (i < cue.text.length) {
            pushStyle(SpanStyle(
                fontStyle = if (italic > 0) FontStyle.Italic else null,
                fontWeight = if (bold > 0) FontWeight.Bold else null,
                textDecoration = if (under > 0) TextDecoration.Underline else null,
            ))
            append(cue.text.substring(i).replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">"))
            pop()
        }
    }
}

private fun activeWindow(): java.awt.Window? =
    java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        ?: java.awt.Window.getWindows().firstOrNull { it.isShowing }

private fun isFullscreen(): Boolean = runCatching {
    val w = activeWindow() ?: return false
    w.graphicsConfiguration.device.fullScreenWindow === w
}.getOrDefault(false)

/** AWT fullscreen on the app's own window — a Compose window is an AWT frame. */
private fun setFullscreen(on: Boolean) {
    runCatching {
        val w = activeWindow() ?: return
        val dev = w.graphicsConfiguration.device
        dev.fullScreenWindow = if (on) w else null
    }
}

private fun fmtTime(ms: Long): String {
    if (ms <= 0L) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** eng→en, jpn/jap→ja, region stripped — the Android player's normLang. */
private fun normLang(l: String?): String {
    val s = (l ?: "").lowercase().substringBefore('-').substringBefore('_')
    return when (s) {
        "eng" -> "en"
        "jpn", "jap" -> "ja"
        else -> if (s.length > 2) s.take(2) else s
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun ControlIcon(icon: ImageVector, desc: String, onClick: () -> Unit, diameter: Int = 44, label: String? = null) {
    var hover by remember { mutableStateOf(false) }
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (hover) RenzoColors.Secondary else RenzoColors.OverlayBlack, RoundedCornerShape(999.dp))
            .onPointerEvent(PointerEventType.Enter) { hover = true }
            .onPointerEvent(PointerEventType.Exit) { hover = false }
            .clickable(onClick = onClick)
            .height(diameter.dp)
            .then(if (label == null) Modifier.width(diameter.dp) else Modifier.padding(horizontal = 14.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = desc, tint = RenzoColors.Foreground, modifier = Modifier.size(if (diameter >= 56) 28.dp else 22.dp))
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(label, color = RenzoColors.Foreground, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

/** The seek bar: click or drag to scrub, with the server's preview frame above it. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DesktopScrubBar(
    positionMs: Long,
    durationMs: Long,
    scrubMs: Long?,
    onScrub: (Long) -> Unit,
    onCommit: () -> Unit,
    previewUrl: (Long) -> String,
    cookie: String?,
) {
    var widthPx by remember { mutableStateOf(1) }
    var hoverMs by remember { mutableStateOf<Long?>(null) }
    val context = LocalPlatformContext.current
    fun msAt(x: Float): Long = if (durationMs <= 0) 0L else ((x / widthPx).coerceIn(0f, 1f) * durationMs).toLong()
    val shown = scrubMs ?: positionMs
    val frac = if (durationMs > 0) (shown.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val bubbleMs = scrubMs ?: hoverMs

    Column(Modifier.fillMaxWidth()) {
        if (bubbleMs != null && durationMs > 0) {
            val bubbleFrac = (bubbleMs.toFloat() / durationMs).coerceIn(0f, 1f)
            Box(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Column(Modifier.align(BiasAlignment(bubbleFrac * 2f - 1f, 0f)), horizontalAlignment = Alignment.CenterHorizontally) {
                    // Served from a local copy of the episode; debrid-only episodes
                    // 404 here and simply show the time bubble.
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(previewUrl(bubbleMs))
                            .apply { if (cookie != null) httpHeaders(NetworkHeaders.Builder().set("Cookie", cookie).build()) }
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.width(192.dp).clip(RoundedCornerShape(6.dp)),
                    )
                    Text(
                        fmtTime(bubbleMs),
                        color = RenzoColors.Foreground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(RenzoColors.OverlayBlack)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(22.dp)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .onPointerEvent(PointerEventType.Move) { e -> hoverMs = msAt(e.changes.first().position.x) }
                .onPointerEvent(PointerEventType.Exit) { hoverMs = null }
                .pointerInput(durationMs) {
                    detectTapGestures { o -> onScrub(msAt(o.x)); onCommit() }
                }
                .pointerInput(durationMs) {
                    detectDragGestures(
                        onDragStart = { o -> onScrub(msAt(o.x)) },
                        onDragEnd = { onCommit() },
                        onDragCancel = { onCommit() },
                        onDrag = { change, _ -> onScrub(msAt(change.position.x)) },
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxWidth().height(6.dp)) {
                val r = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(Color(0x55FFFFFF), size = size, cornerRadius = r)
                drawRoundRect(RenzoColors.Primary, size = Size(size.width * frac, size.height), cornerRadius = r)
                drawCircle(Color.White, radius = size.height * 1.3f, center = Offset(size.width * frac, size.height / 2))
            }
        }
    }
}

@Composable
private fun BuiltInResolvingPane(titleName: String, ep: Int) {
    Box(Modifier.fillMaxSize().background(RenzoColors.Background), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 520.dp).padding(24.dp),
        ) {
            Text(titleName, color = RenzoColors.Foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("Episode $ep", color = RenzoColors.MutedForeground, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            CircularProgressIndicator(color = RenzoColors.Primary, modifier = Modifier.size(36.dp))
            Spacer(Modifier.height(6.dp))
            Text("Resolving stream…", color = RenzoColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Text(
                "Searching sources and preparing playback — this can take up to a minute.",
                color = RenzoColors.MutedForeground,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun BuiltInErrorPane(
    titleName: String,
    ep: Int,
    message: String,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    onExternal: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Color(0xE6000000)), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.widthIn(max = 560.dp).padding(24.dp),
        ) {
            Text("$titleName · E$ep", color = RenzoColors.MutedForeground, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Playback problem", color = RenzoColors.Foreground, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Text(message, color = RenzoColors.MutedForeground, fontSize = 14.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(label = "Try again", onClick = onRetry)
                PillButton(label = "Open in external player", onClick = onExternal, filled = false)
                PillButton(label = "Go back", onClick = onBack, filled = false)
            }
        }
    }
}

/** 8-second auto-next card, bottom-right like the web and Android players. */
@Composable
private fun BuiltInUpNext(next: Int, countdown: Int, onPlayNow: () -> Unit, onCancel: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(36.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(RenzoColors.Card, RoundedCornerShape(14.dp))
                .border(1.dp, RenzoColors.Border, RoundedCornerShape(14.dp))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Up next · E$next", color = RenzoColors.Foreground, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text("Playing in ${countdown}s", color = RenzoColors.MutedForeground, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(label = "Play now", onClick = onPlayNow)
                PillButton(label = "Cancel", onClick = onCancel, filled = false)
            }
        }
    }
}
