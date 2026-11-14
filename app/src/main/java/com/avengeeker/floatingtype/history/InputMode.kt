package com.avengeeker.floatingtype.history

/** Voice-input mode: raw ASR transcription or ASR + AI polish. */
enum class InputMode {
    TRANSCRIBE,
    POLISH;

    fun next(): InputMode = if (this == TRANSCRIBE) POLISH else TRANSCRIBE

    val label: String
        get() = when (this) {
            TRANSCRIBE -> "转写"
            POLISH -> "润色"
        }

    companion object {
        fun fromName(name: String?): InputMode =
            entries.firstOrNull { it.name == name } ?: TRANSCRIBE
    }
}
