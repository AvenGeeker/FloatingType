package com.avengeeker.floatingtype.util

/**
 * Helpers for diagnostic logging.
 *
 * Logging policy for the voice-input chain:
 *  - Never log API keys, base64 audio, or full user text (privacy).
 *  - Log error codes, HTTP status, request ids, sizes and timings instead.
 *  - Raw HTTP error bodies are logged clipped, because they are the single
 *    most useful artifact when a remote call fails.
 */
object LogUtils {

    /** Clip long payloads (e.g. HTTP error bodies) so logcat lines stay readable. */
    fun clip(value: String?, maxLength: Int = 2000): String {
        if (value.isNullOrEmpty()) return "<empty>"
        return if (value.length <= maxLength) {
            value
        } else {
            value.take(maxLength) + "...[truncated, ${value.length} chars total]"
        }
    }
}
