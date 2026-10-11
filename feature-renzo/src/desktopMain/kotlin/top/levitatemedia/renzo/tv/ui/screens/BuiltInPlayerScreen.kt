package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.WindowPlacement
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.levitatemedia.renzo.hub.core.offline.AssetRole
import top.levitatemedia.renzo.hub.core.offline.DesktopOfflineFiles
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.ResumePoint
import top.levitatemedia.renzo.tv.api.TitleDetail
import top.levitatemedia.renzo.tv.offline.episodeKey
import top.levitatemedia.renzo.tv.player.Cue
import top.levitatemedia.renzo.tv.player.VlcRuntime
import top.levitatemedia.renzo.tv.player.VlcVideoPlayer
import top.levitatemedia.renzo.tv.player.WebVtt
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The built-in desktop player page. Behaviour is the Android PlayerScreen's and
 * the web watch page's (resolve, resume from the server's point, 10s saves,
 * mark watched, auto-next, prefetch, preferred subtitles); the engine is libVLC
 * through our own LGPL-clean binding (player/LibVlc.kt). Layout: the stage
 * (PlayerStage.kt) on top, Crunchyroll-style info and the episode list
 * (PlayerInfo.kt) scrolling underneath; fullscreen shows the stage alone.
 */

// Thresholds shared with the server (renzo src/routes/api.ts RESUME_MIN_MS /
// RESUME_TAIL_MS): a few seconds in is noise, and near the end the next launch
// should start the episode over, not land on the credits.
private const val RESUME_MIN_MS = 15_000L
private const val RESUME_TAIL_MS = 60_000L

/** A subtitle track, with however its WebVTT text is fetched. */
private class SubTrack(val label: String, val lang: String, val load: suspend () -> String?)

/** Title-level state: survives episode switches within the same series. */
@Stable
private class TitleSession {
    var detail by mutableStateOf<TitleDetail?>(null)
    var resume by mutableStateOf<Map<String, ResumePoint>>(emptyMap())
    var resumeLoaded by mutableStateOf(false)
    /** The server read succeeded. Until it has, posting could overwrite a point we never saw. */
    var resumeReadOk by mutableStateOf(false)
    var watchedThrough by mutableStateOf(0)
    var lists by mutableStateOf(listOf<String>())
}

/** Fullscreen through Compose's own window placement — not AWT exclusive mode. */
private object WindowFullscreen {
    private var restore: WindowPlacement = WindowPlacement.Floating

    private fun window(): ComposeWindow? =
        (java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
            ?: java.awt.Window.getWindows().firstOrNull { it.isShowing }) as? ComposeWindow

    fun isOn(): Boolean = window()?.placement == WindowPlacement.Fullscreen

    fun set(on: Boolean) {
        val w = window() ?: return
        if (on && w.placement != WindowPlacement.Fullscreen) {
            restore = w.placement
            w.placement = WindowPlacement.Fullscreen
        } else if (!on && w.placement == WindowPlacement.Fullscreen) {
            w.placement = restore
        }
    }
}

