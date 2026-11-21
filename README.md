# kmp-markdown-islands

Domain-agnostic Kotlin Multiplatform & Compose Multiplatform library for parsing fenced interactive islands (` ```demo:<id> `, ` ```widget:<id> `, etc.), extracting outlines & reading time, rendering XSS-safe static HTML fallbacks, and embedding live Compose islands inline within syntax-highlighted Markdown prose.

Source repository: https://github.com/cy-codes/kmp-markdown-islands

---

## Maven Coordinates

| Module | Coordinates | Targets | Compose Dependency |
| :--- | :--- | :--- | :--- |
| **`markdown-islands-core`** | `codes.cy.kit:markdown-islands-core:0.1.0` | `jvm`, `android`, `iosArm64`, `iosSimulatorArm64`, `wasmJs` | None (pure Kotlin stdlib) |
| **`markdown-islands-compose`** | `codes.cy.kit:markdown-islands-compose:0.1.0` | `jvm`, `android`, `iosArm64`, `iosSimulatorArm64`, `wasmJs` | Compose Multiplatform + `multiplatform-markdown-renderer` (`0.45.0`) + `Highlights` (`1.2.0`) |

---

## Public API Overview

### `markdown-islands-core` (`codes.cy.kit.markdown`)

- **`MarkdownFenceSegment`**: `sealed interface` with `Prose(val markdown: String)` and `Island(val kind: String, val id: String, val rawBody: String = "", val metadata: Map<String, String> = emptyMap())`.
- **`MarkdownSegmentParser.parse(markdown, islandPrefixes = setOf("demo:"))`**: Pure fence-aware parser splitting prose from island directives across configurable prefixes, supporting nested/longer fences (4+ backticks, `~~~`), unterminated trailing fences, and inline/body `key=value` metadata.
- **`MarkdownOutlineExtractor.extract(markdown, maxLevel = 4, islandPrefixes = setOf("demo:"))`**: Extracts `MarkdownOutline(headings: List<TocHeading>, embeddedIslands: List<IslandRef>, readingTimeMin: Int)`, skipping `#` comments inside code fences and deduplicating colliding heading `anchorId` slugs.
- **`estimateReadingTimeMinutes(markdown, wordsPerMinute = 200)`**: Rounded-up reading time estimator with a 1-minute floor.
- **`buildIslandFenceSnippet(kind = "demo", id, body = "")`**: Builds fenced directive snippets and automatically widens outer backtick fences if `body` contains ` ``` `.
- **`HtmlMarkdownRenderer(islandPrefixes = setOf("demo:"), islandFallbackHtml = ::defaultIslandFallbackHtml)`**: Static HTML renderer escaping all raw HTML input, enforcing a strict `http://`/`https://`/root-relative `/` `href` allowlist, and emitting customizable fallback callouts for island blocks.

### `markdown-islands-compose` (`codes.cy.kit.markdown.compose`)

- **`IslandRenderer`**: `@Composable fun interface Content(island: MarkdownFenceSegment.Island, modifier: Modifier)`.
- **`IslandRegistry`**: Immutable lookup registry mapping `<kind>:<id>` or `<id>` keys to `IslandRenderer`s (`get`, `resolve`, `Render`) with a customizable `fallbackCard` (defaults to `DefaultIslandFallbackCard`).
- **`MarkdownThemeTokens` & `LocalMarkdownThemeTokens`**: Immutable Material 3 aligned token set (`MarkdownThemeDefaults.currentTokens()`, `fromMaterialTheme()`, `defaultLight()`, `defaultDark()`).
- **`InteractiveMarkdownView`**: Renders segmented prose with `multiplatform-markdown-renderer-m3`, `multiplatform-markdown-renderer-code`, and `Highlights` (`SyntaxThemes.atom`) alongside registered Compose islands.
- **`IslandContainerCard`**: Reusable themed card frame displaying a `<kind>:<id>` badge and divider.

---

## Usage

### Pure KMP Parsing, Outline & SSR HTML

