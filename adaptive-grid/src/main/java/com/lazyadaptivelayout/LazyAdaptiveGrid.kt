package com.lazyadaptivelayout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.lazy.layout.LazyLayout
import androidx.compose.foundation.lazy.layout.LazyLayoutItemProvider
import androidx.compose.foundation.lazy.layout.LazyLayoutMeasureScope
import androidx.compose.foundation.scrollableArea
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.fastRoundToInt
import com.lazyadaptivelayout.layout.clearMeasurementCaches
import com.lazyadaptivelayout.layout.measureFlattenedGrid
import com.lazyadaptivelayout.layout.notifyDataVersion
import com.lazyadaptivelayout.model.AdaptiveGridItemInfo
import com.lazyadaptivelayout.model.GridItem
import com.lazyadaptivelayout.model.GridItemConfig
import com.lazyadaptivelayout.model.GridItemType
import com.lazyadaptivelayout.model.ItemPosition
import com.lazyadaptivelayout.placement.LazySimplePlacementScope
import com.lazyadaptivelayout.provider.GridLayoutProvider
import com.lazyadaptivelayout.state.AdaptiveGridMeasureResult
import com.lazyadaptivelayout.state.AdaptiveGridState

val LocalGridItemConfig = staticCompositionLocalOf<GridItemConfig> {
    error("LocalGridItemConfig not provided")
}

@Composable
/** Returns a remembered [AdaptiveGridState] suitable for use with LazyAdaptiveGrid. */
fun rememberAdaptiveGridState(): AdaptiveGridState {
    return rememberSaveable(saver = AdaptiveGridState.Saver) { AdaptiveGridState() }
}


@Composable
/** Builds and remembers the unified list of groups defined in the [AdaptiveGridScope] content. */
private fun <T> rememberUnifiedGroups(content: AdaptiveGridScope<T>.() -> Unit): List<UnifiedGroup<T>> {
    return remember(content) {
        val scope = AdaptiveGridScopeImpl<T>()
        scope.content()
        scope.unifiedGroups
    }
}

internal data class EnrichedGridItem<T>(
    val item: T,
    val config: GridItemConfig,
    val group: Any,
    val groupColumns: Int,
    val groupItemIndex: Int,
    val key: Any,
    val contentType: Any?
)

@Composable
/** Flattens groups into per-item metadata used by measurement and composition. */
private fun <T> rememberEnrichedItems(
    unifiedGroups: List<UnifiedGroup<T>>,
    density: Density
): List<EnrichedGridItem<T>> {
    return remember(unifiedGroups, density) {
        val enrichedItems = mutableListOf<EnrichedGridItem<T>>()
        for (unifiedGroup in unifiedGroups) {
            when (unifiedGroup) {
                is UnifiedGroup.Regular -> {
                    val group = unifiedGroup.group
                    for ((itemIndex, item) in group.items.withIndex()) {
                        val calculatedHeight = with(density) { group.height.invoke(item, itemIndex).toPx().toInt() }
                        val config = GridItemConfig(
                            type = group.type,
                            height = if (calculatedHeight > 0) calculatedHeight else null,
                            edgeSpacing = group.edgeSpacing,
                            contentPadding = group.contentPadding
                        )
                        enrichedItems.add(
                            EnrichedGridItem(
                                item = item,
                                config = config,
                                group = group,
                                groupColumns = group.columns,
                                groupItemIndex = itemIndex,
                                key = group.key.invoke(item, itemIndex),
                                contentType = group.contentType.invoke(item, itemIndex)
                            )
                        )
                    }
                }
                is UnifiedGroup.Custom -> {
                    val customGroup = unifiedGroup.group
                    for ((itemIndex, customItem) in customGroup.customItems.withIndex()) {
                        val calculatedHeight = if (customItem.height != null) {
                            with(density) { customItem.height.toPx().toInt() }
                        } else {
                            with(density) { customGroup.height.invoke(customItem.item, itemIndex).toPx().toInt() }
                        }
                        val config = GridItemConfig(
                            type = GridItemType.Custom(customItem.span),
                            height = if (calculatedHeight > 0) calculatedHeight else null,
                            edgeSpacing = customGroup.edgeSpacing,
                            contentPadding = customGroup.contentPadding
                        )
                        enrichedItems.add(
                            EnrichedGridItem(
                                item = customItem.item,
                                config = config,
                                group = customGroup,
                                groupColumns = customGroup.columns,
                                groupItemIndex = itemIndex,
                                key = customGroup.key.invoke(customItem.item, itemIndex),
                                contentType = customGroup.contentType.invoke(customItem.item, itemIndex)
                            )
                        )
                    }
                }
            }
        }
        enrichedItems
    }
}

