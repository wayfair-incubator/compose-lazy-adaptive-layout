package com.lazyadaptivelayout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import com.lazyadaptivelayout.model.GridItem
import com.lazyadaptivelayout.model.GridItemConfig
import com.lazyadaptivelayout.model.GridItemType
import com.lazyadaptivelayout.provider.GridLayoutProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for measureGridItems function behavior.
 */
@OptIn(ExperimentalFoundationApi::class)
class MeasureGridItemsTest {

    private class FakePlaceable(private val desiredWidth: Int, private val desiredHeight: Int) : Placeable() {
        init {
            // Use reflection to set private fields 'width' and 'height'
            val cls = Placeable::class.java
            val wField = cls.getDeclaredField("width").apply { isAccessible = true }
            val hField = cls.getDeclaredField("height").apply { isAccessible = true }
            wField.setInt(this, desiredWidth)
            hField.setInt(this, desiredHeight)
        }
        val placed = mutableListOf<IntOffset>()
        override fun get(alignmentLine: AlignmentLine): Int = 0
        override fun placeAt(position: IntOffset, zIndex: Float, layerBlock: (GraphicsLayerScope.() -> Unit)?) {
            placed += position
        }
    }

    private class RecordingMeasurer(private val heightsByIndex: Map<Int, Int>) {
        val calls = mutableListOf<Pair<Int, Constraints>>()
        fun measure(index: Int, constraints: Constraints): List<Placeable> {
            calls += index to constraints
            val h = heightsByIndex[index] ?: 0
            return if (h > 0) listOf(FakePlaceable(constraints.maxWidth, h)) else emptyList()
        }
    }

    private fun <T> fakeProvider(count: Int): GridLayoutProvider<T> = object : GridLayoutProvider<T> {
        override val items: List<GridItem<T>> = emptyList()
        override val itemCount: Int = count
        override fun getItemConfig(index: Int): GridItemConfig = error("unused")
        override fun getItemKey(index: Int): String = index.toString()
        override fun getContentType(index: Int): Any? = null
        @Suppress("UNUSED_PARAMETER")
        @Composable
        override fun ItemContent(item: T, index: Int, columnIndex: Int, columns: Int) { /* unused in tests */ }
    }

    private data class SimpleEnriched<T>(
        val item: T,
        val columns: Int,
        val type: GridItemType = GridItemType.Uniform
    )

    private fun <T> buildEnriched(items: List<SimpleEnriched<T>>): List<EnrichedGridItem<T>> {
        return items.mapIndexed { i, e ->
            EnrichedGridItem(
                item = e.item,
                config = GridItemConfig(type = e.type),
                group = Any(),
                groupColumns = e.columns,
                groupItemIndex = i,
                key = i,
                contentType = null
            )
        }
    }

    private val defaultWidthFn: (GridItemConfig, Int, Int) -> Int = { config, groupColumns, maxWidth ->
        when (val t = config.type) {
            is GridItemType.Custom -> (maxWidth / groupColumns) * minOf(t.spans, groupColumns)
            is GridItemType.FullWidth -> maxWidth
            else -> maxWidth / groupColumns
        }
    }

    @Test
    fun returns_empty_lists_when_item_count_is_zero() {
        val provider = fakeProvider<Any>(0)
        val enriched = emptyList<EnrichedGridItem<Any>>()
        val measuredHeights = mutableMapOf<Int, Int>()

        val (placeables, heights, heightChanges) = measureGridItems(
            layoutProvider = provider,
            enrichedItems = enriched,
            visibleRange = 0..0,
            measuredHeights = measuredHeights,
            constraints = Constraints(maxWidth = 100, maxHeight = 100),
            measurer = { _, _ -> error("should not be called") },
            bufferSize = 0,
            getCachedWidth = defaultWidthFn
        )

        assertTrue(placeables.isEmpty())
        assertTrue(heights.isEmpty())
        assertTrue(heightChanges.isEmpty())
    }

    @Test
    fun measures_only_window_plus_buffer_and_places_only_visible() {
        val itemCount = 20
        val provider = fakeProvider<Any>(itemCount)
        val enriched = buildEnriched(List(itemCount) { SimpleEnriched(item = Any(), columns = 2) })
        val visibleRange = 5..9
        val buffer = 2
        val constraints = Constraints(maxWidth = 200, maxHeight = 10000)

        // All indices return height = 10 when measured
        val scope = RecordingMeasurer((0 until itemCount).associateWith { 10 })
        val measuredHeights = mutableMapOf<Int, Int>()

        val (placeables, heights, heightChanges) = measureGridItems(
            layoutProvider = provider,
            enrichedItems = enriched,
            visibleRange = visibleRange,
            measuredHeights = measuredHeights,
            constraints = constraints,
            measurer = scope::measure,
            bufferSize = buffer,
            getCachedWidth = defaultWidthFn
        )

        val safeStart = maxOf(0, visibleRange.first - buffer)
        val safeEnd = minOf(itemCount - 1, visibleRange.last + buffer)

        // Assert: measured exactly safeStart..safeEnd
        val measuredIndices = scope.calls.map { pair -> pair.first }.toSet()
        assertEquals((safeStart..safeEnd).toSet(), measuredIndices)

        // Assert: placeables only for indices inside visibleRange
        for (i in 0 until itemCount) {
            if (i in visibleRange) {
                assertTrue("expected non-null placeable for visible index $i", placeables[i] != null)
            } else {
                assertTrue("expected null placeable for non-visible index $i", placeables[i] == null)
            }
        }

        // Assert: heights populated for measured range and > 0
        for (i in safeStart..safeEnd) {
            assertTrue(heights[i] > 0)
        }
        // Height changes should be recorded for each measured item (old height was 0)
        assertEquals((safeStart..safeEnd).count(), heightChanges.size)
    }

