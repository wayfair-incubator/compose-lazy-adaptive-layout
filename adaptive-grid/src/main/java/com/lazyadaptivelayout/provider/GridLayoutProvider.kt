package com.lazyadaptivelayout.provider

import androidx.compose.runtime.Composable
import com.lazyadaptivelayout.model.GridItem
import com.lazyadaptivelayout.model.GridItemConfig

/**
 * Interface for providing grid items and their configurations.
 * This allows for flexible implementation of different grid layouts.
 *
 * @param T The type of items in the grid
 */
interface GridLayoutProvider<T> {
    val items: List<GridItem<T>>
    val itemCount: Int get() = items.size
    fun getItemConfig(index: Int): GridItemConfig
    fun getItemKey(index: Int): String
    fun getContentType(index: Int): Any?

    @Composable
    fun ItemContent(item: T, index: Int, columnIndex: Int, columns: Int)
    
    /**
     * Get index for given key. The index is not guaranteed to be known for all keys in layout for
     * optimization purposes, but must be present for elements in current viewport. If the key is
     * not present in the layout or is not known, return -1.
     *
     * @param key the key of an item in the layout.
     * @return The index mapped from [key] if it is present in the layout, otherwise -1.
     */
    fun getIndex(key: Any): Int {
        // Simple linear search - can be optimized with a map if needed
        for (i in 0 until itemCount) {
            if (getItemKey(i) == key.toString()) {
                return i
            }
        }
        return -1
    }
}

/**
 * Finds a position of the item with the given key in the grid. This logic allows us to detect when
 * there were items added or removed before our current first item.
 */
internal fun <T> GridLayoutProvider<T>.findIndexByKey(key: Any?, lastKnownIndex: Int): Int {
    if (key == null || itemCount == 0) {
        // there were no real item during the previous measure
        return lastKnownIndex
    }
    val keyString = key.toString()
    if (lastKnownIndex < itemCount && keyString == getItemKey(lastKnownIndex)) {
        // this item is still at the same index
        return lastKnownIndex
    }
    val newIndex = getIndex(key)
    if (newIndex != -1) {
        return newIndex
    }
    // fallback to the previous index if we don't know the new index of the item
    return lastKnownIndex
}