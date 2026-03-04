package com.lazyadaptivelayout.model

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Grid item types supporting different layout strategies.
 * Each type represents a different way items can be laid out in the grid.
 */
@Immutable
sealed class GridItemType {
    @Immutable
    data object Staggered : GridItemType()
    
    @Immutable
    data object Uniform : GridItemType()
    
    @Immutable
    data object FullWidth : GridItemType()

    // you can specify how many columns this item should span. It can be used for collage layouts
    @Immutable
    data class Custom(val spans: Int) : GridItemType()
}

/**
 * Configuration for a grid item including its type, dimensions, and spacing.
 * 
 * @param type The layout strategy for this item
 * @param height The fixed height in pixels, or null for wrap content
 * @param edgeSpacing Spacing around the edges of the item
 * @param contentPadding Padding inside the item's content
 */
@Immutable
data class GridItemConfig(
    val type: GridItemType,
    val height: Int? = null,
    val edgeSpacing: EdgeSpacing = EdgeSpacing(),
    val contentPadding: ContentPadding = ContentPadding()
)

/**
 * Configuration for spacing around the edges of a grid item.
 * 
 * @param start Spacing at the start (left in LTR)
 * @param end Spacing at the end (right in LTR)
 * @param top Spacing at the top
 * @param bottom Spacing at the bottom
 */
@Immutable
data class EdgeSpacing(
    val start: Dp = 0.dp,
    val end: Dp = 0.dp,
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
)

/**
 * Configuration for padding inside a grid item's content.
 * 
 * @param horizontal Horizontal padding (left and right)
 * @param vertical Vertical padding (top and bottom)
 */
@Immutable
data class ContentPadding(
    val horizontal: Dp = 0.dp,
    val vertical: Dp = 0.dp
)

/**
 * Represents the position and size of an item in the grid.
 * 
 * @param x X coordinate in pixels
 * @param y Y coordinate in pixels
 * @param width Width in pixels
 * @param height Height in pixels
 * @param columnIndex Index of the column this item is placed in
 */
@Immutable
data class ItemPosition(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
    val columnIndex: Int
)

/**
 * Wrapper for an item and its configuration in the grid.
 * 
 * @param item The actual item data
 * @param config Configuration for how this item should be laid out
 */
@Immutable
data class GridItem<T>(
    val item: T,
    val config: GridItemConfig
)

/**
 * Contains information about the layout of visible items in the adaptive grid.
 * Similar to LazyListLayoutInfo but designed for grid layouts.
 * 
 * @param visibleItemsInfo List of information about visible items
 * @param viewportStartOffset The start offset of the viewport in pixels
 * @param viewportEndOffset The end offset of the viewport in pixels
 * @param totalItemsCount Total number of items in the grid
 * @param viewportSize The size of the viewport in pixels
 * @param orientation The orientation of the grid (always vertical for now)
 */
@Immutable
data class AdaptiveGridLayoutInfo(
    val visibleItemsInfo: List<AdaptiveGridItemInfo>,
    val viewportStartOffset: Int,
    val viewportEndOffset: Int,
    val totalItemsCount: Int,
    val viewportSize: Int,
    val orientation: Orientation = Orientation.Vertical
)

/**
 * Contains information about a single visible item in the adaptive grid.
 * 
 * @param index The index of the item in the original data list
 * @param key The stable key for this item
 * @param offset The Y offset from the viewport start in pixels
 * @param size The height of the item in pixels
 * @param position The complete position information for this item
 * @param layoutType The type of layout this item uses
 */
@Immutable
data class AdaptiveGridItemInfo(
    val index: Int,
    val key: Any,
    val offset: Int,
    val size: Int,
    val position: ItemPosition,
    val layoutType: GridItemType
)