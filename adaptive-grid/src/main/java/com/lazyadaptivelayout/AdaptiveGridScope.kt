package com.lazyadaptivelayout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.lazyadaptivelayout.model.ContentPadding
import com.lazyadaptivelayout.model.EdgeSpacing
import com.lazyadaptivelayout.model.GridItemType

/**
 * Represents a custom item within a custom group with its own span configuration.
 */
data class CustomGridItem<T>(
    val item: T,
    val span: Int,
    val height: androidx.compose.ui.unit.Dp? = null,
    val content: @Composable (T, Int, Int, Int) -> Unit
)

/**
 * Represents a layout group of items in the adaptive grid with shared configuration.
 *
 * @param items List of items in this layout
 * @param type The layout strategy for this layout (staggered, uniform, or full-width)
 * @param columns Number of columns for this layout
 * @param edgeSpacing Spacing around the edges of items in this layout
 * @param contentPadding Padding inside items' content in this layout
 * @param key Function to generate unique String keys for items in this layout
 * @param contentType Function to provide content type for composition reuse optimization
 * @param height Function to calculate height for each item in this layout
 * @param content Composable function to render each item in this layout
 */
data class GridGroup<T>(
    val items: List<T>,
    val type: GridItemType,
    val columns: Int = 1, // group-specific columns, defaults to 1
    val edgeSpacing: EdgeSpacing = EdgeSpacing(),
    val contentPadding: ContentPadding = ContentPadding(),
    val key: (T, Int) -> String = { item, index -> "${item.hashCode()}_$index" }, // PERFORMANCE OPTIMIZATION: String keys
    val contentType: (T, Int) -> Any? = { _, _ -> null }, // PERFORMANCE OPTIMIZATION: ContentType for composition reuse
    val height: (T, Int) -> androidx.compose.ui.unit.Dp = { _, _ -> androidx.compose.ui.unit.Dp.Unspecified },
    val content: @Composable (T, Int, Int, Int) -> Unit
)

/**
 * Represents a custom layout with individual item spans for magazine-style layouts.
 * 
 * PERFORMANCE OPTIMIZATION: Uses String keys and contentType for better Compose performance and stability.
 */
data class CustomGridGroup<T>(
    val customItems: List<CustomGridItem<T>>,
    val columns: Int,
    val edgeSpacing: EdgeSpacing = EdgeSpacing(),
    val contentPadding: ContentPadding = ContentPadding(),
    val key: (T, Int) -> String = { item, index -> "${item.hashCode()}_$index" }, // PERFORMANCE OPTIMIZATION: String keys
    val contentType: (T, Int) -> Any? = { _, _ -> null }, // PERFORMANCE OPTIMIZATION: ContentType for composition reuse
    val height: (T, Int) -> androidx.compose.ui.unit.Dp = { _, _ -> androidx.compose.ui.unit.Dp.Unspecified }
)

/**
 * Scope for defining custom layout items inline with individual spans.
 */
interface CustomGroupScope<T> {
    /**
     * Adds an item to the custom layout with a specific span.
     * 
     * @param item The item to add
     * @param span Number of columns this item should span (must be <= layout columns)
     * @param height Height for this specific item (optional)
     * @param content Composable function to render this item
     */
    fun item(
        item: T,
        span: Int,
        height: androidx.compose.ui.unit.Dp? = null,
        content: @Composable (T, Int, Int, Int) -> Unit
    )
}

/**
 * Implementation of CustomGroupScope that collects custom layout items.
 */
class CustomGroupScopeImpl<T> : CustomGroupScope<T> {
    private val _customItems = mutableListOf<CustomGridItem<T>>()
    val customItems: List<CustomGridItem<T>> get() = _customItems.toList()

    override fun item(
        item: T,
        span: Int,
        height: androidx.compose.ui.unit.Dp?,
        content: @Composable (T, Int, Int, Int) -> Unit
    ) {
        _customItems.add(CustomGridItem(item, span, height, content))
    }
}

/**
 * Scope interface for configuring adaptive grid layouts.
 * This provides the DSL API for setting up different layout types: staggered, uniform, full-width, and custom.
 * 
 * @param T The type of items in the grid
 */
