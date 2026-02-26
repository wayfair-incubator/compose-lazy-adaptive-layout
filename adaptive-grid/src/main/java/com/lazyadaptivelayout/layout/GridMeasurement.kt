package com.lazyadaptivelayout.layout


import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.lazyadaptivelayout.EnrichedGridItem
import com.lazyadaptivelayout.model.GridItemConfig
import com.lazyadaptivelayout.model.GridItemType
import com.lazyadaptivelayout.model.ItemPosition
import kotlin.math.min
import kotlin.math.max

/** Returns an approximate "padding-only" height for an item. */
private fun calculateExpectedPaddingHeight(config: GridItemConfig, density: Density): Int {
    val topPadding = with(density) { config.edgeSpacing.top.toPx().toInt() }
    val bottomPadding = with(density) { config.edgeSpacing.bottom.toPx().toInt() }
    val contentTopPadding = with(density) { config.contentPadding.vertical.toPx().toInt() }

    return topPadding + bottomPadding + (contentTopPadding / 2 + 1)
}

private var cachedPositions: List<ItemPosition>? = null
private var cachedHeights: IntArray? = null
private var cachedWidth: Int = -1
private var cachedItemCount: Int = -1
private var cachedDataVersion: Int = Int.MIN_VALUE

/** Invalidates cached positions/heights when backing data changes. */
internal fun notifyDataVersion(version: Int) {
    if (cachedDataVersion != version) {
        cachedDataVersion = version
        cachedPositions = null
        cachedHeights = null
        cachedWidth = -1
        cachedItemCount = -1
    }
}

/** Clears internal measurement caches. */
internal fun clearMeasurementCaches() {
    cachedPositions = null
    cachedHeights = null
    cachedWidth = -1
    cachedItemCount = -1
    cachedDataVersion = Int.MIN_VALUE
}

/**
 * Measures and positions items in a pre-flattened grid layout.
 * Caches intermediate results to reduce churn between passes.
 */
internal fun <T> measureFlattenedGrid(
    enrichedItems: List<EnrichedGridItem<T>>,
    allItemHeights: List<Int>,
    constraints: Constraints,
    density: Density
): List<ItemPosition> {
    val itemCount = enrichedItems.size
    val zeroPositions = MutableList(itemCount) { ItemPosition(0, 0, 0, 0, 0) }
    if (itemCount == 0) return zeroPositions

    val width = constraints.maxWidth
    if (cachedWidth != width || cachedItemCount != itemCount) {
        cachedPositions = null
        cachedHeights = null
        cachedWidth = width
        cachedItemCount = itemCount
    }

    var lastMeasuredIndex = -1
    for (i in itemCount - 1 downTo 0) {
        if (i < allItemHeights.size && allItemHeights[i] > 0) {
            lastMeasuredIndex = i
            break
        }
    }

    val basePositions = (cachedPositions?.takeIf { it.size == itemCount } ?: zeroPositions)
    val positions = basePositions.toMutableList()

    if (lastMeasuredIndex < 0) {
        cachedPositions = positions
        cachedHeights = IntArray(itemCount)
        return positions
    }

    val maxPossibleColumns = enrichedItems.maxOfOrNull {
        when (val type = it.config.type) {
            is GridItemType.Custom -> type.spans
            is GridItemType.FullWidth -> it.groupColumns
            is GridItemType.Staggered -> it.groupColumns
            is GridItemType.Uniform -> it.groupColumns
        }
    } ?: 1

    val globalColumnHeights = IntArray(maxPossibleColumns)

    var currentRowStartIndex = 0
    var currentRowType: GridItemType? = null
    var currentRowColumns = 0
    var currentRowWidth = 0
    var currentRowItemCount = 0
    var currentRowColumnHeights = IntArray(0)

    var index = 0
    while (index <= lastMeasuredIndex) {
        val currentItem = enrichedItems[index]
        val currentItemHeight = allItemHeights.getOrElse(index) { 0 }

        if (currentItemHeight > 0) {
            val expectedPaddingHeight = calculateExpectedPaddingHeight(currentItem.config, density)
            if (currentItemHeight <= expectedPaddingHeight) {
                positions[index] = ItemPosition(0, 0, 0, 0, 0)
                index++
                continue
            }
        }

        val previousItem = if (index > 0) enrichedItems[index - 1] else null

        if (shouldStartNewRow(
                currentItem,
                previousItem,
                currentRowType,
                currentRowColumns,
                currentRowWidth,
                currentRowItemCount,
                maxPossibleColumns
            )
        ) {
            if (currentRowType != null) {
                finalizeCurrentRow<T>(
                    positions,
                    currentRowStartIndex,
                    index - 1,
                    currentRowColumnHeights,
                    globalColumnHeights
                )
            }

            currentRowStartIndex = index
            currentRowType = currentItem.config.type
            currentRowColumns = getColumnCount(currentItem, maxPossibleColumns)
            currentRowWidth = 0
            currentRowItemCount = 0

            val maxGlobalHeight = globalColumnHeights.maxOrNull() ?: 0
            currentRowColumnHeights = IntArray(currentRowColumns) { maxGlobalHeight }
        }

        if (currentItem.config.type is GridItemType.Uniform) {
            currentRowItemCount++
        }

        positionItemInCurrentRow(
            currentItem,
            index,
            allItemHeights,
            positions,
            currentRowColumnHeights,
            currentRowColumns,
            currentRowWidth,
            currentRowItemCount,
            constraints,
            maxPossibleColumns
        )

        currentRowWidth = updateRowState(currentItem, currentRowWidth, constraints)

        index++
    }

    if (currentRowType != null) {
        finalizeCurrentRow<T>(
            positions,
            currentRowStartIndex,
            index - 1,
            currentRowColumnHeights,
            globalColumnHeights
        )
    }

    cachedPositions = positions.toList()
    val newHeights = (cachedHeights ?: IntArray(itemCount))
    for (i in 0..lastMeasuredIndex) {
        if (i < allItemHeights.size) newHeights[i] = allItemHeights[i]
    }
    cachedHeights = newHeights

    return positions
}

