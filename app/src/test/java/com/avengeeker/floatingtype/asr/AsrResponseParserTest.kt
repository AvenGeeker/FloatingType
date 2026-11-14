package com.avengeeker.floatingtype.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class AsrResponseParserTest {

    @Test
    fun parsesSuccessOutputText() {
        val json = """
            {"output":{"text":"你好世界"},"request_id":"abc","usage":{}}
        """.trimIndent()
        assertEquals("你好世界", AsrResponseParser.parse(json))
    }

    @Test
    fun trimsWhitespace() {
        val json = """{"output":{"text":"  hello  "}}"""
        assertEquals("hello", AsrResponseParser.parse(json))
    }

    @Test
    fun apiErrorThrowsWithCode() {
        val json = """{"code":"InvalidApiKey","message":"Invalid API-key provided.","request_id":"x"}"""
        try {
            AsrResponseParser.parse(json)
            fail("expected AsrException")
        } catch (e: AsrException) {
            assertEquals("InvalidApiKey", e.code)
            assertTrue(e.message!!.contains("Invalid API-key"))
        }
    }

    @Test
    fun missingOutputThrows() {
        try {
            AsrResponseParser.parse("""{"request_id":"x"}""")
            fail("expected AsrException")
        } catch (e: AsrException) {
            assertEquals("BadResponse", e.code)
        }
    }

    @Test
    fun emptyTextThrowsEmptyResult() {
        try {
            AsrResponseParser.parse("""{"output":{"text":"  "}}""")
            fail("expected AsrException")
        } catch (e: AsrException) {
            assertEquals("EmptyResult", e.code)
        }
    }

    @Test
    fun malformedJsonThrowsBadResponse() {
        try {
            AsrResponseParser.parse("not json at all")
            fail("expected AsrException")
        } catch (e: AsrException) {
            assertEquals("BadResponse", e.code)
        }
    }

    @Test
    fun httpErrorBodyWithCodeIsMapped() {
        val error = AsrResponseParser.parseError(
            """{"code":"Throttling","message":"Requests rate limit exceeded"}""", 429
        )
        assertEquals("Throttling", error.code)
    }

    @Test
    fun httpStatusWithoutBodyIsMapped() {
        assertEquals("Http401", AsrResponseParser.parseError("", 401).code)
        assertTrue(AsrResponseParser.parseError("", 401).message!!.contains("API Key"))
        assertEquals("Http500", AsrResponseParser.parseError("oops", 500).code)
        assertEquals("Http418", AsrResponseParser.parseError("", 418).code)
    }
}
