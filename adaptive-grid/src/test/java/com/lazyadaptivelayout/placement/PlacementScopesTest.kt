package com.lazyadaptivelayout.placement

import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import com.lazyadaptivelayout.model.ItemPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlacementScopesTest {

    /**
     * A minimal fake Placeable that records calls to placeAt via PlacementScope.placeRelative.
     */
    private class RecordingPlaceable : Placeable() {
        val placements = mutableListOf<IntOffset>()
        override fun get(alignmentLine: androidx.compose.ui.layout.AlignmentLine): Int = 0
        override fun placeAt(
            position: IntOffset,
            zIndex: Float,
            layerBlock: (GraphicsLayerScope.() -> Unit)?
        ) {
            placements += position
        }
    }

    @Test
    fun placeChildren_placesOnlyWithinVisibleRange_andUsesCoordinates() {
        // Arrange: three placeables and matching positions
        val p0 = RecordingPlaceable()
        val p1 = RecordingPlaceable()
        val p2 = RecordingPlaceable()
        val all = listOf<Placeable?>(p0, p1, p2)
        val positions = listOf(
            ItemPosition(x = 10, y = 20, width = 0, height = 0, columnIndex = 0),
            ItemPosition(x = 30, y = 40, width = 0, height = 0, columnIndex = 0),
            ItemPosition(x = 50, y = 60, width = 0, height = 0, columnIndex = 0),
        )

        val scope = LazySimplePlacementScope(
            allPlaceables = all,
            positions = positions,
            parentLayoutDirection = LayoutDirection.Ltr,
            parentWidth = 100,
            visibleRange = 1..2 // only last two should be placed
        )

        // Act
        scope.placeChildren()

        // Assert: p0 not placed; p1 and p2 placed exactly at their coords
        assertTrue(p0.placements.isEmpty())
        assertEquals(listOf(IntOffset(30, 40)), p1.placements)
        assertEquals(listOf(IntOffset(50, 60)), p2.placements)
    }

    @Test
    fun placeChildren_skipsNullPlaceablesOrPositions_andDoesNotCrash() {
        // Arrange: visibleRange includes indices with nulls
        val p0 = RecordingPlaceable()
        val all = listOf<Placeable?>(p0, null, RecordingPlaceable())
        // To trigger null position via getOrNull, pass a shorter positions list than the visible range
        val truncatedPositions: List<ItemPosition> = listOf(
            ItemPosition(1, 2, 0, 0, 0)
        )

        val scope = LazySimplePlacementScope(
            allPlaceables = all,
            positions = truncatedPositions, // getOrNull(1) and getOrNull(2) will be null
            parentLayoutDirection = LayoutDirection.Ltr,
            parentWidth = 100,
            visibleRange = 0..2
        )

        // Act: should only place index 0; indices 1,2 are skipped due to nulls
        scope.placeChildren()

        // Assert
        assertEquals(listOf(IntOffset(1, 2)), (all[0] as RecordingPlaceable).placements)
        // index 1 placeable is null - cannot assert
        assertTrue((all[2] as RecordingPlaceable).placements.isEmpty()) // position null, so not placed
    }

    @Test
    fun placeChildren_respectsLayoutDirectionContext_withoutThrowing() {
        // While placeRelative currently ignores RTL in our scope, ensure it's callable with RTL.
        val p = RecordingPlaceable()
        val scope = LazySimplePlacementScope(
            allPlaceables = listOf(p),
            positions = listOf(ItemPosition(9, 11, 0, 0, 0)),
            parentLayoutDirection = LayoutDirection.Rtl,
            parentWidth = 200,
            visibleRange = 0..0
        )

        scope.placeChildren()
        // In RTL, placeRelative mirrors X: expected X = parentWidth - x - width (width=0 in our fake)
        assertEquals(listOf(IntOffset(191, 11)), p.placements)
    }
}
