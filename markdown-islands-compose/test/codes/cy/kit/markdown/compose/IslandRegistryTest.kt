package codes.cy.kit.markdown.compose

import codes.cy.kit.markdown.MarkdownFenceSegment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class IslandRegistryTest {

    @Test
    fun `registry resolves qualified and unqualified island IDs and returns null for unknown IDs`() {
        val defaultCounter = IslandRenderer { _, _ -> }
        val widgetCounter = IslandRenderer { _, _ -> }
        val sliderRenderer = IslandRenderer { _, _ -> }

        val registry = IslandRegistry(
            renderersById = mapOf(
                "counter" to defaultCounter,
                "widget:counter" to widgetCounter,
                "color-swatch" to sliderRenderer,
            ),
        )

        assertEquals(setOf("counter", "widget:counter", "color-swatch"), registry.registeredIds)
        assertSame(defaultCounter, registry["counter"])
        assertSame(defaultCounter, registry.resolve("  counter  "))
        assertSame(widgetCounter, registry.resolve(MarkdownFenceSegment.Island(kind = "widget", id = "counter")))
        assertSame(defaultCounter, registry.resolve(MarkdownFenceSegment.Island(kind = "demo", id = "counter")))
        assertSame(sliderRenderer, registry.resolve(MarkdownFenceSegment.Island(kind = "widget", id = "color-swatch")))
        assertNull(registry.resolve("unknown-island"))
        assertNull(registry.resolve(MarkdownFenceSegment.Island(kind = "demo", id = "unknown-island")))
    }

    @Test
    fun `theme defaults expose light and dark token configurations`() {
        val light = MarkdownThemeDefaults.defaultLight()
        val dark = MarkdownThemeDefaults.defaultDark()

        assertFalse(light.isDark)
        assertTrue(dark.isDark)
        assertTrue(light.textColor != dark.textColor)
        assertTrue(light.islandSurfaceColor != dark.islandSurfaceColor)
    }
}
