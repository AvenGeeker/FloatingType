package com.avengeeker.floatingtype.asr

/** Pluggable speech-to-text engine. Implementations must be main-safe. */
interface AsrEngine {
    suspend fun transcribe(pcm: ByteArray, sampleRate: Int): Result<String>
}

/** Structured ASR failure with a machine-readable code. */
class AsrException(val code: String, message: String) : Exception(message)