    @Test
    fun uses_cached_height_offscreen_without_measuring() {
        val itemCount = 10
        val provider = fakeProvider<Any>(itemCount)
        val enriched = buildEnriched(List(itemCount) { SimpleEnriched(item = Any(), columns = 2) })
        val visibleRange = 4..5
        val buffer = 1
        val constraints = Constraints(maxWidth = 200, maxHeight = 10000)

        val offscreenBufferedIndex = 3 // within safeStart but not visible
        val measuredHeights = mutableMapOf(offscreenBufferedIndex to 42)

        val scope = RecordingMeasurer(emptyMap()) // if called, would return empty (height 0)

        val (placeables, heights, heightChanges) = measureGridItems(
            layoutProvider = provider,
            enrichedItems = enriched,
            visibleRange = visibleRange,
            measuredHeights = measuredHeights,
            constraints = constraints,
            measurer = scope::measure,
            bufferSize = buffer,
            getCachedWidth = defaultWidthFn
        )

        // Offscreen buffered index should not be measured since it has cached height
        val measuredIndices = scope.calls.map { pair -> pair.first }.toSet()
        assertTrue(!measuredIndices.contains(offscreenBufferedIndex))

        // Height preserved from cache
        assertEquals(42, heights[offscreenBufferedIndex])
        // No placeable created for offscreen
        assertTrue(placeables[offscreenBufferedIndex] == null)
        // No height change recorded for cached reuse
        assertTrue(heightChanges.none { it.first == offscreenBufferedIndex })
    }

    @Test
    fun visible_items_are_measured_even_if_cached_height_exists() {
        val itemCount = 8
        val provider = fakeProvider<Any>(itemCount)
        val enriched = buildEnriched(List(itemCount) { SimpleEnriched(item = Any(), columns = 2) })
        val visibleRange = 3..4
        val constraints = Constraints(maxWidth = 200, maxHeight = 10_000)

        // Provide cached heights for visible items; they still must be measured.
        val measuredHeights = mutableMapOf(3 to 10, 4 to 10)
        val scope = RecordingMeasurer(mapOf(3 to 10, 4 to 10))

        val (_, heights, heightChanges) = measureGridItems(
            layoutProvider = provider,
            enrichedItems = enriched,
            visibleRange = visibleRange,
            measuredHeights = measuredHeights,
            constraints = constraints,
            measurer = scope::measure,
            bufferSize = 0,
            getCachedWidth = defaultWidthFn
        )

        val measuredIndices = scope.calls.map { it.first }.toSet()
        assertEquals(setOf(3, 4), measuredIndices)
        // Heights remain the same; no deltas.
        assertEquals(10, heights[3])
        assertEquals(10, heights[4])
        assertTrue(heightChanges.isEmpty())
    }

    @Test
    fun reports_height_changes_and_updates_cache_when_height_differs() {
        val itemCount = 6
        val provider = fakeProvider<Any>(itemCount)
        val enriched = buildEnriched(List(itemCount) { SimpleEnriched(item = Any(), columns = 2) })
        val visibleRange = 2..3
        val buffer = 1
        val constraints = Constraints(maxWidth = 200, maxHeight = 10000)

        val indexToChange = 2
        val measuredHeights = mutableMapOf(indexToChange to 20)
        val scope = RecordingMeasurer(mapOf(indexToChange to 30, 3 to 30))

        val (_, heights, heightChanges) = measureGridItems(
            layoutProvider = provider,
            enrichedItems = enriched,
            visibleRange = visibleRange,
            measuredHeights = measuredHeights,
            constraints = constraints,
            measurer = scope::measure,
            bufferSize = buffer,
            getCachedWidth = defaultWidthFn
        )

        // Height should update to new value
        assertEquals(30, heights[indexToChange])
        assertEquals(30, measuredHeights[indexToChange])
        // Delta should be reported as +10
        assertTrue(heightChanges.contains(indexToChange to 10))
    }

