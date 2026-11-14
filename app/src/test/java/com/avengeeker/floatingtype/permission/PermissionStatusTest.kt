package com.avengeeker.floatingtype.permission

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PermissionStatusTest {

    @Test
    fun emptyStatusIsNotGranted() {
        assertFalse(PermissionStatus().allGranted)
    }

    @Test
    fun allSixRequired() {
        val full = PermissionStatus(
            overlay = true,
            accessibilityEnabled = true,
            accessibilityConnected = true,
            microphone = true,
            notification = true,
            battery = true,
        )
        assertTrue(full.allGranted)
    }

    @Test
    fun eachMissingPermissionBlocks() {
        val full = PermissionStatus(true, true, true, true, true, true)
        assertFalse(full.copy(overlay = false).allGranted)
        assertFalse(full.copy(accessibilityEnabled = false).allGranted)
        assertFalse(full.copy(accessibilityConnected = false).allGranted)
        assertFalse(full.copy(microphone = false).allGranted)
        assertFalse(full.copy(notification = false).allGranted)
        assertFalse(full.copy(battery = false).allGranted)
    }

    @Test
    fun enabledButNotConnectedIsNotEnough() {
        val status = PermissionStatus(
            overlay = true,
            accessibilityEnabled = true,
            accessibilityConnected = false,
            microphone = true,
            notification = true,
            battery = true,
        )
        assertFalse(status.allGranted)
    }
}
