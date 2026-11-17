package com.avengeeker.floatingtype.pipeline

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.avengeeker.floatingtype.accessibility.FloatingAccessibilityService
import com.avengeeker.floatingtype.accessibility.TextInjector
import com.avengeeker.floatingtype.asr.AsrEngine
import com.avengeeker.floatingtype.asr.AsrException
import com.avengeeker.floatingtype.audio.AudioRecordingForegroundService
import com.avengeeker.floatingtype.audio.RecorderController
import com.avengeeker.floatingtype.history.HistoryRepository
import com.avengeeker.floatingtype.history.InputHistoryRecord
import com.avengeeker.floatingtype.history.InputMode
import com.avengeeker.floatingtype.history.StageStatus
import com.avengeeker.floatingtype.overlay.BallState
import com.avengeeker.floatingtype.overlay.OverlayBallManager
import com.avengeeker.floatingtype.polish.PolishEngine
import com.avengeeker.floatingtype.polish.PolishException
import com.avengeeker.floatingtype.settings.SettingsRepository
import com.avengeeker.floatingtype.translate.TranslateEngine
import com.avengeeker.floatingtype.translate.TranslateException
import com.avengeeker.floatingtype.util.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates two flows, all state transitions on the main thread, network on
 * Dispatchers.IO inside the engines:
 *
 *  1. Voice: ball tap -> microphone FGS -> batch ASR -> optional polish/translate
 *     -> accessibility injection -> local history. Tap starts, tap again stops.
 *  2. Post-processing: long-press opens the radial mode menu; picking POLISH or
 *     TRANSLATE while the focused field already holds text rewrites that text in
 *     place (no recording, no history session). The last text rewrite is undoable.
 *
 * Long-press no longer toggles the mode directly; the overlay drives mode choice
 * through [selectMode]. A short-lived undo snapshot lets the user revert the most
 * recent rewrite (voice injection or post-processing) without clobbering edits
 * made afterwards.
 */
