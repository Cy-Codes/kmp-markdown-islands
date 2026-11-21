package codes.cy.kit.sample.markdown

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import codes.cy.kit.markdown.compose.IslandContainerCard
import codes.cy.kit.markdown.compose.IslandRegistry
import codes.cy.kit.markdown.compose.IslandRenderer
import codes.cy.kit.markdown.compose.LocalMarkdownThemeTokens
import kotlin.math.roundToInt

private const val DEFAULT_INITIAL_COUNT: Int = 0
private const val DEFAULT_STEP_SIZE: Int = 1
private const val DEFAULT_HUE_DEGREES: Float = 210f
private const val DEFAULT_SATURATION: Float = 0.72f
private const val DEFAULT_LIGHTNESS: Float = 0.54f
private const val MAX_HUE_DEGREES: Float = 360f
private const val SWATCH_SIZE_DP: Int = 56
private const val SWATCH_CORNER_DP: Int = 10
private const val CONTROL_SPACING_DP: Int = 10
private const val FULL_PERCENT_SCALE: Float = 100f

internal const val COUNTER_ISLAND_ID: String = "counter"
internal const val COLOR_SWATCH_ISLAND_ID: String = "color-swatch"

internal val SAMPLE_ISLAND_PREFIXES: Set<String> = setOf("demo:", "widget:")

internal val SAMPLE_MARKDOWN: String = """
# Interactive Markdown Islands

This sample demonstrates **kmp-markdown-islands** splitting standard Markdown prose, syntax-highlighted code blocks, and live interactive Compose islands from a single document.

## Highlighted Code Blocks

```kotlin
val registry = IslandRegistry(
    renderersById = mapOf(
        "counter" to CounterIslandRenderer,
        "widget:color-swatch" to ColorSwatchIslandRenderer,
    ),
)
```

## Registered Counter Island

Configured directly via fence metadata (`initial=10`, `step=2`):

```demo:counter initial=10 step=2
title=Interactive Step Counter
```

## Registered Color Swatch Island

Uses a custom `widget:` directive prefix alongside `demo:`:

```widget:color-swatch hue=205
saturation=0.75
lightness=0.52
```

## Nested Code Fence Escaping

Four-backtick blocks safely display literal island directives as code instead of executing them:

````markdown
```demo:counter initial=0
```
````

## Unregistered Island Fallback

Unknown identifiers render a clean domain-neutral fallback card:

```demo:unknown-visualizer
```
""".trimIndent()

internal fun createSampleIslandRegistry(): IslandRegistry =
    IslandRegistry(
        renderersById = mapOf(
            COUNTER_ISLAND_ID to CounterIslandRenderer,
            "widget:$COLOR_SWATCH_ISLAND_ID" to ColorSwatchIslandRenderer,
        ),
    )

private object CounterIslandRenderer : IslandRenderer {
    @Composable
    override fun Content(
        island: codes.cy.kit.markdown.MarkdownFenceSegment.Island,
        modifier: Modifier,
    ) {
        val initialCount = island.metadata["initial"]?.toIntOrNull() ?: DEFAULT_INITIAL_COUNT
        val stepSize = island.metadata["step"]?.toIntOrNull()?.takeIf { it > 0 } ?: DEFAULT_STEP_SIZE
        val title = island.metadata["title"] ?: "Counter Widget"
        var count by remember(island) { mutableIntStateOf(initialCount) }

        IslandContainerCard(
            island = island,
            title = title,
            subtitle = "Adjust the live state initialized from fence metadata (step=$stepSize).",
            modifier = modifier,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Value: $count",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(CONTROL_SPACING_DP.dp)) {
                    OutlinedButton(onClick = { count -= stepSize }) {
                        Text("-$stepSize")
                    }
                    Button(onClick = { count += stepSize }) {
                        Text("+$stepSize")
                    }
                    FilledTonalButton(onClick = { count = initialCount }) {
                        Text("Reset")
                    }
                }
            }
        }
    }
}

private object ColorSwatchIslandRenderer : IslandRenderer {
    @Composable
    override fun Content(
        island: codes.cy.kit.markdown.MarkdownFenceSegment.Island,
        modifier: Modifier,
    ) {
        val initialHue = island.metadata["hue"]?.toFloatOrNull()?.coerceIn(0f, MAX_HUE_DEGREES) ?: DEFAULT_HUE_DEGREES
        val initialSaturation = island.metadata["saturation"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: DEFAULT_SATURATION
        val initialLightness = island.metadata["lightness"]?.toFloatOrNull()?.coerceIn(0f, 1f) ?: DEFAULT_LIGHTNESS

        var hue by remember(island) { mutableFloatStateOf(initialHue) }
        var saturation by remember(island) { mutableFloatStateOf(initialSaturation) }
        var lightness by remember(island) { mutableFloatStateOf(initialLightness) }

        val previewColor = Color.hsl(hue = hue, saturation = saturation, lightness = lightness)
        val tokens = LocalMarkdownThemeTokens.current

        IslandContainerCard(
            island = island,
            title = "Live HSL Color Swatch",
            subtitle = "Scrub hue, saturation, and lightness in real time inside prose.",
            modifier = modifier,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(SWATCH_CORNER_DP.dp),
                    border = BorderStroke(1.dp, tokens.islandBorderColor),
                ) {
                    Box(
                        modifier = Modifier
                            .size(SWATCH_SIZE_DP.dp)
                            .clip(RoundedCornerShape(SWATCH_CORNER_DP.dp))
                            .background(previewColor),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "hsl(${hue.roundToInt()}, ${(saturation * FULL_PERCENT_SCALE).roundToInt()}%, ${(lightness * FULL_PERCENT_SCALE).roundToInt()}%)",
                        style = tokens.inlineCodeStyle,
                    )
                    Slider(
                        value = hue,
                        onValueChange = { hue = it },
                        valueRange = 0f..MAX_HUE_DEGREES,
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                    )
                    Slider(
                        value = saturation,
                        onValueChange = { saturation = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                    )
                    Slider(
                        value = lightness,
                        onValueChange = { lightness = it },
                        valueRange = 0f..1f,
                        modifier = Modifier.fillMaxWidth().height(28.dp),
                    )
                }
            }
        }
    }
}
