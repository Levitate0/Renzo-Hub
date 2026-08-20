package app.renzoshiori.client.ui.util

import androidx.compose.ui.platform.UriHandler
import top.levitatemedia.renzo.hub.core.HubPlatform

/**
 * Open a source-site link in the system browser.
 *
 * Every call site used to be `runCatching { uriHandler.openUri(url) }` — which
 * turns ANY failure (a scheme-less URL, an unencoded space that
 * java.net.URI rejects, a platform UriHandler that throws) into a button
 * that silently does nothing. This normalizes the URL first and falls back
 * to [HubPlatform.openExternal] (Intent on Android, Desktop.browse on the
 * exe) when the Compose handler fails.
 */
fun openSourceUrl(uriHandler: UriHandler, raw: String?) {
    var url = raw?.trim().orEmpty()
    if (url.isEmpty()) return
    // java.net.URI (behind both desktop paths) rejects raw spaces that
    // browsers and Android intents shrug at.
    url = url.replace(" ", "%20")
    // Scheme-less source links ("mangasite.to/title/x", "//cdn…") are real in
    // the wild; a missing scheme fails BOTH the UriHandler and the fallback.
    if (!url.contains("://")) {
        url = if (url.startsWith("//")) "https:$url" else "https://$url"
    }
    val opened = runCatching { uriHandler.openUri(url) }.isSuccess
    if (!opened) HubPlatform.openExternal(url)
}
