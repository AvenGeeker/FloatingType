package com.avengeeker.floatingtype.asr

import org.json.JSONException
import org.json.JSONObject

/**
 * Pure response parsing for the DashScope non-realtime ASR HTTP API
 * (Qwen-Audio-ASR). Success: output.text. Error: {code, message, request_id}.
 * Unit-testable on the JVM with org.json.
 */
object AsrResponseParser {

    fun parse(json: String): String {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            throw AsrException("BadResponse", "无法解析转写响应")
        }
        val code = root.optString("code", "")
        if (code.isNotEmpty()) {
            throw AsrException(code, root.optString("message", "转写失败"))
        }
        val output = root.optJSONObject("output")
            ?: throw AsrException("BadResponse", "响应缺少 output 字段")
        val text = output.optString("text", "")
        if (text.isBlank()) {
            throw AsrException("EmptyResult", "未识别到语音内容")
        }
        return text.trim()
    }

    fun parseError(body: String, httpStatus: Int): AsrException {
        try {
            val root = JSONObject(body)
            val code = root.optString("code", "")
            if (code.isNotEmpty()) {
                return AsrException(code, root.optString("message", "转写失败 (HTTP $httpStatus)"))
            }
        } catch (_: JSONException) {
            // Not a JSON error body; fall through to the HTTP-status mapping.
        }
        val message = when (httpStatus) {
            401, 403 -> "API Key 无效或无权限 (HTTP $httpStatus)"
            429 -> "请求过于频繁，请稍后重试 (HTTP $httpStatus)"
            in 500..599 -> "转写服务暂时不可用 (HTTP $httpStatus)"
            else -> "转写请求失败 (HTTP $httpStatus)"
        }
        return AsrException("Http$httpStatus", message)
    }
}
