package top.levitatemedia.renzo.hub.core

/**
 * True for an absolute http(s) link with a host — the only kind the Hub hands
 * to a browser. Links come from servers and source sites, so anything else
 * (file:, intent:, javascript:, content:, a custom app scheme) could open a
 * local file or fire another app's deep link instead of a web page.
 */
fun isWebUrl(url: String?): Boolean {
    val s = url?.trim()?.takeIf { it.isNotEmpty() } ?: return false
    val uri = runCatching { java.net.URI(s) }.getOrNull()
    if (uri != null) {
        val scheme = uri.scheme?.lowercase() ?: return false
        return (scheme == "http" || scheme == "https") && !uri.host.isNullOrEmpty()
    }
    // java.net.URI rejects characters real source links carry (|, [, ^ in a
    // query) that Android's lenient Uri still opens; judge those by prefix.
    val lower = s.lowercase()
    val rest = when {
        lower.startsWith("https://") -> s.substring(8)
        lower.startsWith("http://") -> s.substring(7)
        else -> return false
    }
    return rest.isNotEmpty() && rest[0] !in "/\\?#@"
}
