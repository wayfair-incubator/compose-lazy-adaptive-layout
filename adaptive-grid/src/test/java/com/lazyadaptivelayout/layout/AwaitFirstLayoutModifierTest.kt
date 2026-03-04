package com.lazyadaptivelayout.layout

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AwaitFirstLayoutModifierTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun waitForFirstLayout_whenCalledBeforeContent_suspendsUntilFirstLayout() = runBlocking {
        val modifier = AwaitFirstLayoutModifier()

        val waiting = async(start = CoroutineStart.UNDISPATCHED) { modifier.waitForFirstLayout() }
        assertFalse(waiting.isCompleted)

        composeTestRule.setContent {
            Box(Modifier.size(10.dp).then(modifier))
        }
        composeTestRule.waitForIdle()

        withTimeout(5_000) {
            waiting.await()
        }
    }

    @Test
    fun waitForFirstLayout_fromLaunchedEffect_completes() = runBlocking {
        val modifier = AwaitFirstLayoutModifier()
        val completed = CompletableDeferred<Unit>()

        composeTestRule.setContent {
            LaunchedEffect(Unit) {
                modifier.waitForFirstLayout()
                completed.complete(Unit)
            }
            Box(Modifier.size(10.dp).then(modifier))
        }

        composeTestRule.waitForIdle()
        withTimeout(5_000) {
            completed.await()
        }
    }

    @Test
    fun waitForFirstLayout_canBeAwaitedAgain_forNextLayoutPass() = runBlocking {
        val modifier = AwaitFirstLayoutModifier()
        var show by mutableIntStateOf(0)

        val firstWait = async(start = CoroutineStart.UNDISPATCHED) { modifier.waitForFirstLayout() }
        assertFalse(firstWait.isCompleted)

        composeTestRule.setContent {
            if (show == 0) {
                Box(Modifier.size(10.dp).then(modifier))
            }
        }
        composeTestRule.waitForIdle()

        withTimeout(5_000) { firstWait.await() }

        // Detach the node.
        composeTestRule.runOnIdle { show = 1 }
        composeTestRule.waitForIdle()

        // Start waiting again BEFORE re-attaching. onAttach() should register the callback.
        val secondWait = async(start = CoroutineStart.UNDISPATCHED) { modifier.waitForFirstLayout() }
        assertFalse(secondWait.isCompleted)

        composeTestRule.runOnIdle { show = 0 }
        composeTestRule.waitForIdle()

        withTimeout(5_000) { secondWait.await() }
    }
}
