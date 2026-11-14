package com.avengeeker.floatingtype.polish

/** Pure configuration helpers for OpenAI-compatible polish endpoints. */
object PolishConfig {

    /** Normalize a base URL and append /chat/completions unless already present. */
    fun completionsUrl(baseUrl: String): String {
        val trimmed = baseUrl.trim().trimEnd('/')
        return if (trimmed.endsWith("/chat/completions")) trimmed else "$trimmed/chat/completions"
    }

    /**
     * Resolve the API key used for polish.
     * An explicit polish key always wins. The ASR key is only reused when the
     * base URL is still the default DashScope-compatible endpoint; a custom
     * endpoint must never receive the DashScope key implicitly.
     */
    fun resolveKey(
        asrKey: String,
        polishKey: String,
        baseUrl: String,
        defaultBaseUrl: String,
    ): String? {
        val explicit = polishKey.trim()
        if (explicit.isNotEmpty()) return explicit
        val normalized = baseUrl.trim().trimEnd('/')
        val defaultNormalized = defaultBaseUrl.trim().trimEnd('/')
        return if (normalized.equals(defaultNormalized, ignoreCase = true)) {
            asrKey.trim().takeIf { it.isNotEmpty() }
        } else {
            null
        }
    }
}
