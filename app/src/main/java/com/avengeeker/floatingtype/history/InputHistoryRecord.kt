package com.avengeeker.floatingtype.history

/** Domain model for one completed voice-input session (success or failure). */
data class InputHistoryRecord(
    val id: Long = 0L,
    val createdAt: Long,
    val durationMs: Long,
    val mode: InputMode,
    val sourcePackage: String?,
    val sourceAppLabel: String?,
    val rawText: String,
    val finalText: String,
    val asrModel: String,
    val polishModel: String?,
    val recordingStatus: StageStatus,
    val asrStatus: StageStatus,
    val polishStatus: StageStatus,
    val injectionStatus: StageStatus,
    val recordingError: String?,
    val asrError: String?,
    val polishError: String?,
    val injectionError: String?,
) {
    val sourceLabel: String
        get() = sourceAppLabel?.takeIf { it.isNotBlank() }
            ?: sourcePackage?.takeIf { it.isNotBlank() }
            ?: "未知应用"

    val hasText: Boolean
        get() = finalText.isNotBlank() || rawText.isNotBlank()
}
