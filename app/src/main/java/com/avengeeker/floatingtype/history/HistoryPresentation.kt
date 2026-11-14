package com.avengeeker.floatingtype.history

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Semantic tone used by status chips and cards. */
enum class StatusTone { SUCCESS, WARNING, ERROR, INFO }

/** Pure presentation helpers; unit-testable on the JVM. */
object HistoryPresentation {

    fun modeLabel(mode: InputMode): String = mode.label

    fun statusLabel(record: InputHistoryRecord): String = when {
        record.recordingStatus == StageStatus.FAILED -> "录音失败"
        record.asrStatus == StageStatus.FAILED -> "转写失败"
        record.injectionStatus == StageStatus.FAILED -> "未写入"
        record.polishStatus == StageStatus.FAILED &&
            record.injectionStatus == StageStatus.SUCCESS -> "原文已输入"
        record.injectionStatus == StageStatus.SUCCESS -> "已输入"
        else -> "未完成"
    }

    fun statusTone(record: InputHistoryRecord): StatusTone = when {
        record.recordingStatus == StageStatus.FAILED -> StatusTone.ERROR
        record.asrStatus == StageStatus.FAILED -> StatusTone.ERROR
        record.injectionStatus == StageStatus.FAILED -> StatusTone.ERROR
        record.polishStatus == StageStatus.FAILED -> StatusTone.WARNING
        record.injectionStatus == StageStatus.SUCCESS -> StatusTone.SUCCESS
        else -> StatusTone.INFO
    }

    fun preview(record: InputHistoryRecord): String = when {
        record.finalText.isNotBlank() -> record.finalText.trim()
        record.rawText.isNotBlank() -> record.rawText.trim()
        else -> firstError(record) ?: "无文本内容"
    }

    fun firstError(record: InputHistoryRecord): String? =
        record.recordingError?.takeIf { it.isNotBlank() }
            ?: record.asrError?.takeIf { it.isNotBlank() }
            ?: record.polishError?.takeIf { it.isNotBlank() }
            ?: record.injectionError?.takeIf { it.isNotBlank() }

    fun formatTime(createdAt: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("HH:mm", Locale.getDefault()).apply { timeZone = zone }
            .format(createdAt)

    fun formatDateTime(createdAt: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy年M月d日 HH:mm", Locale.getDefault()).apply { timeZone = zone }
            .format(createdAt)

    fun formatDuration(durationMs: Long): String = when {
        durationMs <= 0L -> "—"
        durationMs < 1000L -> "<1s"
        else -> String.format(Locale.getDefault(), "%.1fs", durationMs / 1000.0)
    }

    /** Date group header: 今天 / 昨天 / M月d日 / yyyy年M月d日. */
    fun dayLabel(
        createdAt: Long,
        now: Long,
        zone: TimeZone = TimeZone.getDefault(),
    ): String {
        val created = Calendar.getInstance(zone).apply { timeInMillis = createdAt }
        val today = Calendar.getInstance(zone).apply { timeInMillis = now }
        val yesterday = Calendar.getInstance(zone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }
        return when {
            sameDay(created, today) -> "今天"
            sameDay(created, yesterday) -> "昨天"
            created.get(Calendar.YEAR) == today.get(Calendar.YEAR) ->
                SimpleDateFormat("M月d日", Locale.getDefault()).apply { timeZone = zone }
                    .format(createdAt)
            else ->
                SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).apply { timeZone = zone }
                    .format(createdAt)
        }
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
