package com.avengeeker.floatingtype.history

/**
 * Input mode: raw ASR transcription, ASR + AI polish, or ASR + AI translation.
 *
 * The same mode also drives *post-processing* of text already present in the
 * focused field: selecting POLISH or TRANSLATE from the radial menu rewrites
 * the existing text in place, while TRANSCRIBE only switches the mode.
 */
enum class InputMode {
    TRANSCRIBE,
    POLISH,
    TRANSLATE;

    /** Cycle order used by the settings preview / legacy toggle path. */
    fun next(): InputMode = when (this) {
        TRANSCRIBE -> POLISH
        POLISH -> TRANSLATE
        TRANSLATE -> TRANSCRIBE
    }

    val label: String
        get() = when (this) {
            TRANSCRIBE -> "转写"
            POLISH -> "润色"
            TRANSLATE -> "翻译"
        }

    /** Single-character badge drawn on the ball and the radial menu slices. */
    val badge: String
        get() = when (this) {
            TRANSCRIBE -> "转"
            POLISH -> "润"
            TRANSLATE -> "译"
        }

    /** Whether selecting this mode rewrites existing field text (vs. raw ASR). */
    val isTransform: Boolean
        get() = this == POLISH || this == TRANSLATE

    companion object {
        fun fromName(name: String?): InputMode =
            entries.firstOrNull { it.name == name } ?: TRANSCRIBE
    }
}