@Composable
internal fun BuiltInPlayerScreen(
    app: AppServices,
    titleId: Int,
    ep: Int,
    titleName: String,
    onExit: () -> Unit,
    onSwitchEpisode: (Int) -> Unit,
    onSwitchTitle: (Int, Int, String) -> Unit,
    onOpenTitle: (Int) -> Unit,
    onUseExternal: () -> Unit,
) {
    val session = remember(titleId) { TitleSession() }
    LaunchedEffect(titleId) {
        runCatching { app.repo.title(titleId) }.getOrNull()?.let { d ->
            session.detail = d
            session.watchedThrough = d.watchedThrough
            session.lists = d.lists
        }
    }
    LaunchedEffect(titleId) {
        val r = runCatching { app.repo.resumePoints(titleId) }
        session.resume = r.getOrNull().orEmpty()
        session.resumeReadOk = r.isSuccess
        session.resumeLoaded = true
    }
    // Volume, speed and fullscreen carry over from one episode to the next.
    var volume by remember { mutableStateOf(100) }
    var muted by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf(1f) }
    var fullscreen by remember { mutableStateOf(WindowFullscreen.isOn()) }
    // Leaving the player always leaves fullscreen.
    DisposableEffect(Unit) { onDispose { WindowFullscreen.set(false) } }

    key(titleId, ep) {
        EpisodePage(
            app = app,
            session = session,
            titleId = titleId,
            ep = ep,
            titleName = titleName,
            volume = volume,
            muted = muted,
            rate = rate,
            fullscreen = fullscreen,
            setVolume = { volume = it.coerceIn(0, 100); muted = false },
            toggleMute = { muted = !muted },
            setRate = { rate = it },
            toggleFullscreen = {
                WindowFullscreen.set(!WindowFullscreen.isOn())
                fullscreen = WindowFullscreen.isOn()
            },
            exitFullscreen = { WindowFullscreen.set(false); fullscreen = false },
            onExit = onExit,
            onSwitchEpisode = onSwitchEpisode,
            onSwitchTitle = onSwitchTitle,
            onOpenTitle = onOpenTitle,
            onUseExternal = onUseExternal,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun EpisodePage(
    app: AppServices,
    session: TitleSession,
    titleId: Int,
    ep: Int,
    titleName: String,
    volume: Int,
    muted: Boolean,
    rate: Float,
    fullscreen: Boolean,
    setVolume: (Int) -> Unit,
    toggleMute: () -> Unit,
    setRate: (Float) -> Unit,
    toggleFullscreen: () -> Unit,
    exitFullscreen: () -> Unit,
    onExit: () -> Unit,
    onSwitchEpisode: (Int) -> Unit,
    onSwitchTitle: (Int, Int, String) -> Unit,
    onOpenTitle: (Int) -> Unit,
    onUseExternal: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val vlc = remember { VlcRuntime.instance() }
    val todayIso = remember { SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) }
    val resumeKey = remember(titleId, ep) { episodeKey(titleId, ep) }

    var attempt by remember { mutableStateOf(0) }
    var mrl by remember { mutableStateOf<String?>(null) }
    var source by remember { mutableStateOf<String?>(null) }
    var fatalError by remember { mutableStateOf<String?>(null) }
    var subTracks by remember { mutableStateOf(listOf<SubTrack>()) }
    var ccIndex by remember { mutableStateOf(-1) }
    var cues by remember { mutableStateOf(listOf<Cue>()) }
    var player by remember { mutableStateOf<VlcVideoPlayer?>(null) }
    val endedHandled = remember { mutableStateOf(false) }
    val prefetched = remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var pokeCount by remember { mutableStateOf(0) }
    var upNextEp by remember { mutableStateOf<Int?>(null) }
    var countdown by remember { mutableStateOf(8) }
    var autoplay by remember { mutableStateOf(app.prefs.autoplayNext) }
    var downloadRequested by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var linkCopied by remember { mutableStateOf(false) }

    val rootFocus = remember { FocusRequester() }
    val scroll = rememberScrollState()
    var viewportCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var episodesCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    fun poke() { pokeCount++; controlsVisible = true }

    // Downloaded to THIS computer? Then there is nothing to resolve — it plays
    // with no network and without spending a debrid link.
    val offlineItem = remember(titleId, ep) { app.offline.item(episodeKey(titleId, ep))?.takeIf { it.complete } }

    // --- resolve (server-side this can block for up to ~45s) ----------------
    LaunchedEffect(attempt) {
        fatalError = null
        mrl = null
        source = null
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
            source = "offline"
            mrl = file.absolutePath
            return@LaunchedEffect
        }
        try {
            val s = app.repo.play(titleId, ep)
            // A file in the server's library is cookie-gated, and libVLC must never
            // be handed the session cookie (it would follow redirects to other
            // hosts with it). The server signs a per-file, 2-hour URL for exactly
            // this. Debrid URLs need no credentials at all.
            mrl = if (s.source == "local") {
                app.client.absolute(app.repo.offlineLinks(titleId, ep).url)
            } else {
                app.client.absolute(s.url)
            }
            source = s.source
            if (s.downloading != null) note = "Downloading in background — will switch to local when done."
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

    // --- subtitles: preferred language, else English, else the first (web setupCaptions)
    LaunchedEffect(subTracks) {
        if (subTracks.isEmpty()) { ccIndex = -1; cues = emptyList(); return@LaunchedEffect }
        val pref = app.prefs.ccLang
        ccIndex = if (pref == "off") -1 else {
            val want = normLang(pref)
            subTracks.indexOfFirst { normLang(it.lang) == want }
                .takeIf { it >= 0 } ?: subTracks.indexOfFirst { normLang(it.lang) == "en" }.takeIf { it >= 0 } ?: 0
        }
    }
    LaunchedEffect(ccIndex, subTracks) {
        cues = emptyList()
        val t = subTracks.getOrNull(ccIndex) ?: return@LaunchedEffect
        cues = t.load()?.let(WebVtt::parse).orEmpty()
    }

    fun selectSubtitles(i: Int) {
        ccIndex = i
        app.prefs.ccLang = subTracks.getOrNull(i)?.lang?.let(::normLang) ?: "off"
        poke()
    }

    fun saveResume(pl: VlcVideoPlayer) {
        val pos = pl.currentTimeMs()
        val dur = pl.durationMs.value
        if (endedHandled.value) return
        if (pos > RESUME_MIN_MS && (dur <= 0L || dur - pos > RESUME_TAIL_MS)) {
            app.prefs.setResumePosition(resumeKey, pos)
            session.resume = session.resume + (ep.toString() to ResumePoint(pos, dur))
            // Never post over a point we couldn't read (web saveResumeNow).
            if (session.resumeReadOk) scope.launch { runCatching { app.repo.saveResume(titleId, ep, pos, dur) } }
        }
    }

    // --- build / release the player once there is something to play ---------
    DisposableEffect(mrl, session.resumeLoaded) {
        val target = mrl
        val h = vlc
        var built: VlcVideoPlayer? = null
        if (target != null && session.resumeLoaded && h != null) {
            val remote = session.resume[ep.toString()]?.positionMs
            val start = remote ?: app.prefs.resumePositionMs(resumeKey)
            built = VlcVideoPlayer(h).also {
                it.setVolume(if (muted) 0 else volume)
                it.open(target, startMs = start)
            }
            player = built
        }
        onDispose {
            built?.let { pl ->
                saveResume(pl)
                pl.release()
            }
            if (player === built) player = null
        }
    }

    val p = player
    val statusFlow = p?.status
    val status by (statusFlow ?: remember { kotlinx.coroutines.flow.MutableStateFlow(VlcVideoPlayer.Status.Idle) }).collectAsState()
    val isPlaying = status == VlcVideoPlayer.Status.Playing

    LaunchedEffect(p, volume, muted) { p?.setVolume(if (muted) 0 else volume) }
    LaunchedEffect(p, rate) { p?.setRate(rate) }

    // --- status-driven behaviour (web onPlay/onEnded, Android Player.Listener)
    LaunchedEffect(p, status) {
        val pl = p ?: return@LaunchedEffect
        when (status) {
            VlcVideoPlayer.Status.Playing -> {
                // libVLC can drop volume/rate set before its outputs exist.
                pl.setVolume(if (muted) 0 else volume)
                pl.setRate(rate)
                if (offlineItem == null && !prefetched.value) {
                    // Warm the server's cache so auto-next is instant.
                    prefetched.value = true
                    scope.launch { runCatching { app.repo.play(titleId, ep + 1) } }
                }
            }
            VlcVideoPlayer.Status.Ended -> if (!endedHandled.value) {
                endedHandled.value = true
                app.prefs.clearResumePosition(resumeKey)
                session.resume = session.resume - ep.toString()
                session.watchedThrough = maxOf(session.watchedThrough, ep)
                val serverNext = runCatching { app.repo.markWatched(titleId, ep).upNext }.getOrNull()
                val d = session.detail
                val next = serverNext ?: d?.episodeList?.firstOrNull { it.number == ep + 1 }
                    ?.takeIf { d.type != "movie" && !isUnaired(it, d, todayIso) }?.number
                if (next != null && autoplay) {
                    countdown = 8
                    upNextEp = next
                }
            }
            VlcVideoPlayer.Status.Error -> {
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
    LaunchedEffect(p, isPlaying) {
        val pl = p ?: return@LaunchedEffect
        while (isPlaying) {
            delay(10_000)
            saveResume(pl)
        }
    }
    // Pausing saves the exact stop point (web onPause).
    LaunchedEffect(p, status) {
        if (status == VlcVideoPlayer.Status.Paused) p?.let(::saveResume)
    }

    // --- auto-hide 3s after the last interaction, only while playing (web) ---
    LaunchedEffect(controlsVisible, pokeCount, isPlaying) {
        if (controlsVisible && isPlaying) { delay(3000); controlsVisible = false }
    }

    // --- auto-next countdown -------------------------------------------------
    LaunchedEffect(upNextEp) {
        val next = upNextEp ?: return@LaunchedEffect
        for (i in 8 downTo 1) { countdown = i; delay(1000) }
        onSwitchEpisode(next)
    }

    LaunchedEffect(Unit) { runCatching { rootFocus.requestFocus() } }

    val d = session.detail
    val epMeta = d?.episodeList?.firstOrNull { it.number == ep }
    val stageState = StageState(
        player = p,
        resolving = mrl == null || !session.resumeLoaded,
        error = fatalError ?: if (vlc == null) (VlcRuntime.failure ?: "The built-in player couldn't start.") else null,
        backdrop = epMeta?.thumbnail ?: d?.banner ?: d?.poster,
        cues = cues,
        controlsVisible = controlsVisible,
        fullscreen = fullscreen,
        seriesTitle = d?.displayTitle ?: titleName,
        episodeTitle = d?.let { episodeTitle(it, ep) } ?: "Episode $ep",
        subtitleLabels = subTracks.map { it.label },
        ccIndex = ccIndex,
        rate = rate,
        autoplay = autoplay,
        volume = volume,
        muted = muted,
        upNext = upNextEp?.let { n ->
            val m = d?.episodeList?.firstOrNull { it.number == n }
            UpNextInfo(
                title = "E$n" + (m?.epTitle?.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                thumb = m?.thumbnail ?: d?.banner ?: d?.poster,
            )
        },
        countdown = countdown,
        // Preview frames come from the server's copy of the episode.
        previewUrl = if (source == "local") { ms -> app.client.absolute("/api/titles/$titleId/preview/$ep?at=${(ms / 1000 / 10) * 10}") } else null,
        cookie = app.client.cookieHeader(),
    )
    val stageActions = StageActions(
        poke = ::poke,
        back = onExit,
        toggleFullscreen = { toggleFullscreen(); poke() },
        selectSubtitles = ::selectSubtitles,
        setRate = { setRate(it); poke() },
        setAutoplay = { autoplay = it; app.prefs.autoplayNext = it },
        setVolume = { setVolume(it); poke() },
        toggleMute = { toggleMute(); poke() },
        openExternal = onUseExternal,
        retry = { attempt++ },
        playUpNext = { upNextEp?.let(onSwitchEpisode) },
        cancelUpNext = { upNextEp = null; controlsVisible = true },
    )

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(RenzoColors.Background)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val pl = p
                when (e.key) {
                    Key.Spacebar, Key.K, Key.MediaPlayPause -> { pl?.togglePause(); poke(); true }
                    Key.DirectionLeft, Key.J -> { pl?.seekBy(-10_000); poke(); true }
                    Key.DirectionRight, Key.L -> { pl?.seekBy(10_000); poke(); true }
                    Key.DirectionUp -> { setVolume(volume + 10); poke(); true }
                    Key.DirectionDown -> { setVolume(volume - 10); poke(); true }
                    Key.M -> { toggleMute(); poke(); true }
                    Key.F -> { toggleFullscreen(); poke(); true }
                    Key.C -> {
                        if (subTracks.isNotEmpty()) selectSubtitles(if (ccIndex + 1 >= subTracks.size) -1 else ccIndex + 1)
                        true
                    }
                    Key.Escape -> if (fullscreen) { exitFullscreen(); true } else false
                    else -> false
                }
            },
    ) {
        // Web .watch-stage: aspect-video, at most 78% of the viewport tall.
        val stageHeight = if (fullscreen) maxHeight else min(maxWidth * 9f / 16f, maxHeight * 0.78f)
        Column(
            Modifier
                .fillMaxSize()
                .onGloballyPositioned { viewportCoords = it }
                .verticalScroll(scroll, enabled = !fullscreen),
        ) {
            PlayerStage(
                stageState,
                stageActions,
                Modifier
                    .fillMaxWidth()
                    .height(stageHeight)
                    // Clicking the video hands keyboard control back to the player.
                    .onPointerEvent(PointerEventType.Press) { runCatching { rootFocus.requestFocus() } },
            )
            if (!fullscreen) {
                Column(
                    Modifier
                        .align(Alignment.CenterHorizontally)
                        .widthIn(max = 1200.dp)
                        .fillMaxWidth()
                        .padding(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 56.dp),
                ) {
                    if (d == null) {
                        Text(titleName, color = RenzoColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text("Episode $ep", color = RenzoColors.Foreground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    } else {
                        val model = InfoModel(
                            detail = d,
                            ep = ep,
                            todayIso = todayIso,
                            watchedThrough = session.watchedThrough,
                            resume = session.resume,
                            inWatchlist = "watchlist" in session.lists,
                            source = source,
                            failed = fatalError != null,
                            hasSubtitles = subTracks.isNotEmpty() || epMeta?.hasFile == true,
                            downloadsDenied = app.user.value?.downloadsDenied == true,
                            downloadRequested = downloadRequested,
                            note = note,
                            linkCopied = linkCopied,
                        )
                        val actions = InfoActions(
                            openSeries = { onOpenTitle(titleId) },
                            toggleWatchlist = {
                                val on = "watchlist" !in session.lists
                                scope.launch {
                                    runCatching { app.repo.toggleList(titleId, "watchlist", on) }
                                        .onSuccess { session.lists = it.lists }
                                }
                            },
                            copyLink = {
                                scope.launch {
                                    runCatching { app.repo.watchLink(titleId, ep) }.onSuccess { link ->
                                        java.awt.Toolkit.getDefaultToolkit().systemClipboard
                                            .setContents(java.awt.datatransfer.StringSelection(link), null)
                                        linkCopied = true
                                        delay(2500)
                                        linkCopied = false
                                    }
                                }
                            },
                            download = {
                                scope.launch {
                                    runCatching { app.repo.downloadEpisode(titleId, ep) }
                                        .onSuccess {
                                            downloadRequested = true
                                            note = "Downloading in background — will switch to local when done."
                                        }
                                        .onFailure { note = it.message }
                                }
                            },
                            playEpisode = { n -> if (n != ep) onSwitchEpisode(n) },
                            playTitle = { id, name -> onSwitchTitle(id, 1, name) },
                            seeMoreEpisodes = {
                                val v = viewportCoords
                                val e = episodesCoords
                                if (v != null && e != null) {
                                    val target = scroll.value + (e.positionInRoot().y - v.positionInRoot().y).toInt()
                                    scope.launch { scroll.animateScrollTo(target.coerceAtLeast(0)) }
                                }
                            },
                        )
                        PlayerInfo(model, actions)
                        Spacer(Modifier.height(40.dp))
                        PlayerEpisodes(model, actions, Modifier.onGloballyPositioned { episodesCoords = it })
                    }
                }
            }
        }
    }
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
