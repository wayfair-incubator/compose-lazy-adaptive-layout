package com.lazyadaptivelayout.state

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import com.lazyadaptivelayout.model.AdaptiveGridItemInfo
import com.lazyadaptivelayout.model.AdaptiveGridLayoutInfo
import com.lazyadaptivelayout.model.GridItemType
import com.lazyadaptivelayout.model.ItemPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
class AdaptiveGridPrefetchStrategyTest {

    private class RecordingPrefetchScope : AdaptiveGridPrefetchScope {
        val scheduled = mutableListOf<Int>()
        private val prefetchState = LazyLayoutPrefetchState { /* no nested prefetch */ }

        override fun schedulePrefetch(
            index: Int,
            onPrefetchFinished: (AdaptiveGridPrefetchResultScope.() -> Unit)?,
        ): LazyLayoutPrefetchState.PrefetchHandle {
            scheduled += index
            // Use a real PrefetchHandle implementation to avoid sealed/hidden API issues.
            return prefetchState.schedulePrecomposition(index)
        }
    }

    private fun item(index: Int): AdaptiveGridItemInfo =
        AdaptiveGridItemInfo(
            index = index,
            key = "k$index",
            offset = 0,
            size = 10,
            position = ItemPosition(
                x = 0,
                y = 0,
                width = 100,
                height = 10,
                columnIndex = 0,
            ),
            layoutType = GridItemType.Uniform,
        )

    private fun layoutInfo(
        visibleIndices: List<Int>,
        totalItemsCount: Int,
    ): AdaptiveGridLayoutInfo =
        AdaptiveGridLayoutInfo(
            visibleItemsInfo = visibleIndices.map { item(it) },
            viewportStartOffset = 0,
            viewportEndOffset = 100,
            totalItemsCount = totalItemsCount,
            viewportSize = 100,
        )

    private fun assertAllInBounds(indices: List<Int>, itemCount: Int) {
        assertTrue(
            "Expected all indices to be within 0..${itemCount - 1} but was $indices",
            indices.all { it in 0 until itemCount },
        )
    }

    @Test
    fun onScroll_scrollingDown_prefetches3ItemsAhead() {
        val scope = RecordingPrefetchScope()
        val strategy = AdaptiveGridPrefetchStrategyImpl()
        val info = layoutInfo(visibleIndices = listOf(5, 6, 7, 8), totalItemsCount = 20)

        with(strategy) { scope.onScroll(delta = -1f, layoutInfo = info) }

        assertEquals(listOf(9, 10, 11), scope.scheduled)
        assertAllInBounds(scope.scheduled, itemCount = 20)
    }

    @Test
    fun onScroll_scrollingUp_prefetches3ItemsBehind() {
        val scope = RecordingPrefetchScope()
        val strategy = AdaptiveGridPrefetchStrategyImpl()
        val info = layoutInfo(visibleIndices = listOf(5, 6, 7, 8), totalItemsCount = 20)

        with(strategy) { scope.onScroll(delta = 1f, layoutInfo = info) }

        assertEquals(listOf(2, 3, 4), scope.scheduled)
        assertAllInBounds(scope.scheduled, itemCount = 20)
    }

    @Test
    fun onScroll_scrollingDown_clampsToLastIndex() {
        val scope = RecordingPrefetchScope()
        val strategy = AdaptiveGridPrefetchStrategyImpl()
        val info = layoutInfo(visibleIndices = listOf(18, 19), totalItemsCount = 20)

        with(strategy) { scope.onScroll(delta = -10f, layoutInfo = info) }

        assertEquals(listOf(19), scope.scheduled)
        assertAllInBounds(scope.scheduled, itemCount = 20)
    }

    @Test
    fun onScroll_scrollingUp_clampsToZero() {
        val scope = RecordingPrefetchScope()
        val strategy = AdaptiveGridPrefetchStrategyImpl()
        val info = layoutInfo(visibleIndices = listOf(0, 1, 2), totalItemsCount = 20)

        with(strategy) { scope.onScroll(delta = 10f, layoutInfo = info) }

        assertEquals(listOf(0), scope.scheduled)
        assertAllInBounds(scope.scheduled, itemCount = 20)
    }

    @Test
    fun onScroll_emptyVisibleItems_uses0AsVisibleIndex() {
        val scope = RecordingPrefetchScope()
        val strategy = AdaptiveGridPrefetchStrategyImpl()
        val info = layoutInfo(visibleIndices = emptyList(), totalItemsCount = 5)

        with(strategy) { scope.onScroll(delta = -1f, layoutInfo = info) }

        assertEquals(listOf(1, 2, 3), scope.scheduled)
        assertAllInBounds(scope.scheduled, itemCount = 5)
    }

    @Test
    fun onScroll_totalItemCountIsZero_schedulesNothing() {
        val scope = RecordingPrefetchScope()
        val strategy = AdaptiveGridPrefetchStrategyImpl()
        val info = layoutInfo(visibleIndices = emptyList(), totalItemsCount = 0)

        with(strategy) { scope.onScroll(delta = -1f, layoutInfo = info) }
        with(strategy) { scope.onScroll(delta = 1f, layoutInfo = info) }

        assertEquals(emptyList<Int>(), scope.scheduled)
    }

