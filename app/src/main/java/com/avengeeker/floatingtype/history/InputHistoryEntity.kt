package com.avengeeker.floatingtype.history

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "input_history", indices = [Index("createdAt")])
data class InputHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val createdAt: Long,
    val durationMs: Long,
    val mode: String,
    val sourcePackage: String?,
    val sourceAppLabel: String?,
    val rawText: String,
    val finalText: String,
    val asrModel: String,
    val polishModel: String?,
    val recordingStatus: String,
    val asrStatus: String,
    val polishStatus: String,
    val injectionStatus: String,
    val recordingError: String?,
    val asrError: String?,
    val polishError: String?,
    val injectionError: String?,
)

fun InputHistoryEntity.toDomain(): InputHistoryRecord = InputHistoryRecord(
    id = id,
    createdAt = createdAt,
    durationMs = durationMs,
    mode = InputMode.fromName(mode),
    sourcePackage = sourcePackage,
    sourceAppLabel = sourceAppLabel,
    rawText = rawText,
    finalText = finalText,
    asrModel = asrModel,
    polishModel = polishModel,
    recordingStatus = StageStatus.fromName(recordingStatus),
    asrStatus = StageStatus.fromName(asrStatus),
    polishStatus = StageStatus.fromName(polishStatus),
    injectionStatus = StageStatus.fromName(injectionStatus),
    recordingError = recordingError,
    asrError = asrError,
    polishError = polishError,
    injectionError = injectionError,
)

fun InputHistoryRecord.toEntity(): InputHistoryEntity = InputHistoryEntity(
    id = id,
    createdAt = createdAt,
    durationMs = durationMs,
    mode = mode.name,
    sourcePackage = sourcePackage,
    sourceAppLabel = sourceAppLabel,
    rawText = rawText,
    finalText = finalText,
    asrModel = asrModel,
    polishModel = polishModel,
    recordingStatus = recordingStatus.name,
    asrStatus = asrStatus.name,
    polishStatus = polishStatus.name,
    injectionStatus = injectionStatus.name,
    recordingError = recordingError,
    asrError = asrError,
    polishError = polishError,
    injectionError = injectionError,
)
