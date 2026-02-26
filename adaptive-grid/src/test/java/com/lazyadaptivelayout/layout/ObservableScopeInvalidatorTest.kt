package com.lazyadaptivelayout.layout

import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ObservableScopeInvalidatorTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun invalidateScope_whenAttached_triggersRecomposition() {
        val invalidator = ObservableScopeInvalidator()
        val sideEffectRuns = AtomicInteger(0)

        composeTestRule.setContent {
            invalidator.attachToScope()
            SideEffect {
                sideEffectRuns.incrementAndGet()
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(1, sideEffectRuns.get())

        composeTestRule.runOnIdle { invalidator.invalidateScope() }
        composeTestRule.waitForIdle()
        assertEquals(2, sideEffectRuns.get())

        composeTestRule.runOnIdle { invalidator.invalidateScope() }
        composeTestRule.waitForIdle()
        assertEquals(3, sideEffectRuns.get())
    }

    @Test
    fun invalidateScope_whenNotAttached_doesNotTriggerRecomposition() {
        val invalidator = ObservableScopeInvalidator()
        val sideEffectRuns = AtomicInteger(0)

        composeTestRule.setContent {
            SideEffect {
                sideEffectRuns.incrementAndGet()
            }
        }

        composeTestRule.waitForIdle()
        assertEquals(1, sideEffectRuns.get())

        composeTestRule.runOnIdle { invalidator.invalidateScope() }
        composeTestRule.waitForIdle()
        assertEquals(1, sideEffectRuns.get())
    }
}
