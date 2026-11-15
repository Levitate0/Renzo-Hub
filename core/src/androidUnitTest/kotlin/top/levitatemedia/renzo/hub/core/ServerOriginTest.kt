package top.levitatemedia.renzo.hub.core

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.cert.CertificateException
import javax.net.ssl.SSLException
import javax.net.ssl.SSLHandshakeException

/** Pins where credentials may go (isServerOrigin) and the Connect downgrade rule. */
class ServerOriginTest {
    @Test
    fun sameOriginOnly() {
        val server = "https://anime.example.com"
        assertTrue(isServerOrigin("https://anime.example.com/api/me".toHttpUrl(), server))
        assertTrue(isServerOrigin("https://ANIME.example.com:443/x".toHttpUrl(), server))
        assertFalse(isServerOrigin("http://anime.example.com/x".toHttpUrl(), server))
        assertFalse(isServerOrigin("https://anime.example.com:8443/x".toHttpUrl(), server))
        assertFalse(isServerOrigin("https://cdn.debrid.example/x".toHttpUrl(), server))
    }

    @Test
    fun bareAddressMeansHttps() {
        assertTrue(isServerOrigin("https://192.168.1.10:8787/x".toHttpUrl(), "192.168.1.10:8787"))
        assertFalse(isServerOrigin("http://192.168.1.10:8787/x".toHttpUrl(), "192.168.1.10:8787"))
    }

    @Test
    fun upgradeKeepsCredentialsDowngradeDoesNot() {
        assertTrue(isServerOrigin("https://box.lan/x".toHttpUrl(), "http://box.lan:8787"))
        assertTrue(isServerOrigin("https://box.lan:8787/x".toHttpUrl(), "http://box.lan:8787"))
        // The upgrade is 443 or the saved port, never any TLS port on the host.
        assertFalse(isServerOrigin("https://box.lan:9443/x".toHttpUrl(), "http://box.lan:8787"))
        assertFalse(isServerOrigin("http://box.lan:9000/x".toHttpUrl(), "http://box.lan:8787"))
    }

    @Test
    fun cleartextMarker() {
        assertTrue(isCleartextServer("http://192.168.1.10:8787"))
        assertFalse(isCleartextServer("https://anime.example.com"))
        assertFalse(isCleartextServer("anime.example.com"))
    }

    @Test
    fun certificateFailuresAreTold() {
        assertTrue(isCertificateFailure(SSLHandshakeException("x").apply { initCause(CertificateException("untrusted")) }))
        assertFalse(isCertificateFailure(SSLException("Unrecognized SSL message, plaintext connection?")))
        assertFalse(isCertificateFailure(java.net.ConnectException("refused")))
    }
}
