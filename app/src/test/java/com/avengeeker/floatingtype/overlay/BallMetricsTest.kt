package com.avengeeker.floatingtype.overlay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the fix for the recording ripple being clipped ("被吃掉一部分"):
 * the overlay window must reserve enough room for the ripple at full expansion.
 */
class BallMetricsTest {

    @Test
    fun rippleFitsInsideWindowForEverySupportedBallSize() {
        // 40..72 dp at any realistic density (1x..4x) -> ~40..288 px; sweep wider
        // to be safe. The ripple's outer edge must never exceed the window radius.
        for (diameter in 20..600) {
            val windowRadius = BallMetrics.windowSize(diameter) / 2f
            val rippleOuter = BallMetrics.maxRippleOuterRadius(diameter)
            assertTrue(
                "ripple clipped at diameter=$diameter: outer=$rippleOuter > windowRadius=$windowRadius",
                rippleOuter <= windowRadius,
            )
        }
    }

    @Test
    fun windowReservesPositivePaddingOnEverySide() {
        for (diameter in 20..600) {
            val padding = BallMetrics.ripplePadding(diameter)
            assertTrue("padding must be positive at diameter=$diameter", padding > 0)
            assertEquals(BallMetrics.windowSize(diameter), diameter + 2 * padding)
            assertTrue(BallMetrics.windowSize(diameter) > diameter)
        }
    }

    @Test
    fun visibleBallKeepsItsConfiguredDiameter() {
        // The ball circle is inset by the ring space, exactly as before the fix;
        // growing the window must not shrink the visible ball.
        val diameter = 156 // 52dp @3x
        assertEquals(0.42f * diameter, BallMetrics.ballRadius(diameter), 0.001f)
    }

    @Test
    fun rippleActuallyExpandsBeyondTheBall() {
        // Sanity: the effect is a real outward ripple, not a static ring.
        val diameter = 156
        assertTrue(BallMetrics.maxRippleOuterRadius(diameter) > BallMetrics.ballRadius(diameter))
    }
}
