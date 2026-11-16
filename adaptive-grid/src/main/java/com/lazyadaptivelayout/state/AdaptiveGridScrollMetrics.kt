package com.lazyadaptivelayout.state

import com.lazyadaptivelayout.model.AdaptiveGridLayoutInfo

/**
 * Effective scroll span through the scroll-anchor item after subtracting overlapping chrome
 * (e.g. a fixed top bar). Returns 0 when [AdaptiveGridState.firstVisibleItemIndex] is not 0 or
 * when the span would be non-positive.
 *
 * Use this (or [scrollFractionThroughFirstItem]) instead of dividing raw [size] values by hand
 * to avoid zero/negative denominators in overlay animations.
 */
fun AdaptiveGridState.firstItemScrollSpan(overlappingChromeHeightPx: Int): Int {
    if (firstVisibleItemIndex != 0) return 0
    val itemSize = layoutInfo.firstVisibleItem?.size ?: return 0
    return (itemSize - overlappingChromeHeightPx).coerceAtLeast(0)
}

/**
 * Scroll fraction in `[0f, 1f]` through the first item for parallax/fade overlays.
 * Never returns `NaN` or `Infinity` — safe to pass directly to Compose animations.
 *
 * @param overlappingChromeHeightPx Height in pixels of UI overlapping the first item (e.g. top bar).
 */
fun AdaptiveGridState.scrollFractionThroughFirstItem(overlappingChromeHeightPx: Int): Float {
    val span = firstItemScrollSpan(overlappingChromeHeightPx)
    if (span <= 0) return 0f
    return (firstVisibleItemScrollOffset.toFloat() / span).coerceIn(0f, 1f)
}

/**
 * Layout-info variant of [AdaptiveGridState.firstItemScrollSpan] for use with a known scroll anchor
 * index and offset.
 */
fun AdaptiveGridLayoutInfo.firstItemScrollSpan(
    scrollAnchorIndex: Int,
    overlappingChromeHeightPx: Int,
): Int {
    if (scrollAnchorIndex != 0) return 0
    val itemSize = firstVisibleItem?.size ?: return 0
    return (itemSize - overlappingChromeHeightPx).coerceAtLeast(0)
}

/**
 * Layout-info variant of [AdaptiveGridState.scrollFractionThroughFirstItem].
 */
fun AdaptiveGridLayoutInfo.scrollFractionThroughFirstItem(
    scrollAnchorIndex: Int,
    scrollAnchorOffset: Int,
    overlappingChromeHeightPx: Int,
): Float {
    val span = firstItemScrollSpan(scrollAnchorIndex, overlappingChromeHeightPx)
    if (span <= 0) return 0f
    return (scrollAnchorOffset.toFloat() / span).coerceIn(0f, 1f)
}
