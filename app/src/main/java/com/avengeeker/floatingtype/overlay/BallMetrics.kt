package com.avengeeker.floatingtype.overlay

import kotlin.math.ceil

/**
 * Pure pixel geometry shared by [BallView] (drawing) and [OverlayBallManager]
 * (window sizing). Kept free of Android types so the ripple-fit invariant can
 * be unit-tested on the JVM.
 *
 * The recording ripple expands past the visible ball, so the overlay window
 * must reserve padding on every side; otherwise the ring is clipped by the
 * window bounds and the animation looks "eaten". The visible ball keeps its
 * configured diameter — only the window grows to make room for the ripple.
 */
object BallMetrics {

    /** Gap between the ball edge and the ripple start, as a fraction of the ball diameter. */
    const val RING_SPACE_FRACTION = 0.08f

    /** Ripple expansion beyond the ball, in multiples of the ring space. */
    const val RIPPLE_EXPANSION_FACTOR = 2.2f

    /** Ripple stroke width as a fraction of the ball diameter. */
    const val RIPPLE_STROKE_FRACTION = 0.045f

    /** Window padding reserved per side for the ripple, as a fraction of the ball diameter. */
    const val RIPPLE_PADDING_FRACTION = 0.15f

    fun ringSpace(ballDiameterPx: Int): Float = ballDiameterPx * RING_SPACE_FRACTION

    fun ballRadius(ballDiameterPx: Int): Float = ballDiameterPx / 2f - ringSpace(ballDiameterPx)

    fun rippleStrokeWidth(ballDiameterPx: Int): Float = ballDiameterPx * RIPPLE_STROKE_FRACTION

    /** Outer edge of the ripple ring at full expansion, measured from the ball center. */
    fun maxRippleOuterRadius(ballDiameterPx: Int): Float =
        ballRadius(ballDiameterPx) +
            ringSpace(ballDiameterPx) * RIPPLE_EXPANSION_FACTOR +
            rippleStrokeWidth(ballDiameterPx) / 2f

    fun ripplePadding(ballDiameterPx: Int): Int =
        ceil(ballDiameterPx * RIPPLE_PADDING_FRACTION).toInt()

    /** Full overlay window side length: the ball plus ripple padding on both sides. */
    fun windowSize(ballDiameterPx: Int): Int =
        ballDiameterPx + 2 * ripplePadding(ballDiameterPx)
}
