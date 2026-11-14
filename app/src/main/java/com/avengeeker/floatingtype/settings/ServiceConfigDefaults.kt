package com.avengeeker.floatingtype.settings

/**
 * Effective-value resolution for service settings (pure, unit-testable).
 *
 * Precedence: a non-blank build-time value from local.properties wins over
 * the in-app saved value, which wins over the built-in default. This lets a
 * developer preconfigure endpoints/keys in the gitignored local.properties
 * instead of typing them into the app after every (re)install, while leaving
 * every field fully in-app configurable when local.properties stays empty.
 */
object ServiceConfigDefaults {

    fun resolve(localValue: String, savedValue: String?, builtIn: String = ""): String {
        val local = localValue.trim()
        if (local.isNotEmpty()) return local
        val saved = savedValue.orEmpty().trim()
        return saved.ifEmpty { builtIn }
    }

    /** Like [resolve] but normalizes base URLs (no trailing slash). */
    fun resolveBaseUrl(localValue: String, savedValue: String?, builtIn: String): String =
        resolve(localValue, savedValue, builtIn).trimEnd('/')
}