```kotlin
import codes.cy.kit.markdown.HtmlMarkdownRenderer
import codes.cy.kit.markdown.MarkdownOutlineExtractor
import codes.cy.kit.markdown.MarkdownSegmentParser

val prefixes = setOf("demo:", "widget:")
val segments = MarkdownSegmentParser.parse(markdown, islandPrefixes = prefixes)
val outline = MarkdownOutlineExtractor.extract(markdown, islandPrefixes = prefixes)

val htmlRenderer = HtmlMarkdownRenderer(islandPrefixes = prefixes) { island ->
    val safeId = HtmlMarkdownRenderer.escapeHtml(island.id)
    """<div class="island-placeholder" data-island="$safeId">Interactive widget: $safeId</div>"""
}
val staticHtml = htmlRenderer.renderToHtml(markdown)
```

### Compose Multiplatform (`InteractiveMarkdownView`)

```kotlin
import codes.cy.kit.markdown.compose.InteractiveMarkdownView
import codes.cy.kit.markdown.compose.IslandContainerCard
import codes.cy.kit.markdown.compose.IslandRegistry
import codes.cy.kit.markdown.compose.IslandRenderer

val registry = IslandRegistry(
    renderersById = mapOf(
        "counter" to IslandRenderer { island, modifier ->
            IslandContainerCard(island = island, title = "Counter", modifier = modifier) {
                // Interactive Composable content using island.metadata
            }
        },
    ),
)

InteractiveMarkdownView(
    markdown = markdown,
    registry = registry,
    islandPrefixes = setOf("demo:", "widget:"),
    onUriClick = { uri -> openExternalUri(uri) },
)
```

---

## Build, Test, Sample & Publish Commands

```bash
# Build all modules across jvm, android, iosArm64, iosSimulatorArm64, and wasmJs
./kotlin build

# Run multiplatform unit tests across all test-capable targets
./kotlin test

# Run the Compose Desktop sample app (2 registered islands + 1 fallback card)
./kotlin run -m sample-desktop

# Publish libraries to local Maven repository (~/.m2/repository/codes/cy/kit/)
./kotlin publish mavenLocal -m markdown-islands-core -m markdown-islands-compose
```

---

## Preserved Invariants

1. **Strict Explicit API & Zero Domain Leakage:** Both library modules enable `explicitApi: strict`, require KDoc on public symbols, and contain zero application-specific domain nouns.
2. **Compose-Free Core:** `markdown-islands-core` depends strictly on Kotlin stdlib with zero Compose or UI dependencies.
3. **Transitive Core Export:** `markdown-islands-compose` exports `../markdown-islands-core: exported` so consumers automatically see `MarkdownFenceSegment` and core models.
4. **Pure Functional Core:** Parsers and outline/HTML helpers use immutable data classes, read-only collections, and `generateSequence` / `fold` transformations without mutable accumulators.
5. **Fence-Aware & XSS-Safe:** Longer fences (```` ```` ````) and `~~~` blocks protect nested directive examples; `HtmlMarkdownRenderer` escapes HTML entities and rejects `javascript:`, `data:`, `vbscript:`, control-char-obfuscated URLs, and protocol-relative `//` links.
6. **iOS Test Method Naming:** Unit test names on iOS-targeted modules never contain commas inside backticked `@Test` identifiers.

---

## Verification Output

- **`./kotlin build`**: `Build successful` (150 / 150 tasks across `markdown-islands-core`, `markdown-islands-compose`, and `sample-desktop`).
- **`./kotlin test`**: `Tests: 80 passed` (`Command exited with code 0`):
  - `markdown-islands-core`: 18 tests x 4 targets (`jvm`, `android`, `iosSimulatorArm64`, `wasmJs`) = 72 passed
  - `markdown-islands-compose`: 2 tests x 4 targets (`jvm`, `android`, `iosSimulatorArm64`, `wasmJs`) = 8 passed
- **`./kotlin publish mavenLocal -m markdown-islands-core -m markdown-islands-compose`**: `Publication successful` (published `0.1.0` artifacts, sources, and POMs for all 5 platforms + KMP root metadata under `~/.m2/repository/codes/cy/kit/`).
