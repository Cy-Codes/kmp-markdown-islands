package codes.cy.kit.markdown.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import codes.cy.kit.markdown.MarkdownFenceSegment

private const val BADGE_CORNER_RADIUS_DP: Int = 999
private const val CARD_PADDING_DP: Int = 16
private const val CARD_CONTENT_SPACING_DP: Int = 12
private const val HEADER_SPACING_DP: Int = 8
private const val BADGE_HORIZONTAL_PADDING_DP: Int = 10
private const val BADGE_VERTICAL_PADDING_DP: Int = 4

/**
 * Functional renderer contract for an embedded interactive Markdown island.
 */
public fun interface IslandRenderer {
    /**
     * Renders the interactive UI for [island] within [modifier].
     */
    @Composable
    public fun Content(
        island: MarkdownFenceSegment.Island,
        modifier: Modifier,
    )
}

/**
 * Immutable registry mapping island identifiers to [IslandRenderer] implementations,
 * with a configurable [fallbackCard] for unregistered island fences.
 */
@Immutable
public class IslandRegistry(
    private val renderersById: Map<String, IslandRenderer>,
    public val fallbackCard: @Composable (MarkdownFenceSegment.Island, Modifier) -> Unit = { island, modifier ->
        DefaultIslandFallbackCard(island = island, modifier = modifier)
    },
) {
    /**
     * Pure query returning all registered renderer keys.
     */
    public val registeredIds: Set<String>
        get() = renderersById.keys

    /**
     * Pure lookup resolving a renderer by trimmed [id].
     */
    public operator fun get(id: String): IslandRenderer? = resolve(id)

    /**
     * Pure lookup resolving a renderer by trimmed [id].
     */
    public fun resolve(id: String): IslandRenderer? = renderersById[id.trim()]

    /**
     * Pure lookup resolving a renderer for [island], preferring qualified `<kind>:<id>` keys
     * before falling back to unqualified `<id>`.
     */
    public fun resolve(island: MarkdownFenceSegment.Island): IslandRenderer? {
        val qualifiedKey = "${island.kind.trim()}:${island.id.trim()}"
        return renderersById[qualifiedKey] ?: renderersById[island.id.trim()]
    }

    /**
     * Composable entry point rendering [island] with its registered [IslandRenderer],
     * or [fallbackCard] when no matching renderer exists.
     */
    @Composable
    public fun Render(
        island: MarkdownFenceSegment.Island,
        modifier: Modifier = Modifier,
    ) {
        val renderer = resolve(island)
        if (renderer != null) {
            renderer.Content(island, modifier)
        } else {
            fallbackCard(island, modifier)
        }
    }
}

/**
 * Reusable themed card frame for presenting an interactive [island] with a `<kind>:<id>` badge.
 */
@Composable
public fun IslandContainerCard(
    island: MarkdownFenceSegment.Island,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val tokens = MarkdownThemeDefaults.currentTokens()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.islandCornerRadius),
        color = tokens.islandSurfaceColor,
        border = BorderStroke(tokens.islandBorderWidth, tokens.islandBorderColor),
    ) {
        Column(
            modifier = Modifier.padding(CARD_PADDING_DP.dp),
            verticalArrangement = Arrangement.spacedBy(CARD_CONTENT_SPACING_DP.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = tokens.textColor,
                    modifier = Modifier.weight(1f).padding(end = HEADER_SPACING_DP.dp),
                )
                Surface(
                    shape = RoundedCornerShape(BADGE_CORNER_RADIUS_DP.dp),
                    color = tokens.badgeContainerColor,
                ) {
                    Text(
                        text = "${island.kind}:${island.id}",
                        style = tokens.inlineCodeStyle,
                        color = tokens.badgeContentColor,
                        modifier = Modifier.padding(
                            horizontal = BADGE_HORIZONTAL_PADDING_DP.dp,
                            vertical = BADGE_VERTICAL_PADDING_DP.dp,
                        ),
                    )
                }
            }

            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = tokens.mutedTextColor,
                )
            }

            if (content != null) {
                HorizontalDivider(color = tokens.islandBorderColor)
                content()
            }
        }
    }
}

/**
 * Default neutral fallback card rendered when an embedded [island] is not registered in [IslandRegistry].
 */
@Composable
public fun DefaultIslandFallbackCard(
    island: MarkdownFenceSegment.Island,
    modifier: Modifier = Modifier,
) {
    IslandContainerCard(
        island = island,
        title = "Interactive Island",
        subtitle = "Island '${island.kind}:${island.id}' is not registered in this host.",
        modifier = modifier,
    )
}
