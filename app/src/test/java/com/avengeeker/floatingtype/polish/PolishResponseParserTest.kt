package com.avengeeker.floatingtype.polish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PolishResponseParserTest {

    @Test
    fun parse_stringContent() {
        val json = """{"choices":[{"message":{"content":"你好世界"}}]}"""
        assertEquals("你好世界", PolishResponseParser.parse(json))
    }

    @Test
    fun parse_arrayContent() {
        val json = """{"choices":[{"message":{"content":[{"text":"你"},{"text":"好"}]}}]}"""
        assertEquals("你好", PolishResponseParser.parse(json))
    }

    @Test
    fun parse_trimsWhitespace() {
        val json = """{"choices":[{"message":{"content":"  hello  "}}]}"""
        assertEquals("hello", PolishResponseParser.parse(json))
    }

    @Test
    fun parse_emptyContentThrows() {
        try {
            PolishResponseParser.parse("""{"choices":[{"message":{"content":"  "}}]}""")
            fail("expected PolishException")
        } catch (e: PolishException) {
            assertEquals("EmptyResult", e.code)
        }
    }

    @Test
    fun parse_missingChoicesThrows() {
        try {
            PolishResponseParser.parse("""{"id":"x"}""")
            fail("expected PolishException")
        } catch (e: PolishException) {
            assertEquals("BadResponse", e.code)
        }
    }

    @Test
    fun parse_errorObjectThrows() {
        try {
            PolishResponseParser.parse("""{"error":{"code":"InvalidKey","message":"bad key"}}""")
            fail("expected PolishException")
        } catch (e: PolishException) {
            assertEquals("InvalidKey", e.code)
            assertEquals("bad key", e.message)
        }
    }

    @Test
    fun parse_invalidJsonThrows() {
        try {
            PolishResponseParser.parse("not json")
            fail("expected PolishException")
        } catch (e: PolishException) {
            assertEquals("BadResponse", e.code)
        }
    }

    @Test
    fun parseError_jsonError() {
        val e = PolishResponseParser.parseError("""{"error":{"code":"RateLimit","message":"slow down"}}""", 429)
        assertEquals("RateLimit", e.code)
        assertEquals("slow down", e.message)
    }

    @Test
    fun parseError_httpMapping() {
        assertTrue(PolishResponseParser.parseError("", 401).message!!.contains("API Key"))
        assertEquals("Http500", PolishResponseParser.parseError("oops", 500).code)
        assertEquals("Http418", PolishResponseParser.parseError("", 418).code)
    }
}
