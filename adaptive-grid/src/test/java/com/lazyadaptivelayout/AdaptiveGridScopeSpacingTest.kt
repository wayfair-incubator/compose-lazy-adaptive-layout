package com.lazyadaptivelayout

import androidx.compose.ui.unit.Dp
import com.lazyadaptivelayout.model.ContentPadding
import com.lazyadaptivelayout.model.EdgeSpacing
import com.lazyadaptivelayout.model.GridItemType
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptiveGridScopeSpacingTest {

    private val edge = EdgeSpacing(start = Dp(8f), end = Dp(12f), top = Dp(4f), bottom = Dp(6f))
    private val pad = ContentPadding(horizontal = Dp(10f), vertical = Dp(20f))

    @Test
    fun computeItemPaddings_uniform_first_middle_last_columns_and_top_bottom() {
        val scope = AdaptiveGridScopeImpl<Any>()
        val columns = 3
        val indexFirstRow = 1 // < columns
        val indexNextRow = 4 // >= columns

        // First column
        val first = scope.computeItemPaddings(
            type = GridItemType.Uniform,
            columnIndex = 0,
            columns = columns,
            spans = null,
            edgeSpacing = edge,
            contentPadding = pad,
            index = indexFirstRow
        )
        assertEquals(Dp(8f), first.start)
        assertEquals(Dp(5f), first.end) // horizontal/2
        assertEquals(Dp(4f), first.top) // edgeSpacing.top for first row
        assertEquals(Dp(10f), first.bottom) // vertical/2

        // Middle column
        val middle = scope.computeItemPaddings(
            type = GridItemType.Uniform,
            columnIndex = 1,
            columns = columns,
            spans = null,
            edgeSpacing = edge,
            contentPadding = pad,
            index = indexNextRow
        )
        assertEquals(Dp(5f), middle.start)
        assertEquals(Dp(5f), middle.end)
        assertEquals(Dp(10f), middle.top) // content vertical/2 for non-first row
        assertEquals(Dp(10f), middle.bottom)

        // Last column
        val last = scope.computeItemPaddings(
            type = GridItemType.Uniform,
            columnIndex = 2,
            columns = columns,
            spans = null,
            edgeSpacing = edge,
            contentPadding = pad,
            index = indexNextRow
        )
        assertEquals(Dp(5f), last.start)
        assertEquals(Dp(12f), last.end)
        assertEquals(Dp(10f), last.top)
        assertEquals(Dp(10f), last.bottom)
    }

    @Test
    fun computeItemPaddings_fullWidth_usesEdgeStartEnd() {
        val scope = AdaptiveGridScopeImpl<Unit>()
        val p = scope.computeItemPaddings(
            type = GridItemType.FullWidth,
            columnIndex = 0,
            columns = 1,
            spans = null,
            edgeSpacing = edge,
            contentPadding = pad,
            index = 0
        )
        assertEquals(Dp(8f), p.start)
        assertEquals(Dp(12f), p.end)
        assertEquals(Dp(4f), p.top)
        assertEquals(Dp(10f), p.bottom)
    }

    @Test
    fun computeItemPaddings_custom_spans_logic_start_end_middle_and_fullSpan() {
        val scope = AdaptiveGridScopeImpl<String>()
        val columns = 4

        // Full-span behaves like full width
        val fullSpan = scope.computeItemPaddings(
            type = GridItemType.Custom(spans = 5),
            columnIndex = 0,
            columns = columns,
            spans = 5,
            edgeSpacing = edge,
            contentPadding = pad,
            index = 0
        )
        assertEquals(Dp(8f), fullSpan.start)
        assertEquals(Dp(12f), fullSpan.end)

        // Starts at first column -> start edge, end content
        val startEdge = scope.computeItemPaddings(
            type = GridItemType.Custom(spans = 2),
            columnIndex = 0,
            columns = columns,
            spans = 2,
            edgeSpacing = edge,
            contentPadding = pad,
            index = 1
        )
        assertEquals(Dp(8f), startEdge.start)
        assertEquals(Dp(10f), startEdge.end)

        // Ends at or beyond last column -> start content, end edge
        val endEdge = scope.computeItemPaddings(
            type = GridItemType.Custom(spans = 2),
            columnIndex = 3 - 1, // start at column 2, spans 2 -> ends at 4 >= columns
            columns = columns,
            spans = 2,
            edgeSpacing = edge,
            contentPadding = pad,
            index = 2
        )
        assertEquals(Dp(10f), endEdge.start)
        assertEquals(Dp(12f), endEdge.end)

        // Middle area -> content/content
        val middle = scope.computeItemPaddings(
            type = GridItemType.Custom(spans = 2),
            columnIndex = 1,
            columns = columns,
            spans = 2,
            edgeSpacing = edge,
            contentPadding = pad,
            index = 3
        )
        assertEquals(Dp(10f), middle.start)
        assertEquals(Dp(10f), middle.end)
    }

    @Test
    fun resolveItemHeight_prefersItemHeight_overGroupHeightLambda() {
        val scope = AdaptiveGridScopeImpl<Int>()
        val lambda: (Int, Int) -> Dp = { _, _ -> Dp(99f) }

        // itemHeight provided -> should win
        val chosen1 = scope.resolveItemHeight(itemHeight = Dp(42f), height = lambda, item = 1, index = 0)
        assertEquals(Dp(42f), chosen1)

        // itemHeight null -> fallback to lambda
        val chosen2 = scope.resolveItemHeight(itemHeight = null, height = lambda, item = 1, index = 0)
        assertEquals(Dp(99f), chosen2)
    }
}
