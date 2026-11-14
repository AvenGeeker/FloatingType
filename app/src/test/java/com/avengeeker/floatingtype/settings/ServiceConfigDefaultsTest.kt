package com.avengeeker.floatingtype.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceConfigDefaultsTest {

    @Test
    fun localValueWinsOverSaved() {
        assertEquals("local", ServiceConfigDefaults.resolve("local", "saved", "built-in"))
    }

    @Test
    fun blankLocalFallsBackToSaved() {
        assertEquals("saved", ServiceConfigDefaults.resolve("", "saved", "built-in"))
    }

    @Test
    fun whitespaceOnlyLocalFallsBackToSaved() {
        assertEquals("saved", ServiceConfigDefaults.resolve("   ", "saved", "built-in"))
    }

    @Test
    fun blankLocalAndSavedFallBackToBuiltIn() {
        assertEquals("built-in", ServiceConfigDefaults.resolve("", "", "built-in"))
    }

    @Test
    fun nullSavedFallsBackToBuiltIn() {
        assertEquals("built-in", ServiceConfigDefaults.resolve("", null, "built-in"))
    }

    @Test
    fun noBuiltInDefaultsToEmpty() {
        assertEquals("", ServiceConfigDefaults.resolve("", null))
    }

    @Test
    fun localValueIsTrimmed() {
        assertEquals("sk-key", ServiceConfigDefaults.resolve("  sk-key  ", "saved"))
    }

    @Test
    fun savedValueIsTrimmed() {
        assertEquals("sk-key", ServiceConfigDefaults.resolve("", "  sk-key  "))
    }

    @Test
    fun resolveBaseUrl_localWinsAndTrimsSlash() {
        assertEquals(
            "https://custom.example.com/v1",
            ServiceConfigDefaults.resolveBaseUrl(
                localValue = " https://custom.example.com/v1/ ",
                savedValue = "https://saved.example.com/v1",
                builtIn = "https://built-in.example.com/v1",
            ),
        )
    }

    @Test
    fun resolveBaseUrl_fallsBackToSavedThenBuiltIn() {
        assertEquals(
            "https://saved.example.com/v1",
            ServiceConfigDefaults.resolveBaseUrl("", "https://saved.example.com/v1/", "https://built-in.example.com/v1"),
        )
        assertEquals(
            "https://built-in.example.com/v1",
            ServiceConfigDefaults.resolveBaseUrl("", null, "https://built-in.example.com/v1/"),
        )
    }
}