/**
 * Check if two grid types are the same (comparing content, not object references)
 */
private fun <T> isSameGridType(currentItem: EnrichedGridItem<T>, previousItem: EnrichedGridItem<T>?, currentRowType: GridItemType?): Boolean {
    if (previousItem == null || currentRowType == null) return false
    
    val currentType = currentItem.config.type
    val previousType = previousItem.config.type
    
    return when {
        currentType is GridItemType.Uniform && previousType is GridItemType.Uniform -> {
            currentItem.groupColumns == previousItem.groupColumns
        }
        currentType is GridItemType.Staggered && previousType is GridItemType.Staggered -> {
            currentItem.groupColumns == previousItem.groupColumns
        }
        currentType is GridItemType.Custom && previousType is GridItemType.Custom -> true
        currentType is GridItemType.FullWidth && previousType is GridItemType.FullWidth -> true
        else -> false
    }
}

/**
 * SLIDING WINDOW: Check if we need to start a new row
 */
private fun <T> shouldStartNewRow(
    currentItem: EnrichedGridItem<T>,
    previousItem: EnrichedGridItem<T>?,
    currentRowType: GridItemType?,
    currentRowColumns: Int,
    currentRowWidth: Int,
    currentRowItemCount: Int,
    maxPossibleColumns: Int
): Boolean {
    if (previousItem == null) {
        return true
    }
    
    if (!isSameGridType(currentItem, previousItem, currentRowType)) {
        return true
    }
    
    val currentItemColumns = getColumnCount(currentItem, maxPossibleColumns)
    if (currentItemColumns != currentRowColumns) {
        return true
    }
    
    if (!canFitInCurrentRow(currentItem, currentRowWidth, currentRowColumns, currentRowItemCount)) {
        return true
    }
    
    return false
}

/**
 * Get the column count needed for an item type
 */
private fun <T> getColumnCount(item: EnrichedGridItem<T>, maxPossibleColumns: Int): Int {
    return when (item.config.type) {
        is GridItemType.Custom -> maxPossibleColumns
        is GridItemType.FullWidth -> 1
        is GridItemType.Staggered -> item.groupColumns
        is GridItemType.Uniform -> item.groupColumns
    }
}

/**
 * Check if item can fit in current row
 */
