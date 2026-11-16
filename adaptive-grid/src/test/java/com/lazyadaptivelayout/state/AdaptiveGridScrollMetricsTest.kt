package com.lazyadaptivelayout.state

import com.lazyadaptivelayout.model.AdaptiveGridItemInfo
import com.lazyadaptivelayout.model.AdaptiveGridLayoutInfo
import com.lazyadaptivelayout.model.GridItemType
import com.lazyadaptivelayout.model.ItemPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveGridScrollMetricsTest {

    private fun itemInfo(index: Int, size: Int): AdaptiveGridItemInfo =
        AdaptiveGridItemInfo(
            index = index,
            key = "k$index",
            offset = 0,
            size = size,
            position = ItemPosition(0, 0, 100, size, 0),
            layoutType = GridItemType.FullWidth,
        )

    private fun stateWithFirstItem(size: Int, scrollOffset: Int = 0): AdaptiveGridState {
        val state = AdaptiveGridState(firstVisibleItemIndex = 0, firstVisibleItemScrollOffset = scrollOffset)
        val first = itemInfo(index = 0, size = size)
        val measureResult = AdaptiveGridMeasureResult(
            firstVisibleItem = first,
            firstVisibleItemScrollOffset = scrollOffset,
            canScrollForward = false,
            consumedScroll = 0f,
            measureResult = object : androidx.compose.ui.layout.MeasureResult {
                override val alignmentLines = emptyMap<androidx.compose.ui.layout.AlignmentLine, Int>()
                override val height: Int = 0
                override val width: Int = 0
                override fun placeChildren() {}
            },
            scrollBackAmount = 0f,
            remeasureNeeded = false,
            coroutineScope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext),
            density = androidx.compose.ui.unit.Density(1f, 1f),
            childConstraints = androidx.compose.ui.unit.Constraints(),
            visibleItemsInfo = listOf(first),
            viewportStartOffset = 0,
            viewportEndOffset = 100,
            totalItemsCount = 1,
            viewportSize = 100,
        )
        state.applyMeasureResult(measureResult, isLookingAhead = false)
        return state
    }

    @Test
    fun firstItemScrollSpan_returnsZero_whenChromeCoversOrExceedsItemSize() {
        val state = stateWithFirstItem(size = 80)

        assertEquals(0, state.firstItemScrollSpan(overlappingChromeHeightPx = 80))
        assertEquals(0, state.firstItemScrollSpan(overlappingChromeHeightPx = 200))
    }

    @Test
    fun firstItemScrollSpan_returnsPositiveSpan_whenItemIsTallerThanChrome() {
        val state = stateWithFirstItem(size = 400)

        assertEquals(300, state.firstItemScrollSpan(overlappingChromeHeightPx = 100))
    }

    @Test
    fun scrollFractionThroughFirstItem_isZero_whenEffectiveSpanIsZero() {
        val state = stateWithFirstItem(size = 50, scrollOffset = 10)

        assertEquals(0f, state.scrollFractionThroughFirstItem(overlappingChromeHeightPx = 100))
    }

    @Test
    fun scrollFractionThroughFirstItem_isClamped_andNeverProducesInvalidFloats() {
        val state = stateWithFirstItem(size = 400, scrollOffset = 150)

        val fraction = state.scrollFractionThroughFirstItem(overlappingChromeHeightPx = 100)
        assertEquals(0.5f, fraction, 0.0001f)
        assertFalse(fraction.isNaN())
        assertFalse(fraction.isInfinite())
    }

    @Test
    fun scrollFractionThroughFirstItem_returnsZero_whenNotAnchoredOnFirstItem() {
        val state = AdaptiveGridState(firstVisibleItemIndex = 1, firstVisibleItemScrollOffset = 0)
        val first = itemInfo(index = 1, size = 400)
        state.applyMeasureResult(
            AdaptiveGridMeasureResult(
                firstVisibleItem = first,
                firstVisibleItemScrollOffset = 0,
                canScrollForward = false,
                consumedScroll = 0f,
                measureResult = object : androidx.compose.ui.layout.MeasureResult {
                    override val alignmentLines = emptyMap<androidx.compose.ui.layout.AlignmentLine, Int>()
                    override val height: Int = 0
                    override val width: Int = 0
                    override fun placeChildren() {}
                },
                scrollBackAmount = 0f,
                remeasureNeeded = false,
                coroutineScope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext),
                density = androidx.compose.ui.unit.Density(1f, 1f),
                childConstraints = androidx.compose.ui.unit.Constraints(),
                visibleItemsInfo = listOf(first),
                viewportStartOffset = 0,
                viewportEndOffset = 100,
                totalItemsCount = 2,
                viewportSize = 100,
            ),
            isLookingAhead = false,
        )

        assertEquals(0f, state.scrollFractionThroughFirstItem(overlappingChromeHeightPx = 0))
    }

    @Test
    fun layoutInfo_scrollFraction_matchesStateHelpers() {
        val first = itemInfo(index = 0, size = 200)
        val layoutInfo = AdaptiveGridLayoutInfo(
            visibleItemsInfo = listOf(first),
            firstVisibleItem = first,
            viewportStartOffset = 0,
            viewportEndOffset = 100,
            totalItemsCount = 1,
            viewportSize = 100,
        )

        assertEquals(100, layoutInfo.firstItemScrollSpan(scrollAnchorIndex = 0, overlappingChromeHeightPx = 100))
        assertEquals(
            0.25f,
            layoutInfo.scrollFractionThroughFirstItem(
                scrollAnchorIndex = 0,
                scrollAnchorOffset = 25,
                overlappingChromeHeightPx = 100,
            ),
            0.0001f,
        )
        assertTrue(
            layoutInfo
                .scrollFractionThroughFirstItem(
                    scrollAnchorIndex = 0,
                    scrollAnchorOffset = 25,
                    overlappingChromeHeightPx = 200,
                )
                .isFinite(),
        )
    }
}
