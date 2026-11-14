package com.avengeeker.floatingtype.permission

/**
 * Permission checklist for the voice-input flow: overlay / accessibility
 * enabled / accessibility connected / background-run, plus the microphone
 * and notification permissions required for voice input.
 */
data class PermissionStatus(
    val overlay: Boolean = false,
    val accessibilityEnabled: Boolean = false,
    val accessibilityConnected: Boolean = false,
    val microphone: Boolean = false,
    val notification: Boolean = false,
    val battery: Boolean = false,
) {
    val allGranted: Boolean
        get() = overlay && accessibilityEnabled && accessibilityConnected &&
            microphone && notification && battery
}