private fun <T> canFitInCurrentRow(
    item: EnrichedGridItem<T>,
    currentRowWidth: Int,
    currentRowColumns: Int,
    currentRowItemCount: Int,
): Boolean {
    return when (item.config.type) {
        is GridItemType.Staggered -> true
        is GridItemType.FullWidth -> currentRowWidth == 0
        is GridItemType.Uniform -> currentRowItemCount < currentRowColumns
        is GridItemType.Custom -> true
    }
}

/**
 * Position item in current row based on its type
 */
private fun <T> positionItemInCurrentRow(
    item: EnrichedGridItem<T>,
    index: Int,
    allItemHeights: List<Int>,
    positions: MutableList<ItemPosition>,
    currentRowColumnHeights: IntArray,
    currentRowColumns: Int,
    currentRowWidth: Int,
    currentRowItemCount: Int,
    constraints: Constraints,
    maxPossibleColumns: Int
) {
    when (item.config.type) {
        is GridItemType.Staggered -> {
            positionStaggeredItem(item, index, allItemHeights, positions, currentRowColumnHeights, currentRowColumns, constraints, maxPossibleColumns)
        }
        is GridItemType.Uniform -> {
            positionUniformItem(item, index, allItemHeights, positions, currentRowColumnHeights, currentRowColumns, currentRowWidth, currentRowItemCount, constraints, maxPossibleColumns)
        }
        is GridItemType.FullWidth -> {
            positionFullWidthItem(item, index, allItemHeights, positions, currentRowColumnHeights, currentRowColumns, constraints, maxPossibleColumns)
        }
        is GridItemType.Custom -> {
            positionCustomItem(item, index, allItemHeights, positions, currentRowColumnHeights, currentRowColumns, currentRowWidth, constraints, maxPossibleColumns)
        }
    }
}

/**
 * Update row state after positioning item
 */
private fun <T> updateRowState(
    item: EnrichedGridItem<T>,
    currentRowWidth: Int,
    constraints: Constraints
): Int {
    return when (item.config.type) {
        is GridItemType.Uniform -> currentRowWidth + 1
        is GridItemType.Staggered -> currentRowWidth
        is GridItemType.Custom -> currentRowWidth
        is GridItemType.FullWidth -> constraints.maxWidth
    }
}

/**
 * Finalize current row and update global column heights
 */
private fun <T> finalizeCurrentRow(
    positions: MutableList<ItemPosition>,
    startIndex: Int,
    endIndex: Int,
    currentRowColumnHeights: IntArray,
    globalColumnHeights: IntArray,
) {
    for (col in 0 until minOf(currentRowColumnHeights.size, globalColumnHeights.size)) {
        globalColumnHeights[col] = maxOf(globalColumnHeights[col], currentRowColumnHeights[col])
    }
    
    val rowItems = positions.subList(startIndex, endIndex + 1)
    val hasUniformItems = rowItems.any { position -> 
        rowItems.count { it.y == position.y } > 1
    }
    
    if (hasUniformItems) {
        val maxItemHeight = rowItems.maxOfOrNull { it.height } ?: 0
        val rowStartY = rowItems.firstOrNull()?.y ?: 0
        val rowEndY = rowStartY + maxItemHeight
        
        for (col in 0 until currentRowColumnHeights.size) {
            currentRowColumnHeights[col] = rowEndY
        }
        
        for (col in 0 until minOf(currentRowColumnHeights.size, globalColumnHeights.size)) {
            globalColumnHeights[col] = maxOf(globalColumnHeights[col], rowEndY)
        }
    }
}

/**
 * Position staggered item using shortest column algorithm
 */
private fun <T> positionStaggeredItem(
    item: EnrichedGridItem<T>,
    index: Int,
    allItemHeights: List<Int>,
    positions: MutableList<ItemPosition>,
    currentRowColumnHeights: IntArray,
    currentRowColumns: Int,
    constraints: Constraints,
    maxPossibleColumns: Int
) {
    val itemHeight = allItemHeights[index]
    val columnWidth = constraints.maxWidth / currentRowColumns
    
    // Find shortest column
    var shortestColumn = 0
    var minHeight = currentRowColumnHeights[0]
    for (col in 1 until currentRowColumns) {
        if (currentRowColumnHeights[col] < minHeight) {
            minHeight = currentRowColumnHeights[col]
            shortestColumn = col
        }
    }
    
    val x = shortestColumn * columnWidth
    val y = currentRowColumnHeights[shortestColumn]
    
    positions[index] = ItemPosition(x, y, columnWidth, itemHeight, shortestColumn)
    currentRowColumnHeights[shortestColumn] = y + itemHeight
}

