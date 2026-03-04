package com.lazyadaptivelayout.state

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.foundation.lazy.layout.NestedPrefetchScope
import com.lazyadaptivelayout.model.AdaptiveGridLayoutInfo

/**
 * Simplified prefetch strategy for AdaptiveGrid.
 * Mirrors LazyListPrefetchStrategy but adapted for grid layout.
 */
@ExperimentalFoundationApi
internal interface AdaptiveGridPrefetchStrategy {

    fun AdaptiveGridPrefetchScope.onScroll(delta: Float, layoutInfo: AdaptiveGridLayoutInfo)

    fun AdaptiveGridPrefetchScope.onVisibleItemsUpdated(layoutInfo: AdaptiveGridLayoutInfo)

    fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int)
}

/** Scope for prefetch callbacks in AdaptiveGridPrefetchStrategy. */
@ExperimentalFoundationApi
internal interface AdaptiveGridPrefetchScope {
    fun schedulePrefetch(
        index: Int,
        onPrefetchFinished: (AdaptiveGridPrefetchResultScope.() -> Unit)?,
    ): LazyLayoutPrefetchState.PrefetchHandle
}

/** Result scope for prefetch callbacks. */
@ExperimentalFoundationApi
internal interface AdaptiveGridPrefetchResultScope {
    val index: Int
}

/** Default prefetch strategy implementation. */
@ExperimentalFoundationApi
internal class AdaptiveGridPrefetchStrategyImpl : AdaptiveGridPrefetchStrategy {
    override fun AdaptiveGridPrefetchScope.onScroll(delta: Float, layoutInfo: AdaptiveGridLayoutInfo) {
        // Default: prefetch items ahead in scroll direction
        val itemCount = layoutInfo.totalItemsCount

        val lastIndex = itemCount - 1
        if (lastIndex < 0) return

        val firstVisible = (layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0).coerceIn(0, lastIndex)
        val lastVisible = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0).coerceIn(0, lastIndex)

        if (delta < 0) {
            // Scrolling down - prefetch items below
            val prefetchStart = (lastVisible + 1).coerceAtMost(itemCount - 1)
            val prefetchEnd = (prefetchStart + 2).coerceAtMost(itemCount - 1)
            for (i in prefetchStart..prefetchEnd) {
                schedulePrefetch(i, null)
            }
        } else {
            // Scrolling up - prefetch items above
            val prefetchEnd = (firstVisible - 1).coerceAtLeast(0)
            val prefetchStart = (prefetchEnd - 2).coerceAtLeast(0)
            for (i in prefetchStart..prefetchEnd) {
                schedulePrefetch(i, null)
            }
        }
    }

    override fun AdaptiveGridPrefetchScope.onVisibleItemsUpdated(layoutInfo: AdaptiveGridLayoutInfo) {
        // Prefetch items around visible range
        val itemCount = layoutInfo.totalItemsCount

        val lastIndex = itemCount - 1
        if (lastIndex < 0) return

        val firstVisible = (layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0).coerceIn(0, lastIndex)
        val lastVisible = (layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0).coerceIn(0, lastIndex)

        // Prefetch ahead
        val prefetchAheadStart = (lastVisible + 1).coerceAtMost(lastIndex)
        val prefetchAheadEnd = (prefetchAheadStart + 2).coerceAtMost(lastIndex)
        for (i in prefetchAheadStart..prefetchAheadEnd) {
            schedulePrefetch(i, null)
        }

        // Prefetch behind
        val prefetchBehindEnd = (firstVisible - 1).coerceAtLeast(0)
        val prefetchBehindStart = (prefetchBehindEnd - 2).coerceAtLeast(0)
        for (i in prefetchBehindStart..prefetchBehindEnd) {
            schedulePrefetch(i, null)
        }
    }

    override fun NestedPrefetchScope.onNestedPrefetch(firstVisibleItemIndex: Int) {
        // Prefetch a few items ahead
        adaptiveGridNestedPrefetchIndices(firstVisibleItemIndex).forEach { index ->
            schedulePrecomposition(index)
        }
    }
}

@ExperimentalFoundationApi
internal fun adaptiveGridNestedPrefetchIndices(
    firstVisibleItemIndex: Int,
    count: Int = 2,
): IntRange {
    if (count <= 0) return IntRange.EMPTY
    val start = firstVisibleItemIndex.coerceAtLeast(0)
    return start..<start + count
}

