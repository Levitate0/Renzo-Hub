package top.levitatemedia.renzo.hub.core

/**
 * Pull a reset/invite token out of whatever the user pasted.
 *
 * Emailed links open a browser, never the Hub (a self-hosted server's address
 * is user-specific, so app links can't be verified), so both halves take the
 * token through a paste field — and people paste the whole link rather than
 * surgically selecting the token. The two servers shape those links
 * differently:
 *
 *  - Shiori: `…/auth/reset-password?token=abc`, `…/auth/set-password?username=u&token=abc`
 *  - Renzo:  `…/?reset=abc` (reset) and `…/invite/abc` (invite — a PATH, not a query)
 *
 * Tries, in order: (a) the value of the first `key=` in [queryKeys] that the
 * text carries as a query/fragment parameter, (b) the path segment after
 * [pathPrefix], and (c) the trimmed input unchanged — a bare token.
 */
fun tokenFromLink(pasted: String, queryKeys: List<String>, pathPrefix: String? = null): String {
    val text = pasted.trim()
    for (key in queryKeys) {
        queryValue(text, key)?.let { return it }
    }
    if (pathPrefix != null) {
        val at = text.indexOf(pathPrefix)
        if (at >= 0) {
            val seg = text.substring(at + pathPrefix.length).takeWhile { it !in "/?&#" && !it.isWhitespace() }
            if (seg.isNotEmpty()) return seg
        }
    }
    return text
}

/** `key=value` at the start of [text] or after `?`, `&` or `#` — so "reset=" never matches "preset=". */
private fun queryValue(text: String, key: String): String? {
    val marker = "$key="
    var from = 0
    while (true) {
        val at = text.indexOf(marker, from)
        if (at < 0) return null
        if (at == 0 || text[at - 1] in "?&#") {
            return text.substring(at + marker.length).takeWhile { it != '&' && it != '#' && !it.isWhitespace() }
        }
        from = at + 1
    }
}
