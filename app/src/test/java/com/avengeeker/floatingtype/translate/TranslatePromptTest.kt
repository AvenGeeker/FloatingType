package com.avengeeker.floatingtype.translate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TranslatePromptTest {

    @Test
    fun isAuto_caseInsensitive() {
        assertTrue(TranslatePrompt.isAuto("auto"))
        assertTrue(TranslatePrompt.isAuto("AUTO"))
        assertFalse(TranslatePrompt.isAuto("en"))
    }

    @Test
    fun languageName_mapsKnownAndPassesThroughUnknown() {
        assertEquals("英文", TranslatePrompt.languageName("en"))
        assertEquals("中文", TranslatePrompt.languageName("zh"))
        assertEquals("意大利文", TranslatePrompt.languageName("意大利文"))
    }

    @Test
    fun displayName_includesAuto() {
        assertEquals("自动（中英互译）", TranslatePrompt.displayName("auto"))
        assertEquals("日文", TranslatePrompt.displayName("ja"))
    }

    @Test
    fun isChineseDominant_countsLettersOnly() {
        assertTrue(TranslatePrompt.isChineseDominant("你好世界"))
        assertTrue(TranslatePrompt.isChineseDominant("你好，世界！"))
        assertTrue(TranslatePrompt.isChineseDominant("中文测试abc"))
        assertFalse(TranslatePrompt.isChineseDominant("hello world"))
        assertFalse(TranslatePrompt.isChineseDominant("你好hello"))
        assertFalse(TranslatePrompt.isChineseDominant("12345"))
        assertFalse(TranslatePrompt.isChineseDominant(""))
    }

    @Test
    fun autoTargetCode_chineseToEnglishOtherwiseChinese() {
        assertEquals("en", TranslatePrompt.autoTargetCode("你好世界"))
        assertEquals("zh", TranslatePrompt.autoTargetCode("hello world"))
        assertEquals("zh", TranslatePrompt.autoTargetCode("12345"))
    }

    @Test
    fun resolveTarget_expandsAutoOnly() {
        assertEquals("en", TranslatePrompt.resolveTarget("auto", "你好"))
        assertEquals("zh", TranslatePrompt.resolveTarget("auto", "hello"))
        assertEquals("ja", TranslatePrompt.resolveTarget("ja", "你好"))
    }

    @Test
    fun systemPrompt_namesTargetAndForbidsExtraContent() {
        val prompt = TranslatePrompt.systemPrompt("en")
        assertTrue(prompt.contains("英文"))
        assertTrue(prompt.contains("只输出译文"))
        assertFalse(prompt.contains("auto"))
    }
}
