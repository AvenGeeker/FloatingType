package com.avengeeker.floatingtype.accessibility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BallVisibilityPolicyTest {

    @Test
    fun showsWhileKeyboardIsUpAndFieldIsEditable() {
        assertTrue(BallVisibilityPolicy.shouldShow(imeVisible = true, hasEditableFocus = true, sessionBusy = false))
    }

    @Test
    fun staysHiddenWhileKeyboardIsNotUpYet() {
        // Right after a tap the IME window does not exist yet; the ball must
        // wait for the keyboard instead of flashing and disappearing.
        assertFalse(BallVisibilityPolicy.shouldShow(imeVisible = false, hasEditableFocus = true, sessionBusy = false))
        assertFalse(BallVisibilityPolicy.shouldShow(imeVisible = false, hasEditableFocus = false, sessionBusy = false))
    }

    @Test
    fun hidesWhenKeyboardCollapsesEvenIfFieldKeepsFocus() {
        // Android keeps input focus on the EditText after BACK dismisses the
        // IME, so focus alone must not keep the ball on screen.
        assertFalse(BallVisibilityPolicy.shouldShow(imeVisible = false, hasEditableFocus = true, sessionBusy = false))
    }

    @Test
    fun hidesWhenKeyboardIsUpButFocusIsNotEditable() {
        assertFalse(BallVisibilityPolicy.shouldShow(imeVisible = true, hasEditableFocus = false, sessionBusy = false))
    }

    @Test
    fun runningSessionKeepsTheBallRegardlessOfKeyboard() {
        assertTrue(BallVisibilityPolicy.shouldShow(imeVisible = false, hasEditableFocus = false, sessionBusy = true))
        assertTrue(BallVisibilityPolicy.shouldShow(imeVisible = true, hasEditableFocus = true, sessionBusy = true))
    }

    @Test
    fun decideShowsWheneverConditionHolds() {
        assertEquals(
            BallVisibilityPolicy.Decision.SHOW,
            BallVisibilityPolicy.decide(shouldShow = true, currentlyVisible = false, unavailableForMs = 0, graceMs = 300)
        )
        assertEquals(
            BallVisibilityPolicy.Decision.SHOW,
            BallVisibilityPolicy.decide(shouldShow = true, currentlyVisible = true, unavailableForMs = 9999, graceMs = 300)
        )
    }

    @Test
    fun decideHidesImmediatelyWhenAlreadyHidden() {
        assertEquals(
            BallVisibilityPolicy.Decision.HIDE,
            BallVisibilityPolicy.decide(shouldShow = false, currentlyVisible = false, unavailableForMs = 0, graceMs = 300)
        )
    }

    @Test
    fun decideRetriesDuringGraceSoTransitionsDoNotFlicker() {
        assertEquals(
            BallVisibilityPolicy.Decision.RETRY,
            BallVisibilityPolicy.decide(shouldShow = false, currentlyVisible = true, unavailableForMs = 0, graceMs = 300)
        )
        assertEquals(
            BallVisibilityPolicy.Decision.RETRY,
            BallVisibilityPolicy.decide(shouldShow = false, currentlyVisible = true, unavailableForMs = 299, graceMs = 300)
        )
    }

    @Test
    fun decideHidesAfterGraceExpires() {
        assertEquals(
            BallVisibilityPolicy.Decision.HIDE,
            BallVisibilityPolicy.decide(shouldShow = false, currentlyVisible = true, unavailableForMs = 300, graceMs = 300)
        )
        assertEquals(
            BallVisibilityPolicy.Decision.HIDE,
            BallVisibilityPolicy.decide(shouldShow = false, currentlyVisible = true, unavailableForMs = 5000, graceMs = 300)
        )
    }

    @Test
    fun recheckIsImmediateWhenNoEvaluationHappenedYet() {
        assertEquals(0L, BallVisibilityPolicy.recheckDelayMs(lastEvaluateAtMs = 0, nowMs = 1000, minIntervalMs = 80))
    }

    @Test
    fun recheckIsImmediateWhenIntervalAlreadyElapsed() {
        assertEquals(0L, BallVisibilityPolicy.recheckDelayMs(lastEvaluateAtMs = 1000, nowMs = 1080, minIntervalMs = 80))
        assertEquals(0L, BallVisibilityPolicy.recheckDelayMs(lastEvaluateAtMs = 1000, nowMs = 1500, minIntervalMs = 80))
    }

    @Test
    fun recheckCoalescesBurstsIntoTheRemainingInterval() {
        assertEquals(74L, BallVisibilityPolicy.recheckDelayMs(lastEvaluateAtMs = 1000, nowMs = 1006, minIntervalMs = 80))
        assertEquals(1L, BallVisibilityPolicy.recheckDelayMs(lastEvaluateAtMs = 1000, nowMs = 1079, minIntervalMs = 80))
    }

    @Test
    fun recheckWithoutThrottleIsImmediate() {
        assertEquals(0L, BallVisibilityPolicy.recheckDelayMs(lastEvaluateAtMs = 1000, nowMs = 1001, minIntervalMs = 0))
    }
}
