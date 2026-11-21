package codes.cy.kit.markdown

private const val MIN_FENCE_LENGTH: Int = 3
private const val BACKTICK_CHAR: Char = '`'
private const val TILDE_CHAR: Char = '~'
private const val QUOTE_DOUBLE: Char = '"'
private const val QUOTE_SINGLE: Char = '\''
private const val MIN_QUOTED_LENGTH: Int = 2

private val HEADER_ATTR_REGEX: Regex = Regex("""([A-Za-z_][A-Za-z0-9_.-]*)=(?:"([^"]*)"|'([^']*)'|(\S+))""")
private val BODY_KV_REGEX: Regex = Regex("""^([A-Za-z_][A-Za-z0-9_.-]*)\s*=\s*(.*)$""")
private val BACKTICK_RUN_REGEX: Regex = Regex("""`+""")

internal data class FenceOpening(
    val markerChar: Char,
    val markerLength: Int,
    val infoString: String,
)

internal data class ParsedIslandHeader(
    val kind: String,
    val id: String,
    val metadata: Map<String, String>,
)

/**
 * Pure line-ending normalizer splitting Markdown into individual lines.
 */
internal fun normalizeMarkdownLines(markdown: String): List<String> =
    markdown.replace("\r\n", "\n").replace("\r", "\n").split('\n')

/**
 * Pure parser detecting CommonMark-compatible backtick or tilde code fence openings.
 */
internal fun parseFenceOpening(line: String): FenceOpening? {
    val trimmed = line.trim()
    val markerChar = trimmed.firstOrNull() ?: return null
    if (markerChar != BACKTICK_CHAR && markerChar != TILDE_CHAR) return null

    val markerLength = trimmed.takeWhile { it == markerChar }.length
    if (markerLength < MIN_FENCE_LENGTH) return null

    val infoString = trimmed.drop(markerLength).trim()
    if (markerChar == BACKTICK_CHAR && infoString.contains(BACKTICK_CHAR)) {
        return null
    }
    return FenceOpening(
        markerChar = markerChar,
        markerLength = markerLength,
        infoString = infoString,
    )
}

/**
 * Pure check verifying whether [line] closes [opening] using the same character,
 * at least the same marker length, and no trailing non-whitespace info text.
 */
internal fun isClosingFence(line: String, opening: FenceOpening): Boolean {
    val trimmed = line.trim()
    if (trimmed.firstOrNull() != opening.markerChar) return false
    val runLength = trimmed.takeWhile { it == opening.markerChar }.length
    return runLength >= opening.markerLength && trimmed.drop(runLength).isBlank()
}

/**
 * Pure index lookup finding the closing fence line for [opening] after [openingIndex].
 */
internal fun findClosingFenceIndex(
    lines: List<String>,
    openingIndex: Int,
    opening: FenceOpening,
): Int? =
    (openingIndex + 1 until lines.size).firstOrNull { index ->
        isClosingFence(lines[index], opening)
    }

/**
 * Pure normalizer standardizing configured island directive prefixes in longest-first match order.
 */
internal fun normalizeIslandPrefixes(islandPrefixes: Set<String>): List<String> =
    islandPrefixes
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .map { prefix -> if (prefix.endsWith(':')) prefix else "$prefix:" }
        .distinct()
        .sortedByDescending { it.length }
        .toList()

/**
 * Pure parser extracting island kind, identifier, and inline `key=value` attributes from a fence info string.
 */
internal fun parseIslandHeader(
    infoString: String,
    normalizedPrefixes: List<String>,
): ParsedIslandHeader? {
    val matchedPrefix = normalizedPrefixes.firstOrNull { prefix -> infoString.startsWith(prefix) }
        ?: return null
    val remainder = infoString.removePrefix(matchedPrefix).trim()
    if (remainder.isEmpty()) return null

    val firstWhitespace = remainder.indexOfFirst { it.isWhitespace() }
    val rawId = if (firstWhitespace < 0) remainder else remainder.substring(0, firstWhitespace)
    val attributeSegment = if (firstWhitespace < 0) "" else remainder.substring(firstWhitespace + 1).trim()
    if (rawId.isEmpty() || rawId.contains('=')) return null

    return ParsedIslandHeader(
        kind = matchedPrefix.removeSuffix(":"),
        id = rawId,
        metadata = parseHeaderMetadata(attributeSegment),
    )
}

/**
 * Pure parser extracting `key=value` pairs from an island opening info string.
 */
internal fun parseHeaderMetadata(attributeSegment: String): Map<String, String> {
    if (attributeSegment.isBlank()) return emptyMap()
    return HEADER_ATTR_REGEX.findAll(attributeSegment).associate { match ->
        val value = match.groups[2]?.value
            ?: match.groups[3]?.value
            ?: match.groups[4]?.value
            ?: ""
        match.groupValues[1] to value
    }
}

/**
 * Pure parser extracting `key=value` configuration lines from an island body.
 */
internal fun parseBodyMetadata(bodyLines: List<String>): Map<String, String> =
    bodyLines
        .mapNotNull { line -> BODY_KV_REGEX.matchEntire(line.trim()) }
        .associate { match ->
            match.groupValues[1] to unquoteValue(match.groupValues[2].trim())
        }

/**
 * Pure formatter preserving non-blank island bodies while normalizing whitespace-only bodies to empty.
 */
internal fun formatIslandBody(bodyLines: List<String>): String =
    if (bodyLines.all { it.isBlank() }) "" else bodyLines.joinToString("\n")

/**
 * Pure helper computing a fence marker longer than any backtick run present in [body].
 */
internal fun selectNonCollidingBacktickFence(body: String): String {
    val maxInnerRun = BACKTICK_RUN_REGEX.findAll(body).maxOfOrNull { it.value.length } ?: 0
    val fenceLength = maxOf(MIN_FENCE_LENGTH, maxInnerRun + 1)
    return BACKTICK_CHAR.toString().repeat(fenceLength)
}

private fun unquoteValue(raw: String): String {
    if (raw.length >= MIN_QUOTED_LENGTH) {
        val first = raw.first()
        val last = raw.last()
        if ((first == QUOTE_DOUBLE && last == QUOTE_DOUBLE) || (first == QUOTE_SINGLE && last == QUOTE_SINGLE)) {
            return raw.substring(1, raw.length - 1)
        }
    }
    return raw
}
