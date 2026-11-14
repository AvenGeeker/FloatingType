package com.avengeeker.floatingtype.audio

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.avengeeker.floatingtype.R
import com.avengeeker.floatingtype.core.ServiceLocator

/**
 * Microphone-type foreground service. Required on targetSdk 34 for recording
 * while the app is not in the foreground. Recording is user-triggered via
 * the floating ball.
 */
class AudioRecordingForegroundService : Service(), RecorderController.Listener {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand: action=${intent?.action} startId=$startId")
        when (intent?.action) {
            ACTION_START -> {
                try {
                    startForegroundWithNotification()
                } catch (e: Exception) {
                    Log.e(TAG, "startForeground(microphone) failed", e)
                    ServiceLocator.pipeline.onRecordingError("无法启动前台录音服务")
                    stopSelf()
                    return START_NOT_STICKY
                }
                ServiceLocator.recorder.listener = this
                ServiceLocator.recorder.start()
            }
            ACTION_STOP -> {
                if (ServiceLocator.recorder.isRecording) {
                    ServiceLocator.recorder.stop()
                } else {
                    Log.w(TAG, "ACTION_STOP received but recorder is not running; stopping service")
                    stopForegroundCompat()
                    stopSelf()
                }
            }
            else -> Log.w(TAG, "ignored unknown/null action: ${intent?.action}")
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.recording_channel_name),
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.recording_notification_title))
            .setContentText(getString(R.string.recording_notification_text))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    override fun onRecordingStarted() {
        ServiceLocator.pipeline.onRecordingStarted()
    }

    override fun onRecordingFinished(pcm: ByteArray) {
        Log.i(TAG, "recording finished: pcmBytes=${pcm.size}")
        stopForegroundCompat()
        stopSelf()
        ServiceLocator.pipeline.onRecordingComplete(pcm)
    }

    override fun onRecordingError(message: String) {
        Log.e(TAG, "recorder reported error: $message")
        stopForegroundCompat()
        stopSelf()
        ServiceLocator.pipeline.onRecordingError(message)
    }

    override fun onMaxDurationReached() {
        ServiceLocator.pipeline.requestStop()
    }

    override fun onDestroy() {
        ServiceLocator.recorder.listener = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FloatingType.RecSvc"
        const val ACTION_START = "com.avengeeker.floatingtype.action.START_RECORDING"
        const val ACTION_STOP = "com.avengeeker.floatingtype.action.STOP_RECORDING"
        private const val CHANNEL_ID = "voice_recording"
        private const val NOTIFICATION_ID = 1001
    }
}
