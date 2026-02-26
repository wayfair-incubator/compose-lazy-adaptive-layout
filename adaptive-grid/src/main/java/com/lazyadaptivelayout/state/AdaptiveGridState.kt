package com.lazyadaptivelayout.state

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.layout.LazyLayoutPrefetchState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.annotation.FrequentlyChangingValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.layout.Remeasurement
import androidx.compose.ui.layout.RemeasurementModifier
import androidx.compose.ui.util.fastRoundToInt
import com.lazyadaptivelayout.layout.AwaitFirstLayoutModifier
import com.lazyadaptivelayout.layout.ObservableScopeInvalidator
import com.lazyadaptivelayout.model.AdaptiveGridItemInfo
import com.lazyadaptivelayout.model.AdaptiveGridLayoutInfo
import com.lazyadaptivelayout.model.GridItem
import com.lazyadaptivelayout.model.ItemPosition
import com.lazyadaptivelayout.provider.GridLayoutProvider
import kotlin.math.abs

/** Holds scroll and visibility state for LazyAdaptiveGrid. */
@OptIn(ExperimentalFoundationApi::class)
@Stable
class AdaptiveGridState(
    firstVisibleItemIndex: Int = 0,
    firstVisibleItemScrollOffset: Int = 0
) : ScrollableState {

    var viewportHeight: Int = 0
        internal set

    internal var _itemPositions: List<ItemPosition> = emptyList()
    val itemPositions: List<ItemPosition> get() = _itemPositions

    internal var hasLookaheadOccurred: Boolean = false
        private set

    internal var approachLayoutInfo: AdaptiveGridMeasureResult? = null
        private set

    // always execute requests in high priority
    private var executeRequestsInHighPriorityMode = false

    private val scrollPosition =
        AdaptiveGridScrollPosition(firstVisibleItemIndex, firstVisibleItemScrollOffset)

    /**
     * The index of the first item that is visible within the scrollable viewport area.
     */
    val firstVisibleItemIndex: Int
        @FrequentlyChangingValue get() = scrollPosition.index

    /**
     * The scroll offset of the first visible item. Scrolling forward is positive.
     */
    val firstVisibleItemScrollOffset: Int
        @FrequentlyChangingValue get() = scrollPosition.scrollOffset

    private val layoutInfoState = mutableStateOf(EmptyAdaptiveGridMeasureResult, neverEqualPolicy())

    val layoutInfo: AdaptiveGridLayoutInfo
        @FrequentlyChangingValue get() = layoutInfoState.value.toLayoutInfo()

    internal var scrollToBeConsumed = 0f
        private set

    internal val density: androidx.compose.ui.unit.Density
        get() = layoutInfoState.value.density

    // Match LazyList scroll direction handling.
    private val scrollableState = androidx.compose.foundation.gestures.ScrollableState { -onScroll(-it) }

    internal var remeasurement: Remeasurement? = null
        private set

    internal val remeasurementModifier =
        object : RemeasurementModifier {
            override fun onRemeasurementAvailable(remeasurement: Remeasurement) {
                this@AdaptiveGridState.remeasurement = remeasurement
            }
        }

    /**
     * Provides a modifier which allows to delay some interactions (e.g. scroll) until layout is
     * ready.
     */
    internal val awaitLayoutModifier = AwaitFirstLayoutModifier()

    internal val measurementScopeInvalidator = ObservableScopeInvalidator()

    internal val placementScopeInvalidator = ObservableScopeInvalidator()

    internal val nearestRange: IntRange by scrollPosition.nearestRangeState

    private val prefetchStrategy: AdaptiveGridPrefetchStrategy = AdaptiveGridPrefetchStrategyImpl()

    internal val prefetchState =
        LazyLayoutPrefetchState {
            with(prefetchStrategy) {
                onNestedPrefetch(Snapshot.withoutReadObservation { firstVisibleItemIndex })
            }
        }

    private val prefetchScope: AdaptiveGridPrefetchScope =
        object : AdaptiveGridPrefetchScope {
            override fun schedulePrefetch(
                index: Int,
                onPrefetchFinished: (AdaptiveGridPrefetchResultScope.() -> Unit)?,
            ): LazyLayoutPrefetchState.PrefetchHandle {
                return prefetchState.schedulePrecomposition(index)
            }
        }

    override var canScrollForward: Boolean by mutableStateOf(false)
        private set

    override var canScrollBackward: Boolean by mutableStateOf(false)
        private set

    override val isScrollInProgress: Boolean
        get() = scrollableState.isScrollInProgress

    /**
     * [InteractionSource] that will be used to dispatch drag events when this grid is being
     * dragged. If you want to know whether the fling (or animated scroll) is in progress, use
     * [isScrollInProgress].
     */
    val interactionSource: androidx.compose.foundation.interaction.InteractionSource
        get() = internalInteractionSource

    internal val internalInteractionSource: MutableInteractionSource = MutableInteractionSource()

    val lastVisibleItemIndex: Int by derivedStateOf {
        layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
    }

    val visibleItemsInfo: List<AdaptiveGridItemInfo> by derivedStateOf {
        layoutInfo.visibleItemsInfo
    }

    val isScrolledToEnd: Boolean by derivedStateOf {
        layoutInfo.totalItemsCount > 0 &&
            lastVisibleItemIndex >= layoutInfo.totalItemsCount - 1
    }

    /**
     * Instantly brings the item at [index] to the top of the viewport, offset by [scrollOffset]
     * pixels.
     *
     * @param index the index to which to scroll. Must be non-negative.
     * @param scrollOffset the offset that the item should end up after the scroll.
     */
    suspend fun scrollToItem(index: Int, scrollOffset: Int = 0) {
        if (index < 0 || index >= layoutInfo.totalItemsCount) return
        scroll { snapToItemIndexInternal(index, scrollOffset, forceRemeasure = true) }
    }

    /**
     * Snaps to the requested scroll position. Synchronously executes remeasure if [forceRemeasure]
     * is true, and schedules a remeasure if false.
     */
    internal fun snapToItemIndexInternal(index: Int, scrollOffset: Int, forceRemeasure: Boolean) {
        val positionChanged =
            scrollPosition.index != index || scrollPosition.scrollOffset != scrollOffset
        scrollPosition.requestPositionAndForgetLastKnownKey(index, scrollOffset)

        if (forceRemeasure) {
            remeasurement?.forceRemeasure()
        } else {
            measurementScopeInvalidator.invalidateScope()
        }
    }

    /**
     * Call this function to take control of scrolling and gain the ability to send scroll events
     * via [ScrollScope.scrollBy]. All actions that change the logical scroll position must be
     * performed within a [scroll] block.
     */
    override suspend fun scroll(
        scrollPriority: MutatePriority,
        block: suspend ScrollScope.() -> Unit,
    ) {
        if (layoutInfoState.value === EmptyAdaptiveGridMeasureResult) {
            awaitLayoutModifier.waitForFirstLayout()
        }
        scrollableState.scroll(scrollPriority, block)
    }

    override fun dispatchRawDelta(delta: Float): Float = scrollableState.dispatchRawDelta(delta)

    /**
     * Handles scroll delta from user gestures.
     * Called from scrollableState with double inversion: { -onScroll(-it) }
     * This matches LazyList's exact pattern.
     */
    internal fun onScroll(distance: Float): Float {
        // Check direction (same as LazyGrid/LazyList): negative = forward, positive = backward
        if (distance < 0 && !canScrollForward || distance > 0 && !canScrollBackward) {
            return 0f
        }
        executeRequestsInHighPriorityMode = true
        // Use distance directly, no inversion (same as LazyGrid/LazyList)
        scrollToBeConsumed += distance

        // scrollToBeConsumed will be consumed synchronously during the forceRemeasure invocation
        if (abs(scrollToBeConsumed) > 0.5f) {
            val intDelta = scrollToBeConsumed.fastRoundToInt()

            var scrolledLayoutInfo =
                layoutInfoState.value.copyWithScrollDeltaWithoutRemeasure(
                    delta = intDelta,
                    updateAnimations = !hasLookaheadOccurred,
                )
            if (scrolledLayoutInfo != null && this.approachLayoutInfo != null) {
                val scrolledApproachLayoutInfo =
                    approachLayoutInfo?.copyWithScrollDeltaWithoutRemeasure(
                        delta = intDelta,
                        updateAnimations = true,
                    )
                if (scrolledApproachLayoutInfo != null) {
                    approachLayoutInfo = scrolledApproachLayoutInfo
                } else {
                    scrolledLayoutInfo = null
                }
            }

            if (scrolledLayoutInfo != null) {
                applyMeasureResult(
                    result = scrolledLayoutInfo,
                    isLookingAhead = hasLookaheadOccurred,
                    visibleItemsStayedTheSame = true,
                )
                placementScopeInvalidator.invalidateScope()
                // Notify prefetch strategy of scroll (use distance directly, same as LazyGrid)
                with(prefetchStrategy) {
                    prefetchScope.onScroll(distance, layoutInfo)
                }
            } else {
                // forceRemeasure will consume scrollToBeConsumed synchronously during the measure pass
                remeasurement?.forceRemeasure()
                // Also invalidate placement to ensure smooth UI updates during remeasurement
                placementScopeInvalidator.invalidateScope()
                // Notify prefetch strategy of scroll even if remeasure is needed
                with(prefetchStrategy) {
                    prefetchScope.onScroll(distance, layoutInfo)
                }
            }
        }

        // scrollToBeConsumed is already consumed during the forceRemeasure invocation (if remeasure was needed)
        // or during applyMeasureResult (if copyWithScrollDeltaWithoutRemeasure succeeded)
        if (abs(scrollToBeConsumed) <= 0.5f) {
            // We consumed all of it - we'll hold onto the fractional scroll for later, so report
            // that we consumed the whole thing
            return distance
        } else {
            // We did not consume all of it - return the rest to be consumed elsewhere (e.g., nested scrolling)
            val scrollConsumed = distance - scrollToBeConsumed
            scrollToBeConsumed = 0f // We're not consuming the rest, give it back
            return scrollConsumed
        }
    }

    /**
     * Animate (smooth scroll) to the given item.
     *
     * @param index the index to which to scroll. Must be non-negative.
     * @param scrollOffset the offset that the item should end up after the scroll.
     */
    suspend fun animateScrollToItem(index: Int, scrollOffset: Int = 0) {
        if (index < 0 || index >= layoutInfo.totalItemsCount) return
        scroll {
            snapToItemIndexInternal(index, scrollOffset, forceRemeasure = true)
        }
    }

    /** Updates the state with the new calculated scroll position and consumed scroll. */
    internal fun applyMeasureResult(
        result: AdaptiveGridMeasureResult,
        isLookingAhead: Boolean,
        visibleItemsStayedTheSame: Boolean = false,
    ) {
        if (!isLookingAhead && hasLookaheadOccurred) {
            approachLayoutInfo = result
            androidx.compose.runtime.snapshots.Snapshot.withoutReadObservation {
                if (
                    _scrollDeltaBetweenPasses.isActive &&
                    result.firstVisibleItem?.index == scrollPosition.index &&
                    result.firstVisibleItemScrollOffset == scrollPosition.scrollOffset
                ) {
                    _scrollDeltaBetweenPasses.stop()
                }
            }
        } else {
            if (isLookingAhead) {
                hasLookaheadOccurred = true
            }

            canScrollBackward = result.canScrollBackward
            canScrollForward = result.canScrollForward
            scrollToBeConsumed -= result.consumedScroll
            layoutInfoState.value = result

            if (visibleItemsStayedTheSame) {
                scrollPosition.updateScrollOffset(result.firstVisibleItemScrollOffset)
            } else {
                scrollPosition.updateFromMeasureResult(
                    result.firstVisibleItem,
                    result.firstVisibleItemScrollOffset,
                    result.totalItemsCount
                )
                with(prefetchStrategy) {
                    prefetchScope.onVisibleItemsUpdated(layoutInfo)
                }
            }

            if (isLookingAhead) {
                _scrollDeltaBetweenPasses.updateScrollDeltaForApproach(
                    result.scrollBackAmount,
                    result.density,
                    result.coroutineScope,
                )
            }
        }
    }

    internal val scrollDeltaBetweenPasses: Float
        get() = _scrollDeltaBetweenPasses.scrollDeltaBetweenPasses

    private val _scrollDeltaBetweenPasses = AdaptiveGridScrollDeltaBetweenPasses()

    /**
     * When the user provided custom keys for the items we can try to detect when there were items
     * added or removed before our current first visible item and keep this item as the first
     * visible one even given that its index has been changed.
     */
    internal fun updateScrollPositionIfTheFirstItemWasMoved(
        itemProvider: GridLayoutProvider<*>,
        firstItemIndex: Int,
    ): Int = scrollPosition.updateScrollPositionIfTheFirstItemWasMoved(itemProvider, firstItemIndex)

    @Deprecated("Use applyMeasureResult")
    internal fun <T> updateLayoutInfo(
        allItems: List<GridItem<T>>,
        itemPositions: List<ItemPosition>,
        keys: List<Any>,
        scrollOffset: Int,
    ) {
        _itemPositions = itemPositions
        
        val viewportStart = scrollOffset
        val viewportEnd = scrollOffset + viewportHeight
        val visibleItemsInfo = mutableListOf<AdaptiveGridItemInfo>()
        
        itemPositions.forEachIndexed { index, position ->
            val itemTop = position.y
            val itemBottom = position.y + position.height
            
            if (itemBottom >= viewportStart && itemTop <= viewportEnd) {
                val key = keys.getOrNull(index) ?: index
                val itemConfig = allItems.getOrNull(index)?.config
                visibleItemsInfo.add(
                    AdaptiveGridItemInfo(
                        index = index,
                        key = key,
                        offset = itemTop - viewportStart,
                        size = position.height,
                        position = position,
                        layoutType = itemConfig?.type ?: com.lazyadaptivelayout.model.GridItemType.Staggered
                    )
                )
            }
        }
        
        val firstVisibleItem = visibleItemsInfo.firstOrNull()
        val firstVisibleItemScrollOffset = if (firstVisibleItem != null) {
            (viewportStart - firstVisibleItem.position.y).coerceAtLeast(0)
        } else {
            0
        }
        
        // Determine if we can scroll forward
        val canScrollForward = if (itemPositions.isNotEmpty()) {
            val lastItem = itemPositions.lastOrNull()
            lastItem != null && (lastItem.y + lastItem.height) > viewportEnd
        } else {
            false
        }
        
        // Create a measure result to apply
        val measureResult = AdaptiveGridMeasureResult(
            firstVisibleItem = firstVisibleItem,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
            canScrollForward = canScrollForward,
            consumedScroll = 0f,
            measureResult = object : androidx.compose.ui.layout.MeasureResult {
                override val alignmentLines: Map<androidx.compose.ui.layout.AlignmentLine, Int> = emptyMap()
                override val height: Int = viewportHeight
                override val width: Int = 0
                override fun placeChildren() {}
            },
            scrollBackAmount = 0f,
            remeasureNeeded = false,
            coroutineScope = kotlinx.coroutines.CoroutineScope(kotlin.coroutines.EmptyCoroutineContext),
            density = androidx.compose.ui.unit.Density(1f, 1f),
            childConstraints = androidx.compose.ui.unit.Constraints(),
            visibleItemsInfo = visibleItemsInfo,
            viewportStartOffset = viewportStart,
            viewportEndOffset = viewportEnd,
            totalItemsCount = allItems.size,
            viewportSize = viewportHeight,
            orientation = androidx.compose.foundation.gestures.Orientation.Vertical
        )
        
        // Apply the measure result
        applyMeasureResult(measureResult, isLookingAhead = false, visibleItemsStayedTheSame = false)
    }

    /**
     * Checks if a specific item is currently visible in the viewport.
     * 
     * @param index The index of the item to check
     * @return true if the item is visible, false otherwise
     */
    fun isItemVisible(index: Int): Boolean {
        return visibleItemsInfo.any { it.index == index }
    }

    /**
     * Checks if an item is completely visible in the viewport.
     * 
     * @param index The index of the item to check
     * @return true if the item is completely visible, false otherwise
     */
    fun isItemCompletelyVisible(index: Int): Boolean {
        val itemInfo = visibleItemsInfo.find { it.index == index } ?: return false
        return itemInfo.offset >= 0 && itemInfo.offset + itemInfo.size <= viewportHeight
    }

    companion object {
        /**
         * Saver for AdaptiveGridState that preserves scroll position across process death
         * and configuration changes. Mirrors LazyListState.Saver pattern.
         */
        val Saver: Saver<AdaptiveGridState, *> =
            listSaver(
                save = { listOf(it.firstVisibleItemIndex, it.firstVisibleItemScrollOffset) },
                restore = {
                    AdaptiveGridState(
                        firstVisibleItemIndex = it[0] as? Int ?: 0,
                        firstVisibleItemScrollOffset = it[1] as? Int ?: 0,
                    )
                },
            )
    }
} 