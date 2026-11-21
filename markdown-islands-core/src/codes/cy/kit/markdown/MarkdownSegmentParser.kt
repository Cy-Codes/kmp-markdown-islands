package codes.cy.kit.markdown

/**
 * Represents a contiguous segment of a Markdown document after separating standard prose
 * from embedded interactive island fences (for example ` ```demo:<id> `).
 */
public sealed interface MarkdownFenceSegment {
    /**
     * Standard Markdown prose (including ordinary code blocks and non-island fences).
     */
    public data class Prose(
        public val markdown: String,
    ) : MarkdownFenceSegment

    /**
     * Interactive island directive extracted from a fenced block matching a configured prefix.
     */
    public data class Island(
        public val kind: String,
        public val id: String,
        public val rawBody: String = "",
        public val metadata: Map<String, String> = emptyMap(),
    ) : MarkdownFenceSegment
}

/**
 * Pure Markdown fence segment parser separating [MarkdownFenceSegment.Prose] runs from
 * interactive [MarkdownFenceSegment.Island] blocks.
 *
 * Correctly skips island-like lines nested inside regular or longer code fences (such as four-backtick
 * or tilde fences) and handles unterminated trailing fences deterministically.
 */
public object MarkdownSegmentParser {
    private data class ParseStep(
        val nextIndex: Int,
        val segment: MarkdownFenceSegment?,
    )

    /**
     * Pure query splitting [markdown] into ordered [MarkdownFenceSegment] entries for all matching [islandPrefixes].
     */
    public fun parse(
        markdown: String,
        islandPrefixes: Set<String> = DEFAULT_ISLAND_PREFIXES,
    ): List<MarkdownFenceSegment> {
        if (markdown.isBlank()) return emptyList()

        val lines = normalizeMarkdownLines(markdown)
        val normalizedPrefixes = normalizeIslandPrefixes(islandPrefixes)

        return generateSequence(stepSegment(lines, 0, normalizedPrefixes)) { previous ->
            stepSegment(lines, previous.nextIndex, normalizedPrefixes)
        }
            .mapNotNull { it.segment }
            .toList()
    }

    private fun stepSegment(
        lines: List<String>,
        index: Int,
        normalizedPrefixes: List<String>,
    ): ParseStep? {
        if (index >= lines.size) return null

        val fence = parseFenceOpening(lines[index])
        val islandHeader = fence?.let { parseIslandHeader(it.infoString, normalizedPrefixes) }
        if (fence != null && islandHeader != null) {
            val closeIndex = findClosingFenceIndex(lines, index, fence)
            val bodyEnd = closeIndex ?: lines.size
            val bodyLines = lines.subList(index + 1, bodyEnd)
            return ParseStep(
                nextIndex = closeIndex?.plus(1) ?: lines.size,
                segment = MarkdownFenceSegment.Island(
                    kind = islandHeader.kind,
                    id = islandHeader.id,
                    rawBody = formatIslandBody(bodyLines),
                    metadata = islandHeader.metadata + parseBodyMetadata(bodyLines),
                ),
            )
        }

        val proseEnd = findProseEndIndex(lines, index, normalizedPrefixes)
        val proseText = lines.subList(index, proseEnd).joinToString("\n").trim()
        return ParseStep(
            nextIndex = proseEnd,
            segment = proseText.takeIf { it.isNotEmpty() }?.let { MarkdownFenceSegment.Prose(it) },
        )
    }

    private fun findProseEndIndex(
        lines: List<String>,
        startIndex: Int,
        normalizedPrefixes: List<String>,
    ): Int =
        generateSequence(startIndex) { index ->
            if (index >= lines.size) {
                null
            } else {
                val fence = parseFenceOpening(lines[index])
                when {
                    fence != null && parseIslandHeader(fence.infoString, normalizedPrefixes) != null -> null
                    fence != null -> findClosingFenceIndex(lines, index, fence)?.plus(1) ?: lines.size
                    else -> index + 1
                }
            }
        }.last()
}
