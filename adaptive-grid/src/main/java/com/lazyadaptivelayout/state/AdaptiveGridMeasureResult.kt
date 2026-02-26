package com.lazyadaptivelayout.state

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.lazyadaptivelayout.model.AdaptiveGridItemInfo
import com.lazyadaptivelayout.model.AdaptiveGridLayoutInfo
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.math.abs
import kotlinx.coroutines.CoroutineScope

/** The result of the measure pass for adaptive grid layout. */
internal class AdaptiveGridMeasureResult(
    // properties defining the scroll position:
    /** The new first visible item. */
    val firstVisibleItem: AdaptiveGridItemInfo?,
    /** The new value for [AdaptiveGridState.firstVisibleItemScrollOffset]. */
    val firstVisibleItemScrollOffset: Int,
    /** True if there is some space available to continue scrolling in the forward direction. */
    val canScrollForward: Boolean,
    /** The amount of scroll consumed during the measure pass. */
    val consumedScroll: Float,
    /** MeasureResult defining the layout. */
    private val measureResult: MeasureResult,
    /** The amount of scroll-back that happened due to reaching the end of the list. */
    val scrollBackAmount: Float,
    /** True when extra remeasure is required. */
    val remeasureNeeded: Boolean,
    /** Scope for animations. */
    val coroutineScope: CoroutineScope,
    /** Density of the last measure. */
    val density: Density,
    /** Constraints used to measure children. */
    val childConstraints: Constraints,
    // properties representing the info needed for AdaptiveGridLayoutInfo:
    /** see [AdaptiveGridLayoutInfo.visibleItemsInfo] */
    val visibleItemsInfo: List<AdaptiveGridItemInfo>,
    /** see [AdaptiveGridLayoutInfo.viewportStartOffset] */
    val viewportStartOffset: Int,
    /** see [AdaptiveGridLayoutInfo.viewportEndOffset] */
    val viewportEndOffset: Int,
    /** see [AdaptiveGridLayoutInfo.totalItemsCount] */
    val totalItemsCount: Int,
    val viewportSize: Int,
    val orientation: Orientation = Orientation.Vertical
) : MeasureResult by measureResult {

    private val layoutInfo: AdaptiveGridLayoutInfo = AdaptiveGridLayoutInfo(
        visibleItemsInfo = visibleItemsInfo,
        viewportStartOffset = viewportStartOffset,
        viewportEndOffset = viewportEndOffset,
        totalItemsCount = totalItemsCount,
        viewportSize = viewportSize,
        orientation = orientation
    )

    fun toLayoutInfo(): AdaptiveGridLayoutInfo = layoutInfo

    val canScrollBackward: Boolean
        get() = (firstVisibleItem?.index ?: 0) != 0 || firstVisibleItemScrollOffset != 0

    /**
     * Creates a new layout info with applying a scroll [delta] for this layout info. In some cases
     * we can apply small scroll deltas by just changing the offsets for each [visibleItemsInfo].
     * But we can only do so if after applying the delta we would not need to compose a new item or
     * dispose an item which is currently visible. In this case this function will not apply the
     * [delta] and return null.
     *
     * @return new layout info if we can safely apply a passed scroll [delta] to this layout info.
     *   If new layout info is returned, only the placement phase is needed to apply new offsets.
     *   If null is returned, it means we have to rerun the full measure phase to apply the [delta].
     */
    fun copyWithScrollDeltaWithoutRemeasure(
        delta: Int,
        updateAnimations: Boolean,
    ): AdaptiveGridMeasureResult? {
        if (
            remeasureNeeded ||
            visibleItemsInfo.isEmpty() ||
            firstVisibleItem == null
        ) {
            return null
        }

        val canApply = abs(delta) < viewportSize * 20
        return if (canApply) {
            val updatedVisibleItems = visibleItemsInfo.map { item ->
                AdaptiveGridItemInfo(
                    index = item.index,
                    key = item.key,
                    offset = item.offset - delta,
                    size = item.size,
                    position = item.position.copy(y = item.position.y - delta),
                    layoutType = item.layoutType
                )
            }
            val rawOffset = firstVisibleItemScrollOffset - delta
            val newScrollOffset = rawOffset.coerceAtLeast(0)
            AdaptiveGridMeasureResult(
                firstVisibleItem = firstVisibleItem,
                firstVisibleItemScrollOffset = newScrollOffset,
                canScrollForward =
                    canScrollForward ||
                        delta > 0,
                consumedScroll = delta.toFloat(),
                measureResult = measureResult,
                scrollBackAmount = scrollBackAmount,
                remeasureNeeded = remeasureNeeded,
                coroutineScope = coroutineScope,
                density = density,
                childConstraints = childConstraints,
                visibleItemsInfo = updatedVisibleItems,
                viewportStartOffset = viewportStartOffset,
                viewportEndOffset = viewportEndOffset,
                totalItemsCount = totalItemsCount,
                viewportSize = viewportSize,
                orientation = orientation
            )
        } else {
            null
        }
    }
}

/** Empty measure result used as initial value. */
internal val EmptyAdaptiveGridMeasureResult = AdaptiveGridMeasureResult(
    firstVisibleItem = null,
    firstVisibleItemScrollOffset = 0,
    canScrollForward = false,
    consumedScroll = 0f,
    measureResult = object : MeasureResult {
        override val alignmentLines: Map<androidx.compose.ui.layout.AlignmentLine, Int> = emptyMap()
        override val height: Int = 0
        override val width: Int = 0
        override fun placeChildren() {}
    },
    scrollBackAmount = 0f,
    remeasureNeeded = false,
    coroutineScope = CoroutineScope(EmptyCoroutineContext),
    density = Density(1f, 1f),
    childConstraints = Constraints(),
    visibleItemsInfo = emptyList(),
    viewportStartOffset = 0,
    viewportEndOffset = 0,
    totalItemsCount = 0,
    viewportSize = 0,
    orientation = Orientation.Vertical
)

