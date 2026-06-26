package com.lorenzo.aicalendar.data.extract

// Italian article/temporal filler words that rarely belong in an event title.
private val FILLER = setOf(
    "ho", "il", "lo", "la", "i", "gli", "le", "l'", "un", "uno", "una",
    "alle", "alla", "all'", "ore", "ora", "a",
)

private val NUMERIC = Regex("""^\d{1,4}([:.]\d{1,2})?$""")
private val WHITESPACE = Regex("""\s+""")

/**
 * Best-effort title from the leftover text after date/time spans are removed: drops Italian
 * article/temporal filler tokens and bare numbers, collapses whitespace, capitalizes.
 * Falls back to "Evento" when nothing meaningful remains. (The cloud extractor does this far
 * better; this is the offline fallback, so a draft the user can tidy up is good enough.)
 */
fun cleanTitle(text: String): String {
    val kept = text.split(WHITESPACE)
        .filter { it.isNotBlank() }
        .filter { token -> token.lowercase() !in FILLER && !NUMERIC.matches(token) }

    val joined = kept.joinToString(" ").trim()
    if (joined.isEmpty()) return "Evento"
    return joined.replaceFirstChar { it.uppercase() }
}
