package com.avengeeker.floatingtype.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Color
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
import com.avengeeker.floatingtype.R
import com.avengeeker.floatingtype.history.InputMode
import com.avengeeker.floatingtype.pipeline.VoiceInputPipeline
import com.avengeeker.floatingtype.settings.SettingsRepository
import com.avengeeker.floatingtype.util.Haptics
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

/**
 * WindowManager-backed floating ball:
 * TYPE_APPLICATION_OVERLAY (26+) / TYPE_PHONE (24-25), not focusable,
 * draggable with edge snapping, positioned above the keyboard when visible.
 *
 * The resting ball stays simple (gradient orb + mic + mode badge). Long-press
 * opens a radial menu of radially-rotated pills [RadialMenuOverlay] laid on a
 * fan centered on the ball. The fan opens straight away from the nearest screen
 * edge (right -> left, bottom -> up, corner -> diagonal) so it is mirror-symmetric
 * about the X axis for side placements. The menu window is clamped on-screen and
 * the view draws around the ball's origin, so OEMs cannot shift it off the ball.
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

    private val radialMenu = RadialMenuOverlay(context)
    private var undoAvailable = false

    // Radial-menu gesture state, valid only while menuActive.
    private val menuModes = InputMode.entries
    private var menuActive = false
    private var menuHasUndo = false
    private var menuCenterX = 0
    private var menuCenterY = 0
    private var menuCenters: List<Pair<Double, Double>> = emptyList()
    private var menuCaptureRadius = 0.0

    // Ball position before the small shift that makes the fan fully visible.
    private var menuRestoreX = Int.MIN_VALUE
    private var menuRestoreY = Int.MIN_VALUE
    private var restoreAnimator: ValueAnimator? = null

    var isVisible: Boolean = false
        private set

    private var currentState: BallState = BallState.IDLE

    private val settingsListener: () -> Unit = {
        handler.post { if (isVisible) applyAppearanceAndPosition(lastImeTop) }
    }

    private var lastImeTop = -1

    private var appliedX = Int.MIN_VALUE
    private var appliedY = Int.MIN_VALUE
    private var appliedSize = -1

    init {
        settings.addChangeListener(settingsListener)
        pipeline.undoAvailabilityListener = { available -> undoAvailable = available }
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
            isVisible = false
        }
    }

    fun hide() {
        restoreAnimator?.cancel()
        restoreAnimator = null
        menuRestoreX = Int.MIN_VALUE
        menuRestoreY = Int.MIN_VALUE
        menuActive = false
        radialMenu.hide()
        pipeline.clearUndo()
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
        view.ballDiameterPx = size
        params.width = BallMetrics.windowSize(size)
        params.height = BallMetrics.windowSize(size)
        if (view.ballOpacity != settings.ballOpacity) view.ballOpacity = settings.ballOpacity
        if (view.mode != settings.defaultMode) view.mode = settings.defaultMode

        if (menuActive) {
            markApplied(params)
            return
        }

        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        var x = settings.ballX
        var y = settings.ballY
        if (x < 0 || y < 0) {
            x = screenW - size - marginPx()
            y = (screenH * 0.6f).toInt()
        }
        x = x.coerceIn(marginPx(), max(marginPx(), screenW - size - marginPx()))
        y = y.coerceIn(marginPx(), max(marginPx(), screenH - size - marginPx()))
        if (imeTop in 0 until screenH) {
            val maxYAboveKeyboard = imeTop - size - marginPx()
            if (y > maxYAboveKeyboard) y = max(marginPx(), maxYAboveKeyboard)
        }
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

    // ---- Radial mode menu -------------------------------------------------

    private fun buildMenuItems(): List<RadialMenuItem> {
        val items = menuModes.map { RadialMenuItem(it.label, modeColor(it), modeIcon(it)) }.toMutableList()
        if (undoAvailable) {
            items.add(RadialMenuItem(context.getString(R.string.undo_action), UNDO_COLOR, R.drawable.ic_undo))
        }
        return items
    }

    private fun modeIcon(mode: InputMode): Int = when (mode) {
        InputMode.TRANSCRIBE -> R.drawable.ic_mic
        InputMode.POLISH -> R.drawable.ic_mode_polish
        InputMode.TRANSLATE -> R.drawable.ic_mode_translate
    }

    private fun modeColor(mode: InputMode): Int = when (mode) {
        InputMode.TRANSCRIBE -> Color.parseColor("#4F46E5")
        InputMode.POLISH -> Color.parseColor("#0F766E")
        InputMode.TRANSLATE -> Color.parseColor("#0891B2")
    }

    private fun openRadialMenu() {
        val view = ballView ?: return
        val params = layoutParams ?: return
        val metrics = context.resources.displayMetrics
        val screenW = metrics.widthPixels
        val screenH = metrics.heightPixels
        val size = ballSizePx()
        val padding = BallMetrics.ripplePadding(size)
        val curCenterX = params.x + padding + size / 2f
        val curCenterY = params.y + padding + size / 2f
        val ballRadius = BallMetrics.ballRadius(size)
        val capsuleWidth = size * 1.10f
        val capsuleHeight = size * 0.46f
        val aHalf = capsuleWidth / 2f
        val bHalf = capsuleHeight / 2f
        val items = buildMenuItems()
        val count = items.size
        if (count <= 0) return

        val margin = marginPx().toFloat()
        val safeTop = margin
        val imeTop = if (lastImeTop in 0 until screenH) lastImeTop.toFloat() else screenH.toFloat()
        val safeBottom = imeTop - margin
        // Open straight away from the nearest edge (right -> left, etc.) so the fan
        // is mirror-symmetric about the X axis for side placements.
        val centerAngle = RadialMenuGeometry.openDirectionDeg(
            curCenterX, curCenterY, screenW.toFloat(), imeTop,
        )
        val sweep = RadialMenuGeometry.FAN_SWEEP_DEG
        val angles = RadialMenuGeometry.itemAngles(centerAngle, sweep, count)

        val gap = size * 0.06f
        val stepDeg = if (count > 1) sweep / (count - 1) else sweep
        val clearR = ballRadius + gap + aHalf
        val spacingR = (aHalf + (2.0 * bHalf + gap) / (2.0 * sin(Math.toRadians(stepDeg / 2.0)))).toFloat()
        val radius = max(clearR, spacingR)

        fun halfX(ang: Double): Float {
            val rad = Math.toRadians(ang)
            return (abs(cos(rad)) * aHalf + abs(sin(rad)) * bHalf).toFloat()
        }
        fun halfY(ang: Double): Float {
            val rad = Math.toRadians(ang)
            return (abs(sin(rad)) * aHalf + abs(cos(rad)) * bHalf).toFloat()
        }
        val leftExtent = angles.maxOf { -(RadialMenuGeometry.itemCenterX(it, radius.toDouble())) + halfX(it) }.toFloat()
        val rightExtent = angles.maxOf { RadialMenuGeometry.itemCenterX(it, radius.toDouble()) + halfX(it) }.toFloat()
        val topExtent = angles.maxOf { -(RadialMenuGeometry.itemCenterY(it, radius.toDouble())) + halfY(it) }.toFloat()
        val bottomExtent = angles.maxOf { RadialMenuGeometry.itemCenterY(it, radius.toDouble()) + halfY(it) }.toFloat()

        // Shift the ball just enough that the whole (symmetric) fan is visible
        // above the keyboard and within the screen.
        val minAnchorX = margin + leftExtent
        val maxAnchorX = (screenW - margin - rightExtent).coerceAtLeast(minAnchorX)
        val anchorX = curCenterX.coerceIn(minAnchorX, maxAnchorX)
        val minAnchorY = safeTop + topExtent
        val maxAnchorY = (safeBottom - bottomExtent).coerceAtLeast(minAnchorY)
        val anchorY = curCenterY.coerceIn(minAnchorY, maxAnchorY)

        // Reposition instantly (a few px) so the fan and ball are always aligned.
        menuActive = true
        menuRestoreX = params.x
        menuRestoreY = params.y
        params.x = (anchorX - size / 2f - padding).toInt()
        params.y = (anchorY - size / 2f - padding).toInt()
        updateLayout(view, params)

        // Menu window: clamp on-screen and draw around the ball's origin so an OEM
        // window shift can never move the fan off the ball.
        val maxReach = hypot(aHalf.toDouble(), bHalf.toDouble()).toFloat()
        val boxPad = size * 0.10f
        val windowSize = ceil(2f * (radius + maxReach) + 2f * boxPad).toInt()
        val placeX = RadialMenuGeometry.clampWindow(anchorX, windowSize, screenW)
        val placeY = RadialMenuGeometry.clampWindow(anchorY, windowSize, screenH)

        menuCenterX = anchorX.toInt()
        menuCenterY = anchorY.toInt()
        menuCenters = angles.map {
            RadialMenuGeometry.itemCenterX(it, radius.toDouble()) to
                RadialMenuGeometry.itemCenterY(it, radius.toDouble())
        }
        menuCaptureRadius = hypot(capsuleWidth / 2.0, capsuleHeight / 2.0) * 1.05
        menuHasUndo = undoAvailable
        radialMenu.show(
            placeX.windowPos, placeY.windowPos, windowSize, placeX.origin, placeY.origin,
            items, radius, capsuleWidth, capsuleHeight, centerAngle.toFloat(), sweep.toFloat(),
        )
        Haptics.tick(context)
    }

    private fun menuHitTest(rawX: Float, rawY: Float): Int =
        RadialMenuGeometry.hitTest(
            (rawX - menuCenterX).toDouble(),
            (rawY - menuCenterY).toDouble(),
            menuCenters,
            menuCaptureRadius,
        )

    private fun dismissRadialMenu() {
        menuActive = false
        radialMenu.hide()
        restoreBallAfterMenu()
    }

    private fun restoreBallAfterMenu() {
        val targetX = menuRestoreX
        val targetY = menuRestoreY
        menuRestoreX = Int.MIN_VALUE
        menuRestoreY = Int.MIN_VALUE
        if (targetX == Int.MIN_VALUE || targetY == Int.MIN_VALUE) return
        val view = ballView ?: return
        val params = layoutParams ?: return
        restoreAnimator?.cancel()
        val startX = params.x
        val startY = params.y
        if (startX == targetX && startY == targetY) return
        restoreAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 160L
            interpolator = DecelerateInterpolator()
            addUpdateListener { a ->
                val f = a.animatedFraction
                params.x = (startX + (targetX - startX) * f).toInt()
                params.y = (startY + (targetY - startY) * f).toInt()
                updateLayout(view, params)
            }
            start()
        }
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
                if (pipeline.canSelectMode()) openRadialMenu()
            }
        }

        private fun movedEnough(x: Float, y: Float): Boolean =
            hypot((x - downRawX).toDouble(), (y - downRawY).toDouble()) > touchSlop * 1.5

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
                    ballView?.setPressedVisual(true)
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    if (menuActive) {
                        val index = if (movedEnough(event.rawX, event.rawY)) {
                            menuHitTest(event.rawX, event.rawY)
                        } else {
                            -1
                        }
                        radialMenu.highlight(index)
                        return true
                    }
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
                    ballView?.setPressedVisual(false)
                    if (menuActive) {
                        val index = if (movedEnough(event.rawX, event.rawY)) {
                            menuHitTest(event.rawX, event.rawY)
                        } else {
                            -1
                        }
                        val hadUndo = menuHasUndo
                        dismissRadialMenu()
                        if (index >= 0) {
                            if (hadUndo && index == menuModes.size) {
                                pipeline.undo()
                            } else {
                                pipeline.selectMode(menuModes[index])
                            }
                        }
                        return true
                    }
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
                    ballView?.setPressedVisual(false)
                    if (menuActive) {
                        dismissRadialMenu()
                    } else if (dragging) {
                        snapToEdge()
                    }
                    return true
                }
                else -> return false
            }
        }
    }

    companion object {
        private const val CLICK_TIMEOUT_MS = 600L
        private const val LONG_PRESS_MS = 500L
        private val UNDO_COLOR = Color.parseColor("#475569")
    }
}