interface AdaptiveGridScope<T> {
    /**
     * Creates a staggered layout with the given items.
     * Items in staggered layouts have varying heights based on content, creating a Pinterest-style grid.
     *
     * @param items List of items for this staggered layout
     * @param columns Number of columns for this staggered layout
     * @param height Function to calculate height for each item (item, index) -> Dp
     * @param edgeSpacing Spacing around the edges of items
     * @param contentPadding Padding inside items' content
     * @param key Function to generate unique String keys for items
     * @param contentType Function to provide content type for composition reuse optimization
     * @param content Composable function to render each item
     */
    fun staggeredItems(
        items: List<T>,
        columns: Int,
        height: (T, Int) -> androidx.compose.ui.unit.Dp = { _, _ -> androidx.compose.ui.unit.Dp.Unspecified },
        edgeSpacing: EdgeSpacing = EdgeSpacing(),
        contentPadding: ContentPadding = ContentPadding(),
        key: (T, Int) -> String = { item, index -> "${item.hashCode()}_$index" }, // PERFORMANCE OPTIMIZATION: String keys
        contentType: (T, Int) -> Any? = { _, _ -> null }, // PERFORMANCE OPTIMIZATION: ContentType for composition reuse
        content: @Composable (T, Int, Int, Int) -> Unit
    )

    /**
     * Creates a uniform grid layout with the given items.
     * Items in uniform grids have consistent heights within each row.
     *
     * @param items List of items for this uniform grid
     * @param columns Number of columns for this grid layout
     * @param height Function to calculate height for each item (item, index) -> Dp
     * @param edgeSpacing Spacing around the edges of items
     * @param contentPadding Padding inside items' content
     * @param key Function to generate unique String keys for items
     * @param contentType Function to provide content type for composition reuse optimization
     * @param content Composable function to render each item
     */
    fun items(
        items: List<T>,
        columns: Int,
        height: (T, Int) -> androidx.compose.ui.unit.Dp = { _, _ -> androidx.compose.ui.unit.Dp.Unspecified },
        edgeSpacing: EdgeSpacing = EdgeSpacing(),
        contentPadding: ContentPadding = ContentPadding(),
        key: (T, Int) -> String = { item, index -> "${item.hashCode()}_$index" }, // PERFORMANCE OPTIMIZATION: String keys
        contentType: (T, Int) -> Any? = { _, _ -> null }, // PERFORMANCE OPTIMIZATION: ContentType for composition reuse
        content: @Composable (T, Int, Int, Int) -> Unit
    )

    /**
     * Creates a full-width item that spans the entire width of the grid.
     *
     * @param item The item to display
     * @param height Function to calculate height for the item (item, index) -> Dp
     * @param edgeSpacing Spacing around the edges of the item
     * @param contentPadding Padding inside the item's content
     * @param key Function to generate unique String key for the item
     * @param contentType Function to provide content type for composition reuse optimization
     * @param content Composable function to render the item
     */
    fun item(
        item: T,
        height: (T, Int) -> androidx.compose.ui.unit.Dp = { _, _ -> androidx.compose.ui.unit.Dp.Unspecified },
        edgeSpacing: EdgeSpacing = EdgeSpacing(),
        contentPadding: ContentPadding = ContentPadding(),
        key: (T, Int) -> String = { item, index -> "${item.hashCode()}_$index" }, // PERFORMANCE OPTIMIZATION: String keys
        contentType: (T, Int) -> Any? = { _, _ -> null }, // PERFORMANCE OPTIMIZATION: ContentType for composition reuse
        content: @Composable (T, Int, Int, Int) -> Unit
    )