/**
 * Position uniform item in current row
 */
private fun <T> positionUniformItem(
    item: EnrichedGridItem<T>,
    index: Int,
    allItemHeights: List<Int>,
    positions: MutableList<ItemPosition>,
    currentRowColumnHeights: IntArray,
    currentRowColumns: Int,
    currentRowWidth: Int,
    currentRowItemCount: Int,
    constraints: Constraints,
    maxPossibleColumns: Int
) {
    val itemHeight = allItemHeights[index]
    val columnWidth = constraints.maxWidth / currentRowColumns
    
    val columnIndex = (currentRowItemCount - 1).coerceIn(0, currentRowColumns - 1)

    val rowStartY = currentRowColumnHeights.minOrNull() ?: 0

    val x = columnIndex * columnWidth
    val y = rowStartY

    positions[index] = ItemPosition(x, y, columnWidth, itemHeight, columnIndex)

    // Advance the height for the occupied column. This ensures that even if the row
    // contains a single (odd) item, the global max height will reflect the item end Y
    // and the next row/group will start below it, avoiding overlaps.
    if (columnIndex in currentRowColumnHeights.indices) {
        currentRowColumnHeights[columnIndex] = y + itemHeight
    }
}

/**
 * Position full width item (always starts new row)
 */
private fun <T> positionFullWidthItem(
    item: EnrichedGridItem<T>,
    index: Int,
    allItemHeights: List<Int>,
    positions: MutableList<ItemPosition>,
    currentRowColumnHeights: IntArray,
    currentRowColumns: Int,
    constraints: Constraints,
    maxPossibleColumns: Int
) {
    val itemHeight = allItemHeights[index]
    val fullWidth = constraints.maxWidth
    
    val maxColumnHeight = currentRowColumnHeights.maxOrNull() ?: 0
    val y = maxColumnHeight
    
    positions[index] = ItemPosition(0, y, fullWidth, itemHeight, 0)
    
    for (col in 0 until currentRowColumns) {
        currentRowColumnHeights[col] = y + itemHeight
    }
}

/**
 * Position custom item with height fix and span support
 */
private fun <T> positionCustomItem(
    item: EnrichedGridItem<T>,
    index: Int,
    allItemHeights: List<Int>,
    positions: MutableList<ItemPosition>,
    currentRowColumnHeights: IntArray,
    currentRowColumns: Int,
    currentRowWidth: Int,
    constraints: Constraints,
    maxPossibleColumns: Int
) {
    val arrayHeight = allItemHeights[index]
    val configHeight = item.config.height
    
    val finalItemHeight = if (arrayHeight <= 0 && configHeight != null) {
        configHeight
    } else {
        arrayHeight
    }
    
    val customType = item.config.type as GridItemType.Custom
    val spans = min(customType.spans, maxPossibleColumns)
    val columnWidth = constraints.maxWidth / maxPossibleColumns
    val customWidth = columnWidth * spans
    
    var startColumn = 0
    
    if (spans < maxPossibleColumns) {
        var minHeight = Int.MAX_VALUE
        val maxCol = maxPossibleColumns - spans
        for (col in 0..maxCol) {
            var sectionHeight = currentRowColumnHeights[col]
            // Early exit if this section already exceeds current minimum
            if (sectionHeight >= minHeight) continue
            
            val endSpan = minOf(col + spans, maxPossibleColumns)
            for (spanCol in (col + 1) until endSpan) {
                if (currentRowColumnHeights[spanCol] > sectionHeight) {
                    sectionHeight = currentRowColumnHeights[spanCol]
                    // Early exit if this section exceeds current minimum
                    if (sectionHeight >= minHeight) break
                }
            }
            if (sectionHeight < minHeight) {
                minHeight = sectionHeight
                startColumn = col
                // Early exit if we found a zero-height position (optimal)
                if (minHeight == 0) break
            }
        }
    }
    
    var y = currentRowColumnHeights[startColumn]
    for (col in (startColumn + 1) until min(startColumn + spans, maxPossibleColumns)) {
        if (currentRowColumnHeights[col] > y) {
            y = currentRowColumnHeights[col]
        }
    }
    
    val x = startColumn * columnWidth
    
    positions[index] = ItemPosition(x, y, customWidth, finalItemHeight, startColumn)
    
    for (col in startColumn until min(startColumn + spans, maxPossibleColumns)) {
        currentRowColumnHeights[col] = y + finalItemHeight
    }
}

