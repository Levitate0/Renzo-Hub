@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package top.levitatemedia.renzo.tv.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.focusable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Forward10
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.media3.common.TrackSelectionOverride
import coil3.compose.AsyncImage
import top.levitatemedia.renzo.hub.core.offline.AssetRole as OfflineAssetRole
import top.levitatemedia.renzo.hub.core.offline.uriOf
import top.levitatemedia.renzo.tv.offline.episodeKey
import coil3.network.NetworkHeaders
import coil3.network.httpHeaders
import coil3.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ApiError
import top.levitatemedia.renzo.tv.api.ResolvedStream
import top.levitatemedia.renzo.tv.ui.components.PillButton
import top.levitatemedia.renzo.tv.ui.components.focusRing
import top.levitatemedia.renzo.tv.ui.components.tvClickable
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors

/**
 * Seek to a restored position, clamped against THIS file's duration.
 *
 * A point past the end silently fires `ended`, which marks the episode watched
 * and auto-advances — so a stale or mismatched position would skip an episode
 * the user never saw.
 */
private fun seekToResume(p: androidx.media3.exoplayer.ExoPlayer, ms: Long) {
    if (ms <= 0L) return
    val dur = p.duration.takeIf { it != androidx.media3.common.C.TIME_UNSET } ?: 0L
    if (dur > 0L && ms > dur - RESUME_TAIL_MS) return
    p.seekTo(ms)
}

/** Ignore a resume this far into the episode — it is just noise. */
private const val RESUME_MIN_MS = 15_000L

/** ...and this close to the end, start over instead of landing on the credits. */
private const val RESUME_TAIL_MS = 60_000L

/**
 * The playback screen. Safety-critical parts, per the server catalog:
 *
 *  - /play blocks up to ~45s server-side (debrid resolve) → full-screen
 *    "Resolving stream…" state; ApiClient's 75s read timeout covers it.
 *  - The fsa_session cookie is attached ONLY to requests whose host matches
 *    the user's server (local /files + caption tracks). Debrid CDN hosts
 *    NEVER see it (session-token leak).
 *  - STATE_ENDED → markWatched once (server scrobbles trackers) → 8s
 *    auto-next countdown when upNext != null, else exit.
 *  - ep+1 is prefetched fire-and-forget once playback starts to warm the
 *    server's cache so auto-next is instant.
 *  - Fatal player errors (expired debrid links) re-resolve a fresh link and
 *    rebuild the player, resuming near the last position.
 *
 * onSwitchEpisode replaces this screen with the same composable + a new ep;
 * key(titleId, ep) guarantees a full state reset (fresh resolve, fresh player).
 */
@Composable
fun PlayerScreen(
    app: AppServices,
    titleId: Int,
    ep: Int,
    titleName: String,
    onExit: () -> Unit,
    onSwitchEpisode: (Int) -> Unit,
    @Suppress("UNUSED_PARAMETER") onSwitchTitle: (Int, Int, String) -> Unit, // reserved for franchise auto-advance
) {
    // Fully qualified to avoid clashing with the KeyEvent.key extension import.
    // key() guarantees a FULL state reset when the episode (or title) changes:
    // fresh resolve, fresh ExoPlayer, fresh overlay state.
    androidx.compose.runtime.key(titleId, ep) {
        PlayerContent(app, titleId, ep, titleName, onExit, onSwitchEpisode)
    }
}

