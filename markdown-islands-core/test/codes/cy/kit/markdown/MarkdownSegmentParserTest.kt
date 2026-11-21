package codes.cy.kit.markdown

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class MarkdownSegmentParserTest {

    @Test
    fun `parse returns empty list for blank markdown`() {
        assertEquals(emptyList(), MarkdownSegmentParser.parse(""))
        assertEquals(emptyList(), MarkdownSegmentParser.parse("   \n\t\r\n  "))
    }

    @Test
    fun `parse separates island blocks while preserving standard fenced code blocks`() {
        val markdown = """
            # Architecture Overview

            ```kotlin
            val page = 1
            // ```demo:ignored-inside-regular-fence
            ```

            ```demo:adaptive-breakpoints
            ```

            ```sql
            SELECT id FROM items;
            ```
        """.trimIndent()

        val segments = MarkdownSegmentParser.parse(markdown)
        assertEquals(3, segments.size)

        val firstProse = assertIs<MarkdownFenceSegment.Prose>(segments[0])
        assertTrue(firstProse.markdown.contains("val page = 1"))
        assertTrue(firstProse.markdown.contains("ignored-inside-regular-fence"))

        val island = assertIs<MarkdownFenceSegment.Island>(segments[1])
        assertEquals("demo", island.kind)
        assertEquals("adaptive-breakpoints", island.id)
        assertEquals("", island.rawBody)
        assertEquals(emptyMap(), island.metadata)

        val trailingProse = assertIs<MarkdownFenceSegment.Prose>(segments[2])
        assertTrue(trailingProse.markdown.contains("SELECT id FROM items;"))
    }

    @Test
    fun `parse keeps island-like blocks intact inside longer four-backtick and tilde fences`() {
        val markdown = """
            ````markdown
            Example usage:
            ```demo:counter initial=5
            step=2
            ```
            ````

            ~~~text
            ```demo:ignored-inside-tilde
            ```
            ~~~

            ````demo:code-preview
            ```kotlin
            val answer = 42
            ```
            ````
        """.trimIndent()

        val segments = MarkdownSegmentParser.parse(markdown)
        assertEquals(2, segments.size)

        val prose = assertIs<MarkdownFenceSegment.Prose>(segments[0])
        assertTrue(prose.markdown.contains("```demo:counter initial=5"))
        assertTrue(prose.markdown.contains("ignored-inside-tilde"))

        val island = assertIs<MarkdownFenceSegment.Island>(segments[1])
        assertEquals("demo", island.kind)
        assertEquals("code-preview", island.id)
        assertTrue(island.rawBody.contains("val answer = 42"))
        assertTrue(island.rawBody.contains("```kotlin"))
    }

    @Test
    fun `parse handles unterminated regular and island fences cleanly`() {
        val unterminatedRegular = """
            ```kotlin
            val unclosed = true
            ```demo:must-remain-prose
        """.trimIndent()
        val regularSegments = MarkdownSegmentParser.parse(unterminatedRegular)
        assertEquals(1, regularSegments.size)
        val prose = assertIs<MarkdownFenceSegment.Prose>(regularSegments.single())
        assertTrue(prose.markdown.contains("must-remain-prose"))

        val unterminatedIsland = """
            Intro text

            ```demo:trailing-widget initial=3
            label=Active
        """.trimIndent()
        val islandSegments = MarkdownSegmentParser.parse(unterminatedIsland)
        assertEquals(2, islandSegments.size)
        assertIs<MarkdownFenceSegment.Prose>(islandSegments[0])
        val trailingIsland = assertIs<MarkdownFenceSegment.Island>(islandSegments[1])
        assertEquals("trailing-widget", trailingIsland.id)
        assertEquals("label=Active", trailingIsland.rawBody)
        assertEquals(mapOf("initial" to "3", "label" to "Active"), trailingIsland.metadata)
    }

    @Test
    fun `parse matches multiple configured prefixes including tilde islands`() {
        val markdown = """
            ```demo:counter
            ```

            ```widget:color-swatch
            ```

            ~~~chart:throughput
            ~~~
        """.trimIndent()

        val segments = MarkdownSegmentParser.parse(
            markdown = markdown,
            islandPrefixes = setOf("demo:", "widget:", "chart"),
        )
        assertEquals(3, segments.size)

        val first = assertIs<MarkdownFenceSegment.Island>(segments[0])
        assertEquals("demo" to "counter", first.kind to first.id)

        val second = assertIs<MarkdownFenceSegment.Island>(segments[1])
        assertEquals("widget" to "color-swatch", second.kind to second.id)

        val third = assertIs<MarkdownFenceSegment.Island>(segments[2])
        assertEquals("chart" to "throughput", third.kind to third.id)
    }

    @Test
    fun `parse extracts header and body metadata while preserving JSON bodies`() {
        val markdown = """
            ```demo:counter initial=10 title="Tap Counter" theme='dark'
            step=2
            title = Overridden Title
            ```

            ```widget:sparkline mode=compact
            {"points": [1, 2, 3]}
            ```
        """.trimIndent()

        val segments = MarkdownSegmentParser.parse(
            markdown = markdown,
            islandPrefixes = setOf("demo:", "widget:"),
        )
        assertEquals(2, segments.size)

        val counter = assertIs<MarkdownFenceSegment.Island>(segments[0])
        assertEquals("counter", counter.id)
        assertEquals(
            mapOf(
                "initial" to "10",
                "title" to "Overridden Title",
                "theme" to "dark",
                "step" to "2",
            ),
            counter.metadata,
        )
        assertEquals("step=2\ntitle = Overridden Title", counter.rawBody)

        val sparkline = assertIs<MarkdownFenceSegment.Island>(segments[1])
        assertEquals(mapOf("mode" to "compact"), sparkline.metadata)
        assertEquals("""{"points": [1, 2, 3]}""", sparkline.rawBody)
    }

    @Test
    fun `parse treats empty island id and inline backtick lines as prose`() {
        val markdown = """
            ```demo:
            body
            ```

            ```demo: key=only
            ```

            ```inline` not a fence
            ```demo:after-inline
            ```
        """.trimIndent()

        val segments = MarkdownSegmentParser.parse(markdown)
        assertEquals(2, segments.size)
        val prose = assertIs<MarkdownFenceSegment.Prose>(segments[0])
        assertTrue(prose.markdown.contains("```demo:"))
        assertTrue(prose.markdown.contains("key=only"))
        assertTrue(prose.markdown.contains("```inline` not a fence"))

        val valid = assertIs<MarkdownFenceSegment.Island>(segments[1])
        assertEquals("after-inline", valid.id)
    }

    @Test
    fun `parse normalizes CRLF and CR line endings`() {
        val markdown = "# Heading\r\n\r\n```demo:slider\rmin=0\rmax=100\r```\r\nTrailing"
        val segments = MarkdownSegmentParser.parse(markdown)
        assertEquals(3, segments.size)
        val island = assertIs<MarkdownFenceSegment.Island>(segments[1])
        assertEquals("min=0\nmax=100", island.rawBody)
        assertEquals(mapOf("min" to "0", "max" to "100"), island.metadata)
    }
}
