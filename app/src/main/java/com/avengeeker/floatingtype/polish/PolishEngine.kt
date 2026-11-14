package com.avengeeker.floatingtype.polish

/** Pluggable text-polish engine. Implementations must be main-safe. */
interface PolishEngine {
    suspend fun polish(text: String): Result<String>
}

/** Structured polish failure with a machine-readable code. */
class PolishException(val code: String, message: String) : Exception(message)