@Composable
/** Creates a [GridLayoutProvider] backed by the current enriched items. */
private fun <T> rememberLayoutProvider(
    enrichedItems: List<EnrichedGridItem<T>>
): GridLayoutProvider<T> {
    return remember(enrichedItems) {
        createLayoutProviderFromEnrichedItems(enrichedItems)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
/**
 * A high-performance, scrollable adaptive grid.
 *
 * @param modifier Optional [Modifier] for the container.
 * @param state Scroll and visibility state for this grid.
 * @param bufferSize Number of offscreen items to measure on each side of the viewport.
 * @param content Grid content defined via [AdaptiveGridScope].
 */
fun <T> LazyAdaptiveGrid(
    modifier: Modifier = Modifier,
    state: AdaptiveGridState = rememberAdaptiveGridState(),
    bufferSize: Int = 2,
    reverseDirection: Boolean = false,
    scrollEnable: Boolean = true,
    content: AdaptiveGridScope<T>.() -> Unit
) {

    val density = LocalDensity.current

    val unifiedGroups = rememberUnifiedGroups(content)
    val enrichedItems = rememberEnrichedItems(unifiedGroups, density)

    val layoutProvider = rememberLayoutProvider(enrichedItems)

    val dataVersion = remember(enrichedItems) {
        var acc = 1
        for (e in enrichedItems) {
            acc = 31 * acc + (e.key.hashCode() ?: 0)
        }
        31 * acc + enrichedItems.size
    }

    val measuredHeights = remember(dataVersion) {
        mutableMapOf<Int, Int>()
    }

    val positionsState = remember {
        mutableStateOf<List<ItemPosition>>(emptyList())
    }

    val lastDataVersion = remember {
        mutableStateOf(dataVersion)
    }

    val widthCache = remember {
        mutableMapOf<String, Int>()
    }

    val positionCache = remember {
        mutableMapOf<String, List<ItemPosition>>()
    }

    val getCachedWidth = remember {
        { config: GridItemConfig, groupColumns: Int, maxWidth: Int ->
            val key = "${config.type}_${groupColumns}_${maxWidth}"
            widthCache.getOrPut(key) {
                when (val gridType = config.type) {
                    is GridItemType.FullWidth -> maxWidth
                    is GridItemType.Custom -> {
                        val spans = minOf(gridType.spans, groupColumns)
                        (maxWidth / groupColumns) * spans
                    }
                    else -> maxWidth / groupColumns
                }
            }
        }
    }

    val lazyLayoutItemProvider = remember(layoutProvider, enrichedItems) {
        object : LazyLayoutItemProvider {
            override val itemCount: Int = layoutProvider.itemCount

            @Composable
            override fun Item(index: Int, key: Any) {
                val config = enrichedItems[index].config
                val columnIndex = positionsState.value.getOrNull(index)?.columnIndex ?: 0
                CompositionLocalProvider(LocalGridItemConfig provides config) {
                    layoutProvider.ItemContent(
                        item = enrichedItems[index].item,
                        index = index,
                        columnIndex = columnIndex,
                        columns = enrichedItems[index].groupColumns
                    )
                }
            }
            override fun getKey(index: Int): Any = enrichedItems[index].key
            override fun getContentType(index: Int): Any? = enrichedItems[index].contentType
        }
    }

    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(dataVersion) {
        if (dataVersion != lastDataVersion.value) {
            notifyDataVersion(dataVersion)
            lastDataVersion.value = dataVersion
            measuredHeights.clear()
        }
    }

    val measurePolicy = remember<LazyLayoutMeasureScope.(Constraints) -> MeasureResult>(
        state,
        layoutProvider,
        enrichedItems,
        dataVersion,
        density,
        bufferSize,
        getCachedWidth,
        coroutineScope,
    ) {
        { constraints ->
            state.measurementScopeInvalidator.attachToScope()
            state.viewportHeight = constraints.maxHeight

            val hasLookaheadOccurred = state.hasLookaheadOccurred || isLookingAhead
            val itemProvider = layoutProvider

            val firstVisibleItemIndex: Int
            val firstVisibleItemScrollOffset: Int
            Snapshot.withoutReadObservation {
                firstVisibleItemIndex =
                    state.updateScrollPositionIfTheFirstItemWasMoved(
                        itemProvider,
                        state.firstVisibleItemIndex,
                    )
                firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset
            }

            val scrollToBeConsumed =
                if (isLookingAhead || !hasLookaheadOccurred) {
                    state.scrollToBeConsumed
                } else {
                    state.scrollDeltaBetweenPasses
                }

            val itemsCount = itemProvider.itemCount

            var currentFirstItemIndex = firstVisibleItemIndex
            var currentFirstItemScrollOffset = firstVisibleItemScrollOffset
            
            if (currentFirstItemIndex >= itemsCount) {
                currentFirstItemIndex = (itemsCount - 1).coerceAtLeast(0)
                currentFirstItemScrollOffset = 0
            }

            // scrollToBeConsumed: negative = forward/down, positive = backward/up
            var scrollDelta = scrollToBeConsumed.fastRoundToInt()
            
            if (currentFirstItemIndex == 0 && scrollDelta > 0) {
                val maxBackwardScroll = currentFirstItemScrollOffset
                if (scrollDelta > maxBackwardScroll) {
                    scrollDelta = maxBackwardScroll
                }
            }
            
            currentFirstItemScrollOffset -= scrollDelta
            if (currentFirstItemScrollOffset < 0) {
                scrollDelta += currentFirstItemScrollOffset
                currentFirstItemScrollOffset = 0
            }

            val lastMeasuredIndex = state._itemPositions.indexOfLast { it.height > 0 }
            val rangeStartIndex = if (lastMeasuredIndex >= 0) {
                minOf(currentFirstItemIndex, lastMeasuredIndex)
            } else {
                currentFirstItemIndex
            }
            val visibleRange = calculateVisibleRangeFromRelativePosition(
                firstVisibleItemIndex = rangeStartIndex,
                firstVisibleItemScrollOffset = currentFirstItemScrollOffset,
                itemCount = itemsCount,
                bufferSize = bufferSize,
                viewportHeight = constraints.maxHeight,
                lastMeasuredIndex = lastMeasuredIndex,
            )

            val (placeables, itemHeights, heightChanges) = measureGridItems(
                layoutProvider = layoutProvider,
                enrichedItems = enrichedItems,
                visibleRange = visibleRange,
                measuredHeights = measuredHeights,
                constraints = constraints,
                measurer = { index, constraints -> compose(index).map { it.measure(constraints) } },
                bufferSize = bufferSize,
                getCachedWidth = getCachedWidth
            )

            val currentPositions = measureFlattenedGrid(enrichedItems, itemHeights, constraints, density)
            positionsState.value = currentPositions
            state._itemPositions = currentPositions

            currentPositions.maxOfOrNull { it.y + it.height } ?: 0

            val firstItemPosition = currentPositions.getOrNull(currentFirstItemIndex)
            val viewportStart = if (firstItemPosition != null) {
                firstItemPosition.y + currentFirstItemScrollOffset
            } else {
                0
            }
            val viewportEnd = viewportStart + constraints.maxHeight
            val visibleItemsInfo = mutableListOf<AdaptiveGridItemInfo>()

            currentPositions.forEachIndexed { index, position ->
                val itemTop = position.y
                val itemBottom = position.y + position.height

                if (itemBottom >= viewportStart && itemTop <= viewportEnd) {
                    val key = enrichedItems.getOrNull(index)?.key ?: index
                    visibleItemsInfo.add(
                        AdaptiveGridItemInfo(
                            index = index,
                            key = key,
                            offset = itemTop - viewportStart,
                            size = position.height,
                            position = position,
                            layoutType = enrichedItems.getOrNull(index)?.config?.type
                                ?: GridItemType.Staggered
                        )
                    )
                }
            }

            val firstVisibleItem = if (currentFirstItemIndex in 0 until itemsCount) {
                val firstItemPosition = currentPositions.getOrNull(currentFirstItemIndex)
                if (firstItemPosition != null && firstItemPosition.height > 0) {
                    val key = enrichedItems.getOrNull(currentFirstItemIndex)?.key ?: currentFirstItemIndex
                    AdaptiveGridItemInfo(
                        index = currentFirstItemIndex,
                        key = key,
                        offset = firstItemPosition.y - viewportStart,
                        size = firstItemPosition.height,
                        position = firstItemPosition,
                        layoutType = enrichedItems.getOrNull(currentFirstItemIndex)?.config?.type
                            ?: GridItemType.Staggered
                    )
                } else {
                    // If currentFirstItemIndex position is not available, use first visible item
                    visibleItemsInfo.firstOrNull()
                }
            } else {
                // If currentFirstItemIndex is out of bounds, use first visible item
                visibleItemsInfo.firstOrNull()
            }

            val finalFirstVisibleItemScrollOffset = currentFirstItemScrollOffset

            val consumedScroll = scrollDelta.toFloat()
            val scrollBackAmount = 0f // Can be calculated if needed - for now simplified

            val canScrollForward = if (itemsCount > 0) {
                val lastMeasuredIndex = currentPositions.indexOfLast { it.height > 0 }
                val hasMoreItems = lastMeasuredIndex >= 0 && lastMeasuredIndex < itemsCount - 1
                if (hasMoreItems) {
                    true
                } else if (lastMeasuredIndex >= 0) {
                    val lastItem = currentPositions[lastMeasuredIndex]
                    lastItem.y + lastItem.height > viewportEnd
                } else {
                    itemsCount > 0
                }
            } else {
                false
            }

            val measureResult = layout(constraints.maxWidth, constraints.maxHeight) {
                LazySimplePlacementScope(
                    allPlaceables = placeables,
                    positions = currentPositions,
                    parentLayoutDirection = LayoutDirection.Ltr,
                    parentWidth = constraints.maxWidth,
                    visibleRange = (firstVisibleItem?.index ?: 0)..(visibleItemsInfo.lastOrNull()?.index ?: 0),
                    scrollOffset = finalFirstVisibleItemScrollOffset,
                    parentHeight = constraints.maxHeight
                ).placeChildren()
            }

            val adaptiveGridMeasureResult = AdaptiveGridMeasureResult(
                firstVisibleItem = firstVisibleItem,
                firstVisibleItemScrollOffset = finalFirstVisibleItemScrollOffset,
                canScrollForward = canScrollForward,
                consumedScroll = consumedScroll,
                measureResult = measureResult,
                scrollBackAmount = scrollBackAmount,
                remeasureNeeded = false,
                coroutineScope = coroutineScope,
                density = density,
                childConstraints = Constraints.fixedWidth(constraints.maxWidth),
                visibleItemsInfo = visibleItemsInfo,
                viewportStartOffset = viewportStart,
                viewportEndOffset = viewportEnd,
                totalItemsCount = itemsCount,
                viewportSize = constraints.maxHeight,
                orientation = Orientation.Vertical
            )
            state.applyMeasureResult(adaptiveGridMeasureResult, isLookingAhead)

            measureResult
        }
    }

    LazyLayout(
        itemProvider = { lazyLayoutItemProvider },
        measurePolicy = measurePolicy,
        prefetchState = state.prefetchState,
        modifier = modifier
            .clipToBounds()
            .then(state.remeasurementModifier)
            .then(state.awaitLayoutModifier)
            .scrollableArea(
                state = state,
                orientation = Orientation.Vertical,
                enabled = scrollEnable,
                reverseScrolling = reverseDirection,
                interactionSource = state.internalInteractionSource,
            ),
    )

    DisposableEffect(Unit) {
        onDispose {
            widthCache.clear()
            positionCache.clear()
            measuredHeights.clear()
            positionsState.value = emptyList()

            clearMeasurementCaches()
        }
    }
}

/** Returns a conservative index range to measure around the current scroll position. */
private fun calculateVisibleRangeFromRelativePosition(
    firstVisibleItemIndex: Int,
    firstVisibleItemScrollOffset: Int,
    itemCount: Int,
    bufferSize: Int,
    viewportHeight: Int,
    lastMeasuredIndex: Int = -1,
): IntRange {
    if (itemCount == 0) return IntRange.EMPTY
    
    val start = maxOf(0, firstVisibleItemIndex - bufferSize)
    
    val estimatedItemsForViewport = (viewportHeight / 100).coerceAtLeast(20)
    val forwardBuffer = maxOf(
        bufferSize * 10,
        estimatedItemsForViewport * 3
    )
    
    val endFromFirst = firstVisibleItemIndex + forwardBuffer
    val endFromLast = if (lastMeasuredIndex >= 0) {
        lastMeasuredIndex + forwardBuffer
    } else {
        endFromFirst
    }
    val end = minOf(itemCount - 1, maxOf(endFromFirst, endFromLast))
    
    return start..end
}

@OptIn(ExperimentalFoundationApi::class)
/** Measures items in the visible window (plus buffer) and returns placeables and heights. */
internal fun <T> measureGridItems(
    layoutProvider: GridLayoutProvider<T>,
    enrichedItems: List<EnrichedGridItem<T>>,
    visibleRange: IntRange,
    measuredHeights: MutableMap<Int, Int>,
    constraints: Constraints,
    measurer: (Int, Constraints) -> List<Placeable>,
    bufferSize: Int,
    getCachedWidth: (GridItemConfig, Int, Int) -> Int,
): Triple<List<Placeable?>, List<Int>, List<Pair<Int, Int>>> {

    val itemCount = layoutProvider.itemCount
    val placeables = MutableList<Placeable?>(itemCount) { null }
    val itemHeights = MutableList(itemCount) { 0 }
    val heightChanges = mutableListOf<Pair<Int, Int>>()

    if (measuredHeights.isNotEmpty()) {
        val heightEntries = measuredHeights.entries
        for ((idx, h) in heightEntries) {
            if (idx in 0 until itemCount && h > 0) {
                itemHeights[idx] = h
            }
        }
    }

    if (itemCount == 0) {
        return Triple(emptyList(), emptyList(), emptyList())
    }

    val maxIndex = itemCount - 1
    val safeStart = maxOf(0, visibleRange.first - bufferSize).coerceAtMost(maxIndex)
    val safeEnd = minOf(maxIndex, visibleRange.last + bufferSize)

    val constraintsCache = mutableMapOf<Pair<GridItemConfig, Int>, Constraints>()
    
    for (index in safeStart..safeEnd) {
        val config = enrichedItems[index].config
        val groupColumns = enrichedItems[index].groupColumns
        
        val isVisible = index in visibleRange

        val itemWidth = getCachedWidth(config, groupColumns, constraints.maxWidth)

        val constraintKey = config to itemWidth
        val heightConstraint = constraintsCache.getOrPut(constraintKey) {
            Constraints.fixedWidth(itemWidth)
        }

        val cachedHeight = measuredHeights[index]
        val itemHeight = if (cachedHeight != null && cachedHeight > 0 && !isVisible) {
            cachedHeight
        } else {
            val measurementResult = measurer(index, heightConstraint)
            if (measurementResult.isNotEmpty()) {
                val placeable = measurementResult.first()
                if (isVisible) {
                    placeables[index] = placeable
                }
                val newHeight = placeable.height
                val oldHeight = measuredHeights[index] ?: 0
                if (newHeight > 0) {
                    measuredHeights[index] = newHeight
                }
                if (newHeight != oldHeight) {
                    heightChanges.add(index to (newHeight - oldHeight))
                }
                newHeight
            } else {
                0
            }
        }

        if (itemHeight > 0) {
            itemHeights[index] = itemHeight
        }
    }
    
    return Triple(placeables, itemHeights, heightChanges)
}

private fun <T> createLayoutProviderFromEnrichedItems(
    enrichedItems: List<EnrichedGridItem<T>>
): GridLayoutProvider<T> {
    return object : GridLayoutProvider<T> {
        override val items = enrichedItems.map { GridItem(it.item, it.config) }

        override fun getItemConfig(index: Int): GridItemConfig = enrichedItems[index].config

        override fun getItemKey(index: Int): String = enrichedItems[index].key.toString()

        override fun getContentType(index: Int): Any? = enrichedItems[index].contentType

        @Composable
        override fun ItemContent(item: T, index: Int, columnIndex: Int, columns: Int) {
            when (val group = enrichedItems[index].group) {
                is GridGroup<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    (group as GridGroup<T>).content.invoke(item, index, columnIndex, columns)
                }
                is CustomGridGroup<*> -> {
                    @Suppress("UNCHECKED_CAST")
                    val customGroup = group as CustomGridGroup<T>
                    val groupItemIndex = enrichedItems[index].groupItemIndex
                    val customItem = customGroup.customItems[groupItemIndex]
                    customItem.content.invoke(item, index, columnIndex, columns)
                }
            }
        }
    }
}
