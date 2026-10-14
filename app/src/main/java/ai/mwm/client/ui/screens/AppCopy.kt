package ai.mwm.client.ui.screens

import java.util.Locale

/** Presentation-only sentence case for app copy. Never use this when saving data. */
internal fun capitalizeAppCopy(text: String, language: String = "en", preserved: Set<String> = emptySet()): String {
    if (text.isBlank() || text in preserved) return text
    val protected = BooleanArray(text.length)
    fun protect(start: Int, end: Int) { for (i in start until end) protected[i] = true }
    preserved.filter(String::isNotEmpty).forEach { value ->
        var from = 0
        while (from < text.length) {
            val start = text.indexOf(value, from)
            if (start < 0) break
            val end = start + value.length
            val left = start == 0 || !text[start - 1].isLetterOrDigit()
            val right = end == text.length || !text[end].isLetterOrDigit()
            if (left && right) protect(start, end)
            from = end
        }
    }
    // Identifiers are data, even when included in an app-authored status message.
    Regex("https?://\\S+|[\\w.+-]+@[\\w.-]+|@[\\w.-]+").findAll(text).forEach { protect(it.range.first, it.range.last + 1) }
    val locale = Locale.forLanguageTag(language)
    var sentenceStart = true
    return buildString {
        text.forEachIndexed { i, char ->
            if (protected[i]) {
                append(char)
                sentenceStart = false
            } else {
                append(if (sentenceStart && char.isLetter()) char.toString().uppercase(locale) else char.toString())
                if (char.isLetter()) sentenceStart = false
                if (char == '\n' || char == '·' || (char in ".!?…" && (i == text.lastIndex || text[i + 1].isWhitespace()))) sentenceStart = true
            }
        }
    }
}

@androidx.compose.runtime.Composable
internal fun appDisplayText(text: String): String {
    val preserved = LocalUserContent.current
    return capitalizeAppCopy(localized(text, LocalAppLanguage.current, preserved), LocalAppLanguage.current, preserved)
}
