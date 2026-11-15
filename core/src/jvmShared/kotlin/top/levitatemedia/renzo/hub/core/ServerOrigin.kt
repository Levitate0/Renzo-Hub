package top.levitatemedia.renzo.hub.core

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import java.security.cert.CertPathBuilderException
import java.security.cert.CertPathValidatorException
import java.security.cert.CertificateException
import javax.net.ssl.SSLPeerUnverifiedException

/**
 * Where a credential may go, and how the connection to the user's server is
 * secured. One copy for both halves, the shared image loader, the downloader
 * and the players, because each used to answer "is this my server?" its own way.
 */

/** The user's server as typed or saved; a bare host means https, as at Connect. */
fun serverHttpUrl(server: String?): HttpUrl? {
    val s = server?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val withScheme = if (s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)) s
    else "https://$s"
    return withScheme.toHttpUrlOrNull()
}

/**
 * True when [url] is on the user's [server]: same scheme, host and port.
 *
 * Origin, not host. Matching the host alone handed the session to every other
 * port on that machine, and to plain http:// on a server the user reached over
 * https:// — which is the session sent in the clear. The one widening is an
 * upgrade: a server saved as http:// that answers on https:// at the same host
 * keeps its credentials, since that only makes the transport safer — but only
 * on the standard 443 or the saved port, so the upgrade is not "any TLS port
 * on the box" (another service there could otherwise collect the session).
 */
fun isServerOrigin(url: HttpUrl, server: HttpUrl): Boolean {
    if (!url.host.equals(server.host, ignoreCase = true)) return false
    if (url.scheme == server.scheme) return url.port == server.port
    return server.scheme == "http" && url.scheme == "https" && (url.port == 443 || url.port == server.port)
}

fun isServerOrigin(url: HttpUrl, server: String?): Boolean =
    serverHttpUrl(server)?.let { isServerOrigin(url, it) } ?: false

fun isServerOrigin(url: String, server: String?): Boolean =
    url.toHttpUrlOrNull()?.let { isServerOrigin(it, server) } ?: false

/** True when the saved server is reached over plain http — shown as "Not secure". */
fun isCleartextServer(server: String?): Boolean = serverHttpUrl(server)?.scheme == "http"

/**
 * Strips credentials from any request that is not going to the user's server.
 *
 * Install as a NETWORK interceptor: those run once per hop, so they see each
 * redirect. OkHttp carries headers set by hand across a redirect — it drops
 * Authorization only on a host change and never drops Cookie — so a 302 from
 * the server (or from anything it links to) to a CDN would otherwise take the
 * session with it.
 */
fun credentialGuard(isServer: (HttpUrl) -> Boolean): Interceptor = Interceptor { chain ->
    val request = chain.request()
    if (CREDENTIAL_HEADERS.none { request.header(it) != null } || isServer(request.url)) {
        chain.proceed(request)
    } else {
        chain.proceed(request.newBuilder().apply { CREDENTIAL_HEADERS.forEach { removeHeader(it) } }.build())
    }
}

private val CREDENTIAL_HEADERS = listOf("Cookie", "Authorization", "X-Renzo-User")

/**
 * True when [error] is a server that speaks TLS but whose certificate could not
 * be trusted — expired, self-signed, wrong name, or someone in the middle.
 *
 * The Connect screens never offer plain http after one of these: an attacker
 * who can break TLS is exactly who a downgrade helps. A port that does not
 * speak TLS at all fails differently (a protocol error), and that is the LAN
 * case where http is offered, with a warning, for the user to accept.
 */
fun isCertificateFailure(error: Throwable?): Boolean {
    var e = error
    var depth = 0
    while (e != null && depth++ < 10) {
        if (e is SSLPeerUnverifiedException || e is CertificateException ||
            e is CertPathValidatorException || e is CertPathBuilderException ||
            // The desktop JVM wraps PKIX failures in sun.security.validator.ValidatorException.
            e.javaClass.name.endsWith(".ValidatorException")
        ) return true
        e = e.cause
    }
    return false
}
