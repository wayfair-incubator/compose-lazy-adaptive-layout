package com.lazyadaptivelayout.state

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.lazyadaptivelayout.model.AdaptiveGridItemInfo
import com.lazyadaptivelayout.provider.findIndexByKey
import com.lazyadaptivelayout.provider.GridLayoutProvider

private fun checkPrecondition(value: Boolean, lazyMessage: () -> String) {
    if (!value) throw IllegalStateException(lazyMessage())
}

private fun requirePrecondition(value: Boolean, lazyMessage: () -> String) {
    if (!value) throw IllegalArgumentException(lazyMessage())
}

/** Tracks a nearest range of items around the current scroll position for key-to-index lookups. */
internal class AdaptiveGridNearestRangeState(
    firstVisibleItem: Int,
    private val slidingWindowSize: Int,
    private val extraItemCount: Int
) : State<IntRange> {
    override var value: IntRange by mutableStateOf(
        calculateNearestItemsRange(firstVisibleItem, slidingWindowSize, extraItemCount)
    )
        private set

    private var lastFirstVisibleItem = firstVisibleItem

    fun update(firstVisibleItem: Int) {
        if (firstVisibleItem != lastFirstVisibleItem) {
            lastFirstVisibleItem = firstVisibleItem
            value = calculateNearestItemsRange(firstVisibleItem, slidingWindowSize, extraItemCount)
        }
    }

    /** Returns a range of indexes near [firstVisibleItem], with slack to avoid regenerating on every scroll. */
    private fun calculateNearestItemsRange(
        firstVisibleItem: Int,
        slidingWindowSize: Int,
        extraItemCount: Int,
    ): IntRange {
        val slidingWindowStart = slidingWindowSize * (firstVisibleItem / slidingWindowSize)
        val start = maxOf(slidingWindowStart - extraItemCount, 0)
        val end = slidingWindowStart + slidingWindowSize + extraItemCount
        return start until end
    }
}

/** Holds the current scroll position (first visible index and its scroll offset). */
internal class AdaptiveGridScrollPosition(initialIndex: Int = 0, initialScrollOffset: Int = 0) {
    var index by mutableIntStateOf(initialIndex)

    var scrollOffset by mutableIntStateOf(initialScrollOffset)
        private set

    private var hadFirstNotEmptyLayout = false

    private var lastKnownFirstItemKey: Any? = null

    val nearestRangeState =
        AdaptiveGridNearestRangeState(
            initialIndex,
            NearestItemsSlidingWindowSize,
            NearestItemsExtraItemCount,
        )

    fun updateFromMeasureResult(
        firstVisibleItem: AdaptiveGridItemInfo?,
        firstVisibleItemScrollOffset: Int,
        totalItemsCount: Int
    ) {
        lastKnownFirstItemKey = firstVisibleItem?.key
        if (hadFirstNotEmptyLayout || totalItemsCount > 0) {
            hadFirstNotEmptyLayout = true
            checkPrecondition(firstVisibleItemScrollOffset >= 0) { "scrollOffset should be non-negative" }

            val firstIndex = firstVisibleItem?.index ?: 0
            update(firstIndex, firstVisibleItemScrollOffset)
        }
    }

    fun updateScrollOffset(scrollOffset: Int) {
        checkPrecondition(scrollOffset >= 0) { "scrollOffset should be non-negative" }
        this.scrollOffset = scrollOffset
    }

    fun requestPositionAndForgetLastKnownKey(index: Int, scrollOffset: Int) {
        update(index, scrollOffset)
        lastKnownFirstItemKey = null
    }

    @OptIn(ExperimentalFoundationApi::class)
    fun updateScrollPositionIfTheFirstItemWasMoved(
        itemProvider: GridLayoutProvider<*>,
        currentIndex: Int,
    ): Int {
        val newIndex = itemProvider.findIndexByKey(lastKnownFirstItemKey, currentIndex)
        if (currentIndex != newIndex) {
            this.index = newIndex
            nearestRangeState.update(newIndex)
        }
        return newIndex
    }

    private fun update(index: Int, scrollOffset: Int) {
        requirePrecondition(index >= 0) { "Index should be non-negative ($index)" }
        this.index = index
        nearestRangeState.update(index)
        this.scrollOffset = scrollOffset
    }
}

/** Sliding window size used to avoid regenerating the key-to-index map too frequently. */
internal const val NearestItemsSlidingWindowSize = 30

/** The minimum amount of items near the current first visible item we want to have mapping for. */
internal const val NearestItemsExtraItemCount = 100

