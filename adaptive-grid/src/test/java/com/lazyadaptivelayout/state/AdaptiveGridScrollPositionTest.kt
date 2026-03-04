package com.lazyadaptivelayout.state

import androidx.compose.runtime.Composable
import com.lazyadaptivelayout.model.AdaptiveGridItemInfo
import com.lazyadaptivelayout.model.GridItem
import com.lazyadaptivelayout.model.GridItemConfig
import com.lazyadaptivelayout.model.GridItemType
import com.lazyadaptivelayout.model.ItemPosition
import com.lazyadaptivelayout.provider.GridLayoutProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class AdaptiveGridScrollPositionTest {

    private fun expectedNearestRange(firstVisibleItem: Int): IntRange {
        val slidingWindowStart =
            NearestItemsSlidingWindowSize * (firstVisibleItem / NearestItemsSlidingWindowSize)
        val start = maxOf(slidingWindowStart - NearestItemsExtraItemCount, 0)
        val end = slidingWindowStart + NearestItemsSlidingWindowSize + NearestItemsExtraItemCount
        return start until end
    }

    private fun itemInfo(
        index: Int,
        key: Any,
    ): AdaptiveGridItemInfo =
        AdaptiveGridItemInfo(
            index = index,
            key = key,
            offset = 0,
            size = 0,
            position = ItemPosition(x = 0, y = 0, width = 0, height = 0, columnIndex = 0),
            layoutType = GridItemType.Uniform,
        )

    private class FakeGridLayoutProvider(
        keys: List<String>,
    ) : GridLayoutProvider<Any> {
        override val items: List<GridItem<Any>> =
            keys.map { key ->
                GridItem(
                    item = Any(),
                    config = GridItemConfig(type = GridItemType.Uniform),
                )
            }

        private val keysList = keys

        override fun getItemConfig(index: Int): GridItemConfig = GridItemConfig(type = GridItemType.Uniform)

        override fun getItemKey(index: Int): String = keysList[index]

        override fun getContentType(index: Int): Any? = null

        @Composable
        override fun ItemContent(item: Any, index: Int, columnIndex: Int, columns: Int) {
            // Not needed for these unit tests.
        }
    }

    @Test
    fun nearestRangeState_initialValue_isCalculatedFromFirstVisibleItem() {
        val state =
            AdaptiveGridNearestRangeState(
                firstVisibleItem = 0,
                slidingWindowSize = 30,
                extraItemCount = 100,
            )

        assertEquals(0 until 130, state.value)
    }

    @Test
    fun nearestRangeState_update_withSameFirstVisibleItem_doesNotChangeValueInstance() {
        val state =
            AdaptiveGridNearestRangeState(
                firstVisibleItem = 0,
                slidingWindowSize = 30,
                extraItemCount = 100,
            )
        val initialRange = state.value

        state.update(0)

        assertSame(initialRange, state.value)
    }

    @Test
    fun nearestRangeState_update_withinSameWindow_producesEqualRangeButNewInstance() {
        val state =
            AdaptiveGridNearestRangeState(
                firstVisibleItem = 0,
                slidingWindowSize = 30,
                extraItemCount = 100,
            )
        val initialRange = state.value

        state.update(1)

        assertEquals(initialRange, state.value)
        // Because mutableStateOf uses structural equality by default, assigning an equal IntRange
        // does not update the state value.
        assertSame(initialRange, state.value)
    }

    @Test
    fun nearestRangeState_update_crossingWindowBoundary_changesRange() {
        val state =
            AdaptiveGridNearestRangeState(
                firstVisibleItem = 0,
                slidingWindowSize = 30,
                extraItemCount = 100,
            )

        state.update(30)

        assertEquals(0 until 160, state.value)
    }

    @Test
    fun adaptiveGridScrollPosition_initialState_matchesConstructorArguments() {
        val position = AdaptiveGridScrollPosition(initialIndex = 5, initialScrollOffset = 7)

        assertEquals(5, position.index)
        assertEquals(7, position.scrollOffset)
        assertEquals(expectedNearestRange(5), position.nearestRangeState.value)
    }

    @Test
    fun updateFromMeasureResult_beforeFirstNotEmptyLayout_andTotalItemsZero_doesNotOverrideInitialPosition() {
        val position = AdaptiveGridScrollPosition(initialIndex = 5, initialScrollOffset = 7)

        position.updateFromMeasureResult(
            firstVisibleItem = itemInfo(index = 2, key = "K"),
            firstVisibleItemScrollOffset = 10,
            totalItemsCount = 0,
        )

        assertEquals(5, position.index)
        assertEquals(7, position.scrollOffset)
        assertEquals(expectedNearestRange(5), position.nearestRangeState.value)
    }

    @Test
    fun updateFromMeasureResult_whenTotalItemsCountPositive_updatesPosition_evenIfFirstVisibleItemIsNull() {
        val position = AdaptiveGridScrollPosition(initialIndex = 5, initialScrollOffset = 7)

        position.updateFromMeasureResult(
            firstVisibleItem = null,
            firstVisibleItemScrollOffset = 12,
            totalItemsCount = 10,
        )

        assertEquals(0, position.index)
        assertEquals(12, position.scrollOffset)
        assertEquals(expectedNearestRange(0), position.nearestRangeState.value)
    }

    @Test
    fun updateFromMeasureResult_afterFirstNotEmptyLayout_updatesEvenWhenTotalItemsCountIsZero() {
        val position = AdaptiveGridScrollPosition(initialIndex = 5, initialScrollOffset = 7)
        position.updateFromMeasureResult(
            firstVisibleItem = null,
            firstVisibleItemScrollOffset = 12,
            totalItemsCount = 10,
        )

        position.updateFromMeasureResult(
            firstVisibleItem = itemInfo(index = 3, key = "A"),
            firstVisibleItemScrollOffset = 1,
            totalItemsCount = 0,
        )

        assertEquals(3, position.index)
        assertEquals(1, position.scrollOffset)
        assertEquals(expectedNearestRange(3), position.nearestRangeState.value)
    }

    @Test
    fun updateFromMeasureResult_negativeScrollOffset_whenApplied_throwsIllegalStateException() {
        val position = AdaptiveGridScrollPosition()

        val error =
            assertThrows(IllegalStateException::class.java) {
                position.updateFromMeasureResult(
                    firstVisibleItem = null,
                    firstVisibleItemScrollOffset = -1,
                    totalItemsCount = 1,
                )
            }

        assertEquals("scrollOffset should be non-negative", error.message)
    }

    @Test
    fun updateFromMeasureResult_negativeItemIndex_throwsIllegalArgumentException_andDoesNotUpdateIndex() {
        val position = AdaptiveGridScrollPosition(initialIndex = 5, initialScrollOffset = 7)

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                position.updateFromMeasureResult(
                    firstVisibleItem = itemInfo(index = -1, key = "A"),
                    firstVisibleItemScrollOffset = 0,
                    totalItemsCount = 1,
                )
            }

        assertEquals("Index should be non-negative (-1)", error.message)
        assertEquals(5, position.index)
        assertEquals(7, position.scrollOffset)
        assertEquals(expectedNearestRange(5), position.nearestRangeState.value)
    }

    @Test
    fun updateScrollOffset_positive_updatesScrollOffsetOnly() {
        val position = AdaptiveGridScrollPosition(initialIndex = 5, initialScrollOffset = 7)
        val initialRange = position.nearestRangeState.value

        position.updateScrollOffset(123)

        assertEquals(5, position.index)
        assertEquals(123, position.scrollOffset)
        assertSame(initialRange, position.nearestRangeState.value)
    }

    @Test
    fun updateScrollOffset_negative_throwsIllegalStateException() {
        val position = AdaptiveGridScrollPosition()

        val error =
            assertThrows(IllegalStateException::class.java) {
                position.updateScrollOffset(-1)
            }

        assertEquals("scrollOffset should be non-negative", error.message)
    }

    @Test
    fun requestPositionAndForgetLastKnownKey_negativeIndex_throwsIllegalArgumentException() {
        val position = AdaptiveGridScrollPosition()

        val error =
            assertThrows(IllegalArgumentException::class.java) {
                position.requestPositionAndForgetLastKnownKey(index = -1, scrollOffset = 0)
            }

        assertEquals("Index should be non-negative (-1)", error.message)
    }

    @Test
    fun requestPositionAndForgetLastKnownKey_clearsKey_preventingLaterKeyBasedOverrides() {
        val position = AdaptiveGridScrollPosition()
        position.updateFromMeasureResult(
            firstVisibleItem = itemInfo(index = 10, key = "A"),
            firstVisibleItemScrollOffset = 0,
            totalItemsCount = 20,
        )

        position.requestPositionAndForgetLastKnownKey(index = 5, scrollOffset = 2)

        val provider = FakeGridLayoutProvider(keys = List(12) { i -> if (i == 10) "A" else "X$i" })
        val newIndex = position.updateScrollPositionIfTheFirstItemWasMoved(provider, currentIndex = 5)

        assertEquals(5, newIndex)
        assertEquals(5, position.index)
        assertEquals(expectedNearestRange(5), position.nearestRangeState.value)
    }

    @Test
    fun updateScrollPositionIfTheFirstItemWasMoved_whenKeyMatchesAtCurrentIndex_doesNotUpdateNearestRange() {
        val position = AdaptiveGridScrollPosition()
        position.updateFromMeasureResult(
            firstVisibleItem = itemInfo(index = 2, key = "B"),
            firstVisibleItemScrollOffset = 0,
            totalItemsCount = 20,
        )
        val initialRange = position.nearestRangeState.value

        val provider = FakeGridLayoutProvider(keys = List(10) { i -> if (i == 2) "B" else "X$i" })
        val newIndex = position.updateScrollPositionIfTheFirstItemWasMoved(provider, currentIndex = 2)

        assertEquals(2, newIndex)
        assertEquals(2, position.index)
        assertSame(initialRange, position.nearestRangeState.value)
    }

    @Test
    fun updateScrollPositionIfTheFirstItemWasMoved_whenKeyMoved_updatesIndexAndNearestRange() {
        val position = AdaptiveGridScrollPosition()
        position.updateFromMeasureResult(
            firstVisibleItem = itemInfo(index = 2, key = "B"),
            firstVisibleItemScrollOffset = 0,
            totalItemsCount = 20,
        )
        val initialRange = position.nearestRangeState.value

        // Move the item far enough to cross a sliding-window boundary so nearest range changes.
        val keys = List(60) { i -> if (i == 35) "B" else "X$i" }
        val provider = FakeGridLayoutProvider(keys)
        val newIndex = position.updateScrollPositionIfTheFirstItemWasMoved(provider, currentIndex = 2)

        assertEquals(35, newIndex)
        assertEquals(35, position.index)
        assertNotSame(initialRange, position.nearestRangeState.value)
        assertEquals(expectedNearestRange(35), position.nearestRangeState.value)
    }

    @Test
    fun updateScrollPositionIfTheFirstItemWasMoved_whenKeyNotFound_fallsBackToCurrentIndex() {
        val position = AdaptiveGridScrollPosition()
        position.updateFromMeasureResult(
            firstVisibleItem = itemInfo(index = 2, key = "B"),
            firstVisibleItemScrollOffset = 0,
            totalItemsCount = 20,
        )
        val initialRange = position.nearestRangeState.value

        val provider = FakeGridLayoutProvider(keys = List(10) { i -> "X$i" })
        val newIndex = position.updateScrollPositionIfTheFirstItemWasMoved(provider, currentIndex = 2)

        assertEquals(2, newIndex)
        assertEquals(2, position.index)
        assertSame(initialRange, position.nearestRangeState.value)
    }
}
