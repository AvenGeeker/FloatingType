package com.avengeeker.floatingtype.overlay

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import androidx.core.content.ContextCompat
import com.avengeeker.floatingtype.R
import com.avengeeker.floatingtype.history.InputMode

/**
 * Lightweight always-on overlay ball (plain View, not Compose, to keep the
 * resident overlay cheap). Draws a modern gradient "glow orb": a soft accent
 * halo, a glassy top highlight, a crisp rim, a white mic glyph, and a small
 * mode badge (转 / 润 / 译). State animations (recording ripple / transcribing
 * arc) and a springy press-scale keep it feeling tactile.
 *
 * The view/window is intentionally larger than the visible ball: the extra
 * [ballDiameterPx]-relative padding (see [BallMetrics]) gives the halo and the
 * recording ripple room to expand without being clipped by the window bounds.
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
     * reserve space for the halo/ripple; drawing is scaled from this, not from
     * the view size. When unset (e.g. the settings preview), fall back to the
     * view size so the ball still renders.
     */
    var ballDiameterPx: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    var state: BallState = BallState.IDLE
        private set

    private val ballPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val haloPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val badgeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33000000 }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val badgeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    private val micDrawable = ContextCompat.getDrawable(context, R.drawable.ic_mic)

    private var pulseFraction = 0f
    private var spinAngle = 0f
    private var pressScale = 1f
    private var pressAnimator: ValueAnimator? = null

    // Cached shaders, rebuilt only when the state or drawn radius changes.
    private var shaderState: BallState? = null
    private var shaderRadius = -1f
    private var ballShader: Shader? = null
    private var haloShader: Shader? = null
    private var highlightShader: Shader? = null

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

    /** Springy press feedback driven by the overlay touch listener. */
    fun setPressedVisual(pressed: Boolean) {
        val target = if (pressed) 0.93f else 1f
        pressAnimator?.cancel()
        pressAnimator = ValueAnimator.ofFloat(pressScale, target).apply {
            duration = if (pressed) 90L else 190L
            interpolator = if (pressed) DecelerateInterpolator() else OvershootInterpolator(1.5f)
            addUpdateListener {
                pressScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        // Scale from the visible ball diameter, not the (larger) window size, so
        // the halo/ripple expands into the reserved padding instead of clipping.
        val ball = if (ballDiameterPx > 0) ballDiameterPx else minOf(width, height)
        val ringSpace = BallMetrics.ringSpace(ball)
        val radius = BallMetrics.ballRadius(ball)
        val alpha = (ballOpacity * 255).toInt()
        val palette = paletteFor(state)
        ensureShaders(cx, cy, radius, state, palette)

        canvas.save()
        canvas.scale(pressScale, pressScale, cx, cy)

        // Soft accent halo, clipped by the window padding.
        haloPaint.shader = haloShader
        haloPaint.alpha = alpha
        canvas.drawCircle(cx, cy, radius * 1.45f, haloPaint)

        // Gradient body + glassy top highlight + crisp rim.
        ballPaint.shader = ballShader
        ballPaint.alpha = alpha
        canvas.drawCircle(cx, cy, radius, ballPaint)

        highlightPaint.shader = highlightShader
        highlightPaint.alpha = alpha
        canvas.drawCircle(cx, cy, radius, highlightPaint)

        rimPaint.color = Color.WHITE
        rimPaint.alpha = (alpha * 0.42f).toInt()
        rimPaint.strokeWidth = maxOf(1f, radius * 0.035f)
        canvas.drawCircle(cx, cy, radius - rimPaint.strokeWidth / 2f, rimPaint)

        when (state) {
            BallState.RECORDING -> {
                val pulseRadius = radius + pulseFraction * ringSpace * BallMetrics.RIPPLE_EXPANSION_FACTOR
                ringPaint.color = palette.glow
                ringPaint.alpha = ((1f - pulseFraction) * alpha).toInt()
                ringPaint.strokeWidth = BallMetrics.rippleStrokeWidth(ball)
                canvas.drawCircle(cx, cy, pulseRadius, ringPaint)
            }
            BallState.TRANSCRIBING -> {
                arcPaint.alpha = alpha
                arcPaint.strokeWidth = ball * 0.055f
                val inset = radius * 0.50f
                val arcRect = RectF(cx - inset, cy - inset, cx + inset, cy + inset)
                canvas.drawArc(arcRect, spinAngle, 270f, false, arcPaint)
            }
            else -> Unit
        }

        micDrawable?.let { drawable ->
            val iconSize = (radius * 0.92f).toInt()
            val left = (cx - iconSize / 2f).toInt()
            val top = (cy - iconSize / 2f).toInt()
            drawable.setBounds(left, top, left + iconSize, top + iconSize)
            drawable.alpha = alpha
            drawable.draw(canvas)
        }

        drawModeBadge(canvas, radius, alpha, ball)
        canvas.restore()
    }

    private fun ensureShaders(cx: Float, cy: Float, radius: Float, state: BallState, palette: Palette) {
        if (shaderState == state && shaderRadius == radius) return
        shaderState = state
        shaderRadius = radius
        ballShader = LinearGradient(
            cx - radius * 0.7f, cy - radius * 0.9f,
            cx + radius * 0.7f, cy + radius * 0.9f,
            intArrayOf(palette.top, palette.mid, palette.bottom),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        haloShader = RadialGradient(
            cx, cy, radius * 1.45f,
            intArrayOf(withAlpha(palette.glow, 120), withAlpha(palette.glow, 0)),
            floatArrayOf(0.62f, 1f),
            Shader.TileMode.CLAMP,
        )
        highlightShader = RadialGradient(
            cx - radius * 0.30f, cy - radius * 0.46f, radius * 1.02f,
            intArrayOf(withAlpha(Color.WHITE, 70), withAlpha(Color.WHITE, 0)),
            floatArrayOf(0f, 0.75f),
            Shader.TileMode.CLAMP,
        )
    }

    private fun drawModeBadge(canvas: Canvas, radius: Float, alpha: Int, ball: Int) {
        val badgeRadius = ball * 0.17f
        val bx = width / 2f + radius * 0.70f
        val by = height / 2f + radius * 0.70f
        val accent = modeAccent(mode)

        badgeShadowPaint.alpha = (alpha * 0.20f).toInt()
        canvas.drawCircle(bx, by + badgeRadius * 0.10f, badgeRadius + badgeRadius * 0.12f, badgeShadowPaint)

        badgePaint.alpha = (alpha * 0.98f).toInt()
        canvas.drawCircle(bx, by, badgeRadius, badgePaint)

        badgeRingPaint.color = accent
        badgeRingPaint.alpha = (alpha * 0.55f).toInt()
        badgeRingPaint.strokeWidth = maxOf(1f, badgeRadius * 0.10f)
        canvas.drawCircle(bx, by, badgeRadius + badgeRingPaint.strokeWidth / 2f, badgeRingPaint)

        badgeTextPaint.color = accent
        badgeTextPaint.alpha = alpha
        badgeTextPaint.textSize = badgeRadius * 1.10f
        val baseline = by - (badgeTextPaint.ascent() + badgeTextPaint.descent()) / 2f
        canvas.drawText(mode.badge, bx, baseline, badgeTextPaint)
    }

    private fun paletteFor(state: BallState): Palette = when (state) {
        BallState.IDLE -> Palette(0xFF9AA0FF.toInt(), 0xFF5A5CF0.toInt(), 0xFF4032C8.toInt(), 0xFF6366F1.toInt())
        BallState.RECORDING -> Palette(0xFFFF9AAE.toInt(), 0xFFF2435C.toInt(), 0xFFCE1B3C.toInt(), 0xFFF43F5E.toInt())
        BallState.TRANSCRIBING -> Palette(0xFFFFD37A.toInt(), 0xFFF5A623.toInt(), 0xFFD07A05.toInt(), 0xFFF59E0B.toInt())
        BallState.INJECTING -> Palette(0xFF86E8AC.toInt(), 0xFF2DBB72.toInt(), 0xFF14884C.toInt(), 0xFF22C55E.toInt())
        BallState.ERROR -> Palette(0xFFFF9A9A.toInt(), 0xFFE23C4E.toInt(), 0xFFA81C2E.toInt(), 0xFFEF4444.toInt())
    }

    private fun modeAccent(mode: InputMode): Int = when (mode) {
        InputMode.TRANSCRIBE -> 0xFF4F46E5.toInt()
        InputMode.POLISH -> 0xFF0F766E.toInt()
        InputMode.TRANSLATE -> 0xFF0891B2.toInt()
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    override fun onDetachedFromWindow() {
        pulseAnimator.cancel()
        spinAnimator.cancel()
        pressAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private data class Palette(val top: Int, val mid: Int, val bottom: Int, val glow: Int)
}
