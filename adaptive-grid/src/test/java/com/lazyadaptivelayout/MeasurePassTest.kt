package com.lazyadaptivelayout

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import com.lazyadaptivelayout.layout.clearMeasurementCaches
import com.lazyadaptivelayout.model.GridItem
import com.lazyadaptivelayout.model.GridItemConfig
import com.lazyadaptivelayout.model.GridItemType
import com.lazyadaptivelayout.model.ItemPosition
import com.lazyadaptivelayout.provider.GridLayoutProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalFoundationApi::class)
class MeasurePassTest {

    private val bufferSize = 2
    private val viewportHeight = 1000
    private val constraints = Constraints(maxWidth = 1000, maxHeight = viewportHeight)
    private val density = Density(1f)

    @Before
    fun resetGlobalCaches() {
        clearMeasurementCaches()
    }

    // ---------------------------------------------------------------- fakes

    private class FakePlaceable(desiredWidth: Int, desiredHeight: Int) : Placeable() {
        init {
            val cls = Placeable::class.java
            cls.getDeclaredField("width").apply { isAccessible = true }.setInt(this, desiredWidth)
            cls.getDeclaredField("height").apply { isAccessible = true }.setInt(this, desiredHeight)
        }

        override fun get(alignmentLine: AlignmentLine): Int = 0
        override fun placeAt(position: IntOffset, zIndex: Float, layerBlock: (GraphicsLayerScope.() -> Unit)?) = Unit
    }

    /**
     * A feed whose item heights can change between passes; counts every compose-and-measure.
     *
     * Like a real Measurable, an item may be measured only once per measure pass: a second measure() of
     * the same item throws, which crashes the app.
     */
    private class Feed(val heights: MutableList<Int>, val keys: List<Any> = heights.indices.map { "k$it" }) {
        var measured = 0
        private val measuredInThisPass = mutableSetOf<Int>()

        fun beginPass() = measuredInThisPass.clear()
        val provider: GridLayoutProvider<Int> = object : GridLayoutProvider<Int> {
            override val items: List<GridItem<Int>> = emptyList()
            override val itemCount: Int = heights.size
            override fun getItemConfig(index: Int): GridItemConfig = error("unused")
            override fun getItemKey(index: Int): String = index.toString()
            override fun getContentType(index: Int): Any? = null

            @Suppress("UNUSED_PARAMETER")
            @Composable
            override fun ItemContent(item: Int, index: Int, columnIndex: Int, columns: Int) = Unit
        }
        val enriched: List<EnrichedGridItem<Int>> = heights.indices.map {
            EnrichedGridItem(
                item = it,
                config = GridItemConfig(type = GridItemType.FullWidth),
                group = "g$it",
                groupColumns = 1,
                groupItemIndex = 0,
                key = keys[it],
                contentType = null,
            )
        }

        fun measurer(index: Int, constraints: Constraints): List<Placeable> {
            check(measuredInThisPass.add(index)) {
                "measure() may not be called multiple times on the same Measurable (item $index)"
            }
            measured++
            val h = heights[index]
            return if (h > 0) listOf(FakePlaceable(constraints.maxWidth, h)) else emptyList()
        }
    }

    private class Scroller(val feed: Feed, val cache: MeasuredHeightCache = MeasuredHeightCache()) {
        var positions: List<ItemPosition> = emptyList()
        var last: MeasurePassResult? = null

        fun pass(test: MeasurePassTest, anchor: Int = 0): MeasurePassResult {
            feed.beginPass()
            val result = measureWindow(
                layoutProvider = feed.provider,
                enrichedItems = feed.enriched,
                heightCache = cache,
                previousPositions = positions,
                anchorIndex = anchor,
                constraints = test.constraints,
                density = test.density,
                bufferSize = test.bufferSize,
                measurer = feed::measurer,
                getCachedWidth = { _, columns, maxWidth -> maxWidth / columns },
            )
            positions = result.positions
            last = result
            return result
        }

        /** Runs passes until the heights stop changing. */
        fun settle(test: MeasurePassTest, anchor: Int = 0, maxPasses: Int = 200): Int {
            var passes = 0
            do {
                val result = pass(test, anchor)
                passes++
            } while (result.heightChanges.isNotEmpty() && passes < maxPasses)
            return passes
        }
    }

    private fun feedOf(count: Int, height: Int = 200) = Feed(MutableList(count) { height })

    private fun assertEveryItemOnScreenHasAPlaceable(result: MeasurePassResult, top: Int) {
        result.positions.forEachIndexed { index, position ->
            if (position.height > 0 && position.y + position.height > top && position.y < top + viewportHeight) {
                assertNotNull("item $index is on screen but has no placeable", result.placeables[index])
            }
        }
    }

    // ------------------------------------------------------ the window

    @Test
    fun calculateMeasureRange_startsBeforeTheAnchorAndReachesPastTheViewport() {
        // viewport 1000 -> 20 items estimated, 3 viewports ahead = 60 items; the 1.0.0 minimum is 20 * 3.
        assertEquals(0..60, calculateMeasureRange(0, itemCount = 500, bufferSize = 2, viewportHeight = 1000))
        assertEquals(8..70, calculateMeasureRange(10, itemCount = 500, bufferSize = 2, viewportHeight = 1000))
    }

