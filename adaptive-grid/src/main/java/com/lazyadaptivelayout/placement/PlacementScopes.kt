package com.lazyadaptivelayout.placement

import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.LayoutDirection
import com.lazyadaptivelayout.model.ItemPosition

/**
 * Placement scope that only places visible items in a lazy grid layout.
 * 
 * @param allPlaceables List of all placeables (null for non-visible items)
 * @param positions List of item positions
 * @param parentLayoutDirection Layout direction of the parent
 * @param parentWidth Width of the parent layout
 * @param visibleRange Range of items that should be visible
 * @param scrollOffset Current scroll offset to adjust item positions
 * @param parentHeight Height of the parent layout for bounds checking
 */
class LazySimplePlacementScope(
    private val allPlaceables: List<Placeable?>,
    private val positions: List<ItemPosition>,
    override val parentLayoutDirection: LayoutDirection,
    override val parentWidth: Int,
    private val visibleRange: IntRange,
    private val scrollOffset: Int = 0,
    private val parentHeight: Int = Int.MAX_VALUE,
    // unit test purpose only
    private val onPlace: ((index: Int, placeable: Placeable, position: ItemPosition) -> Unit)? = null,
) : Placeable.PlacementScope() {

    fun placeChildren() {
        // Only place visible items that have been measured
        for (index in visibleRange) {
            val placeable = allPlaceables.getOrNull(index)
            val position = positions.getOrNull(index)

            if (placeable != null && position != null) {
                val adjustedY = position.y - scrollOffset
                
                // Only place items that are within viewport bounds
                // Don't allow items to be placed above viewport (prevents overlapping)
                // Allow partial visibility at bottom by checking if item overlaps with viewport
                val itemBottom = adjustedY + placeable.height
                // Place item if it overlaps the viewport: allow negative adjustedY for partial top visibility
                if (itemBottom > 0 && adjustedY < parentHeight) {
                    if (onPlace != null) {
                        onPlace.invoke(index, placeable, position)
                    } else {
                        placeable.placeRelative(position.x, adjustedY)
                    }
                }
            }
        }
    }
}