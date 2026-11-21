package codes.cy.kit.markdown

/**
 * Immutable table-of-contents entry extracted from an ATX Markdown heading.
 */
public data class TocHeading(
    public val level: Int,
    public val title: String,
    public val anchorId: String,
)

/**
 * Immutable reference to an interactive island embedded within a Markdown document.
 */
public data class IslandRef(
    public val kind: String,
    public val id: String,
    public val rawBody: String = "",
    public val metadata: Map<String, String> = emptyMap(),
)

/**
 * Pure conversion from a parsed [MarkdownFenceSegment.Island] to an [IslandRef].
 */
public fun MarkdownFenceSegment.Island.toIslandRef(): IslandRef =
    IslandRef(
        kind = kind,
        id = id,
        rawBody = rawBody,
        metadata = metadata,
    )

/**
 * Complete structural outline extracted from a Markdown document, combining
 * table-of-contents [headings], distinct [embeddedIslands], and [readingTimeMin].
 */
public data class MarkdownOutline(
    public val headings: List<TocHeading>,
    public val embeddedIslands: List<IslandRef>,
    public val readingTimeMin: Int,
)

/**
 * Pure Markdown outline extractor extracting headings, unique embedded island references,
 * and estimated reading time in one consistent fence-aware pass.
 */
public object MarkdownOutlineExtractor {
    /** Default maximum ATX heading depth included in extracted outlines (`h1`..`h4`). */
    public const val DEFAULT_MAX_HEADING_LEVEL: Int = 4

    /** Maximum ATX heading depth supported by Markdown (`h1`..`h6`). */
    public const val MAX_MARKDOWN_HEADING_LEVEL: Int = 6

    /** Fallback anchor slug used when a heading title contains no ASCII alphanumeric characters. */
    public const val DEFAULT_SECTION_ANCHOR: String = "section"

    private const val HEADING_MARKER: Char = '#'
    private val NON_ALNUM_REGEX: Regex = Regex("[^a-z0-9\\s-]")
    private val WHITESPACE_REGEX: Regex = Regex("\\s+")

    private data class RawHeading(
        val level: Int,
        val title: String,
    )

    private data class HeadingScanStep(
        val nextIndex: Int,
        val heading: RawHeading?,
    )

    private data class AnchorAccumulator(
        val headings: List<TocHeading> = emptyList(),
        val anchorCounts: Map<String, Int> = emptyMap(),
    )

    /**
     * Pure query extracting headings (up to [maxLevel]), distinct [IslandRef] blocks for [islandPrefixes],
     * and estimated reading time from [markdown].
     */
    public fun extract(
        markdown: String,
        maxLevel: Int = DEFAULT_MAX_HEADING_LEVEL,
        islandPrefixes: Set<String> = DEFAULT_ISLAND_PREFIXES,
    ): MarkdownOutline {
        val clampedMaxLevel = maxLevel.coerceIn(1, MAX_MARKDOWN_HEADING_LEVEL)
        val segments = MarkdownSegmentParser.parse(markdown, islandPrefixes)
        val embeddedIslands = segments
            .filterIsInstance<MarkdownFenceSegment.Island>()
            .map { it.toIslandRef() }
            .distinct()

        val rawHeadings = segments
            .filterIsInstance<MarkdownFenceSegment.Prose>()
            .flatMap { prose -> extractProseHeadings(prose.markdown, clampedMaxLevel) }

        val uniqueHeadings = rawHeadings.fold(AnchorAccumulator()) { acc, raw ->
            val baseAnchor = toAnchorSlug(raw.title)
            val seenCount = (acc.anchorCounts[baseAnchor] ?: 0) + 1
            val anchorId = if (seenCount == 1) baseAnchor else "$baseAnchor-$seenCount"
            AnchorAccumulator(
                headings = acc.headings + TocHeading(level = raw.level, title = raw.title, anchorId = anchorId),
                anchorCounts = acc.anchorCounts + (baseAnchor to seenCount),
            )
        }.headings

        return MarkdownOutline(
            headings = uniqueHeadings,
            embeddedIslands = embeddedIslands,
            readingTimeMin = estimateReadingTimeMinutes(markdown),
        )
    }

    /**
     * Pure query normalizing a heading [title] into a lowercase hyphenated URL anchor slug.
     */
    public fun toAnchorSlug(title: String): String =
        title.trim()
            .lowercase()
            .replace(NON_ALNUM_REGEX, "")
            .replace(WHITESPACE_REGEX, "-")
            .trim('-')
            .ifEmpty { DEFAULT_SECTION_ANCHOR }

    private fun extractProseHeadings(
        proseMarkdown: String,
        maxLevel: Int,
    ): List<RawHeading> {
        val lines = normalizeMarkdownLines(proseMarkdown)
        return generateSequence(stepHeadingScan(lines, 0, maxLevel)) { previous ->
            stepHeadingScan(lines, previous.nextIndex, maxLevel)
        }
            .mapNotNull { it.heading }
            .toList()
    }

    private fun stepHeadingScan(
        lines: List<String>,
        index: Int,
        maxLevel: Int,
    ): HeadingScanStep? {
        if (index >= lines.size) return null

        val fence = parseFenceOpening(lines[index])
        if (fence != null) {
            val closeIndex = findClosingFenceIndex(lines, index, fence)
            return HeadingScanStep(
                nextIndex = closeIndex?.plus(1) ?: lines.size,
                heading = null,
            )
        }

        val trimmed = lines[index].trim()
        val level = trimmed.takeWhile { it == HEADING_MARKER }.length
        val heading = if (level in 1..maxLevel && trimmed.getOrNull(level) == ' ') {
            trimmed.drop(level).trim().takeIf { it.isNotEmpty() }?.let { title ->
                RawHeading(level = level, title = title)
            }
        } else {
            null
        }
        return HeadingScanStep(nextIndex = index + 1, heading = heading)
    }
}
