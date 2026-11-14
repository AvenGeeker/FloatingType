package com.avengeeker.floatingtype.accessibility

import org.junit.Assert.assertEquals
import org.junit.Test

class TextSpliceTest {

    @Test
    fun insertsAtSelection() {
        val result = TextInjector.splice("hello world", 5, 5, ", brave")
        assertEquals("hello, brave world", result.text)
        assertEquals(12, result.cursor)
    }

    @Test
    fun replacesSelection() {
        val result = TextInjector.splice("hello world", 6, 11, "Kotlin")
        assertEquals("hello Kotlin", result.text)
        assertEquals(12, result.cursor)
    }

    @Test
    fun negativeSelectionAppendsAtEnd() {
        val result = TextInjector.splice("hello", -1, -1, " world")
        assertEquals("hello world", result.text)
        assertEquals(11, result.cursor)
    }

    @Test
    fun nullExistingBecomesInsert() {
        val result = TextInjector.splice(null, -1, -1, "first")
        assertEquals("first", result.text)
        assertEquals(5, result.cursor)
    }

    @Test
    fun outOfBoundsIndicesAreClamped() {
        val result = TextInjector.splice("abc", 99, 100, "X")
        assertEquals("abcX", result.text)
        assertEquals(4, result.cursor)
    }

    @Test
    fun reversedSelectionIsNormalized() {
        val result = TextInjector.splice("abcdef", 4, 2, "X")
        assertEquals("abcdXef", result.text)
        assertEquals(5, result.cursor)
    }

    @Test
    fun insertAtStart() {
        val result = TextInjector.splice("world", 0, 0, "hello ")
        assertEquals("hello world", result.text)
        assertEquals(6, result.cursor)
    }
}