    @Test
    fun onScroll_visibleIndicesOutOfBounds_areClamped() {
        val scope = RecordingPrefetchScope()
        val strategy = AdaptiveGridPrefetchStrategyImpl()
        val info = layoutInfo(visibleIndices = listOf(1000, 1001), totalItemsCount = 10)

        with(strategy) { scope.onScroll(delta = -1f, layoutInfo = info) }

        // lastVisible clamps to 9; start becomes 10 clamped to 9.
        assertEquals(listOf(9), scope.scheduled)
        assertAllInBounds(scope.scheduled, itemCount = 10)
    }

    @Test
    fun onVisibleItemsUpdated_prefetchesAheadAndBehind() {
        val scope = RecordingPrefetchScope()
        val strategy = AdaptiveGridPrefetchStrategyImpl()
        val info = layoutInfo(visibleIndices = listOf(5, 6, 7, 8), totalItemsCount = 20)

        with(strategy) { scope.onVisibleItemsUpdated(info) }

        assertEquals(listOf(9, 10, 11, 2, 3, 4), scope.scheduled)
        assertAllInBounds(scope.scheduled, itemCount = 20)
    }

    @Test
    fun onVisibleItemsUpdated_totalItemCountIsZero_schedulesNothing() {
        val scope = RecordingPrefetchScope()
        val strategy = AdaptiveGridPrefetchStrategyImpl()
        val info = layoutInfo(visibleIndices = emptyList(), totalItemsCount = 0)

        with(strategy) { scope.onVisibleItemsUpdated(info) }

        assertEquals(emptyList<Int>(), scope.scheduled)
    }

    @Test
    fun onVisibleItemsUpdated_itemCountIsOne_schedulesOnlyIndex0Twice() {
        val scope = RecordingPrefetchScope()
        val strategy = AdaptiveGridPrefetchStrategyImpl()
        val info = layoutInfo(visibleIndices = listOf(0), totalItemsCount = 1)

        with(strategy) { scope.onVisibleItemsUpdated(info) }

        assertEquals(listOf(0, 0), scope.scheduled)
        assertAllInBounds(scope.scheduled, itemCount = 1)
    }

    @Test
    fun onVisibleItemsUpdated_scrolledToEnd_clampsAheadPrefetchToLastIndex() {
        val scope = RecordingPrefetchScope()
        val strategy = AdaptiveGridPrefetchStrategyImpl()
        val info = layoutInfo(visibleIndices = listOf(18, 19), totalItemsCount = 20)

        with(strategy) { scope.onVisibleItemsUpdated(info) }

        assertEquals(listOf(19, 15, 16, 17), scope.scheduled)
        assertAllInBounds(scope.scheduled, itemCount = 20)
    }

    @Test
    fun adaptiveGridNestedPrefetchIndices_defaultCount_returnsFirstTwoIndices() {
        assertEquals(10..11, adaptiveGridNestedPrefetchIndices(firstVisibleItemIndex = 10))
    }

    @Test
    fun adaptiveGridNestedPrefetchIndices_negativeIndex_isClampedToZero() {
        assertEquals(0..1, adaptiveGridNestedPrefetchIndices(firstVisibleItemIndex = -10))
    }

    @Test
    fun adaptiveGridNestedPrefetchIndices_zeroCount_isEmpty() {
        assertEquals(IntRange.EMPTY, adaptiveGridNestedPrefetchIndices(firstVisibleItemIndex = 10, count = 0))
    }

    @Test
    fun adaptiveGridNestedPrefetchIndices_negativeCount_isEmpty() {
        assertEquals(IntRange.EMPTY, adaptiveGridNestedPrefetchIndices(firstVisibleItemIndex = 10, count = -1))
    }

    @Test
    fun onVisibleItemsUpdated_and_onScroll_neverScheduleOutOfBoundsIndices() {
        val strategy = AdaptiveGridPrefetchStrategyImpl()

        val scopes = listOf(
            RecordingPrefetchScope() to layoutInfo(visibleIndices = listOf(0), totalItemsCount = 1),
            RecordingPrefetchScope() to layoutInfo(visibleIndices = listOf(0), totalItemsCount = 2),
            RecordingPrefetchScope() to layoutInfo(visibleIndices = listOf(0), totalItemsCount = 3),
            RecordingPrefetchScope() to layoutInfo(visibleIndices = listOf(1000), totalItemsCount = 3),
        )

        for ((scope, info) in scopes) {
            with(strategy) { scope.onScroll(delta = -1f, layoutInfo = info) }
            with(strategy) { scope.onScroll(delta = 1f, layoutInfo = info) }
            with(strategy) { scope.onVisibleItemsUpdated(info) }
            assertTrue(
                "Expected all scheduled indices to be in-bounds for itemCount=${info.totalItemsCount} but was ${scope.scheduled}",
                scope.scheduled.all { it in 0 until info.totalItemsCount },
            )
        }
    }

    @Test
    fun onVisibleItemsUpdated_and_onScroll_whenItemCountIsZero_scheduleNothing() {
        val strategy = AdaptiveGridPrefetchStrategyImpl()
        val scope = RecordingPrefetchScope()
        val info = layoutInfo(visibleIndices = listOf(0), totalItemsCount = 0)

        with(strategy) { scope.onScroll(delta = -1f, layoutInfo = info) }
        with(strategy) { scope.onScroll(delta = 1f, layoutInfo = info) }
        with(strategy) { scope.onVisibleItemsUpdated(info) }

        assertTrue(scope.scheduled.isEmpty())
        assertFalse(scope.scheduled.any { it < 0 })
    }
}
