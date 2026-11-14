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
import com.avengeeker.floatingtype.util.Haptics
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Orchestrates: ball tap -> microphone foreground service -> batch ASR ->
 * optional AI polish -> accessibility text injection -> local history.
 * Toggle interaction: tap to start, tap again to stop. Long-press toggles
 * the persisted input mode. All state transitions run on the main thread;
 * network work runs on Dispatchers.IO inside the engines.
 */
class VoiceInputPipeline(
    private val context: Context,
    private val settings: SettingsRepository,
    private val history: HistoryRepository,
    private val asrFactory: () -> AsrEngine,
    private val polishFactory: () -> PolishEngine,
) {
    var overlay: OverlayBallManager? = null

    private var service: FloatingAccessibilityService? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val handler = Handler(Looper.getMainLooper())
    private var transcribeJob: Job? = null
    private var session: Session? = null

    private val _state = MutableStateFlow(BallState.IDLE)
    val state: StateFlow<BallState> = _state

    private val resetRunnable = Runnable {
        if (_state.value == BallState.ERROR) setState(BallState.IDLE)
    }

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
        session = null
        if (isBusy()) setState(BallState.IDLE)
    }

    /** Long-press entry point: toggle and persist the default input mode. */
    fun toggleInputMode(): Boolean {
        if (isBusy()) {
            Log.w(TAG, "toggleInputMode ignored: session busy (state=${_state.value})")
            toast("当前正在输入，无法切换模式")
            return false
        }
        val next = settings.defaultMode.next()
        settings.defaultMode = next
        Haptics.tick(context)
        toast("已切换为${next.label}模式")
        return true
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
            polishModel = if (mode == InputMode.POLISH) settings.polishModel else null,
        )
        Log.i(
            TAG,
            "session start: mode=$mode asrModel=${settings.model} " +
                "polishModel=${if (mode == InputMode.POLISH) settings.polishModel else "<none>"} " +
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
        if (s.mode == InputMode.POLISH) {
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
        } else {
            s.polishStatus = StageStatus.SKIPPED
            s.finalText = text
            inject(s)
        }
    }

    private fun inject(s: Session) {
        setState(BallState.INJECTING)
        val ok = service?.injectText(s.finalText) ?: false
        if (ok) {
            Log.i(TAG, "injection success: chars=${s.finalText.length}")
            s.injectionStatus = StageStatus.SUCCESS
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
        else -> error.message ?: "未知错误"
    }

    companion object {
        private const val TAG = "FloatingType.Pipeline"
        private const val ERROR_RESET_MS = 2000L
    }
}
