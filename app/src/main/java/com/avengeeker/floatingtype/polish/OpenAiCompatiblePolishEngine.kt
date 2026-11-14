package com.avengeeker.floatingtype.polish

import android.os.SystemClock
import android.util.Log
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
 * OpenAI-compatible Chat Completions polish engine.
 * Default target is DashScope compatible-mode; users may point it at any
 * OpenAI-compatible endpoint. Runs on Dispatchers.IO with timeouts.
 *
 * Diagnostics: every failure path logs to [TAG] (HTTP status + clipped raw
 * error body, parse failures, timeouts and network errors with stack trace).
 * API keys and user text are never logged; only sizes and timings.
 */
class OpenAiCompatiblePolishEngine(
    private val settings: SettingsRepository,
) : PolishEngine {

    override suspend fun polish(text: String): Result<String> = withContext(Dispatchers.IO) {
        val startedAt = SystemClock.elapsedRealtime()
        try {
            if (text.isBlank()) {
                Log.w(TAG, "polish aborted: input text is blank")
                return@withContext Result.failure(PolishException("EmptyInput", "没有可润色的文本"))
            }
            val apiKey = PolishConfig.resolveKey(
                asrKey = settings.apiKey,
                polishKey = settings.polishApiKey,
                baseUrl = settings.polishBaseUrl,
                defaultBaseUrl = SettingsRepository.DEFAULT_POLISH_BASE_URL,
            )
            if (apiKey == null) {
                Log.w(
                    TAG,
                    "polish aborted: no API key resolved. baseUrl=${settings.polishBaseUrl} " +
                        "polishKeyConfigured=${settings.polishApiKey.isNotBlank()} " +
                        "asrKeyConfigured=${settings.apiKey.isNotBlank()}. " +
                        "A custom (non-default) endpoint requires a dedicated polish API key; " +
                        "the ASR key is only reused on the default DashScope endpoint."
                )
                return@withContext Result.failure(
                    PolishException("MissingApiKey", "润色接口未配置 API Key")
                )
            }

            val url = PolishConfig.completionsUrl(settings.polishBaseUrl)
            val body = buildRequestJson(settings.polishModel, settings.polishPrompt, text)
            Log.i(
                TAG,
                "polish start: model=${settings.polishModel} url=$url " +
                    "inputChars=${text.length} promptChars=${settings.polishPrompt.length}"
            )
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = READ_TIMEOUT_MS
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                if (status !in 200..299) {
                    val error = PolishResponseParser.parseError(responseText, status)
                    Log.e(
                        TAG,
                        "polish HTTP $status after ${elapsedMs}ms: " +
                            "code=${error.code} message=${error.message} " +
                            "body=${LogUtils.clip(responseText)}"
                    )
                    return@withContext Result.failure(error)
                }
                val polished = try {
                    PolishResponseParser.parse(responseText)
                } catch (e: PolishException) {
                    Log.e(
                        TAG,
                        "polish response parse failed: code=${e.code} " +
                            "message=${e.message} body=${LogUtils.clip(responseText)}",
                        e
                    )
                    throw e
                }
                Log.i(
                    TAG,
                    "polish ok in ${elapsedMs}ms: inputChars=${text.length} " +
                        "outputChars=${polished.length}"
                )
                Result.success(polished)
            } finally {
                connection.disconnect()
            }
        } catch (e: PolishException) {
            Log.e(TAG, "polish failed: code=${e.code} message=${e.message}")
            Result.failure(e)
        } catch (e: SocketTimeoutException) {
            Log.e(
                TAG,
                "polish timeout after ${SystemClock.elapsedRealtime() - startedAt}ms " +
                    "(connectTimeout=${CONNECT_TIMEOUT_MS}ms readTimeout=${READ_TIMEOUT_MS}ms)",
                e
            )
            Result.failure(PolishException("Timeout", "润色超时，请重试"))
        } catch (e: IOException) {
            Log.e(TAG, "polish network error: ${e.message}", e)
            Result.failure(PolishException("Network", "网络错误：${e.message ?: "无法连接润色服务"}"))
        } catch (e: Exception) {
            Log.e(TAG, "polish unexpected error: ${e.javaClass.simpleName}: ${e.message}", e)
            Result.failure(PolishException("Unexpected", e.message ?: "润色失败"))
        }
    }

    companion object {
        private const val TAG = "FloatingType.Polish"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000

        /** Pure request-body builder; unit-testable on the JVM. */
        fun buildRequestJson(model: String, systemPrompt: String, userText: String): String {
            val system = JSONObject().put("role", "system").put("content", systemPrompt)
            val user = JSONObject().put("role", "user").put("content", userText)
            return JSONObject()
                .put("model", model)
                .put("messages", JSONArray().put(system).put(user))
                .put("temperature", 0.3)
                .put("max_tokens", 1024)
                .toString()
        }
    }
}
