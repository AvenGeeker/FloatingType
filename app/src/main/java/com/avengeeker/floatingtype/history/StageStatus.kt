package com.avengeeker.floatingtype.history

/** Terminal status of one pipeline stage within a recorded session. */
enum class StageStatus {
    NOT_RUN,
    SUCCESS,
    FAILED,
    SKIPPED;

    companion object {
        fun fromName(name: String?): StageStatus =
            entries.firstOrNull { it.name == name } ?: NOT_RUN
    }
}
