package com.lazyadaptivelayout

import androidx.compose.ui.unit.Dp
import com.lazyadaptivelayout.model.ContentPadding
import com.lazyadaptivelayout.model.EdgeSpacing
import com.lazyadaptivelayout.model.GridItemType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveGridScopeTest {

    @Test
    fun staggeredItems_addsGroupAndProperties() {
        val scope = AdaptiveGridScopeImpl<String>()
        val items = listOf("a", "b", "c")
        val edge = EdgeSpacing(start = Dp(1f), end = Dp(2f), top = Dp(3f), bottom = Dp(4f))
        val padding = ContentPadding(horizontal = Dp(5f), vertical = Dp(6f))

        scope.staggeredItems(
            items = items,
            columns = 3,
            height = { _, _ -> Dp.Unspecified },
            edgeSpacing = edge,
            contentPadding = padding,
            key = { item, index -> "K_${item}_${index}" },
            contentType = { _, _ -> "T" }
        ) { _, _, _, _ -> }

        val groups = scope.unifiedGroups
        assertEquals(1, groups.size)
        val g = (groups.first() as UnifiedGroup.Regular).group
        assertEquals(GridItemType.Staggered, g.type)
        assertEquals(3, g.columns)
        assertEquals(edge, g.edgeSpacing)
        assertEquals(padding, g.contentPadding)
        // verify key/contentType mapping functions are stored and callable
        assertEquals("K_a_0", g.key("a", 0))
        assertEquals("T", g.contentType("a", 0))
    }

    @Test
    fun items_emptyList_noGroupAdded() {
        val scope = AdaptiveGridScopeImpl<Int>()
        scope.items(
            items = emptyList(),
            columns = 2
        ) { _, _, _, _ -> }
        assertTrue(scope.unifiedGroups.isEmpty())
    }

    @Test(expected = IllegalArgumentException::class)
    fun items_columnsZero_throws() {
        val scope = AdaptiveGridScopeImpl<Int>()
        scope.items(
            items = listOf(1, 2),
            columns = 0
        ) { _, _, _, _ -> }
    }

    @Test
    fun item_fullWidth_addsSingleGroup() {
        val scope = AdaptiveGridScopeImpl<String>()
        scope.item(
            item = "only",
            height = { _, _ -> Dp.Unspecified },
            edgeSpacing = EdgeSpacing(start = Dp(2f)),
            contentPadding = ContentPadding(vertical = Dp(8f)),
            key = { item, index -> "ID_${item}_${index}" },
            contentType = { _, _ -> "CT" }
        ) { _, _, _, _ -> }

        val groups = scope.unifiedGroups
        assertEquals(1, groups.size)
        val g = (groups.first() as UnifiedGroup.Regular).group
        assertEquals(GridItemType.FullWidth, g.type)
        assertEquals(1, g.items.size)
        assertEquals("ID_only_0", g.key("only", 0))
        assertEquals("CT", g.contentType("only", 0))
    }

    @Test
    fun custom_withItems_buildsCustomGroup_preservesSpansAndCounts() {
        val scope = AdaptiveGridScopeImpl<String>()
        scope.custom(
            columns = 2,
            height = { _, _ -> Dp.Unspecified },
            edgeSpacing = EdgeSpacing(top = Dp(1f)),
            contentPadding = ContentPadding(horizontal = Dp(2f)),
            key = { item, i -> "K_${item}_${i}" },
            contentType = { _, _ -> "TYPE" }
        ) {
            // spans can exceed columns; implementation is lenient
            item("x", span = 3) { _, _, _, _ -> }
            item("y", span = 1) { _, _, _, _ -> }
        }

        val groups = scope.unifiedGroups
        assertEquals(1, groups.size)
        val cg = (groups.first() as UnifiedGroup.Custom).group
        assertEquals(2, cg.columns)
        assertEquals(2, cg.customItems.size)
        assertEquals(3, cg.customItems[0].span)
        assertEquals(1, cg.customItems[1].span)
        // group-level key/contentType should be callable
        assertEquals("K_x_0", cg.key("x", 0))
        assertEquals("TYPE", cg.contentType("x", 0))
    }

    @Test
    fun custom_empty_noGroupAdded() {
        val scope = AdaptiveGridScopeImpl<Int>()
        scope.custom(columns = 3) { /* no items */ }
        assertTrue(scope.unifiedGroups.isEmpty())
    }

    @Test
    fun staggeredItemsIndexed_convertsKeyAndContentType() {
        val scope = AdaptiveGridScopeImpl<String>()
        val items = listOf("a", "b")
        scope.staggeredItemsIndexed(
            items = items,
            columns = 2,
            height = { index, _ -> if (index == 0) Dp(10f) else Dp.Unspecified },
            key = { i, it -> "IDX_${i}_${it}" },
            contentType = { i, _ -> "T_${i}" }
        ) { _, _, _, _ -> }

        val groups = scope.unifiedGroups
        assertEquals(1, groups.size)
        val g = (groups.first() as UnifiedGroup.Regular).group
        assertEquals(GridItemType.Staggered, g.type)
        // The key/contentType lambdas should reflect index-first mapping after conversion
        assertEquals("IDX_0_a", g.key("a", 0))
        assertEquals("T_1", g.contentType("b", 1))
    }

    @Test
    fun itemsIndexed_convertsKeyAndContentType() {
        val scope = AdaptiveGridScopeImpl<String>()
        val items = listOf("p", "q", "r")
        scope.itemsIndexed(
            items = items,
            columns = 3,
            height = { index, _ -> if (index == 2) Dp(42f) else Dp.Unspecified },
            key = { i, it -> "K_${it}@${i}" },
            contentType = { i, _ -> i }
        ) { _, _, _, _ -> }

        val groups = scope.unifiedGroups
        assertEquals(1, groups.size)
        val g = (groups.first() as UnifiedGroup.Regular).group
        assertEquals(GridItemType.Uniform, g.type)
        assertEquals("K_p@0", g.key("p", 0))
        assertEquals(2, g.contentType("r", 2))
    }
}
