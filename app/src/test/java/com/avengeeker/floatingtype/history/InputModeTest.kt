package com.avengeeker.floatingtype.history

import org.junit.Assert.assertEquals
import org.junit.Test

class InputModeTest {

    @Test
    fun next_togglesBetweenModes() {
        assertEquals(InputMode.POLISH, InputMode.TRANSCRIBE.next())
        assertEquals(InputMode.TRANSCRIBE, InputMode.POLISH.next())
    }

    @Test
    fun fromName_fallsBackToTranscribe() {
        assertEquals(InputMode.POLISH, InputMode.fromName("POLISH"))
        assertEquals(InputMode.TRANSCRIBE, InputMode.fromName("unknown"))
        assertEquals(InputMode.TRANSCRIBE, InputMode.fromName(null))
    }

    @Test
    fun labels() {
        assertEquals("转写", InputMode.TRANSCRIBE.label)
        assertEquals("润色", InputMode.POLISH.label)
    }
}
