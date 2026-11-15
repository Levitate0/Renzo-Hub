package top.levitatemedia.renzo.hub.core

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebUrlTest {
    @Test fun httpAndHttpsOpen() {
        assertTrue(isWebUrl("https://mangadex.org/title/x"))
        assertTrue(isWebUrl("HTTP://Example.com"))
        assertTrue(isWebUrl("https://site.to/search?q=a|b"))
    }

    @Test fun otherSchemesAndHostlessRefused() {
        assertFalse(isWebUrl("file:///etc/passwd"))
        assertFalse(isWebUrl("intent://scan#Intent;scheme=zxing;end"))
        assertFalse(isWebUrl("javascript:alert(1)"))
        assertFalse(isWebUrl("content://media/external"))
        assertFalse(isWebUrl("https:///nohost"))
        assertFalse(isWebUrl("https://"))
        assertFalse(isWebUrl(""))
        assertFalse(isWebUrl(null))
    }
}
