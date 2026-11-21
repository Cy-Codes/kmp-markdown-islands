package codes.cy.kit.markdown

/**
 * Pure Markdown-to-HTML renderer for static SSR/reader views.
 *
 * Escapes all raw HTML input, enforces a strict `http`/`https`/`/` `href` allowlist,
 * respects nested/longer code fences, and delegates matched island fences to [islandFallbackHtml].
 */
public class HtmlMarkdownRenderer(
    private val islandPrefixes: Set<String> = DEFAULT_ISLAND_PREFIXES,
    private val islandFallbackHtml: (IslandRef) -> String = ::defaultIslandFallbackHtml,
) {
    private data class HtmlBlockStep(
        val nextIndex: Int,
        val htmlBlock: String?,
    )

    /**
     * Pure query rendering [markdown] into escaped HTML with customizable island fallbacks.
     */
    public fun renderToHtml(markdown: String): String {
        if (markdown.isBlank()) return ""

        val segments = MarkdownSegmentParser.parse(markdown, islandPrefixes)
        return segments
            .mapNotNull { segment ->
                when (segment) {
                    is MarkdownFenceSegment.Prose -> renderProseToHtml(segment.markdown).takeIf { it.isNotEmpty() }
                    is MarkdownFenceSegment.Island -> islandFallbackHtml(segment.toIslandRef()).takeIf { it.isNotEmpty() }
                }
            }
            .joinToString("\n")
    }

    private fun renderProseToHtml(proseMarkdown: String): String {
        val lines = normalizeMarkdownLines(proseMarkdown)
        return generateSequence(stepHtmlBlock(lines, 0)) { previous ->
            stepHtmlBlock(lines, previous.nextIndex)
        }
            .mapNotNull { it.htmlBlock }
            .joinToString("\n")
    }

    private fun stepHtmlBlock(
        lines: List<String>,
        index: Int,
    ): HtmlBlockStep? {
        if (index >= lines.size) return null

        val line = lines[index]
        val trimmed = line.trim()
        if (trimmed.isEmpty()) {
            val nextNonBlank = (index + 1 until lines.size).firstOrNull { lines[it].trim().isNotEmpty() }
                ?: lines.size
            return HtmlBlockStep(nextIndex = nextNonBlank, htmlBlock = null)
        }

        val fence = parseFenceOpening(line)
        if (fence != null) {
            val closeIndex = findClosingFenceIndex(lines, index, fence)
            val codeEnd = closeIndex ?: lines.size
            val codeText = lines.subList(index + 1, codeEnd).joinToString("\n")
            val languageToken = fence.infoString.split(WHITESPACE_REGEX).firstOrNull()?.takeIf { it.isNotEmpty() }
            val languageAttr = languageToken?.let { " class=\"language-${escapeHtml(it)}\"" }.orEmpty()
            return HtmlBlockStep(
                nextIndex = closeIndex?.plus(1) ?: lines.size,
                htmlBlock = "<pre><code$languageAttr>${escapeHtml(codeText)}</code></pre>",
            )
        }

        if (isHorizontalRule(trimmed)) {
            return HtmlBlockStep(nextIndex = index + 1, htmlBlock = "<hr />")
        }

        if (isHeadingLine(trimmed)) {
            val level = trimmed.takeWhile { it == '#' }.length.coerceIn(1, MAX_HTML_HEADING_LEVEL)
            val headingText = trimmed.drop(level).trim()
            return HtmlBlockStep(
                nextIndex = index + 1,
                htmlBlock = "<h$level>${renderInline(headingText)}</h$level>",
            )
        }

        if (trimmed.startsWith('>')) {
            val quoteEnd = (index + 1 until lines.size).firstOrNull { !lines[it].trim().startsWith('>') }
                ?: lines.size
            val quoteContent = lines.subList(index, quoteEnd)
                .joinToString(" ") { it.trim().removePrefix(">").trim() }
            return HtmlBlockStep(
                nextIndex = quoteEnd,
                htmlBlock = "<blockquote><p>${renderInline(quoteContent)}</p></blockquote>",
            )
        }

        if (isUnorderedListItem(trimmed)) {
            val listEnd = (index + 1 until lines.size).firstOrNull { !isUnorderedListItem(lines[it].trim()) }
                ?: lines.size
            val itemsHtml = lines.subList(index, listEnd)
                .joinToString("") { "<li>${renderInline(it.trim().drop(LIST_MARKER_PREFIX_LENGTH).trim())}</li>" }
            return HtmlBlockStep(
                nextIndex = listEnd,
                htmlBlock = "<ul>$itemsHtml</ul>",
            )
        }

        if (ORDERED_ITEM_REGEX.matches(trimmed)) {
            val listEnd = (index + 1 until lines.size).firstOrNull { !ORDERED_ITEM_REGEX.matches(lines[it].trim()) }
                ?: lines.size
            val itemsHtml = lines.subList(index, listEnd)
                .joinToString("") { itemLine ->
                    val content = ORDERED_ITEM_REGEX.matchEntire(itemLine.trim())?.groupValues?.get(1)?.trim().orEmpty()
                    "<li>${renderInline(content)}</li>"
                }
            return HtmlBlockStep(
                nextIndex = listEnd,
                htmlBlock = "<ol>$itemsHtml</ol>",
            )
        }

        val paragraphEnd = (index + 1 until lines.size).firstOrNull { cursor ->
            val candidate = lines[cursor].trim()
            candidate.isEmpty() || isBlockBoundary(lines[cursor], candidate)
        } ?: lines.size
        val paragraphText = lines.subList(index, paragraphEnd).joinToString(" ") { it.trim() }
        return HtmlBlockStep(
            nextIndex = paragraphEnd,
            htmlBlock = "<p>${renderInline(paragraphText)}</p>",
        )
    }

    private fun renderInline(raw: String): String =
        transformWithRegex(
            input = raw,
            regex = INLINE_CODE_REGEX,
            onPlain = ::renderLinksAndEmphasis,
            onMatch = { match -> "<code>${escapeHtml(match.groupValues[1])}</code>" },
        )

    private fun renderLinksAndEmphasis(rawPlain: String): String =
        transformWithRegex(
            input = rawPlain,
            regex = LINK_REGEX,
            onPlain = { text -> renderEmphasis(escapeHtml(text)) },
            onMatch = { match ->
                val formattedLabel = renderEmphasis(escapeHtml(match.groupValues[1]))
                val safeHref = sanitizeHref(match.groupValues[2])
                if (safeHref == null) {
                    formattedLabel
                } else {
                    "<a href=\"${escapeHtml(safeHref)}\">$formattedLabel</a>"
                }
            },
        )

    private fun renderEmphasis(escapedText: String): String =
        escapedText
            .let { text -> BOLD_REGEX.replace(text) { "<strong>${it.groupValues[1]}</strong>" } }
            .let { text -> ITALIC_REGEX.replace(text) { "<em>${it.groupValues[1]}</em>" } }

    private inline fun transformWithRegex(
        input: String,
        regex: Regex,
        crossinline onPlain: (String) -> String,
        crossinline onMatch: (MatchResult) -> String,
    ): String = buildString(input.length) {
        val matches = regex.findAll(input).toList()
        val endCursor = matches.fold(0) { cursor, match ->
            if (match.range.first > cursor) {
                append(onPlain(input.substring(cursor, match.range.first)))
            }
            append(onMatch(match))
            match.range.last + 1
        }
        if (endCursor < input.length) {
            append(onPlain(input.substring(endCursor)))
        }
    }

    private fun isHorizontalRule(trimmed: String): Boolean =
        trimmed == "---" || trimmed == "***" || trimmed == "___"

    private fun isHeadingLine(trimmed: String): Boolean {
        val hashes = trimmed.takeWhile { it == '#' }.length
        return hashes in 1..MAX_HTML_HEADING_LEVEL && trimmed.getOrNull(hashes) == ' '
    }

    private fun isUnorderedListItem(trimmed: String): Boolean =
        trimmed.startsWith("- ") || trimmed.startsWith("* ")

    private fun isBlockBoundary(rawLine: String, trimmed: String): Boolean =
        parseFenceOpening(rawLine) != null ||
            isHorizontalRule(trimmed) ||
            isHeadingLine(trimmed) ||
            trimmed.startsWith('>') ||
            isUnorderedListItem(trimmed) ||
            ORDERED_ITEM_REGEX.matches(trimmed)

    public companion object {
        private const val MAX_HTML_HEADING_LEVEL: Int = 6
        private const val LIST_MARKER_PREFIX_LENGTH: Int = 2
        private const val ASCII_PRINTABLE_MIN: Int = 0x20
        private const val ASCII_DELETE: Int = 0x7F
        private const val HTTPS_SCHEME: String = "https://"
        private const val HTTP_SCHEME: String = "http://"
        private const val RELATIVE_ROOT_PREFIX: String = "/"
        private const val PROTOCOL_RELATIVE_PREFIX: String = "//"

        private val WHITESPACE_REGEX: Regex = Regex("\\s+")
        private val ORDERED_ITEM_REGEX: Regex = Regex("""^\d+\.\s+(.*)$""")
        private val LINK_REGEX: Regex = Regex("""\[([^]]+)]\(([^)]+)\)""")
        private val INLINE_CODE_REGEX: Regex = Regex("""`([^`]+)`""")
        private val BOLD_REGEX: Regex = Regex("""\*\*([^*]+)\*\*""")
        private val ITALIC_REGEX: Regex = Regex("""\*([^*]+)\*""")

        /**
         * Pure query escaping HTML special characters (`&`, `<`, `>`, `"`, `'`) to prevent markup injection.
         */
        public fun escapeHtml(raw: String): String =
            buildString(raw.length) {
                raw.forEach { ch ->
                    when (ch) {
                        '&' -> append("&amp;")
                        '<' -> append("&lt;")
                        '>' -> append("&gt;")
                        '"' -> append("&quot;")
                        '\'' -> append("&#39;")
                        else -> append(ch)
                    }
                }
            }

        /**
         * Pure query validating [rawHref] against an explicit `http://`, `https://`, or root-relative `/` allowlist.
         *
         * Rejects `javascript:`, `data:`, `vbscript:`, protocol-relative `//` URLs, and whitespace/control characters.
         */
        public fun sanitizeHref(rawHref: String): String? {
            val trimmed = rawHref.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.any { ch -> ch.code < ASCII_PRINTABLE_MIN || ch.code == ASCII_DELETE || ch.isWhitespace() }) {
                return null
            }
            return when {
                trimmed.startsWith(HTTPS_SCHEME, ignoreCase = true) && trimmed.length > HTTPS_SCHEME.length ->
                    HTTPS_SCHEME + trimmed.drop(HTTPS_SCHEME.length)

                trimmed.startsWith(HTTP_SCHEME, ignoreCase = true) && trimmed.length > HTTP_SCHEME.length ->
                    HTTP_SCHEME + trimmed.drop(HTTP_SCHEME.length)

                trimmed.startsWith(RELATIVE_ROOT_PREFIX) && !trimmed.startsWith(PROTOCOL_RELATIVE_PREFIX) ->
                    trimmed

                else -> null
            }
        }

        /**
         * Pure default fallback HTML callout emitted for interactive [island] blocks in static HTML output.
         */
        public fun defaultIslandFallbackHtml(island: IslandRef): String {
            val safeKind = escapeHtml(island.kind)
            val safeId = escapeHtml(island.id)
            return """
                <aside class="markdown-island-fallback" data-island-kind="$safeKind" data-island-id="$safeId">
                    <strong>Interactive Island: <code>$safeKind:$safeId</code></strong>
                    <p>This interactive widget is not rendered in static HTML view.</p>
                </aside>
            """.trimIndent()
        }
    }
}
