package com.avengeeker.floatingtype.history

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class HistoryPresentationTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun record(
        mode: InputMode = InputMode.TRANSCRIBE,
        rawText: String = "",
        finalText: String = "",
        recordingStatus: StageStatus = StageStatus.SUCCESS,
        asrStatus: StageStatus = StageStatus.SUCCESS,
        polishStatus: StageStatus = StageStatus.SKIPPED,
        injectionStatus: StageStatus = StageStatus.SUCCESS,
        recordingError: String? = null,
        asrError: String? = null,
        polishError: String? = null,
        injectionError: String? = null,
    ) = InputHistoryRecord(
        createdAt = 0L,
        durationMs = 1000L,
        mode = mode,
        sourcePackage = "com.example",
        sourceAppLabel = "Example",
        rawText = rawText,
        finalText = finalText,
        asrModel = "asr",
        polishModel = null,
        recordingStatus = recordingStatus,
        asrStatus = asrStatus,
        polishStatus = polishStatus,
        injectionStatus = injectionStatus,
        recordingError = recordingError,
        asrError = asrError,
        polishError = polishError,
        injectionError = injectionError,
    )

    @Test
    fun statusLabel_success() {
        assertEquals("已输入", HistoryPresentation.statusLabel(record(finalText = "hi")))
    }

    @Test
    fun statusLabel_polishFallback() {
        val r = record(
            mode = InputMode.POLISH,
            rawText = "raw",
            finalText = "raw",
            polishStatus = StageStatus.FAILED,
            polishError = "Timeout",
        )
        assertEquals("原文已输入", HistoryPresentation.statusLabel(r))
        assertEquals(StatusTone.WARNING, HistoryPresentation.statusTone(r))
    }

    @Test
    fun transformLabel_byMode() {
        assertEquals("润色", HistoryPresentation.transformLabel(InputMode.POLISH))
        assertEquals("翻译", HistoryPresentation.transformLabel(InputMode.TRANSLATE))
        assertEquals("润色", HistoryPresentation.transformLabel(InputMode.TRANSCRIBE))
    }

    @Test
    fun modeLabel_translate() {
        assertEquals("翻译", HistoryPresentation.modeLabel(InputMode.TRANSLATE))
    }

    @Test
    fun statusLabel_translateFallback() {
        val r = record(
            mode = InputMode.TRANSLATE,
            rawText = "你好",
            finalText = "你好",
            polishStatus = StageStatus.FAILED,
            polishError = "Timeout",
        )
        assertEquals("原文已输入", HistoryPresentation.statusLabel(r))
        assertEquals(StatusTone.WARNING, HistoryPresentation.statusTone(r))
    }

    @Test
    fun statusLabel_failures() {
        assertEquals("录音失败", HistoryPresentation.statusLabel(record(recordingStatus = StageStatus.FAILED)))
        assertEquals("转写失败", HistoryPresentation.statusLabel(record(asrStatus = StageStatus.FAILED)))
        assertEquals("未写入", HistoryPresentation.statusLabel(record(injectionStatus = StageStatus.FAILED)))
    }

    @Test
    fun preview_prefersFinalThenRawThenError() {
        assertEquals("final", HistoryPresentation.preview(record(rawText = "raw", finalText = "final")))
        assertEquals("raw", HistoryPresentation.preview(record(rawText = "raw")))
        assertEquals("boom", HistoryPresentation.preview(record(asrStatus = StageStatus.FAILED, asrError = "boom")))
    }

    @Test
    fun formatDuration() {
        assertEquals("—", HistoryPresentation.formatDuration(0))
        assertEquals("<1s", HistoryPresentation.formatDuration(500))
        assertEquals("1.5s", HistoryPresentation.formatDuration(1500))
    }

    @Test
    fun dayLabel_groups() {
        val now = Calendar.getInstance(utc).apply {
            set(2026, Calendar.NOVEMBER, 14, 12, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val today = Calendar.getInstance(utc).apply {
            set(2026, Calendar.NOVEMBER, 14, 8, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterday = Calendar.getInstance(utc).apply {
            set(2026, Calendar.NOVEMBER, 13, 23, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val earlierThisYear = Calendar.getInstance(utc).apply {
            set(2026, Calendar.JANUARY, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val lastYear = Calendar.getInstance(utc).apply {
            set(2025, Calendar.DECEMBER, 31, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        assertEquals("今天", HistoryPresentation.dayLabel(today, now, utc))
        assertEquals("昨天", HistoryPresentation.dayLabel(yesterday, now, utc))
        assertEquals("1月1日", HistoryPresentation.dayLabel(earlierThisYear, now, utc))
        assertEquals("2025年12月31日", HistoryPresentation.dayLabel(lastYear, now, utc))
    }
}
