package com.lazyadaptivelayout.state

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateTo
import androidx.compose.animation.core.copy
import androidx.compose.animation.core.spring
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Manages scroll delta between lookahead and approach passes, animating back-scroll to avoid
 * sudden jumps during size/placement animations.
 */
internal class AdaptiveGridScrollDeltaBetweenPasses {

    internal val scrollDeltaBetweenPasses: Float
        get() = _scrollDeltaBetweenPasses.value

    internal var job: Job? = null

    internal val isActive: Boolean
        get() = _scrollDeltaBetweenPasses.value != 0f

    private var _scrollDeltaBetweenPasses: AnimationState<Float, AnimationVector1D> =
        AnimationState(Float.VectorConverter, 0f, 0f)

    internal fun updateScrollDeltaForApproach(
        delta: Float,
        density: Density,
        coroutineScope: CoroutineScope,
    ) {
        if (delta <= with(density) { DeltaThresholdForScrollAnimation.toPx() }) {
            return
        }

        Snapshot.withoutReadObservation {
            val currentDelta = _scrollDeltaBetweenPasses.value

            job?.cancel()
            if (_scrollDeltaBetweenPasses.isRunning) {
                _scrollDeltaBetweenPasses = _scrollDeltaBetweenPasses.copy(currentDelta - delta)
            } else {
                _scrollDeltaBetweenPasses = AnimationState(Float.VectorConverter, -delta)
            }
            job =
                coroutineScope.launch {
                    _scrollDeltaBetweenPasses.animateTo(
                        0f,
                        spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.5f),
                        true,
                    )
                }
        }
    }

    internal fun stop() {
        job?.cancel()
        _scrollDeltaBetweenPasses = AnimationState(Float.VectorConverter, 0f)
    }
}

private val DeltaThresholdForScrollAnimation = 1.dp

