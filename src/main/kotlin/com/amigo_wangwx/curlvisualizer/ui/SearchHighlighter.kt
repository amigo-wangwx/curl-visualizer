package com.amigo_wangwx.curlvisualizer.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/**
 * Builds highlighted response text for the search box.
 *
 * Lifecycle: recomputed by Compose when the displayed response or search keyword changes.
 */
object SearchHighlighter {
    private val highlightStyle = SpanStyle(
        background = Color(0xFFFFE082),
        color = Color(0xFF111827),
        fontWeight = FontWeight.Bold,
    )
    private val currentHighlightStyle = SpanStyle(
        background = Color(0xFF67E8F9),
        color = Color(0xFF082F49),
        fontWeight = FontWeight.Bold,
    )

    /**
     * Returns highlighted text, match count, and the current match position.
     *
     * Empty keywords keep the original response untouched for fast rendering on large outputs.
     */
    fun highlight(
        text: String,
        keyword: String,
        currentIndex: Int,
    ): SearchResult {
        if (keyword.isBlank()) {
            return SearchResult(AnnotatedString(text), emptyList(), -1)
        }

        val lowerText = text.lowercase()
        val lowerKeyword = keyword.lowercase()
        val matches = mutableListOf<SearchMatch>()
        var searchCursor = 0

        while (searchCursor < text.length) {
            val index = lowerText.indexOf(lowerKeyword, startIndex = searchCursor)
            if (index < 0) break
            matches += SearchMatch(index, index + keyword.length)
            searchCursor = index + keyword.length
        }

        if (matches.isEmpty()) {
            return SearchResult(AnnotatedString(text), emptyList(), -1)
        }

        val selectedIndex = currentIndex.coerceIn(0, matches.lastIndex)
        var cursor = 0
        val annotated = buildAnnotatedString {
            matches.forEachIndexed { index, match ->
                append(text.substring(cursor, match.start))
                val style = if (index == selectedIndex) currentHighlightStyle else highlightStyle
                withStyle(style) {
                    append(text.substring(match.start, match.end))
                }
                cursor = match.end
            }
            append(text.substring(cursor))
        }

        return SearchResult(annotated, matches, selectedIndex)
    }
}

/**
 * Text range for one search hit.
 *
 * Ranges use offsets from the currently displayed response body, not the raw curl output.
 */
data class SearchMatch(
    val start: Int,
    val end: Int,
)

/**
 * Highlight result consumed by the response pane.
 *
 * Match metadata powers previous/next navigation without scanning the body twice.
 */
data class SearchResult(
    val text: AnnotatedString,
    val matches: List<SearchMatch>,
    val currentIndex: Int,
) {
    val count: Int
        get() = matches.size

    val currentMatch: SearchMatch?
        get() = matches.getOrNull(currentIndex)
}
