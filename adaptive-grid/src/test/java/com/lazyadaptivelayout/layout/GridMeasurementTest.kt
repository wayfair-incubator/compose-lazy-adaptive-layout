package com.lazyadaptivelayout.layout

import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.lazyadaptivelayout.EnrichedGridItem
import com.lazyadaptivelayout.model.ContentPadding
import com.lazyadaptivelayout.model.EdgeSpacing
import com.lazyadaptivelayout.model.GridItemConfig
import com.lazyadaptivelayout.model.GridItemType
import com.lazyadaptivelayout.model.ItemPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GridMeasurementTest {

    private val density = Density(1f)
    private val constraints = Constraints(maxWidth = 1000, maxHeight = Constraints.Infinity)

    @org.junit.Before
    fun resetCaches() {
        clearMeasurementCaches()
    }

    private fun <T> item(
        item: T,
        type: GridItemType,
        groupColumns: Int,
        heightPx: Int? = null,
        edge: EdgeSpacing = EdgeSpacing(),
        padding: ContentPadding = ContentPadding(),
        group: Any = "group-${groupColumns}",
        groupItemIndex: Int = 0,
        key: Any = "key-${item}",
        contentType: Any? = null
    ): EnrichedGridItem<T> {
        val config = GridItemConfig(type = type, height = heightPx, edgeSpacing = edge, contentPadding = padding)
        return EnrichedGridItem(
            item = item,
            config = config,
            group = group,
            groupColumns = groupColumns,
            groupItemIndex = groupItemIndex,
            key = key,
            contentType = contentType
        )
    }

    @Test
    fun uniform_twoColumns_positionsAndRowHeights() {
        val items = listOf(
            item(1, GridItemType.Uniform, groupColumns = 2),
            item(2, GridItemType.Uniform, groupColumns = 2),
            item(3, GridItemType.Uniform, groupColumns = 2),
            item(4, GridItemType.Uniform, groupColumns = 2)
        )
        val heights = listOf(100, 120, 80, 140)

        val positions = measureFlattenedGrid(items, heights, constraints, density)

        // Column width = 1000 / 2 = 500
        assertEquals(ItemPosition(0, 0, 500, 100, 0), positions[0])
        assertEquals(ItemPosition(500, 0, 500, 120, 1), positions[1])
        // Row height should be max(100,120) = 120, next row starts at y=120
        assertEquals(ItemPosition(0, 120, 500, 80, 0), positions[2])
        assertEquals(ItemPosition(500, 120, 500, 140, 1), positions[3])
    }

    @Test
    fun staggered_shortestColumnPlacement() {
        val items = listOf(
            item(1, GridItemType.Staggered, groupColumns = 3),
            item(2, GridItemType.Staggered, groupColumns = 3),
            item(3, GridItemType.Staggered, groupColumns = 3),
            item(4, GridItemType.Staggered, groupColumns = 3)
        )
        val heights = listOf(100, 150, 120, 90)

        val positions = measureFlattenedGrid(items, heights, constraints, density)
        // Column width = 1000 / 3 = 333 (integer division)
        // First three fill columns 0..2 at y=0
        assertEquals(ItemPosition(0, 0, 333, 100, 0), positions[0])
        assertEquals(ItemPosition(333, 0, 333, 150, 1), positions[1])
        assertEquals(ItemPosition(666, 0, 333, 120, 2), positions[2])
        // Fourth goes to shortest column, which is column 0 (height 100)
        assertEquals(ItemPosition(0, 100, 333, 90, 0), positions[3])
    }

    @Test
    fun fullWidth_itemStartsNewRow() {
        val items = listOf(
            item(1, GridItemType.Uniform, groupColumns = 2),
            item(2, GridItemType.Uniform, groupColumns = 2),
            item(3, GridItemType.FullWidth, groupColumns = 2),
            item(4, GridItemType.Uniform, groupColumns = 2)
        )
        val heights = listOf(100, 120, 80, 60)

        val positions = measureFlattenedGrid(items, heights, constraints, density)

        // First row
        assertEquals(ItemPosition(0, 0, 500, 100, 0), positions[0])
        assertEquals(ItemPosition(500, 0, 500, 120, 1), positions[1])
        // Full width item starts at y = max(0+100, 0+120) = 120, width=1000
        assertEquals(ItemPosition(0, 120, 1000, 80, 0), positions[2])
        // Next uniform row should start below full-width item at y=200
        assertEquals(ItemPosition(0, 200, 500, 60, 0), positions[3])
    }

    @Test
    fun custom_spansPlacement() {
        val items = listOf(
            item(1, GridItemType.Custom(spans = 2), groupColumns = 3),
            item(2, GridItemType.Custom(spans = 1), groupColumns = 3),
            item(3, GridItemType.Custom(spans = 1), groupColumns = 3)
        )
        val heights = listOf(90, 100, 110)

        val positions = measureFlattenedGrid(items, heights, constraints, density)

        // With only Custom items (spans 2,1,1), maxPossibleColumns = max(spans) = 2.
        val colWidth = 1000 / 2 // 500
        // First spans 2 columns starting at column 0
        assertEquals(ItemPosition(0, 0, colWidth * 2, 90, 0), positions[0])
        // Second picks the (tie) shortest start section, starting at column 0
        assertEquals(ItemPosition(0, 90, colWidth, 100, 0), positions[1])
        // Third goes to remaining shortest column (column 1)
        assertEquals(ItemPosition(colWidth, 90, colWidth, 110, 1), positions[2])
    }

    @Test
    fun paddingOnly_itemsAreSkipped() {
        // Edge top+bottom=8, content vertical=4 -> expected padding height = 8 + (4/2 + 1) = 11
        val edge = EdgeSpacing(top = 4f.toDp(), bottom = 4f.toDp())
        val padding = ContentPadding(vertical = 4f.toDp())
        val items = listOf(
            item(1, GridItemType.Uniform, groupColumns = 2, heightPx = null, edge = edge, padding = padding),
            item(2, GridItemType.Uniform, groupColumns = 2)
        )
        // First measured height <= expected padding height => should be treated as padding-only and skipped
        val heights = listOf(10, 20)

        val positions = measureFlattenedGrid(items, heights, constraints, density)

        assertEquals(ItemPosition(0, 0, 0, 0, 0), positions[0])
        assertEquals(ItemPosition(0, 0, 500, 20, 0), positions[1])
    }

    @Test
    fun initial_noMeasuredHeights_returnsZeros() {
        val items = listOf(
            item(1, GridItemType.Uniform, groupColumns = 2),
            item(2, GridItemType.Uniform, groupColumns = 2)
        )
        // All zeros simulate nothing measured yet
        val heights = listOf(0, 0)

        val positions = measureFlattenedGrid(items, heights, constraints, density)

        assertTrue(positions.all { it == ItemPosition(0, 0, 0, 0, 0) })
    }

    // ---------------- Visible Range tests ----------------

    @Test
    fun visibleRange_basicIntersectionWithBuffer() {
        val positions = listOf(
            ItemPosition(0, 0, 100, 100, 0),
            ItemPosition(0, 100, 100, 120, 0),
            ItemPosition(0, 220, 100, 80, 0),
            ItemPosition(0, 300, 100, 100, 0),
            ItemPosition(0, 400, 100, 100, 0)
        )
        val range = calculateVisibleRange(
            scrollOffset = 150, // viewport 150..350
            viewportHeight = 200,
            itemPositions = positions,
            itemCount = positions.size,
            bufferSize = 1
        )
        // Intersecting indices are 1 (100-220) and 2 (220-300) and 3 (300-400)
        // With buffer=1, expect to extend one before and after within bounds
        assertEquals(0..4, range)
    }

    @Test
    fun visibleRange_emptyPositions_smallInitialWindow() {
        val range = calculateVisibleRange(
            scrollOffset = 0,
            viewportHeight = 300,
            itemPositions = emptyList(),
            itemCount = 25,
            bufferSize = 2
        )
        assertEquals(0..9, range) // min(10, 25)
    }

    @Test
    fun visibleRange_noIntersection_snapsToNearestMeasured() {
        val positions = listOf(
            ItemPosition(0, 0, 100, 50, 0),
            ItemPosition(0, 50, 100, 50, 0),
            ItemPosition(0, 100, 100, 50, 0)
        )
        // Viewport entirely after last measured item
        val rangeAfter = calculateVisibleRange(
            scrollOffset = 1000,
            viewportHeight = 200,
            itemPositions = positions,
            itemCount = 3,
            bufferSize = 0
        )
        assertEquals(2..2, rangeAfter)

        // Viewport entirely before first measured item
        val rangeBefore = calculateVisibleRange(
            scrollOffset = -500,
            viewportHeight = 200,
            itemPositions = positions,
            itemCount = 3,
            bufferSize = 0
        )
        assertEquals(0..0, rangeBefore)
    }

    // -------- Incremental measurement & caching tests --------

    @Test
    fun incremental_onlyMeasuresUpToLastNonZeroHeight() {
        val items = listOf(
            item(1, GridItemType.Uniform, groupColumns = 2),
            item(2, GridItemType.Uniform, groupColumns = 2),
            item(3, GridItemType.Uniform, groupColumns = 2),
            item(4, GridItemType.Uniform, groupColumns = 2),
            item(5, GridItemType.Uniform, groupColumns = 2)
        )
        // Only the first item has a non-zero height
        val heights = listOf(100, 0, 0, 0, 0)

        val positions = measureFlattenedGrid(items, heights, constraints, density)

        // First item measured and positioned
        assertEquals(ItemPosition(0, 0, 500, 100, 0), positions[0])
        // Remaining items should stay zero as they are not measured yet
        assertEquals(ItemPosition(0, 0, 0, 0, 0), positions[1])
        assertEquals(ItemPosition(0, 0, 0, 0, 0), positions[2])
        assertEquals(ItemPosition(0, 0, 0, 0, 0), positions[3])
        assertEquals(ItemPosition(0, 0, 0, 0, 0), positions[4])
    }

    @Test
    fun incremental_cachesTailAndContinuesFromLastMeasured() {
        val items = listOf(
            item(1, GridItemType.Uniform, groupColumns = 2),
            item(2, GridItemType.Uniform, groupColumns = 2),
            item(3, GridItemType.Uniform, groupColumns = 2),
            item(4, GridItemType.Uniform, groupColumns = 2),
            item(5, GridItemType.Uniform, groupColumns = 2)
        )

        // Pass 1: first two items measured
        val heightsPass1 = listOf(100, 120, 0, 0, 0)
        val positionsPass1 = measureFlattenedGrid(items, heightsPass1, constraints, density)
        val firstPos0 = positionsPass1[0]
        val firstPos1 = positionsPass1[1]

        // Sanity: tail is zeros
        assertEquals(ItemPosition(0, 0, 0, 0, 0), positionsPass1[2])
        assertEquals(ItemPosition(0, 0, 0, 0, 0), positionsPass1[3])
        assertEquals(ItemPosition(0, 0, 0, 0, 0), positionsPass1[4])

        // Pass 2: now the third item gets measured; algorithm should recompute up to lastMeasuredIndex=2
        val heightsPass2 = listOf(100, 120, 80, 0, 0)
        val positionsPass2 = measureFlattenedGrid(items, heightsPass2, constraints, density)

        // Previously measured items keep identical positions
        assertEquals(firstPos0, positionsPass2[0])
        assertEquals(firstPos1, positionsPass2[1])

        // Newly measured item is placed in the next row starting at y = 120
        assertEquals(ItemPosition(0, 120, 500, 80, 0), positionsPass2[2])

        // Tail still zeros (not processed yet)
        assertEquals(ItemPosition(0, 0, 0, 0, 0), positionsPass2[3])
        assertEquals(ItemPosition(0, 0, 0, 0, 0), positionsPass2[4])
    }
}

// Simple helpers to create Dp without importing Compose runtime dp extension
private fun Float.toDp(): androidx.compose.ui.unit.Dp = androidx.compose.ui.unit.Dp(this)