    /**
     * Creates a custom layout with inline item definitions and individual spans.
     * Each item in the custom layout can span a different number of columns, enabling magazine-style layouts.
     *
     * @param columns Number of columns for this custom layout
     * @param height Function to calculate height for each item (item, index) -> Dp
     * @param edgeSpacing Spacing around the edges of items
     * @param contentPadding Padding inside items' content
     * @param key Function to generate unique String keys for items
     * @param contentType Function to provide content type for composition reuse optimization
     * @param content DSL scope for defining items with their spans
     */
    fun custom(
        columns: Int,
        height: (T, Int) -> androidx.compose.ui.unit.Dp = { _, _ -> androidx.compose.ui.unit.Dp.Unspecified },
        edgeSpacing: EdgeSpacing = EdgeSpacing(),
        contentPadding: ContentPadding = ContentPadding(),
        key: (T, Int) -> String = { item, index -> "${item.hashCode()}_$index" }, // PERFORMANCE OPTIMIZATION: String keys
        contentType: (T, Int) -> Any? = { _, _ -> null }, // PERFORMANCE OPTIMIZATION: ContentType for composition reuse
        content: CustomGroupScope<T>.() -> Unit
    )

    /**
     * Creates a staggered layout with the given items, providing access to both item and index.
     * Items in staggered layouts have varying heights based on content, creating a Pinterest-style grid.
     * 
     * PERFORMANCE OPTIMIZATION: Uses String keys and contentType for better Compose performance and stability.
     * 
     * @param items List of items for this staggered layout
     * @param columns Number of columns for this staggered layout
     * @param height Function to calculate height for each item (index, item) -> Dp
     * @param edgeSpacing Spacing around the edges of items
     * @param contentPadding Padding inside items' content
     * @param key Function to generate unique String keys for items (index, item) -> String
     * @param contentType Function to provide content type for composition reuse optimization (index, item) -> Any?
     * @param itemContent Composable function to render each item (index, item, columnIndex, columns) -> Unit
     */
    fun staggeredItemsIndexed(
        items: List<T>,
        columns: Int,
        height: (Int, T) -> androidx.compose.ui.unit.Dp = { _, _ -> androidx.compose.ui.unit.Dp.Unspecified },
        edgeSpacing: EdgeSpacing = EdgeSpacing(),
        contentPadding: ContentPadding = ContentPadding(),
        key: (Int, T) -> String = { index, item -> "${item.hashCode()}_$index" }, // PERFORMANCE OPTIMIZATION: String keys
        contentType: (Int, T) -> Any? = { _, _ -> null }, // PERFORMANCE OPTIMIZATION: ContentType for composition reuse
        itemContent: @Composable (Int, T, Int, Int) -> Unit
    )

    /**
     * Creates a uniform grid layout with the given items, providing access to both item and index.
     * Items in uniform grids have consistent heights within each row.
     * 
     * PERFORMANCE OPTIMIZATION: Uses String keys and contentType for better Compose performance and stability.
     * 
     * @param items List of items for this uniform grid
     * @param columns Number of columns for this grid layout
     * @param height Function to calculate height for each item (index, item) -> Dp
     * @param edgeSpacing Spacing around the edges of items
     * @param contentPadding Padding inside items' content
     * @param key Function to generate unique String keys for items (index, item) -> String
     * @param contentType Function to provide content type for composition reuse optimization (index, item) -> Any?
     * @param itemContent Composable function to render each item (index, item, columnIndex, columns) -> Unit
     */
    fun itemsIndexed(
        items: List<T>,
        columns: Int,
        height: (Int, T) -> androidx.compose.ui.unit.Dp = { _, _ -> androidx.compose.ui.unit.Dp.Unspecified },
        edgeSpacing: EdgeSpacing = EdgeSpacing(),
        contentPadding: ContentPadding = ContentPadding(),
        key: (Int, T) -> String = { index, item -> "${item.hashCode()}_$index" }, // PERFORMANCE OPTIMIZATION: String keys
        contentType: (Int, T) -> Any? = { _, _ -> null }, // PERFORMANCE OPTIMIZATION: ContentType for composition reuse
        itemContent: @Composable (Int, T, Int, Int) -> Unit
    )
}

/**
 * Represents a unified layout that can be either regular (staggered/uniform/full-width) or custom.
 */
sealed class UnifiedGroup<T> {
    data class Regular<T>(val group: GridGroup<T>) : UnifiedGroup<T>()
    data class Custom<T>(val group: CustomGridGroup<T>) : UnifiedGroup<T>()
}

