package com.avengeeker.floatingtype.polish

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenAiPolishRequestBuilderTest {

    @Test
    fun buildRequestJson_structure() {
        val json = OpenAiCompatiblePolishEngine.buildRequestJson(
            model = "qwen-plus",
            systemPrompt = "system prompt",
            userText = "原始文本",
        )
        val root = JSONObject(json)
        assertEquals("qwen-plus", root.getString("model"))
        assertEquals(0.3, root.getDouble("temperature"), 1e-9)
        assertEquals(1024, root.getInt("max_tokens"))
        val messages = root.getJSONArray("messages")
        assertEquals(2, messages.length())
        assertEquals("system", messages.getJSONObject(0).getString("role"))
        assertEquals("system prompt", messages.getJSONObject(0).getString("content"))
        assertEquals("user", messages.getJSONObject(1).getString("role"))
        assertEquals("原始文本", messages.getJSONObject(1).getString("content"))
    }
}