fun calculateVisibleRange(
    scrollOffset: Int,
    viewportHeight: Int,
    itemPositions: List<ItemPosition>,
    itemCount: Int,
    bufferSize: Int = 2,
): IntRange {
    if (itemCount <= 0) return 0..-1
    if (itemPositions.isEmpty()) {
        return 0 until min(10, itemCount)
    }

    // Consider only measured items (height > 0) to avoid thrashing on zeros
    var lastMeasuredIndex = -1
    for (i in itemPositions.size - 1 downTo 0) {
        val p = itemPositions[i]
        if (p.height > 0 || p.y > 0) { // y>0 covers cases after first row
            lastMeasuredIndex = i
            break
        }
    }
    if (lastMeasuredIndex < 0) {
        // Nothing measured yet, return small initial window
        return 0 until min(10, itemCount)
    }

    val slice = itemPositions.subList(0, lastMeasuredIndex + 1)
    val viewportBottom = scrollOffset + viewportHeight

    var firstVisibleIndex = findFirstIntersectingIndex(slice, scrollOffset, viewportBottom)
    var lastVisibleIndex = findLastIntersectingIndex(slice, scrollOffset, viewportBottom)

    // Handle no-intersection cases by snapping to nearest measured index
    if (firstVisibleIndex >= slice.size && lastVisibleIndex >= slice.size) {
        // Viewport is entirely after last measured item or entirely before first measured item
        firstVisibleIndex = if (scrollOffset <= slice.first().y) 0 else lastMeasuredIndex
        lastVisibleIndex = firstVisibleIndex
    } else {
        // Clamp indices into the measured slice
        firstVisibleIndex = firstVisibleIndex.coerceIn(0, lastMeasuredIndex)
        lastVisibleIndex = lastVisibleIndex.coerceIn(0, lastMeasuredIndex)
    }

    // Ensure start <= end
    if (firstVisibleIndex > lastVisibleIndex) {
        firstVisibleIndex = lastVisibleIndex
    }

    val firstWithBuffer = max(0, firstVisibleIndex - bufferSize)
    val lastWithBuffer = min(itemCount - 1, lastVisibleIndex + bufferSize)

    return firstWithBuffer..lastWithBuffer
}

private fun findFirstIntersectingIndex(positions: List<ItemPosition>, viewportTop: Int, viewportBottom: Int): Int {
    var left = 0
    var right = positions.size - 1
    var result = positions.size

    while (left <= right) {
        val mid = (left + right) / 2
        val position = positions[mid]
        val itemTop = position.y
        val itemBottom = position.y + position.height

        if (itemBottom >= viewportTop && itemTop <= viewportBottom) {
            result = mid
            right = mid - 1
        } else if (itemBottom < viewportTop) {
            left = mid + 1
        } else {
            right = mid - 1
        }
    }
    return result
}

private fun findLastIntersectingIndex(positions: List<ItemPosition>, viewportTop: Int, viewportBottom: Int): Int {
    var left = 0
    var right = positions.size - 1
    var result = -1

    while (left <= right) {
        val mid = (left + right) / 2
        val position = positions[mid]
        val itemTop = position.y
        val itemBottom = position.y + position.height

        if (itemBottom >= viewportTop && itemTop <= viewportBottom) {
            result = mid
            left = mid + 1
        } else if (itemBottom < viewportTop) {
            left = mid + 1
        } else {
            right = mid - 1
        }
    }
    return if (result == -1) positions.size else result
}
