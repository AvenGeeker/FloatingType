package com.avengeeker.floatingtype.settings

import android.content.Context
import android.content.SharedPreferences
import com.avengeeker.floatingtype.BuildConfig
import com.avengeeker.floatingtype.history.InputMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * App settings backed by SharedPreferences. The DashScope API key is
 * entered by the user on the in-app Settings screen and stored only on
 * device, and is never sent to any party other than the configured
 * endpoint. Optionally, endpoints, keys and models can be preconfigured at
 * build time via the gitignored local.properties (compiled into BuildConfig);
 * a non-blank build-time value takes precedence over the in-app value.
 */
class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val listeners = mutableListOf<() -> Unit>()

    private val _defaultMode = MutableStateFlow(readDefaultMode())
    val defaultModeFlow: StateFlow<InputMode> = _defaultMode

    fun addChangeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.add(listener) }
    }

    fun removeChangeListener(listener: () -> Unit) {
        synchronized(listeners) { listeners.remove(listener) }
    }

    private fun notifyChanged() {
        val snapshot = synchronized(listeners) { listeners.toList() }
        snapshot.forEach { it() }
    }

    /** DashScope API key. local.properties `asr.apiKey` wins when non-blank. */
    var apiKey: String
        get() = ServiceConfigDefaults.resolve(
            localValue = BuildConfig.LOCAL_ASR_API_KEY,
            savedValue = prefs.getString(KEY_API_KEY, null),
        )
        set(value) {
            prefs.edit().putString(KEY_API_KEY, value.trim()).apply()
            notifyChanged()
        }

    /** True when the API key is preconfigured via local.properties. */
    val apiKeyFromLocal: Boolean get() = BuildConfig.LOCAL_ASR_API_KEY.isNotBlank()

    var model: String
        get() = ServiceConfigDefaults.resolve(
            localValue = BuildConfig.LOCAL_ASR_MODEL,
            savedValue = prefs.getString(KEY_MODEL, null),
            builtIn = DEFAULT_MODEL,
        )
        set(value) {
            prefs.edit().putString(KEY_MODEL, value.trim()).apply()
            notifyChanged()
        }

    /** True when the ASR model is preconfigured via local.properties. */
    val modelFromLocal: Boolean get() = BuildConfig.LOCAL_ASR_MODEL.isNotBlank()

    /** Base endpoint; the engine appends the multimodal-generation path. */
    var endpoint: String
        get() = ServiceConfigDefaults.resolveBaseUrl(
            localValue = BuildConfig.LOCAL_ASR_BASE_URL,
            savedValue = prefs.getString(KEY_ENDPOINT, null),
            builtIn = DEFAULT_ENDPOINT,
        )
        set(value) {
            prefs.edit().putString(KEY_ENDPOINT, value.trim().trimEnd('/')).apply()
            notifyChanged()
        }

    /** True when the ASR endpoint is preconfigured via local.properties. */
    val endpointFromLocal: Boolean get() = BuildConfig.LOCAL_ASR_BASE_URL.isNotBlank()

    /** Default voice-input mode; long-pressing the ball writes back here. */
    var defaultMode: InputMode
        get() = _defaultMode.value
        set(value) {
            prefs.edit().putString(KEY_DEFAULT_MODE, value.name).apply()
            _defaultMode.value = value
            notifyChanged()
        }

    private fun readDefaultMode(): InputMode =
        InputMode.fromName(prefs.getString(KEY_DEFAULT_MODE, InputMode.TRANSCRIBE.name))

    var polishBaseUrl: String
        get() = ServiceConfigDefaults.resolveBaseUrl(
            localValue = BuildConfig.LOCAL_POLISH_BASE_URL,
            savedValue = prefs.getString(KEY_POLISH_BASE_URL, null),
            builtIn = DEFAULT_POLISH_BASE_URL,
        )
        set(value) {
            prefs.edit().putString(KEY_POLISH_BASE_URL, value.trim().trimEnd('/')).apply()
            notifyChanged()
        }

    /** True when the polish base URL is preconfigured via local.properties. */
    val polishBaseUrlFromLocal: Boolean get() = BuildConfig.LOCAL_POLISH_BASE_URL.isNotBlank()

    /** Optional dedicated polish key. Blank reuses ASR key only on default base URL. */
    var polishApiKey: String
        get() = ServiceConfigDefaults.resolve(
            localValue = BuildConfig.LOCAL_POLISH_API_KEY,
            savedValue = prefs.getString(KEY_POLISH_API_KEY, null),
        )
        set(value) {
            prefs.edit().putString(KEY_POLISH_API_KEY, value.trim()).apply()
            notifyChanged()
        }

    /** True when the polish API key is preconfigured via local.properties. */
    val polishApiKeyFromLocal: Boolean get() = BuildConfig.LOCAL_POLISH_API_KEY.isNotBlank()

    var polishModel: String
        get() = ServiceConfigDefaults.resolve(
            localValue = BuildConfig.LOCAL_POLISH_MODEL,
            savedValue = prefs.getString(KEY_POLISH_MODEL, null),
            builtIn = DEFAULT_POLISH_MODEL,
        )
        set(value) {
            prefs.edit().putString(KEY_POLISH_MODEL, value.trim()).apply()
            notifyChanged()
        }

    /** True when the polish model is preconfigured via local.properties. */
    val polishModelFromLocal: Boolean get() = BuildConfig.LOCAL_POLISH_MODEL.isNotBlank()

    var polishPrompt: String
        get() = prefs.getString(KEY_POLISH_PROMPT, DEFAULT_POLISH_PROMPT)
            .orEmpty().ifBlank { DEFAULT_POLISH_PROMPT }
        set(value) {
            prefs.edit().putString(KEY_POLISH_PROMPT, value.trim()).apply()
            notifyChanged()
        }

    /**
     * Translation target language code (see TranslatePrompt). "auto" means
     * Chinese-dominant text -> English, otherwise -> Chinese. Translation
     * reuses the polish endpoint/key/model, so this is a plain in-app pref
     * with no local.properties override.
     */
    var translateTargetLanguage: String
        get() = prefs.getString(KEY_TRANSLATE_TARGET, DEFAULT_TRANSLATE_TARGET)
            .orEmpty().ifBlank { DEFAULT_TRANSLATE_TARGET }
        set(value) {
            prefs.edit().putString(KEY_TRANSLATE_TARGET, value.trim().ifBlank { DEFAULT_TRANSLATE_TARGET }).apply()
            notifyChanged()
        }

    var ballEnabled: Boolean
        get() = prefs.getBoolean(KEY_BALL_ENABLED, true)
        set(value) {
            prefs.edit().putBoolean(KEY_BALL_ENABLED, value).apply()
            notifyChanged()
        }

    var ballSizeDp: Int
        get() = prefs.getInt(KEY_BALL_SIZE, DEFAULT_BALL_SIZE_DP)
        set(value) {
            prefs.edit().putInt(KEY_BALL_SIZE, value.coerceIn(MIN_BALL_SIZE_DP, MAX_BALL_SIZE_DP)).apply()
            notifyChanged()
        }

    var ballOpacity: Float
        get() = prefs.getFloat(KEY_BALL_OPACITY, DEFAULT_BALL_OPACITY)
        set(value) {
            prefs.edit().putFloat(KEY_BALL_OPACITY, value.coerceIn(0.3f, 1f)).apply()
            notifyChanged()
        }

    /** Persisted drag position; -1 means "use default placement". */
    var ballX: Int
        get() = prefs.getInt(KEY_BALL_X, -1)
        set(value) { prefs.edit().putInt(KEY_BALL_X, value).apply() }

    var ballY: Int
        get() = prefs.getInt(KEY_BALL_Y, -1)
        set(value) { prefs.edit().putInt(KEY_BALL_Y, value).apply() }

    companion object {
        const val PREFS_NAME = "floating_type_settings"
        const val DEFAULT_MODEL = "qwen-audio-3.1-asr-flash"
        const val DEFAULT_ENDPOINT = "https://dashscope.aliyuncs.com/api/v1"
        const val DEFAULT_POLISH_BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1"
        const val DEFAULT_POLISH_MODEL = "qwen-plus"
        const val DEFAULT_POLISH_PROMPT =
            "你是语音输入的文本润色助手。将用户提供的语音转写文本整理成自然、通顺、简洁的表达，" +
                "修正错别字、标点和口语冗余；保留原意和必要信息，不要扩写、不要添加原文没有的内容、" +
                "不要回答问题，只输出润色后的文本。"
        const val DEFAULT_TRANSLATE_TARGET = "auto"
        const val DEFAULT_BALL_SIZE_DP = 52
        const val MIN_BALL_SIZE_DP = 40
        const val MAX_BALL_SIZE_DP = 72
        const val DEFAULT_BALL_OPACITY = 0.95f

        /** Official Bailian console page where users create their API key. */
        const val API_KEY_CONSOLE_URL =
            "https://bailian.console.aliyun.com/cn-beijing/model/settings/api-key"

        private const val KEY_API_KEY = "api_key"
        private const val KEY_MODEL = "asr_model"
        private const val KEY_ENDPOINT = "asr_endpoint"
        private const val KEY_DEFAULT_MODE = "default_input_mode"
        private const val KEY_POLISH_BASE_URL = "polish_base_url"
        private const val KEY_POLISH_API_KEY = "polish_api_key"
        private const val KEY_POLISH_MODEL = "polish_model"
        private const val KEY_POLISH_PROMPT = "polish_prompt"
        private const val KEY_TRANSLATE_TARGET = "translate_target_language"
        private const val KEY_BALL_ENABLED = "ball_enabled"
        private const val KEY_BALL_SIZE = "ball_size_dp"
        private const val KEY_BALL_OPACITY = "ball_opacity"
        private const val KEY_BALL_X = "ball_x"
        private const val KEY_BALL_Y = "ball_y"
    }
}
