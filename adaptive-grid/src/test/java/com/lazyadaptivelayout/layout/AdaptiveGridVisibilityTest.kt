package com.lazyadaptivelayout.layout

import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveGridVisibilityTest {

    @Test
    fun collapsedZeroHeight_inWindow_includesAnchorsWithinMeasureWindow() {
        // Categories visible through index 3; secondary-fetch anchor at 4 (zero height) is in the
        // measure window (end=5) but not geometrically visible — must still be reported.
        val heights = intArrayOf(100, 100, 100, 100, 0, 0, 200)
        val result = collapsedZeroHeightVisibleIndices(
            lastGeometricVisibleIndex = 3,
            measureWindowEnd = 5,
            itemsCount = 7,
            measureWindowReachedEnd = false,
            effectiveHeightAt = { heights[it] },
        )

        assertEquals(listOf(4, 5), result)
    }

    @Test
    fun collapsedZeroHeight_inWindow_stopsAtFirstRealItemBelowLastVisible() {
        val heights = intArrayOf(100, 100, 100, 100, 0, 200, 0)
        val result = collapsedZeroHeightVisibleIndices(
            lastGeometricVisibleIndex = 3,
            measureWindowEnd = 6,
            itemsCount = 7,
            measureWindowReachedEnd = false,
            effectiveHeightAt = { heights[it] },
        )

        assertEquals(listOf(4), result)
    }

    @Test
    fun collapsedZeroHeight_returnsEmpty_whenMeasureWindowHasNotReachedEnd_andNoInWindowAnchors() {
        val result = collapsedZeroHeightVisibleIndices(
            lastGeometricVisibleIndex = 3,
            measureWindowEnd = 3,
            itemsCount = 6,
            measureWindowReachedEnd = false,
            effectiveHeightAt = { 0 },
        )

        assertEquals(emptyList<Int>(), result)
    }

    @Test
    fun collapsedZeroHeight_trailing_includesZeroHeightAnchors_whenWindowReachedEnd() {
        val heights = intArrayOf(100, 100, 100, 100, 0, 0)
        val result = collapsedZeroHeightVisibleIndices(
            lastGeometricVisibleIndex = 3,
            measureWindowEnd = 5,
            itemsCount = 6,
            measureWindowReachedEnd = true,
            effectiveHeightAt = { heights[it] },
        )

        assertEquals(listOf(4, 5), result)
    }

    @Test
    fun collapsedZeroHeight_trailing_stopsAtFirstRealItem_belowLastVisible() {
        val heights = intArrayOf(100, 100, 100, 100, 0, 100)
        val result = collapsedZeroHeightVisibleIndices(
            lastGeometricVisibleIndex = 3,
            measureWindowEnd = 5,
            itemsCount = 6,
            measureWindowReachedEnd = true,
            effectiveHeightAt = { heights[it] },
        )

        assertEquals(listOf(4), result)
    }

    @Test
    fun collapsedZeroHeight_returnsEmpty_whenLastVisibleIsAlreadyLastItem() {
        val result = collapsedZeroHeightVisibleIndices(
            lastGeometricVisibleIndex = 5,
            measureWindowEnd = 5,
            itemsCount = 6,
            measureWindowReachedEnd = true,
            effectiveHeightAt = { 0 },
        )

        assertEquals(emptyList<Int>(), result)
    }

    @Test
    fun collapsedZeroHeight_returnsEmpty_whenNothingVisible() {
        val result = collapsedZeroHeightVisibleIndices(
            lastGeometricVisibleIndex = -1,
            measureWindowEnd = 5,
            itemsCount = 6,
            measureWindowReachedEnd = true,
            effectiveHeightAt = { 0 },
        )

        assertEquals(listOf(0, 1, 2, 3, 4, 5), result)
    }

    @Test
    fun collapsedZeroHeight_returnsEmpty_whenGridIsEmpty() {
        val result = collapsedZeroHeightVisibleIndices(
            lastGeometricVisibleIndex = -1,
            measureWindowEnd = 0,
            itemsCount = 0,
            measureWindowReachedEnd = true,
            effectiveHeightAt = { 0 },
        )

        assertEquals(emptyList<Int>(), result)
    }
}