    @Test
    fun calculateMeasureRange_followsTheLastMeasuredItem() {
        assertEquals(0..160, calculateMeasureRange(0, itemCount = 500, bufferSize = 2, viewportHeight = 1000, lastMeasuredIndex = 100))
    }

    @Test
    fun calculateMeasureRange_isClampedToTheList() {
        assertEquals(0..29, calculateMeasureRange(0, itemCount = 30, bufferSize = 2, viewportHeight = 1000))
        assertEquals(IntRange.EMPTY, calculateMeasureRange(0, itemCount = 0, bufferSize = 2, viewportHeight = 1000))
    }

    @Test
    fun firstPass_measuresTheWholeWindowFresh_andABufferBehindIt() {
        val feed = feedOf(300)
        val scroller = Scroller(feed)

        val result = scroller.pass(this)

        // Window 0..60 is fresh and kept; the buffer after it is only measured for its height.
        assertEquals(62, result.measuredEnd)
        assertEquals(63, feed.measured)
        assertNotNull(result.placeables[60])
        assertNull(result.placeables[61])
        assertEquals(63 * 200, result.positions[62].y + result.positions[62].height)
    }

    @Test
    fun windowFollowsTheLastMeasuredItem_soContentScrolledIntoViewIsAlreadyMeasured() {
        val feed = feedOf(300)
        val scroller = Scroller(feed)
        scroller.pass(this)

        val second = scroller.pass(this)
        // The last item with a height is 62, so the window now ends at 62 + 60 = 122 (+ buffer 2).
        assertEquals(124, second.measuredEnd)

        var offset = 0
        while (offset < 120 * 200) {
            assertEveryItemOnScreenHasAPlaceable(second, offset)
            offset += 4000
        }
    }

    @Test
    fun anchorPastTheMeasuredPrefix_isPositionedInOnePass() {
        val feed = feedOf(75)
        val scroller = Scroller(feed)

        // A restored index, or scrollToItem / scrollToItemByKey, far past anything measured so far.
        val result = scroller.pass(this, anchor = 40)

        assertEquals(ItemPosition(0, 40 * 200, 1000, 200, 0), result.positions[40])
        assertEveryItemOnScreenHasAPlaceable(result, 40 * 200)
        assertNull("items before the window are measured for their height only", result.placeables[10])
    }

    @Test
    fun anchorAtTheLastItem_isPositioned() {
        val feed = feedOf(30)
        val scroller = Scroller(feed)

        val result = scroller.pass(this, anchor = 29)

        assertEquals(29 * 200, result.positions[29].y)
        assertEquals(29, result.measuredEnd)
    }

    @Test
    fun itemsBeforeTheWindow_keepTheirCachedHeight() {
        val feed = feedOf(300)
        val scroller = Scroller(feed)
        repeat(3) { scroller.pass(this) }
        val knownBefore = scroller.cache.heights.toMap()

        feed.measured = 0
        val result = scroller.pass(this, anchor = 150)

        // Nothing before the window (items 0..147) is composed again; the window itself is measured fresh.
        assertNull(result.placeables[100])
        assertNotNull(result.placeables[150])
        assertTrue("measured ${feed.measured} items", feed.measured <= 300 - 148)
        knownBefore.forEach { (index, height) -> assertEquals(height, scroller.cache.heights[index]) }
    }

    // ---------------------------------------------- scroll-back clamp

    @Test
    fun scrollBack_isSkippedWhileTheEndOfTheContentIsUnknown() {
        val positions = List(10) { ItemPosition(0, it * 200, 1000, 200, 0) } // content bottom 2000
        val unknownEnd = scrollBackToContent(positions, 0, 1500, 0, viewportHeight, contentFullyDiscovered = false)
        assertEquals(1500, unknownEnd.firstItemScrollOffset)

        val knownEnd = scrollBackToContent(positions, 0, 1500, 0, viewportHeight, contentFullyDiscovered = true)
        assertEquals(1000, knownEnd.firstItemScrollOffset)
        assertEquals(500, knownEnd.scrollDelta)
    }

    @Test
    fun restoredOffsetPastTheFirstItems_isNotThrownAwayWhileTheEndIsUnknown() {
        val feed = feedOf(300)
        val scroller = Scroller(feed)

        val result = scroller.pass(this)

        val bottom = result.positions.maxOf { it.y + it.height }
        assertTrue("content must reach the restored viewport, bottom=$bottom", bottom >= 3000 + viewportHeight)
        assertEveryItemOnScreenHasAPlaceable(result, 3000)
        val scrollBack = scrollBackToContent(
            positions = result.positions,
            anchorIndex = 0,
            anchorScrollOffset = 3000,
            scrollDelta = 0,
            viewportHeight = viewportHeight,
            contentFullyDiscovered = result.measuredEnd >= 299,
        )
        assertEquals(3000, scrollBack.firstItemScrollOffset)
        assertEquals(0, scrollBack.scrollDelta)
    }