class VoiceInputPipeline(
    private val context: Context,
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    private val asrFactory: () -> AsrEngine,
    private val polishFactory: () -> PolishEngine,
    private val translateFactory: () -> TranslateEngine,
) {
    var overlay: OverlayBallManager? = null

    private var service: FloatingAccessibilityService? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var transcribeJob: Job? = null
    private var processingJob: Job? = null
    private var session: Session? = null

    private val _state = MutableStateFlow(BallState.IDLE)
    val state: StateFlow<BallState> = _state

    /** Notified (on the main thread) when an undo becomes available or clears. */
    var undoAvailabilityListener: ((Boolean) -> Unit)? = null
    private var lastUndo: UndoOperation? = null

    private val resetRunnable = Runnable {
        if (_state.value == BallState.ERROR) setState(BallState.IDLE)
    }
    private val undoExpireRunnable = Runnable { clearUndo() }

    /** One recorded voice-input session; written to history once at the end. */
    private class Session(
        val createdAt: Long,
        val startedAtElapsed: Long,
        val mode: InputMode,
        val sourcePackage: String?,
        val sourceAppLabel: String?,
        val asrModel: String,
        val polishModel: String?,
    ) {
        var durationMs: Long = 0L
        var rawText: String = ""
        var finalText: String = ""
        var recordingStatus: StageStatus = StageStatus.NOT_RUN
        var asrStatus: StageStatus = StageStatus.NOT_RUN
        var polishStatus: StageStatus = StageStatus.NOT_RUN
        var injectionStatus: StageStatus = StageStatus.NOT_RUN
        var recordingError: String? = null
        var asrError: String? = null
        var polishError: String? = null
        var injectionError: String? = null

        fun toRecord(): InputHistoryRecord = InputHistoryRecord(
            createdAt = createdAt,
            durationMs = durationMs,
            mode = mode,
            sourcePackage = sourcePackage,
            sourceAppLabel = sourceAppLabel,
            rawText = rawText,
            finalText = finalText,
            asrModel = asrModel,
            polishModel = polishModel,
            recordingStatus = recordingStatus,
            asrStatus = asrStatus,
            polishStatus = polishStatus,
            injectionStatus = injectionStatus,
            recordingError = recordingError,
            asrError = asrError,
            polishError = polishError,
            injectionError = injectionError,
        )
    }

    /**
     * Snapshot of the field before a rewrite plus the text we wrote. Undo only
     * restores while the field still equals [resultText], so later user edits are
     * never overwritten. The polish* history columns double as the generic
     * post-ASR transform stage (polish or translate, interpreted by mode).
     */
    private class UndoOperation(
        val originalText: String,
        val originalSelectionStart: Int,
        val originalSelectionEnd: Int,
        val resultText: String,
    )

    fun isBusy(): Boolean =
        _state.value == BallState.RECORDING ||
            _state.value == BallState.TRANSCRIBING ||
            _state.value == BallState.INJECTING

    fun onServiceConnected(service: FloatingAccessibilityService) {
        this.service = service
    }

    fun onServiceDisconnected() {
        if (isBusy()) {
            Log.w(TAG, "accessibility service disconnected while busy; cancelling session")
        }
        service = null
        transcribeJob?.cancel()
        processingJob?.cancel()
        session = null
        clearUndo()
        if (isBusy()) setState(BallState.IDLE)
    }

    /** Guard for opening the radial mode menu (long-press). */
    fun canSelectMode(): Boolean {
        if (isBusy()) {
            Log.w(TAG, "mode selection ignored: busy (state=${_state.value})")
            toast("当前正在输入，无法切换模式")
            return false
        }
        return true
    }

    /**
     * Radial-menu entry point: persist the chosen mode and, for a transform mode
     * with text already in the focused field, post-process that text in place.
     */
    fun selectMode(mode: InputMode) {
        if (isBusy()) {
            toast("当前正在输入，无法切换模式")
            return
        }
        settings.defaultMode = mode
        Haptics.tick(context)
        val svc = service
        if (mode.isTransform && svc != null && FloatingAccessibilityService.isConnected) {
            val snapshot = svc.inputSnapshot()
            if (snapshot != null && snapshot.text.isNotBlank()) {
                postProcessExistingText(mode, snapshot)
                return
            }
        }
        toast("已切换为${mode.label}模式")
    }

    /** Rewrite existing field text with the chosen transform engine (no history). */
    private fun postProcessExistingText(mode: InputMode, snapshot: TextInjector.Snapshot) {
        val svc = service ?: return
        Log.i(TAG, "post-process start: mode=$mode chars=${snapshot.text.length}")
        setState(BallState.TRANSCRIBING)
        processingJob = scope.launch {
            val result = when (mode) {
                InputMode.POLISH -> polishFactory().polish(snapshot.text)
                InputMode.TRANSLATE ->
                    translateFactory().translate(snapshot.text, settings.translateTargetLanguage)
                InputMode.TRANSCRIBE -> Result.success(snapshot.text)
            }
            result.fold(
                onSuccess = { transformed ->
                    // The user may have edited the field while the engine ran;
                    // never clobber those edits.
                    if (svc.currentInputText() != snapshot.text) {
                        Log.w(TAG, "post-process aborted: field changed during processing")
                        setState(BallState.IDLE)
                        toast("输入框内容已变化，已取消${mode.label}")
                        return@fold
                    }
                    setState(BallState.INJECTING)
                    val ok = svc.replaceInputText(transformed)
                    if (ok) {
                        Log.i(TAG, "post-process success: mode=$mode chars=${transformed.length}")
                        registerUndo(snapshot, transformed)
                        Haptics.success(context)
                        setState(BallState.IDLE)
                        toast("${mode.label}完成")
                    } else {
                        Log.e(TAG, "post-process replace failed (focused field lost?)")
                        fail("无法写入输入框，请重新点击输入框后再试")
                    }
                },
                onFailure = { error ->
                    Log.w(TAG, "post-process failed: mode=$mode ${formatError(error)}", error)
                    fail("${mode.label}失败：${error.message ?: "未知错误"}")
                },
            )
        }
    }

    /** Restore the most recent rewrite if the field has not changed since. */
    fun undo() {
        val op = lastUndo
        if (op == null) {
            clearUndo()
            return
        }
        val svc = service
        if (svc == null) {
            clearUndo()
            return
        }
        if (svc.currentInputText() == op.resultText) {
            // Negative selection indices mean "no selection"; restore cursor at end.
            val selStart = op.originalSelectionStart.takeIf { it >= 0 }
            val selEnd = op.originalSelectionEnd.takeIf { it >= 0 }
            val ok = svc.replaceInputText(op.originalText, selStart, selEnd)
            if (ok) {
                Log.i(TAG, "undo success: restoredChars=${op.originalText.length}")
                Haptics.tick(context)
                toast("已撤回")
            } else {
                Log.e(TAG, "undo replace failed (focused field lost?)")
                toast("撤回失败，请重新点击输入框")
            }
        } else {
            Log.w(TAG, "undo skipped: field changed since rewrite")
            toast("内容已变化，无法撤回")
        }
        clearUndo()
    }

    private fun registerUndo(original: TextInjector.Snapshot, resultText: String) {
        lastUndo = UndoOperation(
            originalText = original.text,
            originalSelectionStart = original.selectionStart,
            originalSelectionEnd = original.selectionEnd,
            resultText = resultText,
        )
        handler.removeCallbacks(undoExpireRunnable)
        handler.postDelayed(undoExpireRunnable, UNDO_TTL_MS)
        undoAvailabilityListener?.invoke(true)
    }

    fun clearUndo() {
        lastUndo = null
        handler.removeCallbacks(undoExpireRunnable)
        undoAvailabilityListener?.invoke(false)
    }

    /** Ball click entry point. */
    fun toggle() {
        when (_state.value) {
            BallState.IDLE, BallState.ERROR -> startRecording()
            BallState.RECORDING -> requestStop()
            else -> Unit // Ignore taps while transcribing/injecting.
        }
    }

    private fun startRecording() {
        val svc = service
        if (svc == null || !FloatingAccessibilityService.isConnected) {
            Log.w(TAG, "startRecording aborted: accessibility service not connected (svc=${svc != null})")
            toast("无障碍服务未连接，请先在设置中开启")
            return
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "startRecording aborted: RECORD_AUDIO permission not granted")
            toast("请先授予麦克风权限")
            return
        }
        if (!svc.hasInputFocus()) {
            Log.w(TAG, "startRecording aborted: no focused editable input field")
            toast("请先点击一个输入框")
            return
        }
        if (settings.apiKey.isBlank()) {
            Log.w(TAG, "startRecording aborted: DashScope API key is not configured")
            toast("请先在设置中配置 DashScope API Key")
            return
        }

        val mode = settings.defaultMode
        val sourcePackage = svc.focusedPackageName()
        session = Session(
            createdAt = System.currentTimeMillis(),
            startedAtElapsed = SystemClock.elapsedRealtime(),
            mode = mode,
            sourcePackage = sourcePackage,
            sourceAppLabel = resolveAppLabel(sourcePackage),
            asrModel = settings.model,
            polishModel = if (mode.isTransform) settings.polishModel else null,
        )
        Log.i(
            TAG,
            "session start: mode=$mode asrModel=${settings.model} " +
                "transformModel=${if (mode.isTransform) settings.polishModel else "<none>"} " +
                "sourcePackage=$sourcePackage"
        )
        setState(BallState.RECORDING)
        val intent = Intent(context, AudioRecordingForegroundService::class.java)
            .setAction(AudioRecordingForegroundService.ACTION_START)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (e: Exception) {
            Log.e(TAG, "startForegroundService failed", e)
            failSession("无法启动录音服务：${e.message ?: "未知错误"}")
        }
    }

    fun requestStop() {
        if (_state.value != BallState.RECORDING) return
        val intent = Intent(context, AudioRecordingForegroundService::class.java)
            .setAction(AudioRecordingForegroundService.ACTION_STOP)
        try {
            context.startService(intent)
        } catch (e: Exception) {
            Log.e(TAG, "startService(ACTION_STOP) failed", e)
            failSession("停止录音失败：${e.message ?: "未知错误"}")
        }
    }

    fun onRecordingStarted() {
        setState(BallState.RECORDING)
        Haptics.tick(context)
    }

    fun onRecordingError(message: String) {
        Log.e(TAG, "recording failed: $message")
        session?.let {
            it.recordingStatus = StageStatus.FAILED
            it.recordingError = message
            it.durationMs = SystemClock.elapsedRealtime() - it.startedAtElapsed
        }
        failSession(message)
    }

    fun onRecordingComplete(pcm: ByteArray) {
        val s = session
        if (s == null) {
            Log.w(TAG, "recording complete ignored: session already gone (pcmBytes=${pcm.size})")
            fail("录音会话已失效")
            return
        }
        s.durationMs = SystemClock.elapsedRealtime() - s.startedAtElapsed
        val minBytes = RecorderController.SAMPLE_RATE * 2 * RecorderController.MIN_DURATION_MS / 1000
        if (pcm.size < minBytes) {
            Log.w(
                TAG,
                "recording too short: pcmBytes=${pcm.size} minBytes=$minBytes " +
                    "durationMs=${s.durationMs}"
            )
            s.recordingStatus = StageStatus.FAILED
            s.recordingError = "录音太短，请再说一次"
            failSession(s.recordingError!!)
            return
        }
        Log.i(TAG, "recording complete: pcmBytes=${pcm.size} durationMs=${s.durationMs}; starting ASR")
        s.recordingStatus = StageStatus.SUCCESS
        setState(BallState.TRANSCRIBING)
        transcribeJob = scope.launch {
            val result = asrFactory().transcribe(pcm, RecorderController.SAMPLE_RATE)
            result.fold(
                onSuccess = { text ->
                    Log.i(TAG, "ASR success: chars=${text.length}")
                    s.rawText = text
                    s.asrStatus = StageStatus.SUCCESS
                    afterAsr(s, text)
                },
                onFailure = { error ->
                    Log.e(TAG, "ASR failed: ${formatError(error)}", error)
                    s.asrStatus = StageStatus.FAILED
                    s.asrError = formatError(error)
                    failSession(error.message ?: "转写失败")
                },
            )
        }
    }

    private suspend fun afterAsr(s: Session, text: String) {
        when (s.mode) {
            InputMode.POLISH -> {
                val result = polishFactory().polish(text)
                result.fold(
                    onSuccess = { polished ->
                        Log.i(TAG, "polish success: chars=${polished.length}")
                        s.finalText = polished
                        s.polishStatus = StageStatus.SUCCESS
                        inject(s)
                    },
                    onFailure = { error ->
                        Log.w(TAG, "polish failed, falling back to raw text: ${formatError(error)}", error)
                        s.finalText = text
                        s.polishStatus = StageStatus.FAILED
                        s.polishError = formatError(error)
                        toast("润色失败，已输入原文")
                        inject(s)
                    },
                )
            }
            InputMode.TRANSLATE -> {
                val result = translateFactory().translate(text, settings.translateTargetLanguage)
                result.fold(
                    onSuccess = { translated ->
                        Log.i(TAG, "translate success: chars=${translated.length}")
                        s.finalText = translated
                        s.polishStatus = StageStatus.SUCCESS
                        inject(s)
                    },
                    onFailure = { error ->
                        Log.w(TAG, "translate failed, falling back to raw text: ${formatError(error)}", error)
                        s.finalText = text
                        s.polishStatus = StageStatus.FAILED
                        s.polishError = formatError(error)
                        toast("翻译失败，已输入原文")
                        inject(s)
                    },
                )
            }
            InputMode.TRANSCRIBE -> {
                s.polishStatus = StageStatus.SKIPPED
                s.finalText = text
                inject(s)
            }
        }
    }

    private fun inject(s: Session) {
        setState(BallState.INJECTING)
        val before = service?.inputSnapshot()
        val ok = service?.injectText(s.finalText) ?: false
        if (ok) {
            Log.i(TAG, "injection success: chars=${s.finalText.length}")
            s.injectionStatus = StageStatus.SUCCESS
            val after = service?.currentInputText()
            if (before != null && after != null) registerUndo(before, after)
            Haptics.success(context)
            setState(BallState.IDLE)
            persist(s)
        } else {
            Log.e(
                TAG,
                "injection failed: chars=${s.finalText.length} " +
                    "serviceConnected=${service != null} (focused editable field lost?)"
            )
            s.injectionStatus = StageStatus.FAILED
            s.injectionError = "无法写入输入框，请重新点击输入框后再试"
            failSession(s.injectionError!!)
        }
    }

    private fun failSession(message: String) {
        fail(message)
        session?.let { persist(it) }
    }

    private fun persist(s: Session) {
        if (session === s) session = null
        val record = s.toRecord()
        scope.launch(Dispatchers.IO) {
            runCatching { history.insert(record) }
                .onFailure { Log.e(TAG, "failed to persist history record", it) }
        }
    }

    private fun fail(message: String) {
        Haptics.error(context)
        toast(message)
        setState(BallState.ERROR)
        handler.removeCallbacks(resetRunnable)
        handler.postDelayed(resetRunnable, ERROR_RESET_MS)
    }

    private fun setState(newState: BallState) {
        val old = _state.value
        if (old != newState) Log.d(TAG, "state: $old -> $newState")
        _state.value = newState
        overlay?.setState(newState)
    }

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    private fun resolveAppLabel(packageName: String?): String {
        if (packageName.isNullOrBlank()) return "未知应用"
        return try {
            val pm = context.packageManager
            pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun formatError(error: Throwable): String = when (error) {
        is AsrException -> "${error.code}: ${error.message}"
        is PolishException -> "${error.code}: ${error.message}"
        is TranslateException -> "${error.code}: ${error.message}"
        else -> error.message ?: "未知错误"
    }

    companion object {
        private const val TAG = "FloatingType.Pipeline"
        private const val ERROR_RESET_MS = 2000L

        /** How long the undo button stays available after a rewrite. */
        private const val UNDO_TTL_MS = 8000L
    }
}
