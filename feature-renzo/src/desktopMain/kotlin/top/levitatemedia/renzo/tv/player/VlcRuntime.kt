package top.levitatemedia.renzo.tv.player

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import java.io.File

/**
 * libVLC for the built-in desktop player, located and opened once per process.
 *
 * The Windows installer bundles libVLC next to the app — `<install>\vlc\` holding
 * libvlc.dll, libvlccore.dll and plugins\ (tools/build-windows-exe.sh), taken from
 * VideoLAN's LGPL embedding package — so nobody has to install VLC. libvlccore
 * finds its plugins in the `plugins` folder beside itself, so loading the DLLs by
 * absolute path is all the wiring it needs. Without a bundle (Linux, or a dev
 * run) a system libVLC is used.
 *
 * Opening is slow the first time (libVLC scans ~270 plugins), so [warmUp] starts
 * it in the background at app launch and Play never waits on it.
 *
 * Failure is not fatal: [instance] returns null with [failure] set, and the player
 * route falls back to handing the stream to an external player.
 */
internal object VlcRuntime {
    class Handle(val lib: LibVlc, val instance: Pointer, val version: String)

    @Volatile private var handle: Handle? = null
    @Volatile private var attempted = false

    /** Why libVLC could not be opened, for the fallback screen. */
    @Volatile var failure: String? = null
        private set

    // Subtitles are drawn by the app, never by libVLC: the server already converted
    // every track to WebVTT, and drawing them ourselves keeps them styled like the
    // rest of the app and lets the controls sit above them.
    private val ARGS = arrayOf(
        "--quiet",
        "--no-video-title-show",
        "--no-osd",
        "--no-spu",
        "--no-sub-autodetect-file",
        "--no-snapshot-preview",
        "--no-stats",
        "--no-lua",
    )

    private val windows = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)

    /** `<dir holding RenzoHub.jar>/vlc`, when the installer bundled libVLC there. */
    fun bundledDir(): File? = runCatching {
        val jar = File(VlcRuntime::class.java.protectionDomain.codeSource.location.toURI())
        File(jar.parentFile, "vlc").takeIf { File(it, libFile()).isFile }
    }.getOrNull()

    private fun libFile() = when {
        windows -> "libvlc.dll"
        System.getProperty("os.name").orEmpty().startsWith("Mac") -> "libvlc.dylib"
        else -> "libvlc.so"
    }

    private fun coreFile() = libFile().replace("libvlc", "libvlccore")

    /** Opens libVLC on a background thread so the first Play doesn't pay for it. */
    fun warmUp() {
        if (handle != null || attempted) return
        Thread({ instance() }, "libvlc-warmup").apply { isDaemon = true }.start()
    }

    val available: Boolean get() = instance() != null

    @Synchronized
    fun instance(): Handle? {
        handle?.let { return it }
        if (attempted) return null
        attempted = true
        return try {
            val opts = mapOf(Library.OPTION_STRING_ENCODING to "UTF-8")
            val dir = bundledDir()
            val lib: LibVlc = if (dir != null) {
                // libvlc.dll links against libvlccore.dll; with it already loaded by
                // absolute path, Windows resolves that import without the DLL search
                // order ever looking outside the bundle.
                NativeLibrary.getInstance(File(dir, coreFile()).absolutePath)
                Native.load(File(dir, libFile()).absolutePath, LibVlc::class.java, opts)
            } else {
                loadSystem(opts)
            }
            val inst = lib.libvlc_new(ARGS.size, ARGS)
                ?: error(lib.libvlc_errmsg() ?: "libvlc_new failed")
            Handle(lib, inst, lib.libvlc_get_version().orEmpty()).also { handle = it }
        } catch (t: Throwable) {
            failure = "The built-in player's video engine (libVLC) couldn't start: ${t.message ?: t.javaClass.simpleName}"
            null
        }
    }

    /** A system libVLC. Linux distros ship only the versioned soname without -dev. */
    private fun loadSystem(opts: Map<String, Any>): LibVlc {
        val names = if (windows) listOf("libvlc") else listOf("vlc", "libvlc.so.5")
        var last: Throwable? = null
        for (n in names) {
            try { return Native.load(n, LibVlc::class.java, opts) } catch (t: Throwable) { last = t }
        }
        throw last ?: UnsatisfiedLinkError("libvlc not found")
    }
}
