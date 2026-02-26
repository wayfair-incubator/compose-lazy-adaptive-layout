package com.lazyadaptivelayout.state

import com.lazyadaptivelayout.model.AdaptiveGridItemInfo
import com.lazyadaptivelayout.model.GridItem
import com.lazyadaptivelayout.model.GridItemConfig
import com.lazyadaptivelayout.model.GridItemType
import com.lazyadaptivelayout.model.ItemPosition
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveGridStateTest {

    private fun gridItem(index: Int, type: GridItemType = GridItemType.Uniform): GridItem<Int> =
        GridItem(index, GridItemConfig(type))

    private fun positions(vararg tuples: Pair<Int, Int>): List<ItemPosition> =
        tuples.mapIndexed { i, (y, h) -> ItemPosition(x = 0, y = y, width = 100, height = h, columnIndex = 0) }

    @Test
    fun updateLayoutInfo_withEmptyPositions_setsEmptyVisibleAndViewportMetrics() {
        val state = AdaptiveGridState()
        state.viewportHeight = 300

        val items = List(5) { gridItem(it) }
        val keys = List(items.size) { "key-$it" }

        state.updateLayoutInfo(items, emptyList(), keys, scrollOffset = 0)

        val info = state.layoutInfo
        assertTrue(info.visibleItemsInfo.isEmpty())
        assertEquals(0, info.viewportStartOffset)
        assertEquals(300, info.viewportEndOffset)
        assertEquals(5, info.totalItemsCount)
        assertEquals(300, info.viewportSize)

        // Derived getters when nothing visible
        assertEquals(0, state.firstVisibleItemIndex)
        assertEquals(-1, state.lastVisibleItemIndex)
        assertFalse(state.isScrolledToEnd)
        assertEquals(0, state.firstVisibleItemScrollOffset)
    }

    @Test
    fun updateLayoutInfo_computesVisibleItems_andOffsets_andRespectsKeys() {
        val state = AdaptiveGridState()
        state.viewportHeight = 200

        val items = List(5) { gridItem(it, GridItemType.Uniform) }
        val keys = List(items.size) { "k$it" }
        val pos = positions(
            0 to 100,   // index 0: 0..100
            100 to 120, // index 1: 100..220
            220 to 80,  // index 2: 220..300
            300 to 100, // index 3: 300..400
            400 to 100  // index 4: 400..500
        )

        // viewport = [150, 350]
        state.updateLayoutInfo(items, pos, keys, scrollOffset = 150)

        val visible = state.visibleItemsInfo
        val visibleIdx = visible.map { it.index }
        assertEquals(listOf(1, 2, 3), visibleIdx)

        // Offsets are relative to viewport start (150)
        assertEquals(listOf(
            100 - 150, // -50 (partially visible)
            220 - 150, // 70
            300 - 150  // 150
        ), visible.map { it.offset })

        // Keys must be passed through
        assertEquals(listOf("k1", "k2", "k3"), visible.map { it.key })

        // Derived properties
        assertEquals(1, state.firstVisibleItemIndex)
        assertEquals(3, state.lastVisibleItemIndex)
        assertFalse(state.isScrolledToEnd)
        assertEquals(50, state.firstVisibleItemScrollOffset)
    }

    @Test
    fun updateLayoutInfo_keyFallback_andLayoutTypeFallback_whenKeysShorterThanItems() {
        val state = AdaptiveGridState()
        state.viewportHeight = 300

        val items = listOf(
            gridItem(0, GridItemType.Uniform),
            gridItem(1, GridItemType.Uniform),
            gridItem(2, GridItemType.Uniform)
        )
        // Only one key provided -> others should fallback to index
        val keys = listOf("only0")
        val pos = positions(
            0 to 100,
            100 to 100,
            200 to 100
        )

        state.updateLayoutInfo(items, pos, keys, scrollOffset = 0)

        val visible = state.visibleItemsInfo
        assertEquals(listOf("only0", 1, 2), visible.map { it.key })
        // Layout type should come from the corresponding item
        assertTrue(visible.all { it.layoutType is GridItemType.Uniform })
    }

    @Test
    fun isItemVisible_and_isItemCompletelyVisible() {
        val state = AdaptiveGridState()
        state.viewportHeight = 200

        val items = List(3) { gridItem(it) }
        val keys = List(3) { it }
        val pos = positions(
            0 to 150,   // fully visible at scroll=0
            150 to 100, // partially visible at bottom
            260 to 100  // not visible at scroll=0
        )

        state.updateLayoutInfo(items, pos, keys, scrollOffset = 0)

        assertTrue(state.isItemVisible(0))
        assertTrue(state.isItemCompletelyVisible(0))

        assertTrue(state.isItemVisible(1))
        assertFalse(state.isItemCompletelyVisible(1))

        assertFalse(state.isItemVisible(2))
        assertFalse(state.isItemCompletelyVisible(2))
    }

    @Test
    fun isScrolledToEnd_true_whenLastItemVisible() {
        val state = AdaptiveGridState()
        state.viewportHeight = 200

        val items = List(3) { gridItem(it) }
        val keys = List(3) { it }
        val pos = positions(
            0 to 150,
            150 to 100,
            250 to 50
        )

        // viewport start 100 -> visible should include last index=2
        state.updateLayoutInfo(items, pos, keys, scrollOffset = 100)
        assertTrue(state.isScrolledToEnd)
    }

    @Test
    fun saver_saveAndRestore_preservesScrollPositionAndFirstVisible() {
        val state = AdaptiveGridState(
            firstVisibleItemIndex = 1,
            firstVisibleItemScrollOffset = 10
        )
        state.viewportHeight = 200

        val items = List(3) { gridItem(it) }
        val keys = List(3) { it }
        val pos = positions(100 to 50, 160 to 50, 220 to 50)

        // Set some layout so first visible is index=1 at scroll=170 (layout info uses this)
        state.updateLayoutInfo(items, pos, keys, scrollOffset = 170)

        // Saver stores current scroll position (firstVisibleItemIndex and firstVisibleItemScrollOffset)
        // Test that saver can restore the state correctly
        // listSaver.restore has complex type inference - the issue is that different List<*> instances
        // are considered different types. We need to directly invoke the restore lambda.
        // Since listSaver creates a Saver with restore: (List<*>) -> AdaptiveGridState,
        // we can test it by creating a list and using type erasure
        val saved = listOf(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
        // The restore function from listSaver expects the exact type from save, which is List<Any>
        // but the signature uses List<*> with type constraints. We need to match it exactly.
        // Use @Suppress to bypass the type checking since we know the structure matches
        @Suppress("UNCHECKED_CAST", "TYPE_INFERENCE_ONLY_INPUT_TYPES")
        val restored = (AdaptiveGridState.Saver as androidx.compose.runtime.saveable.Saver<AdaptiveGridState, List<*>>).restore(saved as List<*>)!!

        // Saver restores firstVisibleItemIndex and firstVisibleItemScrollOffset
        assertEquals(state.firstVisibleItemIndex, restored.firstVisibleItemIndex)
        assertEquals(state.firstVisibleItemScrollOffset, restored.firstVisibleItemScrollOffset)
    }

    @Test
    fun scrollToItem_and_animateScrollToItem_outOfBounds_areNoOps() {
        runTest {
            val state = AdaptiveGridState()
            state.viewportHeight = 200

            // Provide positions directly; internal is visible to tests in same module
            state._itemPositions = positions(0 to 100, 120 to 100, 250 to 80)

            val initialIndex = state.firstVisibleItemIndex
            val initialOffset = state.firstVisibleItemScrollOffset

            // Out-of-bounds indices should not change scroll and should not crash
            state.scrollToItem(100, scrollOffset = 20)
            assertEquals(initialIndex, state.firstVisibleItemIndex)
            assertEquals(initialOffset, state.firstVisibleItemScrollOffset)

            state.animateScrollToItem(100, scrollOffset = 0)
            assertEquals(initialIndex, state.firstVisibleItemIndex)
            assertEquals(initialOffset, state.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun updateLayoutInfo_firstVisibleItemScrollOffset_isNeverNegative_whenFirstItemStartsAboveViewport() {
        val state = AdaptiveGridState()
        state.viewportHeight = 200

        val items = List(3) { gridItem(it) }
        val keys = List(3) { it }
        val pos = positions(
            0 to 120,   // index 0: 0..120
            120 to 120, // index 1: 120..240
            240 to 80   // index 2: 240..320
        )

        // viewport = [80, 280] -> first visible item is index 0 and it starts above viewport
        state.updateLayoutInfo(items, pos, keys, scrollOffset = 80)

        assertEquals(0, state.firstVisibleItemIndex)
        // scroll offset represents the amount of the first item scrolled off-screen
        assertEquals(80, state.firstVisibleItemScrollOffset)
        assertTrue(state.firstVisibleItemScrollOffset >= 0)
    }

    @Test
    fun updateLayoutInfo_includesItemsTouchingViewportBounds() {
        val state = AdaptiveGridState()
        state.viewportHeight = 100

        val items = List(4) { gridItem(it) }
        val keys = List(4) { it }
        val pos = positions(
            0 to 50,    // index 0: 0..50
            50 to 50,   // index 1: 50..100 (ends exactly at viewportStart)
            100 to 50,  // index 2: 100..150 (starts exactly at viewportStart)
            150 to 50   // index 3: 150..200 (starts exactly at viewportEnd)
        )

        // viewport = [100, 200]
        state.updateLayoutInfo(items, pos, keys, scrollOffset = 100)

        // Condition in production code: itemBottom >= viewportStart && itemTop <= viewportEnd
        // So items that touch the bounds should still be considered visible.
        assertEquals(listOf(1, 2, 3), state.visibleItemsInfo.map { it.index })
    }

    @Test
    fun updateLayoutInfo_canScrollForward_trueOnlyWhenContentExtendsPastViewportEnd() {
        val state = AdaptiveGridState()
        state.viewportHeight = 200

        val items = List(3) { gridItem(it) }
        val keys = List(3) { it }
        val posExactlyEndsAtViewportEnd = positions(
            0 to 100,
            100 to 100,
            200 to 100
        )
        // viewport = [0, 200], last item ends at 300 (> 200) => canScrollForward should be true
        state.updateLayoutInfo(items, posExactlyEndsAtViewportEnd, keys, scrollOffset = 0)
        assertTrue(state.canScrollForward)

        val posEndsExactlyAtViewportEnd = positions(
            0 to 100,
            100 to 100
        )
        // viewport = [0, 200], last item ends at 200 (== 200) => canScrollForward should be false
        state.updateLayoutInfo(items.take(2), posEndsExactlyAtViewportEnd, keys.take(2), scrollOffset = 0)
        assertFalse(state.canScrollForward)
    }

    @Test
    fun isItemCompletelyVisible_true_whenItemExactlyFitsViewport() {
        val state = AdaptiveGridState()
        state.viewportHeight = 200

        val items = List(2) { gridItem(it) }
        val keys = List(2) { it }
        val pos = positions(
            0 to 200,   // exactly fills viewport at scroll=0
            200 to 50
        )

        state.updateLayoutInfo(items, pos, keys, scrollOffset = 0)

        assertTrue(state.isItemVisible(0))
        assertTrue(state.isItemCompletelyVisible(0))
        assertFalse(state.isItemCompletelyVisible(1))
    }

    @Test
    fun scrollToItem_and_animateScrollToItem_beforeFirstLayout_areNoOps() {
        runTest {
            val state = AdaptiveGridState()

            // layoutInfo is empty initially (totalItemsCount == 0) -> requests should short-circuit
            val initialIndex = state.firstVisibleItemIndex
            val initialOffset = state.firstVisibleItemScrollOffset

            state.scrollToItem(0)
            assertEquals(initialIndex, state.firstVisibleItemIndex)
            assertEquals(initialOffset, state.firstVisibleItemScrollOffset)

            state.animateScrollToItem(0)
            assertEquals(initialIndex, state.firstVisibleItemIndex)
            assertEquals(initialOffset, state.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun scrollToItem_and_animateScrollToItem_negativeIndex_areNoOps() {
        runTest {
            val state = AdaptiveGridState()
            state.viewportHeight = 200

            // Set some initial position so we can verify it doesn't change.
            val initialIndex = state.firstVisibleItemIndex
            val initialOffset = state.firstVisibleItemScrollOffset

            state.scrollToItem(index = -1, scrollOffset = 10)
            assertEquals(initialIndex, state.firstVisibleItemIndex)
            assertEquals(initialOffset, state.firstVisibleItemScrollOffset)

            state.animateScrollToItem(index = -1, scrollOffset = 10)
            assertEquals(initialIndex, state.firstVisibleItemIndex)
            assertEquals(initialOffset, state.firstVisibleItemScrollOffset)
        }
    }

    @Test
    fun isItemVisible_and_isItemCompletelyVisible_returnFalse_forInvalidIndices() {
        val state = AdaptiveGridState()
        state.viewportHeight = 200

        val items = List(2) { gridItem(it) }
        val keys = List(2) { it }
        val pos = positions(
            0 to 100,
            100 to 100,
        )

        state.updateLayoutInfo(items, pos, keys, scrollOffset = 0)

        assertFalse(state.isItemVisible(-1))
        assertFalse(state.isItemCompletelyVisible(-1))

        assertFalse(state.isItemVisible(2))
        assertFalse(state.isItemCompletelyVisible(2))
    }

    @Test
    fun saver_restore_withWrongTypes_fallsBackToDefaults() {
        @Suppress("UNCHECKED_CAST", "TYPE_INFERENCE_ONLY_INPUT_TYPES")
        val saver = AdaptiveGridState.Saver as androidx.compose.runtime.saveable.Saver<AdaptiveGridState, List<*>>

        val restored = saver.restore(listOf("not-int", 12.34) as List<*>)!!

        assertEquals(0, restored.firstVisibleItemIndex)
        assertEquals(0, restored.firstVisibleItemScrollOffset)
    }
}
