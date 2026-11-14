package com.avengeeker.floatingtype.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import com.avengeeker.floatingtype.R
import com.avengeeker.floatingtype.history.InputMode

/**
 * Lightweight always-on overlay ball (plain View, not Compose, to keep the
 * resident overlay cheap). Draws a colored circle + mic icon with state
 * animations and a small mode badge (转 / 润) at the bottom-right.
 *
 * The view/window is intentionally larger than the visible ball: the extra
 * [ballDiameterPx]-relative padding (see [BallMetrics]) gives the recording
 * ripple room to expand without being clipped by the window bounds.
 */
class BallView(context: Context) : View(context) {

    var ballOpacity: Float = 0.95f
        set(value) {
            field = value.coerceIn(0.3f, 1f)
            invalidate()
        }

    var mode: InputMode = InputMode.TRANSCRIBE
        set(value) {
            field = value
            invalidate()
        }

    /**
     * Diameter of the visible ball in pixels. The window may be larger to
     * reserve space for the ripple; drawing is scaled from this, not from the
     * view size. When unset (e.g. the settings preview), fall back to the view
     * size so the ball still renders.
     */
    var ballDiameterPx: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var state: BallState = BallState.IDLE
        private set

    private val circlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val micDrawable = ContextCompat.getDrawable(context, R.drawable.ic_mic)

    private var pulseFraction = 0f
    private var spinAngle = 0f

    private val pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 1100L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            pulseFraction = it.animatedFraction
            invalidate()
        }
    }
    private val spinAnimator = ValueAnimator.ofFloat(0f, 360f).apply {
        duration = 900L
        repeatCount = ValueAnimator.INFINITE
        interpolator = LinearInterpolator()
        addUpdateListener {
            spinAngle = it.animatedValue as Float
            invalidate()
        }
    }

    fun setState(newState: BallState) {
        if (state == newState) return
        state = newState
        pulseAnimator.cancel()
        spinAnimator.cancel()
        when (newState) {
            BallState.RECORDING -> pulseAnimator.start()
            BallState.TRANSCRIBING -> spinAnimator.start()
            else -> Unit
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        // Scale from the visible ball diameter, not the (larger) window size, so
        // the ripple expands into the reserved padding instead of being clipped.
        val ball = if (ballDiameterPx > 0) ballDiameterPx else minOf(width, height)
        val ringSpace = BallMetrics.ringSpace(ball)
        val radius = BallMetrics.ballRadius(ball)
        val alpha = (ballOpacity * 255).toInt()

        circlePaint.color = baseColor()
        circlePaint.alpha = alpha
        canvas.drawCircle(cx, cy, radius, circlePaint)

        when (state) {
            BallState.RECORDING -> {
                val ringRadius = radius + pulseFraction * ringSpace * BallMetrics.RIPPLE_EXPANSION_FACTOR
                ringPaint.color = baseColor()
                ringPaint.alpha = ((1f - pulseFraction) * alpha).toInt()
                ringPaint.strokeWidth = BallMetrics.rippleStrokeWidth(ball)
                canvas.drawCircle(cx, cy, ringRadius, ringPaint)
            }
            BallState.TRANSCRIBING -> {
                arcPaint.alpha = alpha
                arcPaint.strokeWidth = ball * 0.06f
                val inset = radius * 0.45f
                val arcRect = RectF(cx - inset, cy - inset, cx + inset, cy + inset)
                canvas.drawArc(arcRect, spinAngle, 270f, false, arcPaint)
            }
            else -> Unit
        }

        micDrawable?.let { drawable ->
            val iconSize = (radius * 1.05f).toInt()
            val left = (cx - iconSize / 2f).toInt()
            val top = (cy - iconSize / 2f).toInt()
            drawable.setBounds(left, top, left + iconSize, top + iconSize)
            drawable.alpha = alpha
            drawable.draw(canvas)
        }

        drawModeBadge(canvas, cx, cy, radius, alpha, ball)
    }

    private fun drawModeBadge(canvas: Canvas, cx: Float, cy: Float, radius: Float, alpha: Int, ball: Int) {
        val badgeRadius = ball * 0.17f
        val bx = cx + radius * 0.62f
        val by = cy + radius * 0.62f
        badgePaint.color = Color.WHITE
        badgePaint.alpha = alpha
        canvas.drawCircle(bx, by, badgeRadius, badgePaint)
        badgeTextPaint.color = if (mode == InputMode.POLISH) {
            Color.parseColor("#0F766E")
        } else {
            Color.parseColor("#4F46E5")
        }
        badgeTextPaint.alpha = alpha
        badgeTextPaint.textSize = badgeRadius * 1.15f
        val label = if (mode == InputMode.POLISH) "润" else "转"
        val baseline = by - (badgeTextPaint.ascent() + badgeTextPaint.descent()) / 2f
        canvas.drawText(label, bx, baseline, badgeTextPaint)
    }

    private fun baseColor(): Int = when (state) {
        BallState.IDLE -> Color.parseColor("#4F46E5")
        BallState.RECORDING -> Color.parseColor("#DC2626")
        BallState.TRANSCRIBING -> Color.parseColor("#D97706")
        BallState.INJECTING -> Color.parseColor("#16A34A")
        BallState.ERROR -> Color.parseColor("#B91C1C")
    }

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        spinAnimator.cancel()
        super.onDetachedFromWindow()
    }
}
