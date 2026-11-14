package com.avengeeker.floatingtype.asr

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class DashScopeRequestBuilderTest {

    @Test
    fun requestMatchesOfficialSchema() {
        val json = DashScopeAsrEngine.buildRequestJson(
            model = "qwen-audio-3.1-asr-flash",
            audioDataUri = "data:audio/wav;base64,AAAA",
            sampleRate = 16000,
        )
        val root = JSONObject(json)
        assertEquals("qwen-audio-3.1-asr-flash", root.getString("model"))

        val messages = root.getJSONObject("input").getJSONArray("messages")
        assertEquals(1, messages.length())
        val message = messages.getJSONObject(0)
        assertEquals("user", message.getString("role"))

        val content = message.getJSONArray("content").getJSONObject(0)
        assertEquals("input_audio", content.getString("type"))
        assertEquals(
            "data:audio/wav;base64,AAAA",
            content.getJSONObject("input_audio").getString("data")
        )

        val parameters = root.getJSONObject("parameters")
        assertEquals("wav", parameters.getString("format"))
        assertEquals("16000", parameters.getString("sample_rate"))
    }

    @Test
    fun generationPathIsOfficial() {
        assertEquals(
            "/services/aigc/multimodal-generation/generation",
            DashScopeAsrEngine.GENERATION_PATH
        )
    }

    @Test
    fun resolveUrlAppendsPathToNativeBase() {
        assertEquals(
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
            DashScopeAsrEngine.resolveGenerationUrl("https://dashscope.aliyuncs.com/api/v1")
        )
    }

    @Test
    fun resolveUrlTrimsWhitespaceAndTrailingSlashes() {
        assertEquals(
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
            DashScopeAsrEngine.resolveGenerationUrl("  https://dashscope.aliyuncs.com/api/v1///  ")
        )
    }

    @Test
    fun resolveUrlRewritesCompatibleModeToNativeApi() {
        // The exact misconfiguration seen in the field: the polish base URL
        // (OpenAI-compatible mode) pasted into the ASR endpoint setting.
        assertEquals(
            "https://token-plan.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
            DashScopeAsrEngine.resolveGenerationUrl(
                "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1"
            )
        )
        assertEquals(
            "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
            DashScopeAsrEngine.resolveGenerationUrl("https://dashscope.aliyuncs.com/compatible-mode/v1/")
        )
    }

    @Test
    fun resolveUrlKeepsWorkspaceDomainNativeBase() {
        assertEquals(
            "https://ws123.cn-beijing.maas.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation",
            DashScopeAsrEngine.resolveGenerationUrl("https://ws123.cn-beijing.maas.aliyuncs.com/api/v1")
        )
    }

    @Test
    fun resolveUrlDoesNotDuplicateFullPath() {
        val full = "https://dashscope.aliyuncs.com/api/v1/services/aigc/multimodal-generation/generation"
        assertEquals(full, DashScopeAsrEngine.resolveGenerationUrl(full))
        assertEquals(full, DashScopeAsrEngine.resolveGenerationUrl(full + "/"))
    }
}
