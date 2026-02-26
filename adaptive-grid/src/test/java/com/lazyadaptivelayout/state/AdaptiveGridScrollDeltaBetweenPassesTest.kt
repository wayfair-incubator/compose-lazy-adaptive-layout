package com.lazyadaptivelayout.state

import androidx.compose.runtime.MonotonicFrameClock
import androidx.compose.ui.unit.Density
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AdaptiveGridScrollDeltaBetweenPassesTest {

    private class ManualFrameClock(
        private val frameAdvanceNanos: Long = 16_000_000L,
    ) : MonotonicFrameClock {

        private var frameTimeNanos: Long = 0L
        private val frames = Channel<Long>(capacity = Channel.UNLIMITED)

        fun sendFrame() {
            frameTimeNanos += frameAdvanceNanos
            val result = frames.trySend(frameTimeNanos)
            check(result.isSuccess)
        }

        override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
            return onFrame(frames.receive())
        }
    }

    private suspend fun TestScope.driveAnimationToCompletion(
        state: AdaptiveGridScrollDeltaBetweenPasses,
        frameClock: ManualFrameClock,
        framesBudget: Int = 10_000,
    ) {
        withTimeout(5_000) {
            repeat(framesBudget) {
                if (state.job?.isActive != true) return@withTimeout
                frameClock.sendFrame()
                runCurrent()
            }
            // If we get here, we ran out of frames while the job was still active.
            error(
                "Animation did not complete within $framesBudget frames; " +
                    "delta=${state.scrollDeltaBetweenPasses}, jobActive=${state.job?.isActive}",
            )
        }
    }

    @Test
    fun initialState_isInactive_andHasNoJob() {
        val state = AdaptiveGridScrollDeltaBetweenPasses()

        assertEquals(0f, state.scrollDeltaBetweenPasses, 0f)
        assertFalse(state.isActive)
        assertNull(state.job)
    }

    @Test
    fun updateScrollDeltaForApproach_deltaBelowThreshold_doesNothing() = runTest {
        val state = AdaptiveGridScrollDeltaBetweenPasses()
        val density = Density(1f)
        val frameClock = ManualFrameClock()
        val scope = CoroutineScope(coroutineContext + frameClock)

        state.updateScrollDeltaForApproach(
            delta = 0.5f,
            density = density,
            coroutineScope = scope,
        )

        assertNull(state.job)
        assertEquals(0f, state.scrollDeltaBetweenPasses, 0f)
        assertFalse(state.isActive)
    }

    @Test
    fun updateScrollDeltaForApproach_deltaEqualToThreshold_doesNothing() = runTest {
        val state = AdaptiveGridScrollDeltaBetweenPasses()
        val density = Density(1f)
        val frameClock = ManualFrameClock()
        val scope = CoroutineScope(coroutineContext + frameClock)

        state.updateScrollDeltaForApproach(
            delta = 1f,
            density = density,
            coroutineScope = scope,
        )

        assertNull(state.job)
        assertEquals(0f, state.scrollDeltaBetweenPasses, 0f)
        assertFalse(state.isActive)
    }

    @Test
    fun updateScrollDeltaForApproach_deltaIsNegative_doesNothing() = runTest {
        val state = AdaptiveGridScrollDeltaBetweenPasses()
        val density = Density(1f)
        val frameClock = ManualFrameClock()
        val scope = CoroutineScope(coroutineContext + frameClock)

        state.updateScrollDeltaForApproach(
            delta = -100f,
            density = density,
            coroutineScope = scope,
        )

        assertNull(state.job)
        assertEquals(0f, state.scrollDeltaBetweenPasses, 0f)
        assertFalse(state.isActive)
    }

    @Test
    fun updateScrollDeltaForApproach_thresholdRespectsDensity() = runTest {
        val state = AdaptiveGridScrollDeltaBetweenPasses()
        val density = Density(2f) // 1.dp == 2px threshold
        val frameClock = ManualFrameClock()
        val scope = CoroutineScope(coroutineContext + frameClock)

        state.updateScrollDeltaForApproach(
            delta = 1.9f,
            density = density,
            coroutineScope = scope,
        )

        assertNull(state.job)
        assertEquals(0f, state.scrollDeltaBetweenPasses, 0f)
        assertFalse(state.isActive)

        state.updateScrollDeltaForApproach(
            delta = 2.1f,
            density = density,
            coroutineScope = scope,
        )

        assertNotNull(state.job)
        assertTrue(state.isActive)
        assertEquals(-2.1f, state.scrollDeltaBetweenPasses, 0.0001f)

        // Ensure we don't leak the animation coroutine at test end.
        state.stop()
        runCurrent()
        assertFalse(state.isActive)
    }

    @Test
    fun updateScrollDeltaForApproach_deltaAboveThreshold_startsAnimation_andEventuallySettlesToZero() = runTest {
        val state = AdaptiveGridScrollDeltaBetweenPasses()
        val density = Density(1f)
        val frameClock = ManualFrameClock()
        val scope = CoroutineScope(coroutineContext + frameClock)

        state.updateScrollDeltaForApproach(
            delta = 2f,
            density = density,
            coroutineScope = scope,
        )

        assertNotNull(state.job)
        assertTrue(state.isActive)
        assertEquals(-2f, state.scrollDeltaBetweenPasses, 0.0001f)

        driveAnimationToCompletion(state, frameClock)

        // Spring uses a 0.5f visibility threshold; allow small tolerance.
        assertTrue(abs(state.scrollDeltaBetweenPasses) <= 0.5f)
        assertFalse(state.isActive)
        assertTrue(state.job?.isActive != true)
    }

    @Test
    fun updateScrollDeltaForApproach_deltaBelowThreshold_doesNotCancelAnOngoingAnimation() = runTest {
        val state = AdaptiveGridScrollDeltaBetweenPasses()
        val density = Density(1f)
        val frameClock = ManualFrameClock()
        val scope = CoroutineScope(coroutineContext + frameClock)

        state.updateScrollDeltaForApproach(
            delta = 3f,
            density = density,
            coroutineScope = scope,
        )
        val jobBefore = state.job
        assertNotNull(jobBefore)

        // This returns early and should not touch/cancel the current animation.
        state.updateScrollDeltaForApproach(
            delta = 0.5f,
            density = density,
            coroutineScope = scope,
        )

        assertSame(jobBefore, state.job)
        assertTrue(state.job?.isActive == true)

        driveAnimationToCompletion(state, frameClock)
        assertFalse(state.isActive)
    }

    @Test
    fun updateScrollDeltaForApproach_calledWhileRunning_cancelsPreviousJob_andAccumulatesDelta() = runTest {
        val state = AdaptiveGridScrollDeltaBetweenPasses()
        val density = Density(1f)
        val frameClock = ManualFrameClock()
        val scope = CoroutineScope(coroutineContext + frameClock)

        state.updateScrollDeltaForApproach(
            delta = 5f,
            density = density,
            coroutineScope = scope,
        )
        val firstJob = state.job
        assertNotNull(firstJob)
        assertEquals(-5f, state.scrollDeltaBetweenPasses, 0.0001f)

        // Let the animation start progressing so we take the "isRunning" path.
        frameClock.sendFrame()
        runCurrent()

        val beforeSecondUpdate = state.scrollDeltaBetweenPasses

        state.updateScrollDeltaForApproach(
            delta = 3f,
            density = density,
            coroutineScope = scope,
        )

        val secondJob = state.job
        assertNotNull(secondJob)
        assertNotSame(firstJob, secondJob)
        assertTrue(firstJob?.isCancelled == true || firstJob?.isActive == false)

        // When running, the new delta state is based on currentDelta - delta.
        assertEquals(beforeSecondUpdate - 3f, state.scrollDeltaBetweenPasses, 0.001f)

        driveAnimationToCompletion(state, frameClock)
        assertFalse(state.isActive)
    }

    @Test
    fun stop_cancelsJob_andResetsDeltaToZero_andRemainsZeroAfterMoreFrames() = runTest {
        val state = AdaptiveGridScrollDeltaBetweenPasses()
        val density = Density(1f)
        val frameClock = ManualFrameClock()
        val scope = CoroutineScope(coroutineContext + frameClock)

        state.updateScrollDeltaForApproach(
            delta = 2f,
            density = density,
            coroutineScope = scope,
        )
        val jobBeforeStop = state.job
        assertNotNull(jobBeforeStop)

        state.stop()

        assertEquals(0f, state.scrollDeltaBetweenPasses, 0f)
        assertFalse(state.isActive)
        assertTrue(jobBeforeStop?.isCancelled == true || jobBeforeStop?.isActive == false)

        // Even if frames are produced, there should be no animation running anymore.
        repeat(10) {
            frameClock.sendFrame()
            runCurrent()
        }
        assertEquals(0f, state.scrollDeltaBetweenPasses, 0f)
        assertFalse(state.isActive)
    }
}
