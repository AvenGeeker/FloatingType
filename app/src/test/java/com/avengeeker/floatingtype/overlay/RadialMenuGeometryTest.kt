package com.avengeeker.floatingtype.overlay

import org.junit.Assert.assertEquals
import org.junit.Test

class RadialMenuGeometryTest {

    @Test
    fun openDirection_ballOnRightOpensLeftHorizontally() {
        // right edge nearest -> opens straight left (symmetric about the X axis)
        val a = RadialMenuGeometry.openDirectionDeg(965f, 1033f, 1080f, 1244f)
        assertEquals(180.0, a, 1e-6)
    }

    @Test
    fun openDirection_ballBottomLeftOpensUpperRight() {
        // left and keyboard edges are both near -> diagonal up-right
        val a = RadialMenuGeometry.openDirectionDeg(150f, 1900f, 1080f, 2100f)
        assertEquals(-45.0, a, 1e-6)
    }

    @Test
    fun openDirection_nearTopOpensDown() {
        val a = RadialMenuGeometry.openDirectionDeg(540f, 60f, 1080f, 2100f)
        assertEquals(90.0, a, 1e-6)
    }

    @Test
    fun openDirection_equidistantDefaultsToUp() {
        assertEquals(-90.0, RadialMenuGeometry.openDirectionDeg(540f, 1050f, 1080f, 2100f), 1e-6)
    }

    @Test
    fun itemAngles_symmetricAroundCenterInclusive() {
        assertEquals(listOf(0.0, 75.0, 150.0), RadialMenuGeometry.itemAngles(75.0, 150.0, 3))
        assertEquals(listOf(-45.0, 5.0, 55.0), RadialMenuGeometry.itemAngles(5.0, 100.0, 3))
    }

    @Test
    fun itemAngles_singleItemAtCenter() {
        assertEquals(listOf(30.0), RadialMenuGeometry.itemAngles(30.0, 150.0, 1))
    }

    @Test
    fun itemAngles_emptyForNonPositiveCount() {
        assertEquals(emptyList<Double>(), RadialMenuGeometry.itemAngles(0.0, 150.0, 0))
    }

    @Test
    fun itemCenter_leftTopRight() {
        val r = 100.0
        assertEquals(-r, RadialMenuGeometry.itemCenterX(180.0, r), 1e-6)
        assertEquals(0.0, RadialMenuGeometry.itemCenterY(180.0, r), 1e-6)
        assertEquals(0.0, RadialMenuGeometry.itemCenterX(270.0, r), 1e-6)
        assertEquals(-r, RadialMenuGeometry.itemCenterY(270.0, r), 1e-6)
        assertEquals(r, RadialMenuGeometry.itemCenterX(360.0, r), 1e-6)
        assertEquals(0.0, RadialMenuGeometry.itemCenterY(360.0, r), 1e-6)
    }

    @Test
    fun clampWindow_keepsWindowOnScreenAndContentCentered() {
        // near the right edge: window is clamped, origin compensates
        val p = RadialMenuGeometry.clampWindow(center = 900f, windowSize = 500, screenSize = 1080)
        assertEquals(580, p.windowPos)          // 1080 - 500
        assertEquals(900f, p.windowPos + p.origin, 1e-3f)  // content still at 900
    }

    @Test
    fun clampWindow_centeredWhenRoomAllows() {
        val p = RadialMenuGeometry.clampWindow(center = 500f, windowSize = 500, screenSize = 1080)
        assertEquals(250, p.windowPos)
        assertEquals(500f, p.windowPos + p.origin, 1e-3f)
    }

    @Test
    fun clampWindow_oversizedWindowStaysAtZero() {
        val p = RadialMenuGeometry.clampWindow(center = 400f, windowSize = 2000, screenSize = 1080)
        assertEquals(0, p.windowPos)
        assertEquals(400f, p.windowPos + p.origin, 1e-3f)
    }

    private val centers = listOf(-100.0 to 0.0, 0.0 to -100.0, 100.0 to 0.0)
    private val capture = 40.0

    @Test
    fun hitTest_selectsEachOval() {
        assertEquals(0, RadialMenuGeometry.hitTest(-100.0, 0.0, centers, capture))
        assertEquals(1, RadialMenuGeometry.hitTest(0.0, -100.0, centers, capture))
        assertEquals(2, RadialMenuGeometry.hitTest(100.0, 0.0, centers, capture))
    }

    @Test
    fun hitTest_centerDeadZoneSelectsNothing() {
        assertEquals(-1, RadialMenuGeometry.hitTest(0.0, 0.0, centers, capture))
    }

    @Test
    fun hitTest_gapBetweenOvalsSelectsNothing() {
        assertEquals(-1, RadialMenuGeometry.hitTest(-70.0, -70.0, centers, capture))
    }

    @Test
    fun hitTest_farAwaySelectsNothing() {
        assertEquals(-1, RadialMenuGeometry.hitTest(0.0, -1000.0, centers, capture))
    }

    @Test
    fun hitTest_picksNearestWhenTwoInRange() {
        val close = listOf(0.0 to 0.0, 10.0 to 0.0)
        assertEquals(1, RadialMenuGeometry.hitTest(9.0, 0.0, close, 50.0))
    }
}
