package com.avengeeker.floatingtype.overlay

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Pure layout + hit-testing for the long-press radial menu, free of Android types
 * so the math can be unit-tested on the JVM.
 *
 * The menu is a **fan/sector**: the floating ball is the sector's center (apex) and
 * the capsules sit on an arc of a fixed radius around it, spread across
 * [FAN_SWEEP_DEG]. The caller points the fan away from the nearest screen edge
 * (toward the open space) by choosing the start angle, so a ball on the right
 * opens left, a ball bottom-left opens upper-right, and so on.
 *
 * Screen coordinates are y-down: angle 0 right, 90 down, 180 left, 270 up.
 * Hit-testing is by proximity to each capsule center, so gaps and the center
 * select nothing.
 */
object RadialMenuGeometry {

    /** Angular width of the fan. */
    const val FAN_SWEEP_DEG = 120.0

    /**
     * Direction (degrees, y-down) the fan should open: straight away from the
     * nearest screen edge, so the fan's bisector is perpendicular to that edge and
     * the items are mirror-symmetric about it. A ball near the right edge opens
     * left (horizontal -> top/bottom symmetry about the X axis); near the bottom it
     * opens up; in a corner it opens diagonally. Edges within [tolerance] times the
     * nearest distance also contribute, which produces the diagonal in corners.
     *
     * [bottomEdge] is the effective bottom boundary (the keyboard top), so a ball
     * resting on the keyboard opens upward. Falls back to straight up when the ball
     * is equidistant from every edge.
     */
    fun openDirectionDeg(
        ballX: Float,
        ballY: Float,
        screenW: Float,
        bottomEdge: Float,
        tolerance: Float = 1.5f,
    ): Double {
        val dl = ballX
        val dr = screenW - ballX
        val dt = ballY
        val db = bottomEdge - ballY
        val nearest = minOf(dl, dr, dt, db)
        var vx = 0f
        var vy = 0f
        if (dl <= nearest * tolerance) vx += 1f   // left edge -> open right
        if (dr <= nearest * tolerance) vx -= 1f   // right edge -> open left
        if (dt <= nearest * tolerance) vy += 1f   // top edge -> open down
        if (db <= nearest * tolerance) vy -= 1f   // keyboard/bottom -> open up
        if (vx == 0f && vy == 0f) return -90.0
        return Math.toDegrees(atan2(vy.toDouble(), vx.toDouble()))
    }

    /**
     * Angles (degrees) for [count] items spread across the fan centered on
     * [centerAngleDeg], inclusive of both arc ends. A single item sits at the
     * center angle.
     */
    fun itemAngles(centerAngleDeg: Double, sweepDeg: Double, count: Int): List<Double> {
        if (count <= 0) return emptyList()
        if (count == 1) return listOf(centerAngleDeg)
        val start = centerAngleDeg - sweepDeg / 2.0
        return List(count) { i -> start + sweepDeg * i / (count - 1) }
    }

    /** X offset of an item center from the ball center, at [angleDeg] on [radius]. */
    fun itemCenterX(angleDeg: Double, radius: Double): Double =
        radius * cos(Math.toRadians(angleDeg))

    /** Y offset of an item center from the ball center (y-down). */
    fun itemCenterY(angleDeg: Double, radius: Double): Double =
        radius * sin(Math.toRadians(angleDeg))

    /**
     * Where to place a [windowSize] overlay window so that content conceptually
     * centered at [center] is still drawn at [center], while the window itself is
     * kept fully on-screen. Returning both the clamped window position and the
     * local origin avoids the OEM behaviour where an off-screen overlay window is
     * shifted into the display (which used to drag the fan off the ball).
     */
    data class WindowPlacement(val windowPos: Int, val origin: Float)

    fun clampWindow(center: Float, windowSize: Int, screenSize: Int): WindowPlacement {
        val maxPos = (screenSize - windowSize).coerceAtLeast(0)
        val pos = (center - windowSize / 2f).toInt().coerceIn(0, maxPos)
        return WindowPlacement(pos, center - pos)
    }

    /**
     * Index of the nearest item center within [captureRadius] of ([dx], [dy])
     * (both relative to the ball center), or -1 when none is close enough.
     */
    fun hitTest(
        dx: Double,
        dy: Double,
        centers: List<Pair<Double, Double>>,
        captureRadius: Double,
    ): Int {
        var best = -1
        var bestDist = Double.MAX_VALUE
        for (i in centers.indices) {
            val d = hypot(dx - centers[i].first, dy - centers[i].second)
            if (d <= captureRadius && d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return best
    }
}
