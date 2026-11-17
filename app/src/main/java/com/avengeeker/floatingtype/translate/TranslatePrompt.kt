package com.avengeeker.floatingtype.translate

/**
 * Pure translation prompt + target-language helpers (no Android dependencies).
 *
 * Translation reuses the polish OpenAI-compatible endpoint/key/model; the only
 * thing that changes is the system prompt built here from the target language.
 * "auto" means Chinese-dominant text is translated to English and everything
 * else to Chinese, which covers the common bilingual input case without asking
 * the user to pick a direction.
 */
object TranslatePrompt {

    const val AUTO = "auto"

    /** Curated target languages offered in settings (code to Chinese name). */
    val SUPPORTED: List<Pair<String, String>> = listOf(
        AUTO to "自动（中英互译）",
        "zh" to "中文",
        "en" to "英文",
        "ja" to "日文",
        "ko" to "韩文",
        "fr" to "法文",
        "de" to "德文",
        "es" to "西班牙文",
        "ru" to "俄文",
    )

    fun isAuto(code: String): Boolean = code.trim().equals(AUTO, ignoreCase = true)

    /** Chinese display name for a concrete language code; unknown codes pass through. */
    fun languageName(code: String): String {
        val normalized = code.trim()
        return SUPPORTED.firstOrNull { it.first == normalized && it.first != AUTO }?.second
            ?: normalized
    }

    /** Display name including the auto option, for the settings selector. */
    fun displayName(code: String): String =
        if (isAuto(code)) SUPPORTED.first { it.first == AUTO }.second else languageName(code)

    /**
     * Whether the text is predominantly Chinese, counting only CJK ideographs
     * and Latin letters (digits/punctuation/whitespace are ignored). A text with
     * no letters at all is treated as non-Chinese.
     */
    fun isChineseDominant(text: String): Boolean {
        var cjk = 0
        var latin = 0
        for (ch in text) {
            when {
                ch in '\u4e00'..'\u9fff' -> cjk++
                ch in 'a'..'z' || ch in 'A'..'Z' -> latin++
            }
        }
        val letters = cjk + latin
        if (letters == 0) return false
        return cjk.toDouble() / letters >= 0.5
    }

    /** Concrete target code for auto mode: Chinese -> English, otherwise Chinese. */
    fun autoTargetCode(text: String): String = if (isChineseDominant(text)) "en" else "zh"

    /** Resolve the effective target code, expanding "auto" against the text. */
    fun resolveTarget(settingCode: String, text: String): String =
        if (isAuto(settingCode)) autoTargetCode(text) else settingCode.trim()

    /** System prompt for a concrete target language code. */
    fun systemPrompt(targetCode: String): String {
        val target = languageName(targetCode)
        return "你是专业翻译引擎。将用户提供的文本翻译成$target。" +
            "只输出译文，不要解释、不要添加原文没有的内容、不要回答问题；" +
            "保留原文的语义、语气、格式与换行。"
    }
}
