package codes.cy.kit.markdown

/** Default directive prefix matched for interactive Markdown islands. */
public const val DEFAULT_ISLAND_PREFIX: String = "demo:"

/** Default set of directive prefixes matched when parsing interactive Markdown islands. */
public val DEFAULT_ISLAND_PREFIXES: Set<String> = setOf(DEFAULT_ISLAND_PREFIX)

/** Default reading speed used by [estimateReadingTimeMinutes]. */
public const val DEFAULT_WORDS_PER_MINUTE: Int = 200

/** Minimum estimated reading time returned for short or empty documents. */
public const val MIN_READING_TIME_MINUTES: Int = 1

/** Default directive kind used by [buildIslandFenceSnippet]. */
public const val DEFAULT_ISLAND_KIND: String = "demo"

private val WHITESPACE_SPLIT_REGEX: Regex = Regex("\\s+")

/**
 * Pure query estimating rounded-up reading time in whole minutes for [markdown].
 *
 * Returns at least [MIN_READING_TIME_MINUTES]. Requires [wordsPerMinute] to be positive.
 */
public fun estimateReadingTimeMinutes(
    markdown: String,
    wordsPerMinute: Int = DEFAULT_WORDS_PER_MINUTE,
): Int {
    require(wordsPerMinute > 0) { "wordsPerMinute must be positive, was $wordsPerMinute." }
    val wordCount = markdown.trim()
        .takeIf { it.isNotEmpty() }
        ?.split(WHITESPACE_SPLIT_REGEX)
        ?.count { it.isNotEmpty() }
        ?: 0
    if (wordCount == 0) return MIN_READING_TIME_MINUTES
    val roundedUp = (wordCount + wordsPerMinute - 1) / wordsPerMinute
    return roundedUp.coerceAtLeast(MIN_READING_TIME_MINUTES)
}

/**
 * Pure query building a fenced Markdown island snippet (` ```<kind>:<id> `) with optional [body].
 *
 * Automatically widens the outer backtick fence when [body] contains triple-backtick sequences.
 */
public fun buildIslandFenceSnippet(
    kind: String = DEFAULT_ISLAND_KIND,
    id: String,
    body: String = "",
): String {
    val normalizedKind = kind.trim().trimEnd(':')
    require(normalizedKind.isNotEmpty()) { "Island kind must not be blank." }
    val normalizedId = id.trim()
    require(normalizedId.isNotEmpty()) { "Island id must not be blank." }

    val normalizedBody = body.replace("\r\n", "\n").replace("\r", "\n").trim('\n')
    val fence = selectNonCollidingBacktickFence(normalizedBody)
    return if (normalizedBody.isEmpty()) {
        "$fence$normalizedKind:$normalizedId\n$fence"
    } else {
        "$fence$normalizedKind:$normalizedId\n$normalizedBody\n$fence"
    }
}
