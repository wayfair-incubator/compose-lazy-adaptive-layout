package com.lazyadaptivelayout.layout

/**
 * Returns indices of zero-height items that should be reported as visible even though they never
 * geometrically intersect the viewport.
 *
 * Pagination and secondary-fetch anchors render nothing (height 0) but must appear in
 * [com.lazyadaptivelayout.state.AdaptiveGridState.layoutInfo.visibleItemsInfo] so consumers can
 * load the next page or secondary data when their index is reached.
 *
 * Two cases are handled:
 * 1. **In-window anchors** — zero-height slots within the current measure window immediately after
 *    the last geometrically visible item. This keeps secondary-fetch working while the grid loads
 *    progressively (the measure window no longer spans the entire list on every pass).
 * 2. **Trailing anchors** — when the measure window has reached the list end, any contiguous
 *    zero-height run after the last visible item through the list tail (pagination anchors).
 *
 * A real item (height > 0) below the scan position stops the run so not-yet-visible content must
 * still be scrolled into view normally.
 *
 * @param lastGeometricVisibleIndex index of the last geometrically visible item, or -1 if none.
 * @param measureWindowEnd last index included in the current measure pass.
 * @param itemsCount total number of items in the grid.
 * @param measureWindowReachedEnd whether the current measure pass attempted to measure the last item.
 * @param effectiveHeightAt effective (normalized) height of the item at a given index; unmeasured
 *   indices should return [Int.MAX_VALUE] so the scan stops before them.
 */
internal fun collapsedZeroHeightVisibleIndices(
    lastGeometricVisibleIndex: Int,
    measureWindowEnd: Int,
    itemsCount: Int,
    measureWindowReachedEnd: Boolean,
    effectiveHeightAt: (Int) -> Int,
): List<Int> {
    if (itemsCount == 0) return emptyList()

    val result = mutableListOf<Int>()

    // In-window zero-height anchors (secondary fetch, etc.) within the progressive measure window.
    val inWindowStart = (lastGeometricVisibleIndex + 1).coerceAtLeast(0)
    val inWindowEnd = measureWindowEnd.coerceAtMost(itemsCount - 1)
    if (inWindowStart <= inWindowEnd) {
        for (index in inWindowStart..inWindowEnd) {
            if (effectiveHeightAt(index) > 0) break
            result.add(index)
        }
    }

    // Trailing zero-height anchors at the list tail (pagination).
    if (measureWindowReachedEnd && lastGeometricVisibleIndex < itemsCount - 1) {
        val trailingStart = maxOf(
            lastGeometricVisibleIndex + 1,
            (result.lastOrNull()?.plus(1)) ?: (lastGeometricVisibleIndex + 1),
            measureWindowEnd + 1,
        )
        for (index in trailingStart until itemsCount) {
            if (effectiveHeightAt(index) > 0) break
            if (index !in result) {
                result.add(index)
            }
        }
    }

    return result
}

/** @see collapsedZeroHeightVisibleIndices */
@Deprecated("Use collapsedZeroHeightVisibleIndices", ReplaceWith("collapsedZeroHeightVisibleIndices"))
internal fun trailingCollapsedIndices(
    lastVisibleIndex: Int,
    itemsCount: Int,
    measureWindowReachedEnd: Boolean,
    effectiveHeightAt: (Int) -> Int,
): List<Int> = collapsedZeroHeightVisibleIndices(
    lastGeometricVisibleIndex = lastVisibleIndex,
    measureWindowEnd = if (measureWindowReachedEnd) itemsCount - 1 else lastVisibleIndex,
    itemsCount = itemsCount,
    measureWindowReachedEnd = measureWindowReachedEnd,
    effectiveHeightAt = effectiveHeightAt,
)