    @Test
    fun preserves_previous_heights_outside_window() {
        val itemCount = 12
        val provider = fakeProvider<Any>(itemCount)
        val enriched = buildEnriched(List(itemCount) { SimpleEnriched(item = Any(), columns = 2) })
        val visibleRange = 5..6
        val buffer = 1 // safe window will be 4..7
        val constraints = Constraints(maxWidth = 200, maxHeight = 10000)

        // Pre-fill cache with heights for indices well outside the window
        val measuredHeights = mutableMapOf(0 to 11, 9 to 13, 11 to 17)
        val scope = RecordingMeasurer(mapOf(4 to 21, 5 to 22, 6 to 23, 7 to 24))

        val (_, heights, _) = measureGridItems(
            layoutProvider = provider,
            enrichedItems = enriched,
            visibleRange = visibleRange,
            measuredHeights = measuredHeights,
            constraints = constraints,
            measurer = scope::measure,
            bufferSize = buffer,
            getCachedWidth = defaultWidthFn
        )

        // Ensure heights outside safe window preserve previous values
        assertEquals(11, heights[0])
        assertEquals(13, heights[9])
        assertEquals(17, heights[11])

        // Inside window should reflect measured values
        assertEquals(21, heights[4])
        assertEquals(22, heights[5])
        assertEquals(23, heights[6])
        assertEquals(24, heights[7])
    }

    @Test
    fun ignores_invalid_measuredHeights_entries_out_of_bounds_or_non_positive() {
        val itemCount = 5
        val provider = fakeProvider<Any>(itemCount)
        val enriched = buildEnriched(List(itemCount) { SimpleEnriched(item = Any(), columns = 2) })
        val constraints = Constraints(maxWidth = 200, maxHeight = 10_000)

        val measuredHeights = mutableMapOf(
            -1 to 99, // out of bounds
            100 to 99, // out of bounds
            1 to -10, // invalid
            2 to 0, // invalid
            3 to 33 // valid
        )
        val scope = RecordingMeasurer(mapOf(0 to 10, 1 to 11, 2 to 12, 3 to 13, 4 to 14))

        val (_, heights, _) = measureGridItems(
            layoutProvider = provider,
            enrichedItems = enriched,
            visibleRange = 0..4,
            measuredHeights = measuredHeights,
            constraints = constraints,
            measurer = scope::measure,
            bufferSize = 0,
            getCachedWidth = defaultWidthFn
        )

        // Cached valid height for index 3 is overwritten by measurement because it is visible.
        assertEquals(13, heights[3])
        // Invalid cached heights should not leak into the initial heights list.
        // Index 1 and 2 should come from measurement.
        assertEquals(11, heights[1])
        assertEquals(12, heights[2])
    }

    @Test
    fun uses_fixed_width_constraints_based_on_item_type() {
        val itemCount = 3
        val provider = fakeProvider<Any>(itemCount)
        val enriched = buildEnriched(
            listOf(
                SimpleEnriched(item = Any(), columns = 4, type = GridItemType.Uniform),
                SimpleEnriched(item = Any(), columns = 4, type = GridItemType.FullWidth),
                SimpleEnriched(item = Any(), columns = 4, type = GridItemType.Custom(spans = 10)),
            )
        )
        val constraints = Constraints(maxWidth = 100, maxHeight = 10_000)
        val scope = RecordingMeasurer(mapOf(0 to 10, 1 to 11, 2 to 12))

        measureGridItems(
            layoutProvider = provider,
            enrichedItems = enriched,
            visibleRange = 0..2,
            measuredHeights = mutableMapOf(),
            constraints = constraints,
            measurer = scope::measure,
            bufferSize = 0,
            getCachedWidth = defaultWidthFn
        )

        val c0 = scope.calls.first { it.first == 0 }.second
        val c1 = scope.calls.first { it.first == 1 }.second
        val c2 = scope.calls.first { it.first == 2 }.second

        assertEquals(25, c0.maxWidth)
        assertEquals(25, c0.minWidth)
        assertEquals(100, c1.maxWidth)
        assertEquals(100, c1.minWidth)
        // Custom spans clamped to columns (spans=10, columns=4) => full width
        assertEquals(100, c2.maxWidth)
        assertEquals(100, c2.minWidth)
    }

    @Test(expected = ArithmeticException::class)
    fun throws_when_getCachedWidth_divides_by_zero_groupColumns() {
        val provider = fakeProvider<Any>(1)
        val enriched = listOf(
            EnrichedGridItem(
                item = Any(),
                config = GridItemConfig(type = GridItemType.Uniform),
                group = Any(),
                groupColumns = 0,
                groupItemIndex = 0,
                key = 0,
                contentType = null
            )
        )

        measureGridItems(
            layoutProvider = provider,
            enrichedItems = enriched,
            visibleRange = 0..0,
            measuredHeights = mutableMapOf(),
            constraints = Constraints(maxWidth = 100, maxHeight = 100),
            measurer = { _, _ -> emptyList() },
            bufferSize = 0,
            getCachedWidth = defaultWidthFn
        )
    }
}
