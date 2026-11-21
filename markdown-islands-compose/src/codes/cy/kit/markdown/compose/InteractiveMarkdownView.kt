package codes.cy.kit.markdown.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration
import codes.cy.kit.markdown.DEFAULT_ISLAND_PREFIXES
import codes.cy.kit.markdown.MarkdownFenceSegment
import codes.cy.kit.markdown.MarkdownSegmentParser
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeBlock
import com.mikepenz.markdown.compose.elements.MarkdownHighlightedCodeFence
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.m3.markdownTypography
import dev.snipme.highlights.Highlights
import dev.snipme.highlights.model.SyntaxThemes

/**
 * Renders rich Markdown content with `Highlights` syntax-colored code blocks and
 * inline interactive Compose islands resolved via [registry].
 */
@Composable
public fun InteractiveMarkdownView(
    markdown: String,
    registry: IslandRegistry,
    modifier: Modifier = Modifier,
    style: MarkdownThemeTokens = MarkdownThemeDefaults.currentTokens(),
    islandPrefixes: Set<String> = DEFAULT_ISLAND_PREFIXES,
    onUriClick: ((String) -> Unit)? = null,
) {
    val segments = remember(markdown, islandPrefixes) {
        MarkdownSegmentParser.parse(markdown = markdown, islandPrefixes = islandPrefixes)
    }

    val ambientUriHandler = LocalUriHandler.current
    val effectiveUriHandler = remember(onUriClick, ambientUriHandler) {
        if (onUriClick == null) {
            ambientUriHandler
        } else {
            object : UriHandler {
                override fun openUri(uri: String) = onUriClick(uri)
            }
        }
    }

    val highlightsBuilder = remember(style.isDark) {
        Highlights.Builder().theme(SyntaxThemes.atom(darkMode = style.isDark))
    }

    val mdColors = markdownColor(
        text = style.textColor,
        codeBackground = style.codeBackgroundColor,
        inlineCodeBackground = style.inlineCodeBackgroundColor,
        dividerColor = style.dividerColor,
        tableBackground = style.tableBackgroundColor,
        darkTheme = style.isDark,
    )

    val mdTypography = markdownTypography(
        h1 = style.h1Style,
        h2 = style.h2Style,
        h3 = style.h3Style,
        h4 = style.h4Style,
        paragraph = style.bodyStyle,
        text = style.bodyStyle,
        quote = style.quoteStyle,
        code = style.codeStyle,
        inlineCode = style.inlineCodeStyle,
        textLink = TextLinkStyles(
            style = SpanStyle(
                color = style.linkColor,
                textDecoration = TextDecoration.Underline,
            ),
        ),
    )

    val mdComponents = remember(highlightsBuilder) {
        markdownComponents(
            codeFence = { model ->
                MarkdownHighlightedCodeFence(
                    content = model.content,
                    node = model.node,
                    style = model.typography.code,
                    highlightsBuilder = highlightsBuilder,
                    showHeader = true,
                )
            },
            codeBlock = { model ->
                MarkdownHighlightedCodeBlock(
                    content = model.content,
                    node = model.node,
                    style = model.typography.code,
                    highlightsBuilder = highlightsBuilder,
                )
            },
        )
    }

    CompositionLocalProvider(
        LocalMarkdownThemeTokens provides style,
        LocalUriHandler provides effectiveUriHandler,
    ) {
        Column(
            modifier = modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(style.blockSpacing),
        ) {
            segments.forEach { segment ->
                when (segment) {
                    is MarkdownFenceSegment.Prose -> {
                        Markdown(
                            content = segment.markdown,
                            colors = mdColors,
                            typography = mdTypography,
                            components = mdComponents,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    is MarkdownFenceSegment.Island -> {
                        registry.Render(
                            island = segment,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}
