package com.avengeeker.floatingtype.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import com.avengeeker.floatingtype.pipeline.VoiceInputPipeline
import com.avengeeker.floatingtype.settings.SettingsRepository
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * WindowManager-backed floating ball:
 * TYPE_APPLICATION_OVERLAY (26+) / TYPE_PHONE (24-25), not focusable,
 * draggable with edge snapping, positioned above the keyboard when visible.
 */
class OverlayBallManager(
    private val context: Context,
    private val settings: SettingsRepository,
    private val pipeline: VoiceInputPipeline,
) {
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val handler = Handler(Looper.getMainLooper())
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    private var ballView: BallView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var snapAnimator: ValueAnimator? = null

    var isVisible: Boolean = false
        private set

    private var currentState: BallState = BallState.IDLE

    private val settingsListener: () -> Unit = {
        handler.post { if (isVisible) applyAppearanceAndPosition(lastImeTop) }
    }

    private var lastImeTop = -1

    // Last values handed to WindowManager. Focus is re-evaluated while the user
    // types, so skip relayouts that would not change anything on screen.
    private var appliedX = Int.MIN_VALUE
    private var appliedY = Int.MIN_VALUE
    private var appliedSize = -1

    init {
        settings.addChangeListener(settingsListener)
    }

    fun show(imeTop: Int) {
        if (!Settings.canDrawOverlays(context)) return
        lastImeTop = imeTop
        ensureView()
        applyAppearanceAndPosition(imeTop)
        val view = ballView ?: return
        val params = layoutParams ?: return
        try {
            if (!isVisible) {
                windowManager.addView(view, params)
                isVisible = true
                markApplied(params)
            }
            view.setState(currentState)
        } catch (_: Exception) {
            // Overlay permission may have been revoked; stay hidden.
            isVisible = false
        }
    }

    fun hide() {
        val view = ballView
        if (isVisible && view != null) {
            try {
                windowManager.removeView(view)
            } catch (_: Exception) {
                // Already detached.
            }
        }
        isVisible = false
        appliedX = Int.MIN_VALUE
        appliedY = Int.MIN_VALUE
        appliedSize = -1
    }

    fun setState(state: BallState) {
        currentState = state
        ballView?.setState(state)
    }

    private fun ensureView() {
        if (ballView != null) return
        val view = BallView(context)
        view.setOnTouchListener(BallTouchListener())
        ballView = view
        layoutParams = WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0
        }
    }

    private fun ballSizePx(): Int =
        (settings.ballSizeDp * context.resources.displayMetrics.density).toInt()

    private fun marginPx(): Int =
        (8 * context.resources.displayMetrics.density).toInt()

    private fun applyAppearanceAndPosition(imeTop: Int) {
        val view = ballView ?: return
        val params = layoutParams ?: return
        val size = ballSizePx()
        val padding = BallMetrics.ripplePadding(size)
        // Reserve ripple room: the window is larger than the visible ball, which
        // keeps its configured diameter and is centered in the padded window.
        view.ballDiameterPx = size
        params.width = BallMetrics.windowSize(size)
        params.height = BallMetrics.windowSize(size)
        if (view.ballOpacity != settings.ballOpacity) view.ballOpacity = settings.ballOpacity
        if (view.mode != settings.defaultMode) view.mode = settings.defaultMode

        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val margin = marginPx()

        var x = settings.ballX
        var y = settings.ballY
        if (x < 0 || y < 0) {
            x = screenW - size - margin
            y = (screenH * 0.6f).toInt()
        }
        x = x.coerceIn(margin, max(margin, screenW - size - margin))
        y = y.coerceIn(margin, max(margin, screenH - size - margin))
        if (imeTop in 0 until screenH) {
            val maxYAboveKeyboard = imeTop - size - margin
            if (y > maxYAboveKeyboard) y = max(margin, maxYAboveKeyboard)
        }
        // (x, y) is the visible ball's top-left; offset by the padding so the
        // padded window places the ball exactly there.
        params.x = x - padding
        params.y = y - padding
        if (params.x != appliedX || params.y != appliedY || params.width != appliedSize) {
            updateLayout(view, params)
        }
    }

    private fun markApplied(params: WindowManager.LayoutParams) {
        appliedX = params.x
        appliedY = params.y
        appliedSize = params.width
    }

    private fun updateLayout(view: View, params: WindowManager.LayoutParams) {
        if (!isVisible) return
        try {
            windowManager.updateViewLayout(view, params)
            markApplied(params)
        } catch (_: Exception) {
            isVisible = false
        }
    }

    private fun persistPosition() {
        val params = layoutParams ?: return
        // Persist the visible ball's top-left (not the padded window's), so the
        // stored coordinates keep their meaning across size changes and restarts.
        val padding = BallMetrics.ripplePadding(ballSizePx())
        settings.ballX = params.x + padding
        settings.ballY = params.y + padding
    }

    private fun snapToEdge() {
        val view = ballView ?: return
        val params = layoutParams ?: return
        val metrics = context.resources.displayMetrics
        val size = ballSizePx()
        val padding = BallMetrics.ripplePadding(size)
        val margin = marginPx()
        // Snap in visible-ball coordinates, then convert back to window coords.
        val center = params.x + padding + size / 2f
        val targetBallX = if (center < metrics.widthPixels / 2f) {
            margin
        } else {
            metrics.widthPixels - size - margin
        }
        val targetX = targetBallX - padding
        snapAnimator?.cancel()
        val startX = params.x
        snapAnimator = ValueAnimator.ofInt(startX, targetX).apply {
            duration = 160L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                params.x = animator.animatedValue as Int
                updateLayout(view, params)
            }
            start()
        }
        params.x = targetX
        persistPosition()
    }

    private inner class BallTouchListener : View.OnTouchListener {
        private var downRawX = 0f
        private var downRawY = 0f
        private var downX = 0
        private var downY = 0
        private var downTime = 0L
        private var dragging = false
        private var longPressHandled = false

        private val longPressRunnable = Runnable {
            if (!dragging) {
                longPressHandled = true
                pipeline.toggleInputMode()
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = layoutParams ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    snapAnimator?.cancel()
                    downRawX = event.rawX
                    downRawY = event.rawY
                    downX = params.x
                    downY = params.y
                    downTime = System.currentTimeMillis()
                    dragging = false
                    longPressHandled = false
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downRawX
                    val dy = event.rawY - downRawY
                    if (!dragging && hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        dragging = true
                        handler.removeCallbacks(longPressRunnable)
                    }
                    if (dragging) {
                        val metrics = context.resources.displayMetrics
                        val margin = marginPx()
                        val size = ballSizePx()
                        val padding = BallMetrics.ripplePadding(size)
                        // Clamp the visible ball (not the padded window) to the
                        // same margins as before, then offset back to window coords.
                        val ballX = (downX + padding + dx).toInt()
                            .coerceIn(margin, max(margin, metrics.widthPixels - size - margin))
                        val ballY = (downY + padding + dy).toInt()
                            .coerceIn(margin, max(margin, metrics.heightPixels - size - margin))
                        params.x = ballX - padding
                        params.y = ballY - padding
                        updateLayout(v, params)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    val duration = System.currentTimeMillis() - downTime
                    if (dragging) {
                        snapToEdge()
                    } else if (!longPressHandled && duration < CLICK_TIMEOUT_MS) {
                        v.performClick()
                        pipeline.toggle()
                    }
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (dragging) snapToEdge()
                    return true
                }
                else -> return false
            }
        }
    }

    companion object {
        private const val CLICK_TIMEOUT_MS = 600L
        private const val LONG_PRESS_MS = 500L
    }
}
