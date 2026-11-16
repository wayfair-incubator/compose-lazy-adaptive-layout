package com.lazyadaptivelayout

import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.lazyadaptivelayout.layout.invalidatePositionCache
import com.lazyadaptivelayout.layout.invalidatePositionCacheIfHeightsChanged
import com.lazyadaptivelayout.layout.measureFlattenedGrid
import com.lazyadaptivelayout.model.GridItemConfig
import com.lazyadaptivelayout.model.ItemPosition
import com.lazyadaptivelayout.provider.GridLayoutProvider

/**
 * Items whose height is not known up front can only be sized by composing and measuring them. Every pass
 * measures one window of items that reaches well past the viewport: everything from the scroll anchor to
 * the end of the window is composed and measured fresh (and stays composed), so content that scrolls into
 * view has already been composed and measured. Items before the window reuse their cached height.
 */

/**
 * Heights learned by measuring, shared by every pass of one grid.
 *
 * Only items that currently have a height are cached: an item that renders nothing is measured again on
 * every pass, so it expands as soon as its content arrives.
 */
internal class MeasuredHeightCache {
    val heights = mutableMapOf<Int, Int>()

    private var keys: List<Any> = emptyList()
    private var itemsRef: List<*>? = null

    /**
     * Keeps the heights of items whose key is unchanged at the same index (for example when a page is
     * appended to a feed) and drops the rest. Returns true when an existing item changed, which also
     * invalidates the positions computed from the old heights.
     */
    fun syncWith(items: List<EnrichedGridItem<*>>): Boolean {
        if (itemsRef === items) return false
        itemsRef = items

        val previous = keys
        val common = minOf(previous.size, items.size)
        var firstChanged = common
        for (index in 0 until common) {
            if (previous[index] != items[index].key) {
                firstChanged = index
                break
            }
        }
        keys = items.map { it.key }

        heights.keys.removeAll { it >= firstChanged }
        // Items appended or removed at the end do not move the others; a changed key does.
        return firstChanged < common
    }

    fun clear() {
        heights.clear()
        keys = emptyList()
        itemsRef = null
    }
}

internal class MeasurePassResult(
    val placeables: List<Placeable?>,
    val itemHeights: List<Int>,
    val heightChanges: List<Pair<Int, Int>>,
    val positions: List<ItemPosition>,
    /** Highest item index visited in this pass. */
    val measuredEnd: Int,
)

/**
 * The item range to measure around the scroll position: from [bufferSize] items before [firstItemIndex]
 * to well past the last item measured so far, so that content scrolled into view is already measured.
 */
internal fun calculateMeasureRange(
    firstItemIndex: Int,
    itemCount: Int,
    bufferSize: Int,
    viewportHeight: Int,
    lastMeasuredIndex: Int = -1,
): IntRange {
    if (itemCount == 0) return IntRange.EMPTY

    val start = maxOf(0, firstItemIndex - bufferSize)

    val estimatedItemsForViewport = (viewportHeight / 100).coerceAtLeast(20)
    val forwardBuffer = maxOf(
        bufferSize * 10,
        estimatedItemsForViewport * 3,
    )

    val endFromFirst = firstItemIndex + forwardBuffer
    val endFromLast = if (lastMeasuredIndex >= 0) {
        lastMeasuredIndex + forwardBuffer
    } else {
        endFromFirst
    }
    val end = minOf(itemCount - 1, maxOf(endFromFirst, endFromLast))

    return start..end
}

/**
 * One measure pass over the grid.
 *
 * Every item from 0 to the end of the window is visited, so positions are right even when the scroll
 * anchor is far past what was measured before (a restored position, or a scroll to an item). Items in the
 * window ([calculateMeasureRange]) are measured fresh and keep their placeables; items before it keep
 * their cached height and are only measured, once, when they have none yet.
 */
internal fun <T> measureWindow(
    layoutProvider: GridLayoutProvider<T>,
    enrichedItems: List<EnrichedGridItem<T>>,
    heightCache: MeasuredHeightCache,
    previousPositions: List<ItemPosition>,
    anchorIndex: Int,
    constraints: Constraints,
    density: Density,
    bufferSize: Int,
    measurer: (Int, Constraints) -> List<Placeable>,
    getCachedWidth: (GridItemConfig, Int, Int) -> Int,
): MeasurePassResult {
    val itemCount = layoutProvider.itemCount
    if (itemCount == 0) {
        return MeasurePassResult(emptyList(), emptyList(), emptyList(), emptyList(), -1)
    }
    val lastIndex = itemCount - 1

    if (heightCache.syncWith(enrichedItems)) invalidatePositionCache()

    val lastMeasuredIndex = previousPositions.indexOfLast { it.height > 0 }
    val rangeStartIndex = if (lastMeasuredIndex >= 0) minOf(anchorIndex, lastMeasuredIndex) else anchorIndex
    val window = calculateMeasureRange(
        firstItemIndex = rangeStartIndex,
        itemCount = itemCount,
        bufferSize = bufferSize,
        viewportHeight = constraints.maxHeight,
        lastMeasuredIndex = lastMeasuredIndex,
    )
    val visitedEnd = minOf(lastIndex, window.last + bufferSize)

    val (placeables, itemHeights, heightChanges) = measureGridItems(
        layoutProvider = layoutProvider,
        enrichedItems = enrichedItems,
        visibleRange = 0..visitedEnd,
        freshRange = window,
        measuredHeights = heightCache.heights,
        constraints = constraints,
        measurer = measurer,
        bufferSize = 0,
        getCachedWidth = getCachedWidth,
    )

    invalidatePositionCacheIfHeightsChanged(heightChanges)
    val positions = measureFlattenedGrid(enrichedItems, itemHeights, constraints, density)
    return MeasurePassResult(placeables, itemHeights, heightChanges, positions, visitedEnd)
}

/** The scroll position after keeping the viewport inside the content. */
internal data class ScrollBackResult(val firstItemScrollOffset: Int, val scrollDelta: Int)

/**
 * Pulls the scroll position back when the viewport would extend past the end of the content.
 *
 * Only done once [contentFullyDiscovered] (every item has been visited): before that the content's end is
 * unknown, so a short content height is not a limit. Clamping on it would throw away a restored or
 * requested scroll offset and cut a fling short before the rest of the list is measured.
 */
internal fun scrollBackToContent(
    positions: List<ItemPosition>,
    anchorIndex: Int,
    anchorScrollOffset: Int,
    scrollDelta: Int,
    viewportHeight: Int,
    contentFullyDiscovered: Boolean,
): ScrollBackResult {
    var offset = anchorScrollOffset
    var delta = scrollDelta
    val totalContentHeight = positions.maxOfOrNull { it.y + it.height } ?: 0
    if (!contentFullyDiscovered || totalContentHeight <= 0) return ScrollBackResult(offset, delta)
    val anchor = positions.getOrNull(anchorIndex) ?: return ScrollBackResult(offset, delta)

    val viewportEnd = anchor.y + offset + viewportHeight
    if (viewportEnd > totalContentHeight) {
        val toScrollBack = viewportEnd - totalContentHeight
        offset -= toScrollBack
        delta += toScrollBack
        if (offset < 0) {
            delta += offset
            offset = 0
        }
    }

    val contentExtentBelowStart = totalContentHeight - (anchor.y + offset)
    if (contentExtentBelowStart < viewportHeight) {
        val toScrollBack = viewportHeight - contentExtentBelowStart
        offset -= toScrollBack
        delta += toScrollBack
        if (offset < 0) {
            delta += offset
            offset = 0
        }
    }
    return ScrollBackResult(offset, delta)
}
