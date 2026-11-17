package com.avengeeker.floatingtype.translate

/** Pluggable text-translation engine. Implementations must be main-safe. */
interface TranslateEngine {
    /** Translate [text] into [targetLanguage] (a code from [TranslatePrompt], or "auto"). */
    suspend fun translate(text: String, targetLanguage: String): Result<String>
}

/** Structured translation failure with a machine-readable code. */
class TranslateException(val code: String, message: String) : Exception(message)
