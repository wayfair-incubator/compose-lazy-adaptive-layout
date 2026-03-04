package com.lazyadaptivelayout.state

import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.MeasureResult
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import com.lazyadaptivelayout.model.AdaptiveGridItemInfo
import com.lazyadaptivelayout.model.GridItemType
import com.lazyadaptivelayout.model.ItemPosition
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveGridMeasureResultTest {

    private fun measureResult(width: Int = 0, height: Int = 0): MeasureResult =
        object : MeasureResult {
            override val alignmentLines: Map<AlignmentLine, Int> = emptyMap()
            override val height: Int = height
            override val width: Int = width
            override fun placeChildren() {}
        }

    private fun item(
        index: Int,
        key: Any = "k$index",
        offset: Int,
        size: Int = 10,
        y: Int,
        type: GridItemType = GridItemType.Uniform
    ): AdaptiveGridItemInfo =
        AdaptiveGridItemInfo(
            index = index,
            key = key,
            offset = offset,
            size = size,
            position = ItemPosition(
                x = 0,
                y = y,
                width = 100,
                height = size,
                columnIndex = 0
            ),
            layoutType = type
        )

    private fun result(
        firstVisibleItem: AdaptiveGridItemInfo?,
        firstVisibleItemScrollOffset: Int,
        canScrollForward: Boolean = false,
        consumedScroll: Float = 0f,
        scrollBackAmount: Float = 0f,
        remeasureNeeded: Boolean = false,
        visibleItemsInfo: List<AdaptiveGridItemInfo>,
        viewportStartOffset: Int = 0,
        viewportEndOffset: Int = 100,
        totalItemsCount: Int = 0,
        viewportSize: Int = viewportEndOffset - viewportStartOffset,
        orientation: Orientation = Orientation.Vertical,
        measureResult: MeasureResult = measureResult(width = 300, height = 400),
    ): AdaptiveGridMeasureResult =
        AdaptiveGridMeasureResult(
            firstVisibleItem = firstVisibleItem,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
            canScrollForward = canScrollForward,
            consumedScroll = consumedScroll,
            measureResult = measureResult,
            scrollBackAmount = scrollBackAmount,
            remeasureNeeded = remeasureNeeded,
            coroutineScope = CoroutineScope(EmptyCoroutineContext),
            density = Density(1f, 1f),
            childConstraints = Constraints(),
            visibleItemsInfo = visibleItemsInfo,
            viewportStartOffset = viewportStartOffset,
            viewportEndOffset = viewportEndOffset,
            totalItemsCount = totalItemsCount,
            viewportSize = viewportSize,
            orientation = orientation
        )

    @Test
    fun toLayoutInfo_containsExpectedFields_andIsStableReference() {
        val items = listOf(
            item(index = 0, offset = 0, y = 0),
            item(index = 1, offset = 10, y = 10)
        )
        val measure = measureResult(width = 123, height = 456)
        val r =
            result(
                firstVisibleItem = items.first(),
                firstVisibleItemScrollOffset = 0,
                visibleItemsInfo = items,
                viewportStartOffset = 5,
                viewportEndOffset = 105,
                totalItemsCount = 10,
                viewportSize = 100,
                orientation = Orientation.Horizontal,
                measureResult = measure
            )

        val info1 = r.toLayoutInfo()
        val info2 = r.toLayoutInfo()

        assertSame(info1, info2)
        assertEquals(items, info1.visibleItemsInfo)
        assertEquals(5, info1.viewportStartOffset)
        assertEquals(105, info1.viewportEndOffset)
        assertEquals(10, info1.totalItemsCount)
        assertEquals(100, info1.viewportSize)
        assertEquals(Orientation.Horizontal, info1.orientation)

        // MeasureResult delegation should still work
        assertEquals(123, r.width)
        assertEquals(456, r.height)
    }

    @Test
    fun canScrollBackward_isFalse_whenFirstIndex0_andOffset0() {
        val first = item(index = 0, offset = 0, y = 0)
        val r = result(
            firstVisibleItem = first,
            firstVisibleItemScrollOffset = 0,
            visibleItemsInfo = listOf(first),
            viewportSize = 100
        )

        assertFalse(r.canScrollBackward)
    }

    @Test
    fun canScrollBackward_isTrue_whenFirstVisibleItemNull_butOffsetNonZero() {
        val r =
            result(
                firstVisibleItem = null,
                firstVisibleItemScrollOffset = 10,
                visibleItemsInfo = listOf(item(index = 0, offset = 0, y = 0)),
                viewportSize = 100
            )

        assertTrue(r.canScrollBackward)
    }

    @Test
    fun copyWithScrollDeltaWithoutRemeasure_returnsNull_whenRemeasureNeeded() {
        val first = item(index = 0, offset = 0, y = 0)
        val r =
            result(
                firstVisibleItem = first,
                firstVisibleItemScrollOffset = 0,
                remeasureNeeded = true,
                visibleItemsInfo = listOf(first),
                viewportSize = 100
            )

        val updated = r.copyWithScrollDeltaWithoutRemeasure(delta = 1, updateAnimations = false)
        assertNull(updated)
    }

    @Test
    fun copyWithScrollDeltaWithoutRemeasure_returnsNull_whenVisibleItemsEmpty() {
        val first = item(index = 0, offset = 0, y = 0)
        val r =
            result(
                firstVisibleItem = first,
                firstVisibleItemScrollOffset = 0,
                visibleItemsInfo = emptyList(),
                viewportSize = 100
            )

        val updated = r.copyWithScrollDeltaWithoutRemeasure(delta = 1, updateAnimations = false)
        assertNull(updated)
    }

    @Test
    fun copyWithScrollDeltaWithoutRemeasure_returnsNull_whenFirstVisibleItemNull() {
        val r =
            result(
                firstVisibleItem = null,
                firstVisibleItemScrollOffset = 0,
                visibleItemsInfo = listOf(item(index = 0, offset = 0, y = 0)),
                viewportSize = 100
            )

        val updated = r.copyWithScrollDeltaWithoutRemeasure(delta = 1, updateAnimations = false)
        assertNull(updated)
    }

    @Test
    fun copyWithScrollDeltaWithoutRemeasure_returnsNull_whenDeltaIsTooLargeOrAtLimit() {
        val first = item(index = 0, offset = 0, y = 0)
        val r =
            result(
                firstVisibleItem = first,
                firstVisibleItemScrollOffset = 0,
                visibleItemsInfo = listOf(first),
                viewportSize = 100
            )

        // abs(delta) must be strictly less than viewportSize * 20
        assertNull(r.copyWithScrollDeltaWithoutRemeasure(delta = 2000, updateAnimations = false))
        assertNull(r.copyWithScrollDeltaWithoutRemeasure(delta = -2000, updateAnimations = false))
    }

    @Test
    fun copyWithScrollDeltaWithoutRemeasure_appliesDelta_updatesItems_andClampsScrollOffsetToZero() {
        val i0 = item(index = 0, offset = 100, y = 100)
        val i1 = item(index = 1, offset = 150, y = 150)
        val items = listOf(i0, i1)

        val r =
            result(
                firstVisibleItem = i0,
                firstVisibleItemScrollOffset = 10,
                canScrollForward = false,
                visibleItemsInfo = items,
                viewportSize = 100
            )

        val updated = r.copyWithScrollDeltaWithoutRemeasure(delta = 30, updateAnimations = true)
        assertNotNull(updated)
        updated!!

        assertNotSame(items, updated.visibleItemsInfo)
        assertEquals(listOf(100 - 30, 150 - 30), updated.visibleItemsInfo.map { it.offset })
        assertEquals(listOf(100 - 30, 150 - 30), updated.visibleItemsInfo.map { it.position.y })

        // 10 - 30 = -20 -> clamp to 0
        assertEquals(0, updated.firstVisibleItemScrollOffset)
        assertEquals(30f, updated.consumedScroll)
        assertTrue(updated.canScrollForward)
        assertSame(r.firstVisibleItem, updated.firstVisibleItem)
    }

    @Test
    fun copyWithScrollDeltaWithoutRemeasure_allowsPositiveScrollOffsetOvershoot_andDoesNotForceCanScrollForwardForNegativeDelta() {
        val i0 = item(index = 0, offset = 0, y = 0)
        val items = listOf(i0)
        val r =
            result(
                firstVisibleItem = i0,
                firstVisibleItemScrollOffset = 10,
                canScrollForward = false,
                visibleItemsInfo = items,
                viewportSize = 100
            )

        val updated = r.copyWithScrollDeltaWithoutRemeasure(delta = -50, updateAnimations = false)
        assertNotNull(updated)
        updated!!

        // 10 - (-50) = 60 (no upper clamp)
        assertEquals(60, updated.firstVisibleItemScrollOffset)
        assertEquals(-50f, updated.consumedScroll)
        assertFalse(updated.canScrollForward)
        assertEquals(listOf(50), updated.visibleItemsInfo.map { it.offset })
        assertEquals(listOf(50), updated.visibleItemsInfo.map { it.position.y })
    }

    @Test
    fun copyWithScrollDeltaWithoutRemeasure_updateAnimationsFlag_doesNotAffectResult() {
        val i0 = item(index = 0, offset = 10, y = 10)
        val r =
            result(
                firstVisibleItem = i0,
                firstVisibleItemScrollOffset = 10,
                canScrollForward = true,
                visibleItemsInfo = listOf(i0),
                viewportSize = 100
            )

        val a = r.copyWithScrollDeltaWithoutRemeasure(delta = 5, updateAnimations = false)
        val b = r.copyWithScrollDeltaWithoutRemeasure(delta = 5, updateAnimations = true)

        assertNotNull(a)
        assertNotNull(b)
        a!!
        b!!

        assertEquals(a.firstVisibleItemScrollOffset, b.firstVisibleItemScrollOffset)
        assertEquals(a.canScrollForward, b.canScrollForward)
        assertEquals(a.consumedScroll, b.consumedScroll)
        assertEquals(a.visibleItemsInfo, b.visibleItemsInfo)
        assertEquals(a.viewportSize, b.viewportSize)
        assertEquals(a.orientation, b.orientation)
    }

    @Test
    fun emptyAdaptiveGridMeasureResult_hasExpectedDefaults() {
        val r = EmptyAdaptiveGridMeasureResult
        val info = r.toLayoutInfo()

        assertTrue(info.visibleItemsInfo.isEmpty())
        assertEquals(0, info.viewportStartOffset)
        assertEquals(0, info.viewportEndOffset)
        assertEquals(0, info.totalItemsCount)
        assertEquals(0, info.viewportSize)
        assertEquals(Orientation.Vertical, info.orientation)

        assertEquals(0, r.width)
        assertEquals(0, r.height)
        assertFalse(r.canScrollBackward)
        assertFalse(r.canScrollForward)
        assertEquals(0f, r.consumedScroll)

        assertNull(r.copyWithScrollDeltaWithoutRemeasure(delta = 1, updateAnimations = false))
    }
}
