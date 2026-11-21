package codes.cy.kit.sample.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import codes.cy.kit.markdown.MarkdownOutline
import codes.cy.kit.markdown.MarkdownOutlineExtractor
import codes.cy.kit.markdown.compose.InteractiveMarkdownView

private const val WINDOW_WIDTH_DP: Int = 1120
private const val WINDOW_HEIGHT_DP: Int = 760
private const val SIDEBAR_WIDTH_DP: Int = 270
private const val MAX_CONTENT_WIDTH_DP: Int = 780
private const val OUTER_PADDING_DP: Int = 20
private const val SECTION_SPACING_DP: Int = 16
private const val HEADING_INDENT_STEP_DP: Int = 12

public fun main() {
    application {
        val windowState = rememberWindowState(
            width = WINDOW_WIDTH_DP.dp,
            height = WINDOW_HEIGHT_DP.dp,
        )
        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "kmp-markdown-islands · Desktop Sample",
        ) {
            SampleDesktopApp()
        }
    }
}

@Composable
private fun SampleDesktopApp() {
    var darkMode by remember { mutableStateOf(false) }
    var lastClickedUri by remember { mutableStateOf<String?>(null) }

    val registry = remember { createSampleIslandRegistry() }
    val outline = remember {
        MarkdownOutlineExtractor.extract(
            markdown = SAMPLE_MARKDOWN,
            islandPrefixes = SAMPLE_ISLAND_PREFIXES,
        )
    }

    MaterialTheme(
        colorScheme = if (darkMode) darkColorScheme() else lightColorScheme(),
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                SampleTopBar(
                    readingTimeMin = outline.readingTimeMin,
                    darkMode = darkMode,
                    lastClickedUri = lastClickedUri,
                    onToggleDarkMode = { darkMode = !darkMode },
                )
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxSize().padding(OUTER_PADDING_DP.dp),
                    horizontalArrangement = Arrangement.spacedBy(SECTION_SPACING_DP.dp),
                ) {
                    OutlineSidebar(
                        outline = outline,
                        modifier = Modifier.width(SIDEBAR_WIDTH_DP.dp).fillMaxHeight(),
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        InteractiveMarkdownView(
                            markdown = SAMPLE_MARKDOWN,
                            registry = registry,
                            islandPrefixes = SAMPLE_ISLAND_PREFIXES,
                            onUriClick = { uri -> lastClickedUri = uri },
                            modifier = Modifier.widthIn(max = MAX_CONTENT_WIDTH_DP.dp).fillMaxWidth(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SampleTopBar(
    readingTimeMin: Int,
    darkMode: Boolean,
    lastClickedUri: String?,
    onToggleDarkMode: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = OUTER_PADDING_DP.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = "kmp-markdown-islands",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "$readingTimeMin min read" + (lastClickedUri?.let { " · Clicked: $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilterChip(
            selected = darkMode,
            onClick = onToggleDarkMode,
            label = { Text(if (darkMode) "Dark Theme" else "Light Theme") },
        )
    }
}

@Composable
private fun OutlineSidebar(
    outline: MarkdownOutline,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Document Outline",
                style = MaterialTheme.typography.titleMedium,
            )
            outline.headings.forEach { heading ->
                val indentDp = ((heading.level - 1) * HEADING_INDENT_STEP_DP).dp
                Text(
                    text = "${heading.title} (#${heading.anchorId})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = indentDp),
                )
            }

            HorizontalDivider()
            Text(
                text = "Embedded Islands (${outline.embeddedIslands.size})",
                style = MaterialTheme.typography.titleSmall,
            )
            outline.embeddedIslands.forEach { island ->
                Text(
                    text = "${island.kind}:${island.id}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