    @Test
    fun scrollBack_clampsToTheRealEndOnceEverythingIsVisited() {
        val feed = feedOf(8) // 1600px of content
        val scroller = Scroller(feed)
        val result = scroller.pass(this)
        assertEquals(7, result.measuredEnd)

        val scrollBack = scrollBackToContent(result.positions, 0, 1500, 0, viewportHeight, contentFullyDiscovered = true)
        assertEquals(600, scrollBack.firstItemScrollOffset)
    }

    @Test
    fun trailingAnchors_areVisitedOnceTheWindowReachesTheEnd() {
        val heights = MutableList(13) { if (it < 10) 200 else 0 } // 3 trailing zero-height anchors
        val feed = Feed(heights)
        val scroller = Scroller(feed)

        val result = scroller.pass(this)

        assertEquals(12, result.measuredEnd)
    }

    // ------------------------------------- each item is measured once per pass

    @Test
    fun scrollingToTheEndOfAFeedWithTrailingAnchors_neverMeasuresAnItemTwiceInAPass() {
        // 25 items of content followed by anchors (pagination / secondary fetch) that have no height.
        val feed = Feed(MutableList(40) { if (it < 25) 200 else 0 })
        val scroller = Scroller(feed)

        repeat(120) { scroller.settle(this) }
        assertEquals(39, scroller.last!!.measuredEnd)
    }

    @Test
    fun blockThatCollapsesInTheViewport_pullsInItemsBehindEmptyBlocks_withoutMeasuringThemTwice() {
        // Items 15..18 are empty blocks (no height yet) in front of sized items.
        val heights = MutableList(40) { if (it in 15..18) 0 else 200 }
        val feed = Feed(heights)
        val scroller = Scroller(feed)
        scroller.settle(this)

        // A visible block goes back to loading and collapses. Everything below moves up.
        feed.heights[10] = 0
        val result = scroller.pass(this)

        assertEveryItemOnScreenHasAPlaceable(result, 1900)
        assertNotNull("item 19 is on screen", result.placeables[19])
    }

    @Test
    fun measureWindow_neverMeasuresAnItemTwiceInAPass_whateverTheScrollAndHeightChanges() {
        // The Feed fake throws on a second measure() of the same item in one pass, like a real Measurable.
        // Random feeds mix empty blocks with sized ones; blocks load and unload and the anchor jumps.
        repeat(300) { seed ->
            val random = java.util.Random(seed.toLong())
            val count = 20 + random.nextInt(120)
            val feed = Feed(MutableList(count) { if (random.nextInt(4) == 0) 0 else 40 + random.nextInt(700) })
            val scroller = Scroller(feed)
            repeat(60) { step ->
                if (random.nextInt(3) == 0) {
                    val changed = random.nextInt(count)
                    feed.heights[changed] = if (feed.heights[changed] > 0) 0 else 40 + random.nextInt(700)
                }
                val anchor = if (random.nextInt(8) == 0) random.nextInt(count) else 0
                try {
                    scroller.pass(this, anchor = anchor)
                } catch (e: IllegalStateException) {
                    throw AssertionError("seed $seed step $step anchor $anchor: ${e.message}", e)
                }
            }
        }
    }

    // ---------------------------------------------------- height changes

    @Test
    fun blockThatLoadsAboveTheViewport_leavesNoUnplacedItemOnScreen() {
        val feed = feedOf(40)
        val scroller = Scroller(feed)
        scroller.settle(this)

        feed.heights[2] = 900
        val result = scroller.pass(this)
        assertEveryItemOnScreenHasAPlaceable(result, 2000)
    }

    // -------------------------------------------------- height cache

    @Test
    fun appendedItems_keepTheHeightsAlreadyKnown() {
        val feed = feedOf(20)
        val scroller = Scroller(feed)
        scroller.settle(this)
        val known = scroller.cache.heights.toMap()
        assertTrue(known.isNotEmpty())

        val grown = Feed(MutableList(40) { 200 }) // same keys for 0..19, 20 new items
        val next = Scroller(grown, scroller.cache)
        next.positions = scroller.positions
        next.pass(this)

        known.forEach { (index, height) -> assertEquals(height, scroller.cache.heights[index]) }
    }

    @Test
    fun changedKey_dropsTheHeightsFromThatIndexOn() {
        val cache = MeasuredHeightCache()
        val original = Feed(MutableList(10) { 100 })
        assertFalse(cache.syncWith(original.enriched))
        (0 until 10).forEach { cache.heights[it] = 100 }

        val reordered = Feed(MutableList(10) { 100 }, keys = List(10) { if (it < 4) "k$it" else "other$it" })
        assertTrue(cache.syncWith(reordered.enriched))

        assertEquals(setOf(0, 1, 2, 3), cache.heights.keys)
    }

    @Test
    fun removedItems_dropTheirHeights() {
        val cache = MeasuredHeightCache()
        cache.syncWith(Feed(MutableList(10) { 100 }).enriched)
        (0 until 10).forEach { cache.heights[it] = 100 }

        assertFalse(cache.syncWith(Feed(MutableList(6) { 100 }).enriched))

        assertEquals((0 until 6).toSet(), cache.heights.keys)
    }
}