/**
 * Implementation of AdaptiveGridScope that collects layout configurations for later processing.
 * 
 * @param T The type of items in the grid
 */
class AdaptiveGridScopeImpl<T> : AdaptiveGridScope<T> {
    private val _unifiedGroups = mutableListOf<UnifiedGroup<T>>()
    
    val unifiedGroups: List<UnifiedGroup<T>> get() = _unifiedGroups.toList()

    /**
     * Wraps content with appropriate edge spacing based on item position and type.
     */
    private fun wrapContentWithSpacing(
        content: @Composable (T, Int, Int, Int) -> Unit,
        edgeSpacing: EdgeSpacing,
        contentPadding: ContentPadding,
        height: (T, Int) -> androidx.compose.ui.unit.Dp,
        itemHeight: androidx.compose.ui.unit.Dp? = null
    ): @Composable (T, Int, Int, Int) -> Unit {
        return { item, index, columnIndex, columns ->
            val config = LocalGridItemConfig.current

            val (startPadding, endPadding, topPadding, bottomPadding) = computeItemPaddings(
                type = config.type,
                columnIndex = columnIndex,
                columns = columns,
                spans = (config.type as? GridItemType.Custom)?.spans,
                edgeSpacing = edgeSpacing,
                contentPadding = contentPadding,
                index = index
            )

            // Use itemHeight if provided (for custom groups), otherwise use the height function
            val finalHeight = resolveItemHeight(itemHeight, height, item, index)
            
            Box(
                modifier = Modifier
                    .padding(
                        start = startPadding,
                        end = endPadding,
                        top = topPadding,
                        bottom = bottomPadding
                    )
                    .then(
                        if (finalHeight != androidx.compose.ui.unit.Dp.Unspecified)
                            Modifier.height(finalHeight)
                        else
                            Modifier.wrapContentHeight()
                    )
            ) {
                content(
                    item,
                    index,
                    columnIndex,
                    columns
                )
            }
        }
    }

    internal data class ItemPaddings(
        val start: androidx.compose.ui.unit.Dp,
        val end: androidx.compose.ui.unit.Dp,
        val top: androidx.compose.ui.unit.Dp,
        val bottom: androidx.compose.ui.unit.Dp,
    )

    internal fun computeItemPaddings(
        type: GridItemType,
        columnIndex: Int,
        columns: Int,
        spans: Int?,
        edgeSpacing: EdgeSpacing,
        contentPadding: ContentPadding,
        index: Int,
    ): ItemPaddings {
        val startPadding: androidx.compose.ui.unit.Dp
        val endPadding: androidx.compose.ui.unit.Dp

        when {
            type is GridItemType.FullWidth -> {
                startPadding = edgeSpacing.start
                endPadding = edgeSpacing.end
            }

            type is GridItemType.Custom -> {
                val s = spans ?: 1
                when {
                    s >= columns -> {
                        startPadding = edgeSpacing.start
                        endPadding = edgeSpacing.end
                    }
                    columnIndex == 0 -> {
                        startPadding = edgeSpacing.start
                        endPadding = contentPadding.horizontal
                    }
                    columnIndex + s >= columns -> {
                        startPadding = contentPadding.horizontal
                        endPadding = edgeSpacing.end
                    }
                    else -> {
                        startPadding = contentPadding.horizontal
                        endPadding = contentPadding.horizontal
                    }
                }
            }

            columnIndex == 0 -> {
                startPadding = edgeSpacing.start
                endPadding = contentPadding.horizontal / 2
            }
            columnIndex == columns - 1 -> {
                startPadding = contentPadding.horizontal / 2
                endPadding = edgeSpacing.end
            }
            else -> {
                startPadding = contentPadding.horizontal / 2
                endPadding = contentPadding.horizontal / 2
            }
        }

        val topPadding = if (index < columns) edgeSpacing.top else contentPadding.vertical / 2
        val bottomPadding = contentPadding.vertical / 2
        return ItemPaddings(startPadding, endPadding, topPadding, bottomPadding)
    }

