package app.renzoshiori.client.ui.util

private val LINE_BREAKS = Regex("""\s*[\r\n]+\s*""")

/**
 * A multi-line paste into a single-line field, as one line: each run of line
 * breaks (and the spaces around it) becomes a single space. A single-line
 * field can't type a newline, so this only ever changes a paste. Web twin:
 * the document paste handler in RenzoFrontend's one-line-paste.ts.
 */
internal fun String.oneLine(): String =
    if (indexOf('\n') < 0 && indexOf('\r') < 0) this else replace(LINE_BREAKS, " ").trim()
