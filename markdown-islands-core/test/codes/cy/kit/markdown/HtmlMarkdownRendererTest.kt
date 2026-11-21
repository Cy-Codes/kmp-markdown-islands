package codes.cy.kit.markdown

import kotlin.test.Test

class HtmlMarkdownRendererTest {

    @Test
    fun `renderToHtml renders structured blocks and escapes raw XSS payloads`() {
        val markdown = """
            # Intro & Guide

            Paragraph with **bold**, *italic*, `<code>` inline, and <script>alert(1)</script>.

            > Quoted text with <b>tag</b>

            - First item
            - Second item

            1. Step one
            2. Step two

            ---

            ```kotlin" onclick="alert(1)
            val html = "<img src=x onerror=alert(1)>"
            ```
        """.trimIndent()

        val html = HtmlMarkdownRenderer().renderToHtml(markdown)
        assertTrue(html.contains("<h1>Intro &amp; Guide</h1>"))
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<em>italic</em>"))
        assertTrue(html.contains("<code>&lt;code&gt;</code>"))
        assertFalse(html.contains("<script>alert(1)</script>"))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertTrue(html.contains("<blockquote><p>Quoted text with &lt;b&gt;tag&lt;/b&gt;</p></blockquote>"))
        assertTrue(html.contains("<ul><li>First item</li><li>Second item</li></ul>"))
        assertTrue(html.contains("<ol><li>Step one</li><li>Step two</li></ol>"))
        assertTrue(html.contains("<hr />"))
        assertFalse(html.contains("\" onclick=\"alert(1)"))
        assertTrue(html.contains("&lt;img src=x onerror=alert(1)&gt;"))
    }

    @Test
    fun `renderToHtml enforces strict http https and root-relative href allowlist`() {
        val markdown = """
            Safe [HTTPS](https://example.com/docs?q=1&mode="raw"),
            [HTTP](http://localhost:8080/health),
            [Path](/guides/intro),
            [JS](javascript:alert(1)),
            [MixedJS](JaVaScRiPt:alert(1)),
            [Data](data:text/html,<script>alert(1)</script>),
            [ProtoRel](//evil.example/phish)
        """.trimIndent()

        val html = HtmlMarkdownRenderer().renderToHtml(markdown)
        assertTrue(html.contains("<a href=\"https://example.com/docs?q=1&amp;mode=&quot;raw&quot;\">HTTPS</a>"))
        assertTrue(html.contains("<a href=\"http://localhost:8080/health\">HTTP</a>"))
        assertTrue(html.contains("<a href=\"/guides/intro\">Path</a>"))
        assertFalse(html.contains("javascript:"))
        assertFalse(html.contains("data:"))
        assertFalse(html.contains("//evil.example"))
        assertTrue(html.contains("JS"))
        assertTrue(html.contains("MixedJS"))
        assertTrue(html.contains("Data"))
        assertTrue(html.contains("ProtoRel"))
    }

    @Test
    fun `sanitizeHref rejects control characters whitespace and disallowed schemes`() {
        assertEquals("https://example.com", HtmlMarkdownRenderer.sanitizeHref("  https://example.com  "))
        assertEquals("http://example.com", HtmlMarkdownRenderer.sanitizeHref("HTTP://example.com"))
        assertEquals("/read/article", HtmlMarkdownRenderer.sanitizeHref("/read/article"))
        assertNull(HtmlMarkdownRenderer.sanitizeHref(""))
        assertNull(HtmlMarkdownRenderer.sanitizeHref("https://"))
        assertNull(HtmlMarkdownRenderer.sanitizeHref("//evil.example/path"))
        assertNull(HtmlMarkdownRenderer.sanitizeHref("javascript:alert(1)"))
        assertNull(HtmlMarkdownRenderer.sanitizeHref("java\tscript:alert(1)"))
        assertNull(HtmlMarkdownRenderer.sanitizeHref("data:text/plain,hello"))
        assertNull(HtmlMarkdownRenderer.sanitizeHref("vbscript:msgbox(1)"))
    }

    @Test
    fun `renderToHtml invokes customizable islandFallbackHtml and escapes default fallback`() {
        val markdown = """
            ```widget:chart-view theme=light
            series=cpu
            ```

            ```demo:<evil>
            ```
        """.trimIndent()

        val customRenderer = HtmlMarkdownRenderer(
            islandPrefixes = setOf("demo:", "widget:"),
        ) { island ->
            val safeKind = HtmlMarkdownRenderer.escapeHtml(island.kind)
            val safeId = HtmlMarkdownRenderer.escapeHtml(island.id)
            """<div data-kind="$safeKind" data-id="$safeId">${HtmlMarkdownRenderer.escapeHtml(island.metadata["theme"].orEmpty())}</div>"""
        }
        val customHtml = customRenderer.renderToHtml(markdown)
        assertTrue(customHtml.contains("""<div data-kind="widget" data-id="chart-view">light</div>"""))
        assertTrue(customHtml.contains("""<div data-kind="demo" data-id="&lt;evil&gt;"></div>"""))

        val defaultHtml = HtmlMarkdownRenderer().renderToHtml("```demo:<xss>\n```")
        assertFalse(defaultHtml.contains("<xss>"))
        assertTrue(defaultHtml.contains("<code>demo:&lt;xss&gt;</code>"))
        assertTrue(defaultHtml.contains("markdown-island-fallback"))
    }

    @Test
    fun `renderToHtml preserves nested fences inside outer four-backtick code blocks`() {
        val markdown = """
            ````markdown
            ```demo:not-an-island
            ```
            ````
        """.trimIndent()

        val html = HtmlMarkdownRenderer().renderToHtml(markdown)
        assertFalse(html.contains("markdown-island-fallback"))
        assertTrue(html.contains("<pre><code class=\"language-markdown\">```demo:not-an-island\n```</code></pre>"))
    }
}
