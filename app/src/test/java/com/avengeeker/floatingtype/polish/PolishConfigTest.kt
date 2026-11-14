package com.avengeeker.floatingtype.polish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PolishConfigTest {

    private val defaultBase = "https://dashscope.aliyuncs.com/compatible-mode/v1"

    @Test
    fun completionsUrl_appendsPath() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            PolishConfig.completionsUrl("https://api.example.com/v1"),
        )
    }

    @Test
    fun completionsUrl_trimsTrailingSlash() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            PolishConfig.completionsUrl("https://api.example.com/v1/"),
        )
    }

    @Test
    fun completionsUrl_doesNotDuplicatePath() {
        assertEquals(
            "https://api.example.com/v1/chat/completions",
            PolishConfig.completionsUrl("https://api.example.com/v1/chat/completions"),
        )
    }

    @Test
    fun resolveKey_explicitPolishKeyWins() {
        assertEquals(
            "pk",
            PolishConfig.resolveKey("asr", "pk", "https://custom.example.com/v1", defaultBase),
        )
    }

    @Test
    fun resolveKey_defaultBaseReusesAsrKey() {
        assertEquals(
            "asr",
            PolishConfig.resolveKey("asr", "", defaultBase, defaultBase),
        )
    }

    @Test
    fun resolveKey_defaultBaseComparisonIgnoresCaseAndSlash() {
        assertEquals(
            "asr",
            PolishConfig.resolveKey("asr", "", defaultBase.uppercase() + "/", defaultBase),
        )
    }

    @Test
    fun resolveKey_customBaseDoesNotReuseAsrKey() {
        assertNull(
            PolishConfig.resolveKey("asr", "", "https://custom.example.com/v1", defaultBase),
        )
    }

    @Test
    fun resolveKey_blankAsrKeyOnDefaultReturnsNull() {
        assertNull(PolishConfig.resolveKey("", "", defaultBase, defaultBase))
    }
}
