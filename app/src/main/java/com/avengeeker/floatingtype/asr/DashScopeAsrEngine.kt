package com.avengeeker.floatingtype.asr

import android.os.SystemClock
import android.util.Base64
import android.util.Log
import com.avengeeker.floatingtype.audio.WavEncoder
import com.avengeeker.floatingtype.settings.SettingsRepository
import com.avengeeker.floatingtype.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * DashScope (阿里云百炼) non-realtime ASR via the officially documented
 * multimodal-generation HTTP API, non-streaming mode:
 *
 *   POST {endpoint}/services/aigc/multimodal-generation/generation
 *   Authorization: Bearer <key>
 *   X-DashScope-SSE: disable
 *   { model, input.messages[{role:user, content:[{type:input_audio,
 *     input_audio:{data:"data:audio/wav;base64,..."}}]}],
 *     parameters:{format:"wav", sample_rate:"16000"} }
 *
 * Default model: qwen-audio-3.1-asr-flash (current official recommendation
 * for short audio <= 5 min; replaces the legacy sensevoice-v1). Model and
 * endpoint are configurable in settings. Uses HttpURLConnection to avoid
 * extra dependencies; runs on Dispatchers.IO with timeouts.
 *
 * Diagnostics: every failure path logs to [TAG] (HTTP status + clipped raw
 * error body, parse failures, timeouts and network errors with stack trace).
 * API keys and audio payloads are never logged.
 */
class DashScopeAsrEngine(private val settings: SettingsRepository) : AsrEngine {

    override suspend fun transcribe(pcm: ByteArray, sampleRate: Int): Result<String> =
        withContext(Dispatchers.IO) {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                val apiKey = settings.apiKey
                if (apiKey.isBlank()) {
                    Log.w(TAG, "transcribe aborted: DashScope API key is not configured")
                    return@withContext Result.failure(
                        AsrException("MissingApiKey", "请先在设置中配置 DashScope API Key")
                    )
                }
                val wav = WavEncoder.encode(pcm, sampleRate)
                val dataUri = "data:audio/wav;base64," + Base64.encodeToString(wav, Base64.NO_WRAP)
                val body = buildRequestJson(settings.model, dataUri, sampleRate)
                val url = resolveGenerationUrl(settings.endpoint)
                val naiveUrl = settings.endpoint.trim().trimEnd('/') + GENERATION_PATH
                if (url != naiveUrl) {
                    Log.w(
                        TAG,
                        "ASR endpoint adjusted: configured=${settings.endpoint} -> url=$url " +
                            "(the native multimodal-generation API lives under /api/v1; " +
                            "/compatible-mode/v1 is only for OpenAI-style chat completions)"
                    )
                }
                val audioMs = pcm.size * 1000L / (sampleRate * 2).coerceAtLeast(1)
                Log.i(
                    TAG,
                    "transcribe start: model=${settings.model} url=$url " +
                        "pcmBytes=${pcm.size} sampleRate=$sampleRate audioMs=$audioMs " +
                        "bodyBytes=${body.length}"
                )

                val connection = URL(url).openConnection() as HttpURLConnection
                try {
                    connection.requestMethod = "POST"
                    connection.connectTimeout = CONNECT_TIMEOUT_MS
                    connection.readTimeout = READ_TIMEOUT_MS
                    connection.setRequestProperty("Authorization", "Bearer $apiKey")
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.setRequestProperty("X-DashScope-SSE", "disable")
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                    val status = connection.responseCode
                    val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                    val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                    val elapsedMs = SystemClock.elapsedRealtime() - startedAt

                    if (status !in 200..299) {
                        val error = AsrResponseParser.parseError(responseText, status)
                        Log.e(
                            TAG,
                            "transcribe HTTP $status after ${elapsedMs}ms: " +
                                "code=${error.code} message=${error.message} " +
                                "body=${LogUtils.clip(responseText)}"
                        )
                        return@withContext Result.failure(error)
                    }
                    val text = try {
                        AsrResponseParser.parse(responseText)
                    } catch (e: AsrException) {
                        Log.e(
                            TAG,
                            "transcribe response parse failed: code=${e.code} " +
                                "message=${e.message} body=${LogUtils.clip(responseText)}",
                            e
                        )
                        throw e
                    }
                    Log.i(
                        TAG,
                        "transcribe ok in ${elapsedMs}ms: chars=${text.length} " +
                            "requestId=${requestIdOf(responseText)}"
                    )
                    Result.success(text)
                } finally {
                    connection.disconnect()
                }
            } catch (e: AsrException) {
                Log.e(TAG, "transcribe failed: code=${e.code} message=${e.message}")
                Result.failure(e)
            } catch (e: SocketTimeoutException) {
                Log.e(
                    TAG,
                    "transcribe timeout after ${SystemClock.elapsedRealtime() - startedAt}ms " +
                        "(connectTimeout=${CONNECT_TIMEOUT_MS}ms readTimeout=${READ_TIMEOUT_MS}ms)",
                    e
                )
                Result.failure(AsrException("Timeout", "转写超时，请重试"))
            } catch (e: IOException) {
                Log.e(TAG, "transcribe network error: ${e.message}", e)
                Result.failure(AsrException("Network", "网络错误：${e.message ?: "无法连接转写服务"}"))
            } catch (e: Exception) {
                Log.e(TAG, "transcribe unexpected error: ${e.javaClass.simpleName}: ${e.message}", e)
                Result.failure(AsrException("Unexpected", e.message ?: "转写失败"))
            }
        }

    companion object {
        private const val TAG = "FloatingType.ASR"
        const val GENERATION_PATH = "/services/aigc/multimodal-generation/generation"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000

        /**
         * Resolve the full generation URL from the configured base endpoint.
         * Tolerates two common misconfigurations:
         *  - an OpenAI-compatible base URL (.../compatible-mode/v1), which is
         *    for chat completions only: rewrite it to the native /api/v1 base;
         *  - a full generation URL pasted as the endpoint: do not append the
         *    path a second time.
         * Pure function; unit-testable on the JVM.
         */
        fun resolveGenerationUrl(endpoint: String): String {
            val trimmed = endpoint.trim().trimEnd('/')
            if (trimmed.endsWith(GENERATION_PATH)) return trimmed
            val compatibleSuffix = "/compatible-mode/v1"
            val base = if (trimmed.endsWith(compatibleSuffix, ignoreCase = true)) {
                trimmed.dropLast(compatibleSuffix.length) + "/api/v1"
            } else {
                trimmed
            }
            return base + GENERATION_PATH
        }

        /** Pure request-body builder; unit-testable on the JVM. */
        fun buildRequestJson(model: String, audioDataUri: String, sampleRate: Int): String {
            val content = JSONObject()
                .put("type", "input_audio")
                .put("input_audio", JSONObject().put("data", audioDataUri))
            val message = JSONObject()
                .put("role", "user")
                .put("content", JSONArray().put(content))
            val input = JSONObject().put("messages", JSONArray().put(message))
            val parameters = JSONObject()
                .put("format", "wav")
                .put("sample_rate", sampleRate.toString())
            return JSONObject()
                .put("model", model)
                .put("input", input)
                .put("parameters", parameters)
                .toString()
        }

        /** DashScope request_id, for correlating failures with server-side logs. */
        private fun requestIdOf(responseText: String): String =
            runCatching { JSONObject(responseText).optString("request_id", "") }.getOrDefault("")
    }
}
