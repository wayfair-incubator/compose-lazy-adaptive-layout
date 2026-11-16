package com.lazyadaptivelayout

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import com.lazyadaptivelayout.model.GridItemConfig
import com.lazyadaptivelayout.model.GridItemType
import com.lazyadaptivelayout.provider.GridLayoutProvider
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LazyAdaptiveGridPrivateHelpersTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun <T> createLayoutProvider(enrichedItems: List<EnrichedGridItem<T>>): GridLayoutProvider<T> {
        val clazz = Class.forName("com.lazyadaptivelayout.LazyAdaptiveGridKt")
        val method = clazz.getDeclaredMethod("createLayoutProviderFromEnrichedItems", List::class.java)
        method.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        return method.invoke(null, enrichedItems) as GridLayoutProvider<T>
    }

    @Test
    fun localGridItemConfig_withoutProvider_throws() {
        try {
            composeTestRule.setContent {
                // Reading this without a provider should throw during composition.
                LocalGridItemConfig.current
            }
            composeTestRule.waitForIdle()
            fail("Expected LocalGridItemConfig.current to throw when not provided")
        } catch (t: Throwable) {
            // Compose/Robolectric may wrap; just ensure the root is an IllegalStateException.
            val root = generateSequence(t) { it.cause }.last()
            assertTrue(root is IllegalStateException)
        }
    }

    @Test
    fun createLayoutProviderFromEnrichedItems_exposesStableMappings() {
        val enriched = listOf(
            EnrichedGridItem(
                item = "a",
                config = GridItemConfig(type = GridItemType.Uniform),
                group = Any(),
                groupColumns = 2,
                groupItemIndex = 0,
                key = 123,
                contentType = "typeA",
            ),
            EnrichedGridItem(
                item = "b",
                config = GridItemConfig(type = GridItemType.FullWidth),
                group = Any(),
                groupColumns = 1,
                groupItemIndex = 1,
                key = "keyB",
                contentType = null,
            ),
        )

        val provider = createLayoutProvider(enriched)
        assertEquals(2, provider.items.size)
        assertEquals("a", provider.items[0].item)
        assertEquals(GridItemType.Uniform, provider.items[0].config.type)
        assertEquals("b", provider.items[1].item)
        assertEquals(GridItemType.FullWidth, provider.items[1].config.type)

        assertEquals(GridItemType.Uniform, provider.getItemConfig(0).type)
        assertEquals("123", provider.getItemKey(0))
        assertEquals("typeA", provider.getContentType(0))
        assertEquals("keyB", provider.getItemKey(1))
        assertEquals(null, provider.getContentType(1))
    }

    @Test
    fun itemContent_dispatchesToGridGroupContent() {
        val calls = AtomicReference<List<Any?>>(emptyList())
        val group = GridGroup(
            items = listOf("x"),
            type = GridItemType.Uniform,
            columns = 3,
        ) { item, index, columnIndex, columns ->
            SideEffect { calls.set(listOf(item, index, columnIndex, columns)) }
        }

        val provider = createLayoutProvider(
            listOf(
                EnrichedGridItem(
                    item = "x",
                    config = GridItemConfig(type = GridItemType.Uniform),
                    group = group,
                    groupColumns = 3,
                    groupItemIndex = 0,
                    key = "k",
                    contentType = null,
                )
            )
        )

        composeTestRule.setContent {
            provider.ItemContent(item = "x", index = 0, columnIndex = 1, columns = 3)
        }
        composeTestRule.waitForIdle()

        assertEquals(listOf("x", 0, 1, 3), calls.get())
    }

    @Test
    fun itemContent_dispatchesToCustomGridItemContent_byGroupItemIndex() {
        val aCalls = AtomicInteger(0)
        val bCalls = AtomicInteger(0)

        val customGroup = CustomGridGroup(
            columns = 4,
            customItems = listOf(
                CustomGridItem(
                    item = "a",
                    span = 1,
                    content = { _, _, _, _ -> SideEffect { aCalls.incrementAndGet() } },
                ),
                CustomGridItem(
                    item = "b",
                    span = 2,
                    content = { _, _, _, _ -> SideEffect { bCalls.incrementAndGet() } },
                ),
            )
        )

        val provider = createLayoutProvider(
            listOf(
                EnrichedGridItem(
                    item = "a",
                    config = GridItemConfig(type = GridItemType.Custom(spans = 1)),
                    group = customGroup,
                    groupColumns = 4,
                    groupItemIndex = 0,
                    key = "ka",
                    contentType = null,
                ),
                EnrichedGridItem(
                    item = "b",
                    config = GridItemConfig(type = GridItemType.Custom(spans = 2)),
                    group = customGroup,
                    groupColumns = 4,
                    groupItemIndex = 1,
                    key = "kb",
                    contentType = null,
                ),
            )
        )

        composeTestRule.setContent {
            provider.ItemContent(item = "b", index = 1, columnIndex = 0, columns = 4)
        }
        composeTestRule.waitForIdle()
        assertEquals(0, aCalls.get())
        assertEquals(1, bCalls.get())
    }
}
