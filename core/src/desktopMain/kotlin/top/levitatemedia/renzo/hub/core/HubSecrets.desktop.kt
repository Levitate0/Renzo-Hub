package top.levitatemedia.renzo.hub.core

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

private val secretStores = ConcurrentHashMap<String, SecretPrefs>()

/**
 * A .properties store like every other setting (so it shares the lock and the
 * atomic replace), with each value sealed by DPAPI on Windows. Elsewhere the
 * value is stored as is, in a file only its owner can read (see
 * PropertiesKeyValue) — there is no OS secret store reachable without extra
 * native dependencies, and owner-only is what ssh keys get too.
 */
actual fun secretPrefs(name: String): SecretPrefs = secretStores.getOrPut(name) { DesktopSecretPrefs(keyValuePrefs(name)) }

private class DesktopSecretPrefs(private val file: KeyValuePrefs) : SecretPrefs {
    /** Raw stored value -> plaintext, so a request does not pay a DPAPI call. */
    private val opened = ConcurrentHashMap<String, Pair<String, String>>()

    override fun isAvailable(): Boolean = true

    override fun getString(key: String): String? {
        val raw = file.getString(key, null)
        if (raw == null) {
            opened.remove(key)
            return null
        }
        opened[key]?.takeIf { it.first == raw }?.let { return it.second }
        val plain = if (raw.startsWith(DPAPI_PREFIX)) {
            // Fails when the file was copied to another Windows account or PC:
            // that reads as signed out, which is the point.
            Dpapi.unprotect(raw.removePrefix(DPAPI_PREFIX)) ?: return null
        } else {
            raw
        }
        opened[key] = raw to plain
        return plain
    }

    override fun putString(key: String, value: String?): Boolean {
        opened.remove(key)
        val stored = value?.let { v -> Dpapi.protect(v)?.let { DPAPI_PREFIX + it } ?: v }
        file.putStringDurable(key, stored)
        return true
    }

    private companion object {
        const val DPAPI_PREFIX = "dpapi:"
    }
}

// Top-level and internal (public in bytecode): JNA reaches these by reflection.

/** Win32 DATA_BLOB. */
@Structure.FieldOrder("cbData", "pbData")
internal class DpapiBlob : Structure() {
    @JvmField var cbData: Int = 0
    @JvmField var pbData: Pointer? = null

    fun set(bytes: ByteArray): DpapiBlob {
        val mem = Memory(maxOf(1, bytes.size).toLong())
        mem.write(0, bytes, 0, bytes.size)
        pbData = mem
        cbData = bytes.size
        return this
    }

    fun bytes(): ByteArray = pbData?.getByteArray(0, cbData) ?: ByteArray(0)
}

@Suppress("FunctionName")
internal interface DpapiCrypt32 : Library {
    fun CryptProtectData(
        dataIn: DpapiBlob, description: Pointer?, entropy: DpapiBlob?, reserved: Pointer?,
        prompt: Pointer?, flags: Int, dataOut: DpapiBlob,
    ): Boolean

    fun CryptUnprotectData(
        dataIn: DpapiBlob, description: Pointer?, entropy: DpapiBlob?, reserved: Pointer?,
        prompt: Pointer?, flags: Int, dataOut: DpapiBlob,
    ): Boolean
}

@Suppress("FunctionName")
internal interface DpapiKernel32 : Library {
    fun LocalFree(mem: Pointer?): Pointer?
}

/**
 * Windows DPAPI (CryptProtectData), bound over JNA by hand like the libVLC
 * player: the blob can only be opened by the same Windows user, so a copied
 * or synced %APPDATA% folder carries no usable session.
 */
private object Dpapi {
    private const val CRYPTPROTECT_UI_FORBIDDEN = 0x1

    private val libs: Pair<DpapiCrypt32, DpapiKernel32>? by lazy {
        if (!System.getProperty("os.name").lowercase().contains("win")) return@lazy null
        runCatching {
            Native.load("Crypt32", DpapiCrypt32::class.java) to Native.load("Kernel32", DpapiKernel32::class.java)
        }.getOrNull()
    }

    /** Base64 of the sealed bytes, or null off Windows (or if DPAPI failed). */
    fun protect(plain: String): String? {
        val (crypt, kernel) = libs ?: return null
        return runCatching {
            val out = DpapiBlob()
            if (!crypt.CryptProtectData(DpapiBlob().set(plain.toByteArray(Charsets.UTF_8)), null, null, null, null, CRYPTPROTECT_UI_FORBIDDEN, out)) {
                return null
            }
            try { Base64.getEncoder().encodeToString(out.bytes()) } finally { kernel.LocalFree(out.pbData) }
        }.getOrNull()
    }

    fun unprotect(sealed: String): String? {
        val (crypt, kernel) = libs ?: return null
        return runCatching {
            val out = DpapiBlob()
            val input = DpapiBlob().set(Base64.getDecoder().decode(sealed))
            if (!crypt.CryptUnprotectData(input, null, null, null, null, CRYPTPROTECT_UI_FORBIDDEN, out)) return null
            try { String(out.bytes(), Charsets.UTF_8) } finally { kernel.LocalFree(out.pbData) }
        }.getOrNull()
    }
}
