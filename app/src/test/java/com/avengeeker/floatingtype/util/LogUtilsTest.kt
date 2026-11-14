package com.avengeeker.floatingtype.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogUtilsTest {

    @Test
    fun nullAndEmptyBecomePlaceholder() {
        assertEquals("<empty>", LogUtils.clip(null))
        assertEquals("<empty>", LogUtils.clip(""))
    }

    @Test
    fun shortValuePassesThrough() {
        assertEquals("{\"code\":\"InvalidApiKey\"}", LogUtils.clip("{\"code\":\"InvalidApiKey\"}"))
    }

    @Test
    fun longValueIsTruncatedWithTotalLength() {
        val body = "x".repeat(5000)
        val clipped = LogUtils.clip(body, maxLength = 100)
        assertTrue(clipped.startsWith("x".repeat(100)))
        assertTrue(clipped.contains("5000 chars total"))
        assertTrue(clipped.length < body.length)
    }
}
