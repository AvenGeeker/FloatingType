package com.avengeeker.floatingtype.audio

import android.content.Context
import android.content.pm.ApplicationInfo
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * AudioRecord-based 16 kHz mono PCM16 capture. Owned by the microphone
 * foreground service (required for background recording on targetSdk 34).
 * All listener callbacks are delivered on the main thread.
 */
class RecorderController(private val context: Context) {

    interface Listener {
        fun onRecordingStarted()
        fun onRecordingFinished(pcm: ByteArray)
        fun onRecordingError(message: String)
        fun onMaxDurationReached()
    }

    var listener: Listener? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioRecord: AudioRecord? = null
    private var recordThread: Thread? = null

    @Volatile
    private var running = false

    private var startedAtElapsed = 0L
    private var readErrorLogged = false

    private val buffer = ByteArrayOutputStream()

    private val debuggable: Boolean by lazy {
        (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    val isRecording: Boolean get() = running

    fun start() {
        if (running) {
            Log.d(TAG, "start ignored: already recording")
            return
        }
        buffer.reset()
        readErrorLogged = false

        val minBufferSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBufferSize <= 0) {
            Log.e(TAG, "AudioRecord.getMinBufferSize returned $minBufferSize (sampleRate=$SAMPLE_RATE mono pcm16)")
            postError("无法初始化录音器")
            return
        }

        val record = try {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBufferSize * 2
            )
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord constructor failed (permission denied or mic busy)", e)
            postError("麦克风不可用（权限被拒绝或被占用）")
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord not initialized: state=${record.state} (mic busy or missing permission)")
            record.release()
            postError("麦克风被占用或不可用")
            return
        }

        try {
            record.startRecording()
        } catch (e: Exception) {
            Log.e(TAG, "AudioRecord.startRecording threw", e)
            record.release()
            postError("启动录音失败")
            return
        }
        if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
            Log.e(TAG, "AudioRecord not recording after start: recordingState=${record.recordingState}")
            record.release()
            postError("启动录音失败")
            return
        }

        audioRecord = record
        running = true
        val startTime = SystemClock.elapsedRealtime()
        startedAtElapsed = startTime
        Log.i(TAG, "recording started: bufferSize=${minBufferSize * 2} sampleRate=$SAMPLE_RATE")
        recordThread = Thread {
            val chunk = ByteArray(minBufferSize)
            while (running) {
                val read = try {
                    record.read(chunk, 0, chunk.size)
                } catch (e: Exception) {
                    if (!readErrorLogged) {
                        readErrorLogged = true
                        Log.w(TAG, "AudioRecord.read threw; continuing", e)
                    }
                    -1
                }
                if (read > 0) {
                    synchronized(buffer) { buffer.write(chunk, 0, read) }
                } else if (read < 0 && !readErrorLogged) {
                    readErrorLogged = true
                    Log.w(TAG, "AudioRecord.read returned error code $read; continuing")
                }
                if (SystemClock.elapsedRealtime() - startTime >= MAX_DURATION_MS) {
                    Log.i(TAG, "max recording duration reached (${MAX_DURATION_MS}ms); auto-stopping")
                    mainHandler.post { listener?.onMaxDurationReached() }
                    break
                }
            }
        }.apply { start() }

        mainHandler.post { listener?.onRecordingStarted() }
    }

    fun stop() {
        if (!running) return
        running = false
        try {
            recordThread?.join(500)
        } catch (e: InterruptedException) {
            Log.w(TAG, "interrupted while joining record thread", e)
            Thread.currentThread().interrupt()
        }
        val record = audioRecord
        audioRecord = null
        recordThread = null
        try {
            record?.stop()
        } catch (e: Exception) {
            // Already stopped.
            Log.w(TAG, "AudioRecord.stop threw (already stopped?)", e)
        }
        record?.release()
        val pcm = synchronized(buffer) { buffer.toByteArray() }
        Log.i(
            TAG,
            "recording stopped: pcmBytes=${pcm.size} " +
                "durationMs=${SystemClock.elapsedRealtime() - startedAtElapsed}"
        )
        if (pcm.isNotEmpty() && pcm.all { it == 0.toByte() }) {
            Log.w(
                TAG,
                "recorded audio is all-zero (silence): microphone produced no signal. " +
                    "On emulators check mic bridging; the ASR service rejects silent audio " +
                    "with an empty HTTP 400."
            )
        }
        if (debuggable) {
            // Keep the most recent recording so ASR failures can be diagnosed
            // by inspecting the exact audio that was sent.
            runCatching {
                val dir = File(context.getExternalFilesDir(null), "debug")
                dir.mkdirs()
                val file = File(dir, "last_recording.wav")
                file.writeBytes(WavEncoder.encode(pcm, SAMPLE_RATE))
                Log.d(TAG, "debug: recording saved to ${file.absolutePath}")
            }.onFailure { Log.w(TAG, "debug: failed to save recording", it) }
        }
        mainHandler.post { listener?.onRecordingFinished(pcm) }
    }

    private fun postError(message: String) {
        mainHandler.post { listener?.onRecordingError(message) }
    }

    companion object {
        private const val TAG = "FloatingType.Recorder"
        const val SAMPLE_RATE = 16000
        const val MAX_DURATION_MS = 60_000L
        const val MIN_DURATION_MS = 300L
    }
}
