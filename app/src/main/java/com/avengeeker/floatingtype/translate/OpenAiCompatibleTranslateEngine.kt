package com.avengeeker.floatingtype.translate

import android.os.SystemClock
import android.util.Log
import com.avengeeker.floatingtype.polish.OpenAiCompatiblePolishEngine
import com.avengeeker.floatingtype.polish.PolishConfig
import com.avengeeker.floatingtype.polish.PolishException
import com.avengeeker.floatingtype.polish.PolishResponseParser
import com.avengeeker.floatingtype.settings.SettingsRepository
import com.avengeeker.floatingtype.util.LogUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

/**
 * OpenAI-compatible Chat Completions translation engine.
 *
 * Translation deliberately reuses the polish endpoint/key/model (the same LLM
 * performs both tasks); only the system prompt differs, built by [TranslatePrompt].
 * The pure URL/key/request/response helpers are shared with the polish engine so
 * the key-reuse rule (invariant 9) and config precedence stay identical. Runs on
 * Dispatchers.IO with timeouts. API keys and user text are never logged.
 */
class OpenAiCompatibleTranslateEngine(
    private val settings: SettingsRepository,
) : TranslateEngine {

    override suspend fun translate(text: String, targetLanguage: String): Result<String> =
        withContext(Dispatchers.IO) {
            val startedAt = SystemClock.elapsedRealtime()
            try {
                if (text.isBlank()) {
                    Log.w(TAG, "translate aborted: input text is blank")
                    return@withContext Result.failure(TranslateException("EmptyInput", "没有可翻译的文本"))
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
                        "translate aborted: no API key resolved. baseUrl=${settings.polishBaseUrl} " +
                            "polishKeyConfigured=${settings.polishApiKey.isNotBlank()} " +
                            "asrKeyConfigured=${settings.apiKey.isNotBlank()}. " +
                            "A custom (non-default) endpoint requires a dedicated API key."
                    )
                    return@withContext Result.failure(TranslateException("MissingApiKey", "翻译接口未配置 API Key"))
                }

                val target = TranslatePrompt.resolveTarget(targetLanguage, text)
                val url = PolishConfig.completionsUrl(settings.polishBaseUrl)
                val body = OpenAiCompatiblePolishEngine.buildRequestJson(
                    model = settings.polishModel,
                    systemPrompt = TranslatePrompt.systemPrompt(target),
                    userText = text,
                )
                Log.i(
                    TAG,
                    "translate start: model=${settings.polishModel} target=$target url=$url " +
                        "inputChars=${text.length}"
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
                        val error = PolishResponseParser.parseError(responseText, status).asTranslate()
                        Log.e(
                            TAG,
                            "translate HTTP $status after ${elapsedMs}ms: " +
                                "code=${error.code} message=${error.message} body=${LogUtils.clip(responseText)}"
                        )
                        return@withContext Result.failure(error)
                    }
                    val translated = try {
                        PolishResponseParser.parse(responseText)
                    } catch (e: PolishException) {
                        Log.e(
                            TAG,
                            "translate response parse failed: code=${e.code} " +
                                "message=${e.message} body=${LogUtils.clip(responseText)}",
                            e
                        )
                        throw e.asTranslate()
                    }
                    Log.i(
                        TAG,
                        "translate ok in ${elapsedMs}ms: inputChars=${text.length} " +
                            "outputChars=${translated.length} target=$target"
                    )
                    Result.success(translated)
                } finally {
                    connection.disconnect()
                }
            } catch (e: TranslateException) {
                Log.e(TAG, "translate failed: code=${e.code} message=${e.message}")
                Result.failure(e)
            } catch (e: SocketTimeoutException) {
                Log.e(
                    TAG,
                    "translate timeout after ${SystemClock.elapsedRealtime() - startedAt}ms " +
                        "(connectTimeout=${CONNECT_TIMEOUT_MS}ms readTimeout=${READ_TIMEOUT_MS}ms)",
                    e
                )
                Result.failure(TranslateException("Timeout", "翻译超时，请重试"))
            } catch (e: IOException) {
                Log.e(TAG, "translate network error: ${e.message}", e)
                Result.failure(TranslateException("Network", "网络错误：${e.message ?: "无法连接翻译服务"}"))
            } catch (e: Exception) {
                Log.e(TAG, "translate unexpected error: ${e.javaClass.simpleName}: ${e.message}", e)
                Result.failure(TranslateException("Unexpected", e.message ?: "翻译失败"))
            }
        }

    private fun PolishException.asTranslate(): TranslateException = TranslateException(code, message ?: "翻译失败")

    companion object {
        private const val TAG = "FloatingType.Translate"
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}