    internal fun <T> resolveItemHeight(
        itemHeight: androidx.compose.ui.unit.Dp?,
        height: (T, Int) -> androidx.compose.ui.unit.Dp,
        item: T,
        index: Int
    ): androidx.compose.ui.unit.Dp {
        return itemHeight ?: height(item, index)
    }

    override fun staggeredItems(
        items: List<T>,
        columns: Int,
        height: (T, Int) -> androidx.compose.ui.unit.Dp,
        edgeSpacing: EdgeSpacing,
        contentPadding: ContentPadding,
        key: (T, Int) -> String,
        contentType: (T, Int) -> Any?,
        content: @Composable (T, Int, Int, Int) -> Unit
    ) {
        // Input validation
        require(columns > 0) { "Columns must be greater than 0, got: $columns" }
        
        // Handle empty list gracefully (consistent with Compose lazy layouts)
        if (items.isEmpty()) return
        
        val wrappedContent = wrapContentWithSpacing(content, edgeSpacing, contentPadding, height)
        val group = GridGroup(
            items = items,
            type = GridItemType.Staggered,
            columns = columns,
            edgeSpacing = edgeSpacing,
            contentPadding = contentPadding,
            key = key,
            contentType = contentType,
            height = height,
            content = wrappedContent
        )
        _unifiedGroups.add(UnifiedGroup.Regular(group))
    }

    override fun items(
        items: List<T>,
        columns: Int,
        height: (T, Int) -> androidx.compose.ui.unit.Dp,
        edgeSpacing: EdgeSpacing,
        contentPadding: ContentPadding,
        key: (T, Int) -> String,
        contentType: (T, Int) -> Any?,
        content: @Composable (T, Int, Int, Int) -> Unit
    ) {
        // Input validation
        require(columns > 0) { "Columns must be greater than 0, got: $columns" }
        
        // Handle empty list gracefully (consistent with Compose lazy layouts)
        if (items.isEmpty()) return
        
        val wrappedContent = wrapContentWithSpacing(content, edgeSpacing, contentPadding, height)
        val group = GridGroup(
            items = items,
            type = GridItemType.Uniform,
            columns = columns,
            edgeSpacing = edgeSpacing,
            contentPadding = contentPadding,
            key = key,
            contentType = contentType,
            height = height,
            content = wrappedContent
        )
        _unifiedGroups.add(UnifiedGroup.Regular(group))
    }

    override fun item(
        item: T,
        height: (T, Int) -> androidx.compose.ui.unit.Dp,
        edgeSpacing: EdgeSpacing,
        contentPadding: ContentPadding,
        key: (T, Int) -> String,
        contentType: (T, Int) -> Any?,
        content: @Composable (T, Int, Int, Int) -> Unit
    ) {
        val wrappedContent = wrapContentWithSpacing(content, edgeSpacing, contentPadding, height)
        val group = GridGroup(
            items = listOf(item), // Wrap single item in a list
            type = GridItemType.FullWidth,
            columns = 1, // Full-width items don't use columns, but we need a default value
            edgeSpacing = edgeSpacing,
            contentPadding = contentPadding,
            key = key,
            contentType = contentType,
            height = height,
            content = wrappedContent
        )
        _unifiedGroups.add(UnifiedGroup.Regular(group))
    }

    override fun custom(
        columns: Int,
        height: (T, Int) -> androidx.compose.ui.unit.Dp,
        edgeSpacing: EdgeSpacing,
        contentPadding: ContentPadding,
        key: (T, Int) -> String,
        contentType: (T, Int) -> Any?,
        content: CustomGroupScope<T>.() -> Unit
    ) {
        // Input validation
        require(columns > 0) { "Columns must be greater than 0, got: $columns" }
        
        val customScope = CustomGroupScopeImpl<T>()
        customScope.content()
        
        // Handle empty custom group gracefully
        if (customScope.customItems.isEmpty()) return
        
        // Validate spans don't exceed column count
        customScope.customItems.forEach { customItem ->
            require(customItem.span > 0) { "Item span must be greater than 0, got: ${customItem.span}" }
            if (customItem.span > columns) {
                // Warning: allow spans > columns but clamp them
                // This is more forgiving than throwing an error
            }
        }
        
        // Wrap each custom item's content with spacing
        val wrappedCustomItems = customScope.customItems.mapIndexed { _, customItem ->
            val wrappedContent = wrapContentWithSpacing(
                customItem.content, 
                edgeSpacing, 
                contentPadding, 
                height,
                customItem.height // Pass individual item height
            )
            CustomGridItem(
                item = customItem.item,
                span = customItem.span,
                height = customItem.height,
                content = wrappedContent
            )
        }
        
        val customGroup = CustomGridGroup(
            customItems = wrappedCustomItems,
            columns = columns,
            edgeSpacing = edgeSpacing,
            contentPadding = contentPadding,
            key = key,
            contentType = contentType,
            height = height
        )
        _unifiedGroups.add(UnifiedGroup.Custom(customGroup))
    }

