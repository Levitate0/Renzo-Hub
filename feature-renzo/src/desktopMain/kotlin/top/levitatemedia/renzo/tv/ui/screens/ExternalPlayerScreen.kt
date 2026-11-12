package top.levitatemedia.renzo.tv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.levitatemedia.renzo.tv.AppServices
import top.levitatemedia.renzo.tv.api.ResolvedStream
import top.levitatemedia.renzo.tv.offline.episodeKey
import top.levitatemedia.renzo.tv.renzoToast
import top.levitatemedia.renzo.tv.ui.theme.RenzoColors
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Desktop playback, phase one (RENZO-DESKTOP-NATIVE-HANDOFF.md §6.5): resolve
 * the stream — the same up-to-~45s debrid block the Android player sits
 * through behind a full-screen state — then hand the URL, the VTT sidecars
 * and the resume position to an external mpv/VLC. The built-in libVLC player
 * (BuiltInPlayerScreen) now sits behind the same RenzoPlayerRoute seam; this
 * screen is what the route falls back to when the user prefers an external
 * player, libVLC is missing, or "Open in external player" was chosen.
 *
 * Progress can't flow back from an external process, so the screen stays up
 * with explicit "Mark watched" actions, exactly as the handoff prescribes.
 */
@Composable
internal fun ExternalPlayerScreen(
    app: AppServices,
    titleId: Int,
    ep: Int,
    titleName: String,
    onExit: () -> Unit,
    onSwitchEpisode: (Int) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var phase by remember(titleId, ep) { mutableStateOf<Phase>(Phase.Resolving) }
    var playerPath by remember { mutableStateOf(app.prefs.externalPlayerPath ?: detectPlayer()) }

    fun launchIn(stream: ResolvedStream) {
        val exe = playerPath
        if (exe == null) {
            phase = Phase.NoPlayer(stream)
            return
        }
        val result = runCatching { launchExternal(app, exe, stream, titleId, ep, titleName) }
        phase = if (result.isSuccess) {
            Phase.Playing(stream, File(exe).nameWithoutExtension)
        } else {
            Phase.Error("Couldn't start ${File(exe).name}: ${result.exceptionOrNull()?.message}", stream)
        }
    }

    LaunchedEffect(titleId, ep) {
        phase = Phase.Resolving
        val result = withContext(Dispatchers.IO) { runCatching { app.repo.play(titleId, ep) } }
        result.fold(
            onSuccess = { launchIn(it) },
            onFailure = { phase = Phase.Error(it.message ?: "Couldn't resolve a stream.", null) },
        )
    }

    fun choosePlayer(stream: ResolvedStream?) {
        val dialog = FileDialog(null as Frame?, "Choose a video player", FileDialog.LOAD)
        dialog.isVisible = true
        val file = dialog.file?.let { File(dialog.directory, it) } ?: return
        playerPath = file.absolutePath
        app.prefs.externalPlayerPath = file.absolutePath
        if (stream != null) launchIn(stream)
    }

    Box(Modifier.fillMaxSize().background(RenzoColors.Background), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.widthIn(max = 520.dp).padding(24.dp),
        ) {
            Text(
                "$titleName — Episode $ep",
                color = RenzoColors.Foreground,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            when (val ph = phase) {
                is Phase.Resolving -> {
                    CircularProgressIndicator(color = RenzoColors.Primary, modifier = Modifier.size(32.dp))
                    Text(
                        "Resolving stream — this can take up to a minute while the " +
                            "debrid link is prepared.",
                        color = RenzoColors.MutedForeground,
                        fontSize = 13.sp,
                    )
                    ActionButton("Cancel", primary = false, onClick = onExit)
                }
                is Phase.NoPlayer -> {
                    Text(
                        "No external player found. Install mpv or VLC, or point Renzo at " +
                            "one directly.",
                        color = RenzoColors.MutedForeground,
                        fontSize = 13.sp,
                    )
                    ActionButton("Choose player…", primary = true) { choosePlayer(ph.stream) }
                    ActionButton("Back", primary = false, onClick = onExit)
                }
                is Phase.Playing -> {
                    Text(
                        "Playing in ${ph.player}. Subtitles and your resume position went " +
                            "with it.",
                        color = RenzoColors.MutedForeground,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Mark watched · next episode", primary = true) {
                            scope.launch {
                                markWatched(app, titleId, ep)
                                onSwitchEpisode(ep + 1)
                            }
                        }
                        ActionButton("Mark watched · back", primary = false) {
                            scope.launch {
                                markWatched(app, titleId, ep)
                                onExit()
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("Replay", primary = false) { launchIn(ph.stream) }
                        ActionButton("Different player…", primary = false) { choosePlayer(ph.stream) }
                        ActionButton("Back", primary = false, onClick = onExit)
                    }
                }
                is Phase.Error -> {
                    Text(ph.message, color = RenzoColors.Red400, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (ph.stream != null) {
                            ActionButton("Try again", primary = true) { launchIn(ph.stream) }
                            ActionButton("Different player…", primary = false) { choosePlayer(ph.stream) }
                        }
                        ActionButton("Back", primary = false, onClick = onExit)
                    }
                }
            }
        }
    }
}

private sealed interface Phase {
    data object Resolving : Phase
    data class NoPlayer(val stream: ResolvedStream) : Phase
    data class Playing(val stream: ResolvedStream, val player: String) : Phase
    data class Error(val message: String, val stream: ResolvedStream?) : Phase
}

@Composable
private fun ActionButton(label: String, primary: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (primary) Modifier.background(RenzoColors.Primary)
                else Modifier.border(1.dp, RenzoColors.Border, RoundedCornerShape(8.dp)),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            color = if (primary) RenzoColors.PrimaryForeground else RenzoColors.Foreground,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

private suspend fun markWatched(app: AppServices, titleId: Int, ep: Int) {
    withContext(Dispatchers.IO) {
        runCatching { app.repo.markWatched(titleId, ep) }
            .onSuccess { app.prefs.clearResumePosition(episodeKey(titleId, ep)) }
            .onFailure { renzoToast("Couldn't mark the episode watched.") }
    }
}

/** mpv preferred (URL sub-files, --start), VLC next, PATH then the usual dirs. */
private fun detectPlayer(): String? {
    val exes = if (isWindows()) listOf("mpv.exe", "vlc.exe") else listOf("mpv", "vlc")
    val pathDirs = (System.getenv("PATH") ?: "").split(File.pathSeparator)
    for (exe in exes) {
        for (dir in pathDirs) {
            val f = File(dir, exe)
            if (f.isFile && f.canExecute()) return f.absolutePath
        }
    }
    val known = listOf(
        "C:\\Program Files\\mpv\\mpv.exe",
        "C:\\Program Files\\VideoLAN\\VLC\\vlc.exe",
        "C:\\Program Files (x86)\\VideoLAN\\VLC\\vlc.exe",
        "/usr/bin/mpv",
        "/usr/bin/vlc",
        "/Applications/VLC.app/Contents/MacOS/VLC",
    )
    return known.firstOrNull { File(it).isFile }
}

private fun isWindows(): Boolean =
    System.getProperty("os.name")?.lowercase()?.contains("win") == true

/**
 * §6.5's hand-off: subtitles ride along as temp files, the resume position
 * becomes --start, and the session cookie goes ONLY where it is needed.
 *
 * The cookie is the user's live Renzo session, so two rules:
 *  - **Scoped to the server's own host.** It is handed over only when the
 *    video itself is served by Renzo (a local /files episode). A debrid CDN
 *    stream never gets it — the player would otherwise send it to every host
 *    it touches. Captions come from the server too, so the Hub fetches them
 *    itself (cookie on that request only) and gives the player plain files.
 *  - **Never on the command line**, where any local process can read it.
 *    mpv takes it from a private, short-lived `--include` config file. VLC has
 *    no option for a cookie value at all (`http-cookies` is an internal
 *    cookie-jar variable, not a command-line option), so a server-hosted
 *    episode cannot be handed to VLC — the user is told to use mpv or the
 *    built-in player instead.
 */
private fun launchExternal(
    app: AppServices,
    exe: String,
    stream: ResolvedStream,
    titleId: Int,
    ep: Int,
    titleName: String,
) {
    val videoUrl = app.client.absolute(stream.url)
    val serverHost = runCatching { java.net.URI(app.client.base()).host }.getOrNull()
    val videoHost = runCatching { java.net.URI(videoUrl).host }.getOrNull()
    val videoOnServer = serverHost != null && videoHost != null && videoHost.equals(serverHost, ignoreCase = true)
    val cookie = if (videoOnServer) app.client.cookieHeader() else null
    val resumeSec = app.prefs.resumePositionMs(episodeKey(titleId, ep)) / 1000
    val subUrls = stream.subtitles.map { app.client.absolute("/api/captions/${it.id}.vtt") }
    val isMpv = File(exe).name.lowercase().startsWith("mpv")

    if (!isMpv && cookie != null) {
        error(
            "VLC can't send your Renzo sign-in, so it can't play episodes stored on your server. " +
                "Use mpv or the built-in player for this one.",
        )
    }

    var secretFile: File? = null
    val args = buildList {
        add(exe)
        if (isMpv) {
            add(videoUrl)
            add("--force-media-title=$titleName — Episode $ep")
            subUrls.forEach { url -> downloadToTemp(app, url)?.let { add("--sub-file=${it.absolutePath}") } }
            if (resumeSec > 0) add("--start=$resumeSec")
            if (cookie != null) {
                secretFile = privateTempFile("renzo-mpv-", ".conf").also { f ->
                    // `-append` adds ONE list item (no comma splitting) and
                    // %len% quotes the value verbatim.
                    val header = "Cookie: $cookie"
                    f.writeText("http-header-fields-append=%${header.length}%$header\n")
                    add("--include=${f.absolutePath}")
                }
            }
        } else {
            add(videoUrl)
            // VLC won't take a URL for sub-file — fetch the first sidecar to a
            // temp path (the server already picked the best track per language).
            subUrls.firstOrNull()?.let { url ->
                downloadToTemp(app, url)?.let { add("--sub-file=${it.absolutePath}") }
            }
            if (resumeSec > 0) add("--start-time=$resumeSec")
        }
    }
    ProcessBuilder(args)
        .redirectOutput(ProcessBuilder.Redirect.DISCARD)
        .redirectError(ProcessBuilder.Redirect.DISCARD)
        .start()
    // mpv reads --include while starting up; the session must not outlive that
    // on disk. deleteOnExit covers a Hub that quits first.
    secretFile?.let { f ->
        Thread {
            runCatching { Thread.sleep(30_000) }
            f.delete()
        }.apply { isDaemon = true }.start()
    }
}

/** A temp file only this OS user can read or write. */
private fun privateTempFile(prefix: String, suffix: String): File {
    val f = File.createTempFile(prefix, suffix)
    f.deleteOnExit()
    f.setReadable(false, false); f.setReadable(true, true)
    f.setWritable(false, false); f.setWritable(true, true)
    f.setExecutable(false, false)
    return f
}

private fun downloadToTemp(app: AppServices, url: String): File? = runCatching {
    val builder = okhttp3.Request.Builder().url(url)
    app.client.cookieHeader()?.let { builder.header("Cookie", it) }
    app.client.http.newCall(builder.build()).execute().use { resp ->
        if (!resp.isSuccessful) return null
        val f = File.createTempFile("renzo-sub-", ".vtt")
        f.deleteOnExit()
        f.writeBytes(resp.body?.bytes() ?: return null)
        f
    }
}.getOrNull()
