package codes.cy.kit.markdown

import kotlin.test.Test
import kotlin.test.assertFailsWith

class MarkdownOutlineExtractorAndHelpersTest {

    @Test
    fun `extract ignores fenced code comments and captures headings islands and reading time`() {
        val markdown = """
            # Architecture Overview

            ```kotlin
            # Not a heading inside code!
            val a = 1
            ```

            ````markdown
            ## Not a heading inside longer fence
            ```demo:nested-ignored
            ```
            ````

            ## Adaptive Breakpoints
            ```demo:adaptive-breakpoints initial=1280
            step=10
            ```

            ### State Flow & UDF
            ##### Deeper than default level 4
        """.trimIndent()

        val outline = MarkdownOutlineExtractor.extract(markdown)
        assertEquals(3, outline.headings.size)
        assertEquals(TocHeading(1, "Architecture Overview", "architecture-overview"), outline.headings[0])
        assertEquals(TocHeading(2, "Adaptive Breakpoints", "adaptive-breakpoints"), outline.headings[1])
        assertEquals(TocHeading(3, "State Flow & UDF", "state-flow-udf"), outline.headings[2])

        assertEquals(1, outline.embeddedIslands.size)
        assertEquals(
            IslandRef(
                kind = "demo",
                id = "adaptive-breakpoints",
                rawBody = "step=10",
                metadata = mapOf("initial" to "1280", "step" to "10"),
            ),
            outline.embeddedIslands.single(),
        )
        assertEquals(1, outline.readingTimeMin)
    }

    @Test
    fun `extract supports configurable maxLevel prefixes and unique duplicate anchor slugs`() {
        val markdown = """
            # Setup
            ## Setup
            ### Setup
            ##### Deep Section

            ```widget:gauge max=100
            ```

            ```widget:gauge max=100
            ```
        """.trimIndent()

        val outline = MarkdownOutlineExtractor.extract(
            markdown = markdown,
            maxLevel = 5,
            islandPrefixes = setOf("demo:", "widget:"),
        )
        assertEquals(
            listOf("setup", "setup-2", "setup-3", "deep-section"),
            outline.headings.map { it.anchorId },
        )
        assertEquals(1, outline.embeddedIslands.size)
        assertEquals("widget", outline.embeddedIslands.single().kind)
        assertEquals("gauge", outline.embeddedIslands.single().id)
    }

    @Test
    fun `toAnchorSlug normalizes punctuation whitespace and empty titles`() {
        assertEquals("hello-world", MarkdownOutlineExtractor.toAnchorSlug("  Hello, World!  "))
        assertEquals("section", MarkdownOutlineExtractor.toAnchorSlug("!!! --- ???"))
    }

    @Test
    fun `estimateReadingTimeMinutes rounds up with minimum one minute`() {
        assertEquals(1, estimateReadingTimeMinutes(""))
        assertEquals(1, estimateReadingTimeMinutes("   \n  "))
        assertEquals(1, estimateReadingTimeMinutes("word ".repeat(200)))
        assertEquals(2, estimateReadingTimeMinutes("word ".repeat(201)))
        assertEquals(3, estimateReadingTimeMinutes("word ".repeat(250), wordsPerMinute = 100))
        assertFailsWith<IllegalArgumentException> {
            estimateReadingTimeMinutes("hello", wordsPerMinute = 0)
        }
    }

    @Test
    fun `buildIslandFenceSnippet formats empty body non-empty body and widens colliding fences`() {
        assertEquals(
            "```demo:counter\n```",
            buildIslandFenceSnippet(id = "counter"),
        )
        assertEquals(
            "```widget:palette\nhue=180\n```",
            buildIslandFenceSnippet(kind = "widget:", id = "palette", body = "hue=180"),
        )

        val snippetWithInnerFence = buildIslandFenceSnippet(
            kind = "demo",
            id = "code-viewer",
            body = "```kotlin\nval x = 1\n```",
        )
        assertTrue(snippetWithInnerFence.startsWith("````demo:code-viewer\n"))
        assertTrue(snippetWithInnerFence.endsWith("\n````"))
        assertFailsWith<IllegalArgumentException> {
            buildIslandFenceSnippet(id = "   ")
        }
    }
}
