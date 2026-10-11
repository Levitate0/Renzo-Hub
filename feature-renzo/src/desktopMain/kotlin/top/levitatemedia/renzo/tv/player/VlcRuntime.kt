package top.levitatemedia.renzo.tv.player

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import java.io.File

/**
 * libvlc for the built-in desktop player, located and opened once per process.
 *
 * The Windows installer bundles libvlc next to the app — `<install>\vlc\` holding
 * libvlc.dll, libvlccore.dll and plugins\ (tools/build-windows-exe.sh) — so nobody
 * has to install VLC. vlcj's discovery consults `jna.library.path`, and on Windows
 * it then points VLC_PLUGIN_PATH at the `plugins` folder beside the DLLs, so adding
 * the bundled folder to that property is all the wiring it needs. Without a bundle
 * (Linux, or a dev run) discovery falls back to a system VLC.
 *
 * Licensing: vlcj is GPLv3; libvlc is LGPL. Distributing this inside the installer
 * puts the Hub's binaries under GPLv3 terms.
 *
 * Failure is not fatal: [factory] returns null with [failure] set, and the player
 * route falls back to handing the stream to an external player.
 */
internal object VlcRuntime {
    @Volatile private var factory: MediaPlayerFactory? = null
    @Volatile private var attempted = false

    /** Why libvlc could not be opened, for the fallback screen. */
    @Volatile var failure: String? = null
        private set

    // Subtitles are drawn by the app (WebVttOverlay), never by libvlc: the server
    // already converted every track to WebVTT, and drawing them ourselves keeps
    // them styled like the rest of the app and lets the controls sit above them.
    private val ARGS = arrayOf(
        "--quiet",
        "--no-video-title-show",
        "--no-osd",
        "--no-spu",
        "--no-sub-autodetect-file",
        "--no-snapshot-preview",
        "--no-stats",
    )

    /** `<dir holding RenzoHub.jar>/vlc`, when the installer bundled libvlc there. */
    fun bundledDir(): File? = runCatching {
        val jar = File(VlcRuntime::class.java.protectionDomain.codeSource.location.toURI())
        File(jar.parentFile, "vlc").takeIf { dir ->
            File(dir, "libvlc.dll").isFile || File(dir, "libvlc.so").isFile || File(dir, "libvlc.dylib").isFile
        }
    }.getOrNull()

    @Synchronized
    fun factory(): MediaPlayerFactory? {
        factory?.let { return it }
        if (attempted) return null
        attempted = true
        return try {
            bundledDir()?.let { dir ->
                val prior = System.getProperty("jna.library.path")
                System.setProperty(
                    "jna.library.path",
                    listOfNotNull(dir.absolutePath, prior?.takeIf { it.isNotBlank() }).joinToString(File.pathSeparator),
                )
            }
            if (!NativeDiscovery().discover()) {
                failure = "The built-in player's video engine (libvlc) wasn't found."
                return null
            }
            MediaPlayerFactory(*ARGS).also { factory = it }
        } catch (t: Throwable) {
            failure = "The built-in player couldn't start: ${t.message ?: t.javaClass.simpleName}"
            null
        }
    }

    val available: Boolean get() = factory() != null
}
