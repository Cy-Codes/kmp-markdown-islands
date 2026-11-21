package codes.cy.kit.markdown.compose

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val DARK_BACKGROUND_LUMINANCE_THRESHOLD: Float = 0.5f
private const val CODE_CONTAINER_ALPHA: Float = 0.55f
private const val DEFAULT_BLOCK_SPACING_DP: Int = 16
private const val DEFAULT_ISLAND_CORNER_RADIUS_DP: Int = 12
private const val DEFAULT_ISLAND_BORDER_WIDTH_DP: Int = 1

/**
 * Immutable visual design tokens governing [InteractiveMarkdownView] prose, code blocks,
 * and embedded interactive island containers.
 */
@Immutable
public data class MarkdownThemeTokens(
    public val isDark: Boolean,
    public val textColor: Color,
    public val mutedTextColor: Color,
    public val linkColor: Color,
    public val codeBackgroundColor: Color,
    public val inlineCodeBackgroundColor: Color,
    public val codeBorderColor: Color,
    public val dividerColor: Color,
    public val tableBackgroundColor: Color,
    public val islandSurfaceColor: Color,
    public val islandBorderColor: Color,
    public val badgeContainerColor: Color,
    public val badgeContentColor: Color,
    public val h1Style: TextStyle,
    public val h2Style: TextStyle,
    public val h3Style: TextStyle,
    public val h4Style: TextStyle,
    public val bodyStyle: TextStyle,
    public val quoteStyle: TextStyle,
    public val codeStyle: TextStyle,
    public val inlineCodeStyle: TextStyle,
    public val blockSpacing: Dp,
    public val islandCornerRadius: Dp,
    public val islandBorderWidth: Dp,
)

private val UnprovidedDefaultTokens: MarkdownThemeTokens = createStaticTokens(isDark = false)

/**
 * CompositionLocal supplying [MarkdownThemeTokens] to [InteractiveMarkdownView] and nested islands.
 */
public val LocalMarkdownThemeTokens: ProvidableCompositionLocal<MarkdownThemeTokens> =
    staticCompositionLocalOf { UnprovidedDefaultTokens }

/**
 * Pure factory helpers producing Material 3 aligned [MarkdownThemeTokens].
 */
public object MarkdownThemeDefaults {
    /**
     * Pure query returning [LocalMarkdownThemeTokens.current] when explicitly provided,
     * otherwise deriving MaterialTheme-aligned tokens from the active [MaterialTheme].
     */
    @Composable
    @ReadOnlyComposable
    public fun currentTokens(): MarkdownThemeTokens {
        val provided = LocalMarkdownThemeTokens.current
        return if (provided === UnprovidedDefaultTokens) {
            fromMaterialTheme()
        } else {
            provided
        }
    }

    /**
     * Pure query building [MarkdownThemeTokens] from the ambient [MaterialTheme] color scheme and typography.
     */
    @Composable
    @ReadOnlyComposable
    public fun fromMaterialTheme(
        isDark: Boolean = MaterialTheme.colorScheme.background.luminance() < DARK_BACKGROUND_LUMINANCE_THRESHOLD,
    ): MarkdownThemeTokens {
        val colorScheme = MaterialTheme.colorScheme
        val typography = MaterialTheme.typography
        val codeBg = colorScheme.surfaceVariant.copy(alpha = CODE_CONTAINER_ALPHA)
        return MarkdownThemeTokens(
            isDark = isDark,
            textColor = colorScheme.onBackground,
            mutedTextColor = colorScheme.onSurfaceVariant,
            linkColor = colorScheme.primary,
            codeBackgroundColor = codeBg,
            inlineCodeBackgroundColor = codeBg,
            codeBorderColor = colorScheme.outlineVariant,
            dividerColor = colorScheme.outlineVariant,
            tableBackgroundColor = colorScheme.surfaceVariant,
            islandSurfaceColor = colorScheme.surfaceContainerLow,
            islandBorderColor = colorScheme.outlineVariant,
            badgeContainerColor = colorScheme.primaryContainer,
            badgeContentColor = colorScheme.onPrimaryContainer,
            h1Style = typography.headlineMedium,
            h2Style = typography.headlineSmall,
            h3Style = typography.titleLarge,
            h4Style = typography.titleMedium,
            bodyStyle = typography.bodyLarge,
            quoteStyle = typography.bodyLarge.copy(color = colorScheme.onSurfaceVariant),
            codeStyle = typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            inlineCodeStyle = typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            blockSpacing = DEFAULT_BLOCK_SPACING_DP.dp,
            islandCornerRadius = DEFAULT_ISLAND_CORNER_RADIUS_DP.dp,
            islandBorderWidth = DEFAULT_ISLAND_BORDER_WIDTH_DP.dp,
        )
    }

    /**
     * Pure query returning standalone Material 3 light [MarkdownThemeTokens] outside a Compose tree.
     */
    public fun defaultLight(): MarkdownThemeTokens = createStaticTokens(isDark = false)

    /**
     * Pure query returning standalone Material 3 dark [MarkdownThemeTokens] outside a Compose tree.
     */
    public fun defaultDark(): MarkdownThemeTokens = createStaticTokens(isDark = true)
}

private fun createStaticTokens(isDark: Boolean): MarkdownThemeTokens {
    val scheme = if (isDark) darkColorScheme() else lightColorScheme()
    val typography = Typography()
    val codeBg = scheme.surfaceVariant.copy(alpha = CODE_CONTAINER_ALPHA)
    return MarkdownThemeTokens(
        isDark = isDark,
        textColor = scheme.onBackground,
        mutedTextColor = scheme.onSurfaceVariant,
        linkColor = scheme.primary,
        codeBackgroundColor = codeBg,
        inlineCodeBackgroundColor = codeBg,
        codeBorderColor = scheme.outlineVariant,
        dividerColor = scheme.outlineVariant,
        tableBackgroundColor = scheme.surfaceVariant,
        islandSurfaceColor = scheme.surfaceContainerLow,
        islandBorderColor = scheme.outlineVariant,
        badgeContainerColor = scheme.primaryContainer,
        badgeContentColor = scheme.onPrimaryContainer,
        h1Style = typography.headlineMedium,
        h2Style = typography.headlineSmall,
        h3Style = typography.titleLarge,
        h4Style = typography.titleMedium,
        bodyStyle = typography.bodyLarge,
        quoteStyle = typography.bodyLarge.copy(color = scheme.onSurfaceVariant),
        codeStyle = typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        inlineCodeStyle = typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
        blockSpacing = DEFAULT_BLOCK_SPACING_DP.dp,
        islandCornerRadius = DEFAULT_ISLAND_CORNER_RADIUS_DP.dp,
        islandBorderWidth = DEFAULT_ISLAND_BORDER_WIDTH_DP.dp,
    )
}
