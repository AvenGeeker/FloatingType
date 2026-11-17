package com.avengeeker.floatingtype.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InputModeTest {

    @Test
    fun next_cyclesThroughThreeModes() {
        assertEquals(InputMode.POLISH, InputMode.TRANSCRIBE.next())
        assertEquals(InputMode.TRANSLATE, InputMode.POLISH.next())
        assertEquals(InputMode.TRANSCRIBE, InputMode.TRANSLATE.next())
    }

    @Test
    fun fromName_fallsBackToTranscribe() {
        assertEquals(InputMode.POLISH, InputMode.fromName("POLISH"))
        assertEquals(InputMode.TRANSLATE, InputMode.fromName("TRANSLATE"))
        assertEquals(InputMode.TRANSCRIBE, InputMode.fromName("unknown"))
        assertEquals(InputMode.TRANSCRIBE, InputMode.fromName(null))
    }

    @Test
    fun labels() {
        assertEquals("转写", InputMode.TRANSCRIBE.label)
        assertEquals("润色", InputMode.POLISH.label)
        assertEquals("翻译", InputMode.TRANSLATE.label)
    }

    @Test
    fun badges() {
        assertEquals("转", InputMode.TRANSCRIBE.badge)
        assertEquals("润", InputMode.POLISH.badge)
        assertEquals("译", InputMode.TRANSLATE.badge)
    }

    @Test
    fun isTransform_onlyForPolishAndTranslate() {
        assertFalse(InputMode.TRANSCRIBE.isTransform)
        assertTrue(InputMode.POLISH.isTransform)
        assertTrue(InputMode.TRANSLATE.isTransform)
    }
}