    override fun staggeredItemsIndexed(
        items: List<T>,
        columns: Int,
        height: (Int, T) -> androidx.compose.ui.unit.Dp,
        edgeSpacing: EdgeSpacing,
        contentPadding: ContentPadding,
        key: (Int, T) -> String,
        contentType: (Int, T) -> Any?,
        itemContent: @Composable (Int, T, Int, Int) -> Unit
    ) {
        // Input validation
        require(columns > 0) { "Columns must be greater than 0, got: $columns" }
        
        // Handle empty list gracefully (consistent with Compose lazy layouts)
        if (items.isEmpty()) return
        
        // Convert indexed parameters to regular parameters for reuse
        val convertedHeight: (T, Int) -> androidx.compose.ui.unit.Dp = { item, index -> height(index, item) }
        val convertedKey: (T, Int) -> String = { item, index -> key(index, item) }
        val convertedContentType: (T, Int) -> Any? = { item, index -> contentType(index, item) }
        val convertedContent: @Composable (T, Int, Int, Int) -> Unit = { item, globalIndex, columnIndex, columns ->
            itemContent(globalIndex, item, columnIndex, columns)
        }
        
        val wrappedContent = wrapContentWithSpacing(convertedContent, edgeSpacing, contentPadding, convertedHeight)
        val group = GridGroup(
            items = items,
            type = GridItemType.Staggered,
            columns = columns,
            edgeSpacing = edgeSpacing,
            contentPadding = contentPadding,
            key = convertedKey,
            contentType = convertedContentType,
            height = convertedHeight,
            content = wrappedContent
        )
        _unifiedGroups.add(UnifiedGroup.Regular(group))
    }

    override fun itemsIndexed(
        items: List<T>,
        columns: Int,
        height: (Int, T) -> androidx.compose.ui.unit.Dp,
        edgeSpacing: EdgeSpacing,
        contentPadding: ContentPadding,
        key: (Int, T) -> String,
        contentType: (Int, T) -> Any?,
        itemContent: @Composable (Int, T, Int, Int) -> Unit
    ) {
        // Input validation
        require(columns > 0) { "Columns must be greater than 0, got: $columns" }
        
        // Handle empty list gracefully (consistent with Compose lazy layouts)  
        if (items.isEmpty()) return
        
        // Convert indexed parameters to regular parameters for reuse
        val convertedHeight: (T, Int) -> androidx.compose.ui.unit.Dp = { item, index -> height(index, item) }
        val convertedKey: (T, Int) -> String = { item, index -> key(index, item) }
        val convertedContentType: (T, Int) -> Any? = { item, index -> contentType(index, item) }
        val convertedContent: @Composable (T, Int, Int, Int) -> Unit = { item, globalIndex, columnIndex, columns ->
            itemContent(globalIndex, item, columnIndex, columns)
        }
        
        val wrappedContent = wrapContentWithSpacing(convertedContent, edgeSpacing, contentPadding, convertedHeight)
        val group = GridGroup(
            items = items,
            type = GridItemType.Uniform,
            columns = columns,
            edgeSpacing = edgeSpacing,
            contentPadding = contentPadding,
            key = convertedKey,
            contentType = convertedContentType,
            height = convertedHeight,
            content = wrappedContent
        )
        _unifiedGroups.add(UnifiedGroup.Regular(group))
    }
} 