package com.avengeeker.floatingtype.polish

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Pure response parsing for OpenAI-compatible Chat Completions. */
object PolishResponseParser {

    fun parse(json: String): String {
        val root = try {
            JSONObject(json)
        } catch (_: JSONException) {
            throw PolishException("BadResponse", "无法解析润色响应")
        }
        val error = root.optJSONObject("error")
        if (error != null) {
            throw PolishException(
                error.optString("code", "PolishError"),
                error.optString("message", "润色失败"),
            )
        }
        val choices = root.optJSONArray("choices")
        val message = choices?.optJSONObject(0)?.optJSONObject("message")
            ?: throw PolishException("BadResponse", "响应缺少 choices[0].message")
        val text = extractContent(message.opt("content"))
        if (text.isBlank()) {
            throw PolishException("EmptyResult", "润色结果为空")
        }
        return text.trim()
    }

    private fun extractContent(content: Any?): String = when (content) {
        is String -> content
        is JSONArray -> {
            val sb = StringBuilder()
            for (i in 0 until content.length()) {
                val part = content.optJSONObject(i) ?: continue
                sb.append(part.optString("text", ""))
            }
            sb.toString()
        }
        else -> ""
    }

    fun parseError(body: String, httpStatus: Int): PolishException {
        try {
            val root = JSONObject(body)
            val error = root.optJSONObject("error")
            if (error != null) {
                return PolishException(
                    error.optString("code", "Http$httpStatus"),
                    error.optString("message", "润色失败 (HTTP $httpStatus)"),
                )
            }
            val code = root.optString("code", "")
            if (code.isNotEmpty()) {
                return PolishException(code, root.optString("message", "润色失败 (HTTP $httpStatus)"))
            }
        } catch (_: JSONException) {
            // Not a JSON error body; fall through to HTTP-status mapping.
        }
        val message = when (httpStatus) {
            401, 403 -> "润色 API Key 无效或无权限 (HTTP $httpStatus)"
            429 -> "润色请求过于频繁，请稍后重试 (HTTP $httpStatus)"
            in 500..599 -> "润色服务暂时不可用 (HTTP $httpStatus)"
            else -> "润色请求失败 (HTTP $httpStatus)"
        }
        return PolishException("Http$httpStatus", message)
    }
}