@Composable
private fun PlayerContent(
    app: AppServices,
    titleId: Int,
    ep: Int,
    titleName: String,
    onExit: () -> Unit,
    onSwitchEpisode: (Int) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Phones/tablets: lock to landscape and hide the system bars while the
    // player is up (web parity: fullscreen-landscape when watching). On TV
    // both are no-ops — always landscape, no bars.
    DisposableEffect(Unit) {
        val act = context as? android.app.Activity
        val prevOrientation = act?.requestedOrientation
        act?.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        val controller = act?.window?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
        }
        controller?.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            act?.requestedOrientation = prevOrientation
                ?: android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            controller?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    // --- state ------------------------------------------------------------
    var attempt by remember { mutableStateOf(0) }                 // bump = re-resolve + rebuild
    var stream by remember { mutableStateOf<ResolvedStream?>(null) }
    var fatalError by remember { mutableStateOf<String?>(null) }  // resolve or mid-play fatal

    var player by remember { mutableStateOf<ExoPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var positionMs by remember { mutableStateOf(0L) }
    var durationMs by remember { mutableStateOf(0L) }
    // Carries position across an in-session error rebuild, AND seeds from the
    // position persisted the last time this episode was open.
    val resumeKey = remember(titleId, ep) { episodeKey(titleId, ep) }
    var resumeMs by remember { mutableStateOf(0L) }

    /**
     * Saves are suppressed until a resume READ has completed.
     *
     * Otherwise a failed GET starts playback at 0, the 10s tick posts a
     * from-zero position, and the server's under-15s rule deletes the real
     * saved point — losing exactly what we were trying to restore.
     */
    var resumeLoaded by remember { mutableStateOf(false) }

    // Server-owned so the position follows you between phone, TV and the web
    // player. The local copy is the offline fallback, not the source of truth.
    LaunchedEffect(titleId, ep) {
        val local = app.prefs.resumePositionMs(resumeKey)
        val remote = runCatching { app.repo.resumePoints(titleId)[ep.toString()] }.getOrNull()
        resumeMs = remote?.positionMs ?: local
        resumeLoaded = true
    }
    val endedHandled = remember { mutableStateOf(false) }
    val prefetched = remember { mutableStateOf(false) }

    // Available text tracks (embedded MKV + sideloaded server subs). Selection
    // is by EXACT track group (TrackSelectionOverride) — language-string
    // matching broke on en/eng mismatches and label fallbacks (reported CC bug).
    var textGroups by remember { mutableStateOf(listOf<Tracks.Group>()) }
    var ccIndex by remember { mutableStateOf(-1) }   // -1 = off; else index into textGroups
    val ccInitialized = remember { mutableStateOf(false) }

    // Seek-bar scrubbing: non-null while the user is choosing a position.
    var scrubMs by remember { mutableStateOf<Long?>(null) }

    var controlsVisible by remember { mutableStateOf(true) }
    var pokeCount by remember { mutableStateOf(0) }               // resets the 4s auto-hide timer

    var upNextEp by remember { mutableStateOf<Int?>(null) }
    var countdown by remember { mutableStateOf(8) }

    val serverHost = remember { runCatching { java.net.URI(app.client.base()).host }.getOrNull() }

    val rootFocus = remember { FocusRequester() }
    val playFocus = remember { FocusRequester() }
    val cancelFocus = remember { FocusRequester() }
    val retryFocus = remember { FocusRequester() }

    // --- helpers (declared before any lambda captures them) ---------------
    fun poke() {
        pokeCount++
        controlsVisible = true
    }

    fun applyTextSelection(p: Player, group: Tracks.Group?) {
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().apply {
            clearOverridesOfType(C.TRACK_TYPE_TEXT)
            if (group == null) {
                setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            } else {
                setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                addOverride(TrackSelectionOverride(group.mediaTrackGroup, 0))
            }
        }.build()
    }

    fun ccLabel(): String {
        val f = textGroups.getOrNull(ccIndex)?.getTrackFormat(0) ?: return "Off"
        return f.label ?: f.language?.uppercase() ?: "CC"
    }

    fun togglePlay() {
        val p = player ?: return
        if (p.playbackState == Player.STATE_ENDED) {
            // Replay after a cancelled auto-next: restart, let ended flow re-run.
            endedHandled.value = false
            p.seekTo(0)
            p.play()
        } else if (p.isPlaying) p.pause() else p.play()
    }

    fun seekBy(deltaMs: Long) {
        val p = player ?: return
        val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: Long.MAX_VALUE
        p.seekTo((p.currentPosition + deltaMs).coerceIn(0L, dur))
    }

    fun cycleSubtitles() {
        val p = player ?: return
        if (textGroups.isEmpty()) return
        // Each available track → Off, wrapping.
        val next = if (ccIndex + 1 >= textGroups.size) -1 else ccIndex + 1
        ccIndex = next
        val g = textGroups.getOrNull(next)
        app.prefs.ccLang = g?.getTrackFormat(0)?.language?.let(::normLang) ?: "off"
        applyTextSelection(p, g)
    }

    // Downloaded on this device? Then there is nothing to resolve: skip the
    // play endpoint entirely, which also means playback works with no network
    // and without burning a debrid link.
    val offlineItem = remember(titleId, ep) {
        app.offline.item(episodeKey(titleId, ep))?.takeIf { it.complete }
    }

    // --- resolve the stream (blocks server-side up to ~45s) ----------------
    LaunchedEffect(attempt) {
        fatalError = null
        stream = null
        if (offlineItem != null) return@LaunchedEffect
        try {
            stream = app.repo.play(titleId, ep)
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

    // --- build / release the player per resolved stream --------------------
    DisposableEffect(stream, offlineItem) {
        val s = stream
        var built: ExoPlayer? = null

        /**
         * Track preferences + the listener that drives everything outside the
         * surface: play/pause state, the CC picker, marking watched, auto-next.
         *
         * Shared by both branches deliberately — when the offline branch built
         * its own bare player it had none of this, so a downloaded episode
         * never marked itself watched, never auto-advanced, and left the
         * transport controls inert.
         *
         * [offline] suppresses the two things that only make sense with a
         * server in reach: prefetching the next episode, and blaming a failure
         * on an expired debrid link.
         */
        fun configure(p: ExoPlayer, offline: Boolean) {
            // Initial preference by language; onTracksChanged then syncs the UI
            // to what actually got selected (or forces the closest track).
            p.trackSelectionParameters = p.trackSelectionParameters.buildUpon().apply {
                val pref = app.prefs.ccLang
                if (pref == "off") setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                else setPreferredTextLanguage(pref)
            }.build()

            p.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }

                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) {
                        // Restore here, not at prepare(): duration is unknown
                        // until the media is ready, so an earlier seek cannot be
                        // clamped and may land past the end.
                        if (resumeMs > 0L) {
                            seekToResume(p, resumeMs)
                            resumeMs = 0L
                        }
                        if (!offline && !prefetched.value) {
                            // Warm the server's cache so auto-next is instant.
                            prefetched.value = true
                            scope.launch {
                                try { app.repo.play(titleId, ep + 1) } catch (_: Exception) {}
                            }
                        }
                    }
                    if (state == Player.STATE_ENDED && !endedHandled.value) {
                        // markWatched below clears the server-side point too.
                        app.prefs.clearResumePosition(resumeKey)
                        endedHandled.value = true
                        scope.launch {
                            val next = try {
                                app.repo.markWatched(titleId, ep).upNext
                            } catch (_: Exception) { null }
                            if (next != null) {
                                controlsVisible = false
                                countdown = 8
                                upNextEp = next
                            } else onExit()
                        }
                    }
                }

                override fun onTracksChanged(tracks: Tracks) {
                    val groups = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.length > 0 }
                    textGroups = groups
                    if (ccInitialized.value || groups.isEmpty()) {
                        // Keep the UI honest if groups shuffle: re-find our pick.
                        if (ccIndex >= groups.size) ccIndex = groups.indexOfFirst { it.isSelected }
                        return
                    }
                    ccInitialized.value = true
                    if (app.prefs.ccLang == "off") { ccIndex = -1; return }
                    val selected = groups.indexOfFirst { it.isSelected }
                    if (selected >= 0) { ccIndex = selected; return }
                    // Preferred-language matching found nothing (en vs eng vs
                    // label-only tracks) — force the closest track: exact
                    // normalized language, else the FIRST track. Subs on beats
                    // silently missing (the reported "CC isn't working").
                    val want = normLang(app.prefs.ccLang)
                    val idx = groups.indexOfFirst { normLang(it.getTrackFormat(0).language) == want }
                        .let { if (it >= 0) it else 0 }
                    ccIndex = idx
                    applyTextSelection(p, groups[idx])
                }

                override fun onPlayerError(error: PlaybackException) {
                    built?.let { resumeMs = it.currentPosition }
                    fatalError = if (offline) {
                        "This download can't be played — it may be incomplete."
                    } else {
                        // Debrid links expire — Try again re-resolves a fresh
                        // link and rebuilds from here.
                        "Playback failed — the stream link may have expired"
                    }
                }
            })
        }

        if (offlineItem != null) {
            val videoUri = app.offline
                .assetsOf(offlineItem.itemKey, OfflineAssetRole.VIDEO)
                .firstOrNull()
                ?.let { app.offline.uriOf(it) }
            if (videoUri == null) {
                fatalError = "This download is missing its video file."
            } else {
                val subs = app.offline
                    .assetsOf(offlineItem.itemKey, OfflineAssetRole.SUBTITLE)
                    .mapNotNull { asset ->
                        app.offline.uriOf(asset)?.let { uri ->
                            MediaItem.SubtitleConfiguration.Builder(uri)
                                .setMimeType(MimeTypes.TEXT_VTT)
                                .setLanguage(asset.label ?: "und")
                                .setLabel(asset.label ?: "Subtitles")
                                .build()
                        }
                    }
                // No cookie-scoped data source and no OkHttp: these are local
                // file/content URIs, so ExoPlayer's default sources apply.
                val p = ExoPlayer.Builder(context).build()
                configure(p, offline = true)
                p.setMediaItem(
                    MediaItem.Builder().setUri(videoUri).setSubtitleConfigurations(subs).build(),
                )
                p.playWhenReady = true
                p.prepare()
                built = p
                player = p
            }
        } else if (s != null) {
            // Cookie-scoped data source: fsa_session ONLY when the request host
            // is the user's server (local files + caption tracks). Debrid CDN
            // hosts must never receive it.
            val upstream = OkHttpDataSource.Factory(app.client.http)
            val cookieAware = ResolvingDataSource.Factory(upstream) { spec ->
                val cookie = app.client.cookieHeader()
                if (cookie != null && serverHost != null &&
                    spec.uri.host?.equals(serverHost, ignoreCase = true) == true
                ) {
                    spec.withAdditionalHeaders(mapOf("Cookie" to cookie))
                } else spec
            }

            val subtitleConfigs = s.subtitles.map { sub ->
                MediaItem.SubtitleConfiguration.Builder(Uri.parse(app.repo.captionUrl(sub.id)))
                    .setMimeType(MimeTypes.TEXT_VTT)
                    .setLanguage(sub.lang)
                    .setLabel(sub.label.ifBlank { sub.lang })
                    .build()
            }
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(app.client.absolute(s.url)))
                .setSubtitleConfigurations(subtitleConfigs)
                .build()

            val p = ExoPlayer.Builder(context)
                .setMediaSourceFactory(DefaultMediaSourceFactory(cookieAware))
                .build()
            configure(p, offline = false)

            p.setMediaItem(mediaItem)
            p.playWhenReady = true
            p.prepare()
            built = p
            player = p
        }
        onDispose {
            built?.let { pl ->
                val pos = pl.currentPosition
                val dur = pl.duration.takeIf { it != C.TIME_UNSET } ?: 0L
                // Only worth resuming from somewhere in the middle: a few
                // seconds in is noise, and near the end the next launch should
                // start the episode over rather than land on the credits.
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

    // --- position polling (500ms) while the overlay is visible -------------
    LaunchedEffect(player, controlsVisible) {
        val p = player ?: return@LaunchedEffect
        while (controlsVisible) {
            positionMs = p.currentPosition
            durationMs = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            delay(500)
        }
    }

    // --- persist the resume point while playing -----------------------------
    // onDispose covers backing out and switching episodes; this covers the
    // process being killed mid-episode, which is the common case on a phone.
    LaunchedEffect(player, isPlaying, resumeLoaded) {
        val p = player ?: return@LaunchedEffect
        if (!resumeLoaded) return@LaunchedEffect
        while (isPlaying) {
            delay(10_000)
            val pos = p.currentPosition
            val dur = p.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            if (pos > RESUME_MIN_MS && (dur <= 0L || dur - pos > RESUME_TAIL_MS)) {
                app.prefs.setResumePosition(resumeKey, pos)
                // The server enforces the same thresholds and is authoritative;
                // posting freely is safe. Failure is fine — the local copy still
                // resumes this device, which is what offline playback needs.
                runCatching { app.repo.saveResume(titleId, ep, pos, dur) }
            }
        }
    }

    // --- auto-hide 4s after the last interaction, only while playing -------
    LaunchedEffect(controlsVisible, pokeCount, isPlaying) {
        if (controlsVisible && isPlaying) {
            delay(4000)
            controlsVisible = false
        }
    }

    // --- auto-next countdown ------------------------------------------------
    LaunchedEffect(upNextEp) {
        val next = upNextEp ?: return@LaunchedEffect
        for (i in 8 downTo 1) {
            countdown = i
            delay(1000)
        }
        onSwitchEpisode(next)
    }

    // --- focus routing -------------------------------------------------------
    LaunchedEffect(controlsVisible, player, upNextEp, fatalError) {
        runCatching {
            when {
                upNextEp != null -> cancelFocus.requestFocus()
                fatalError != null -> retryFocus.requestFocus()
                player != null && controlsVisible -> playFocus.requestFocus()
                else -> rootFocus.requestFocus()
            }
        }
    }

    // Back: visible overlay → hide it; otherwise leave the player. Declared
    // here (innermost) so it wins over MainActivity's stack-popping handler.
    BackHandler(enabled = true) {
        when {
            scrubMs != null -> scrubMs = null // abandon the scrub, keep playing
            upNextEp != null -> { upNextEp = null; onExit() }
            player != null && controlsVisible -> controlsVisible = false
            else -> onExit()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .onPreviewKeyEvent { ev ->
                if (ev.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                val k = ev.key
                when {
                    // Let BackHandler own Back; don't wake the overlay for it.
                    k == Key.Back || k == Key.Escape -> false
                    // Panes/countdown have their own focused buttons.
                    fatalError != null || stream == null || upNextEp != null -> false
                    k == Key.MediaPlayPause || k == Key.MediaPlay || k == Key.MediaPause -> {
                        togglePlay(); poke(); true
                    }
                    !controlsVisible &&
                        (k == Key.DirectionCenter || k == Key.Enter || k == Key.NumPadEnter) -> {
                        // Hidden overlay: center = play/pause, web-player style.
                        togglePlay(); poke(); true
                    }
                    !controlsVisible -> { poke(); false }
                    else -> { poke(); false }
                }
            }
            .focusable(),
    ) {
        if (player != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        keepScreenOn = true
                        setShowBuffering(PlayerView.SHOW_BUFFERING_ALWAYS)
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { view -> view.player = player },
                modifier = Modifier.fillMaxSize(),
            )
            // Demo (store-listing) build only: a decoded video frame is
            // copyrighted content too, so the surface is covered while the
            // real playback keeps running underneath. null in debug/release.
            top.levitatemedia.renzo.tv.demo.DemoMode.videoCoverAsset?.let { cover ->
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        when {
            fatalError != null -> ErrorPane(
                titleName = titleName,
                ep = ep,
                message = fatalError ?: "",
                retryFocus = retryFocus,
                onRetry = { attempt++ },
                onBack = onExit,
            )
            stream == null -> ResolvingPane(titleName = titleName, ep = ep)
            else -> {
                if (controlsVisible) {
                    // Top: title context over a soft scrim.
                    Column(
                        Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)),
                            )
                            .padding(horizontal = 40.dp, vertical = 26.dp),
                    ) {
                        Text(
                            "$titleName · E$ep",
                            color = RenzoColors.Foreground,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        stream?.filename?.let {
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

                    // Bottom: progress + transport controls.
                    Column(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(listOf(Color.Transparent, Color(0xE6000000))),
                            )
                            .padding(horizontal = 40.dp, vertical = 26.dp),
                    ) {
                        ScrubBar(
                            positionMs = positionMs,
                            durationMs = durationMs,
                            scrubMs = scrubMs,
                            onScrub = { target -> poke(); scrubMs = target },
                            onCommit = {
                                scrubMs?.let { t -> player?.seekTo(t) }
                                scrubMs = null
                                poke()
                            },
                            onCancel = { scrubMs = null },
                            previewUrl = { ms ->
                                val bucket = (ms / 1000 / 10) * 10
                                app.client.absolute("/api/titles/$titleId/preview/$ep?at=$bucket")
                            },
                            cookie = app.client.cookieHeader(),
                        )
                        Spacer(Modifier.height(18.dp))
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ControlButton(
                                icon = Icons.Rounded.Replay10,
                                contentDesc = "Back 10 seconds",
                                onClick = { seekBy(-10_000) },
                            )
                            ControlButton(
                                icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDesc = if (isPlaying) "Pause" else "Play",
                                onClick = { togglePlay() },
                                modifier = Modifier.focusRequester(playFocus),
                                diameter = 56.dp,
                            )
                            ControlButton(
                                icon = Icons.Rounded.Forward10,
                                contentDesc = "Forward 10 seconds",
                                onClick = { seekBy(10_000) },
                            )
                            ControlButton(
                                icon = Icons.Rounded.ClosedCaption,
                                contentDesc = "Cycle subtitles",
                                onClick = { cycleSubtitles() },
                                label = ccLabel(),
                            )
                            ControlButton(
                                icon = Icons.Rounded.SkipNext,
                                contentDesc = "Next episode",
                                onClick = { onSwitchEpisode(ep + 1) },
                                label = "Next",
                            )
                        }
                    }
                }

                upNextEp?.let { next ->
                    UpNextOverlay(
                        next = next,
                        countdown = countdown,
                        cancelFocus = cancelFocus,
                        onPlayNow = { onSwitchEpisode(next) },
                        onCancel = {
                            upNextEp = null
                            controlsVisible = true
                        },
                    )
                }
            }
        }
    }
}

// --- panes & pieces ---------------------------------------------------------

/** Full-screen blocking state while the server resolves the stream (up to ~45s). */
@Composable
private fun ResolvingPane(titleName: String, ep: Int) {
    Box(
        Modifier.fillMaxSize().background(RenzoColors.Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.widthIn(max = 520.dp).padding(24.dp),
        ) {
            Text(
                titleName,
                color = RenzoColors.Foreground,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text("Episode $ep", color = RenzoColors.MutedForeground, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                "Resolving stream…",
                color = RenzoColors.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
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
private fun ErrorPane(
    titleName: String,
    ep: Int,
    message: String,
    retryFocus: FocusRequester,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    Box(
        Modifier.fillMaxSize().background(Color(0xE6000000)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.widthIn(max = 560.dp).padding(24.dp),
        ) {
            Text(
                "$titleName · E$ep",
                color = RenzoColors.MutedForeground,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Playback problem",
                color = RenzoColors.Foreground,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                message,
                color = RenzoColors.MutedForeground,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                PillButton(
                    label = "Try again",
                    onClick = onRetry,
                    modifier = Modifier.focusRequester(retryFocus),
                )
                PillButton(label = "Go back", onClick = onBack, filled = false)
            }
        }
    }
}

/** 8-second auto-next card, bottom-right like the web player. */
@Composable
private fun UpNextOverlay(
    next: Int,
    countdown: Int,
    cancelFocus: FocusRequester,
    onPlayNow: () -> Unit,
    onCancel: () -> Unit,
) {
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
            Text(
                "Up next · E$next",
                color = RenzoColors.Foreground,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text("Playing in ${countdown}s", color = RenzoColors.MutedForeground, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillButton(label = "Play now", onClick = onPlayNow)
                PillButton(
                    label = "Cancel",
                    onClick = onCancel,
                    filled = false,
                    modifier = Modifier.focusRequester(cancelFocus),
                )
            }
        }
    }
}

/** Round/pill transport button: rose focus ring + subtle scale, web-player look. */
@Composable
private fun ControlButton(
    icon: ImageVector,
    contentDesc: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 46.dp,
    label: String? = null,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier
            .scale(if (focused) 1.08f else 1f)
            .clip(RoundedCornerShape(999.dp))
            .focusRing(focused, 999.dp)
            .background(
                if (focused) RenzoColors.Secondary else RenzoColors.OverlayBlack,
                RoundedCornerShape(999.dp),
            )
            .tvClickable(onFocused = { focused = it }, onClick = onClick)
            .height(diameter)
            .then(if (label == null) Modifier.width(diameter) else Modifier.padding(horizontal = 16.dp)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            imageVector = icon,
            contentDescription = contentDesc,
            colorFilter = ColorFilter.tint(RenzoColors.Foreground),
            modifier = Modifier.size(if (diameter >= 56.dp) 28.dp else 22.dp),
        )
        if (label != null) {
            Spacer(Modifier.width(8.dp))
            Text(
                label,
                color = RenzoColors.Foreground,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
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

/** Normalize language codes for comparison: eng→en, jpn/jap→ja, region stripped. */
private fun normLang(l: String?): String {
    val s = (l ?: "").lowercase().substringBefore('-').substringBefore('_')
    return when (s) {
        "eng" -> "en"
        "jpn", "jap" -> "ja"
        else -> if (s.length > 2) s.take(2) else s
    }
}

/**
 * The seek bar — scrubbable with the D-pad (focus it, Left/Right move the
 * target in 10s steps, Center commits, Back cancels) and with touch (drag or
 * tap). While scrubbing, a preview frame for the target position floats above
 * the bar — served by the server from a local copy of the episode; episodes
 * that only exist as debrid streams simply show the time bubble without an
 * image (the request 404s and Coil renders nothing).
 */
@Composable
private fun ScrubBar(
    positionMs: Long,
    durationMs: Long,
    scrubMs: Long?,
    onScrub: (Long) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    previewUrl: (Long) -> String,
    cookie: String?,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    var barWidthPx by remember { mutableStateOf(1) }
    var focused by remember { mutableStateOf(false) }
    val shown = scrubMs ?: positionMs
    val frac = if (durationMs > 0) (shown.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    Column(Modifier.fillMaxWidth()) {
        if (scrubMs != null && durationMs > 0) {
            Box(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                Column(
                    Modifier.align(BiasAlignment(frac * 2f - 1f, 0f)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(previewUrl(scrubMs))
                            // Coil 3 dropped ImageRequest.setHeader — per-request
                            // HTTP headers now go through the network layer.
                            .apply {
                                if (cookie != null) {
                                    httpHeaders(NetworkHeaders.Builder().set("Cookie", cookie).build())
                                }
                            }
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(200.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.Black)
                            .border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp)),
                    )
                    Text(
                        fmtTime(scrubMs),
                        color = RenzoColors.Foreground,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(fmtTime(shown), color = RenzoColors.Foreground, fontSize = 12.sp)
            Spacer(Modifier.width(12.dp))
            Box(
                Modifier
                    .weight(1f)
                    .height(20.dp) // generous touch target; the visual bar is 4dp
                    .onSizeChanged { barWidthPx = it.width.coerceAtLeast(1) }
                    .onFocusChanged { focused = it.isFocused; if (!it.isFocused) onCancel() }
                    .onKeyEvent { ev ->
                        if (durationMs <= 0 || ev.type != KeyEventType.KeyDown) return@onKeyEvent false
                        val cur = scrubMs ?: positionMs
                        when (ev.key) {
                            Key.DirectionLeft -> { onScrub((cur - 10_000).coerceAtLeast(0)); true }
                            Key.DirectionRight -> { onScrub((cur + 10_000).coerceAtMost(durationMs)); true }
                            Key.DirectionCenter, Key.Enter, Key.NumPadEnter ->
                                if (scrubMs != null) { onCommit(); true } else false
                            else -> false
                        }
                    }
                    .focusable()
                    .pointerInput(durationMs) {
                        detectTapGestures { off ->
                            if (durationMs > 0) {
                                onScrub((off.x / barWidthPx.toFloat() * durationMs).toLong().coerceIn(0, durationMs))
                                onCommit()
                            }
                        }
                    }
                    .pointerInput(durationMs) {
                        detectDragGestures(
                            onDragEnd = { onCommit() },
                            onDragCancel = { onCancel() },
                        ) { change, _ ->
                            change.consume()
                            if (durationMs > 0) {
                                onScrub(
                                    (change.position.x / barWidthPx.toFloat() * durationMs)
                                        .toLong().coerceIn(0, durationMs),
                                )
                            }
                        }
                    },
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0x4DFFFFFF)),
                )
                Box(
                    Modifier
                        .fillMaxWidth(frac)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(RenzoColors.Primary),
                )
                if (focused || scrubMs != null) {
                    Box(
                        Modifier
                            .offset(x = with(density) { (barWidthPx * frac).toDp() } - 6.dp)
                            .size(12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(RenzoColors.Primary)
                            .border(2.dp, Color.White, RoundedCornerShape(999.dp)),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text(
                if (durationMs > 0L) fmtTime(durationMs) else "--:--",
                color = RenzoColors.MutedForeground,
                fontSize = 12.sp,
            )
        }
    }
}
