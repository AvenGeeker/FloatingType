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
import android.graphics.drawable.Drawable
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat

/** One item in the radial menu: a mode button, or the undo action. */
data class RadialMenuItem(
    val label: String,
    val color: Int,
    val iconRes: Int? = null,
)

/**
 * Draws the long-press radial menu as *radially rotated pills*: the floating ball
 * is the fan's center, each pill's long axis points outward along its radius (so
 * the pills fan out like spokes), and the text is flipped to stay readable. This
 * mirrors the classic voice-assistant fan menu. Pills have an accent glow, a light
 * border and a top sheen, and the fan blooms outward with a short staggered
 * scale/fade on open. The center is left transparent for the ball.
 *
 * Purely visual: the window is FLAG_NOT_TOUCHABLE, so the long-press gesture stays
 * with the ball window and [RadialMenuGeometry] does the proximity hit-testing.
 * Angles are y-down degrees (0 right, 90 down, 270 up).
 */
class RadialMenuView(context: Context) : View(context) {

    private var items: List<RadialMenuItem> = emptyList()
    private var iconDrawables: List<Drawable?> = emptyList()
    private var radius = 0f
    private var capsuleWidth = 0f
    private var capsuleHeight = 0f
    private var centerAngle = -90f
    private var sweep = RadialMenuGeometry.FAN_SWEEP_DEG.toFloat()
    // Ball center in this view's local coordinates (the fan is drawn around it).
    private var originX = 0f
    private var originY = 0f

    var highlightedIndex: Int = -1
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private var reveal = 0f
    private var revealAnimator: ValueAnimator? = null

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val sheenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
    }
    private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val capsuleRect = RectF()

    fun configure(
        items: List<RadialMenuItem>,
        radiusPx: Float,
        capsuleWidthPx: Float,
        capsuleHeightPx: Float,
        centerAngleDeg: Float,
        sweepDeg: Float,
        originXPx: Float,
        originYPx: Float,
    ) {
        this.items = items
        this.iconDrawables = items.map { item ->
            item.iconRes?.let { res -> ContextCompat.getDrawable(context, res)?.mutate() }
        }
        this.radius = radiusPx
        this.capsuleWidth = capsuleWidthPx
        this.capsuleHeight = capsuleHeightPx
        this.centerAngle = centerAngleDeg
        this.sweep = sweepDeg
        this.originX = originXPx
        this.originY = originYPx
        this.highlightedIndex = -1
        startReveal()
    }

    private fun startReveal() {
        revealAnimator?.cancel()
        reveal = 0f
        revealAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 260L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                reveal = it.animatedFraction
                invalidate()
            }
            start()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (radius <= 0f || items.isEmpty()) return
        val cx = originX
        val cy = originY
        val angles = RadialMenuGeometry.itemAngles(centerAngle.toDouble(), sweep.toDouble(), items.size)

        drawHubAura(canvas, cx, cy)

        for (i in items.indices) {
            val fullX = cx + RadialMenuGeometry.itemCenterX(angles[i], radius.toDouble()).toFloat()
            val fullY = cy + RadialMenuGeometry.itemCenterY(angles[i], radius.toDouble()).toFloat()
            val local = ((reveal - i * STAGGER) / (1f - STAGGER * (items.size - 1).coerceAtLeast(1)))
                .coerceIn(0f, 1f)
            val eased = easeOutCubic(local)
            if (eased <= 0f) continue
            // Bloom outward from the ball while scaling/fading in.
            val radialFactor = 0.62f + 0.38f * eased
            val ax = cx + (fullX - cx) * radialFactor
            val ay = cy + (fullY - cy) * radialFactor
            val highlighted = i == highlightedIndex
            val scale = (0.72f + 0.28f * eased) * if (highlighted) 1.06f else 1f
            drawCapsule(
                canvas, ax, ay, angles[i], items[i], iconDrawables.getOrNull(i),
                scale, eased, highlighted,
            )
        }
    }

    private fun drawHubAura(canvas: Canvas, cx: Float, cy: Float) {
        val glowRadius = capsuleHeight * 2.4f
        val alpha = (18 * reveal).toInt()
        if (alpha <= 0) return
        hubPaint.shader = RadialGradient(
            cx, cy, glowRadius,
            intArrayOf(withAlpha(Color.WHITE, (alpha * 1.4f).toInt()), withAlpha(Color.WHITE, 0)),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(cx, cy, glowRadius, hubPaint)
    }

    private fun drawCapsule(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        angleDeg: Double,
        item: RadialMenuItem,
        icon: Drawable?,
        scale: Float,
        ease: Float,
        highlighted: Boolean,
    ) {
        val w = capsuleWidth * scale
        val h = capsuleHeight * scale
        val fade = ease.coerceIn(0f, 1f)

        // Accent glow behind the capsule (a circle, so rotation does not matter).
        val glowAlpha = ((if (highlighted) 150f else 70f) * fade).toInt()
        if (glowAlpha > 0) {
            glowPaint.shader = RadialGradient(
                cx, cy, h * 1.55f,
                intArrayOf(withAlpha(item.color, glowAlpha), withAlpha(item.color, 0)),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP,
            )
            canvas.drawCircle(cx, cy, h * 1.55f, glowPaint)
        }

        // Rotate the pill so its long axis is radial; flip so text stays readable.
        canvas.save()
        canvas.translate(cx, cy)
        canvas.rotate(readableAngle(angleDeg.toFloat()))

        val left = -w / 2f
        val top = -h / 2f
        val right = w / 2f
        val bottom = h / 2f
        val corner = h / 2f
        capsuleRect.set(left, top, right, bottom)

        val baseTop = if (highlighted) 0xFF39424F.toInt() else 0xFF252C39.toInt()
        val baseBottom = if (highlighted) 0xFF202632.toInt() else 0xFF171B24.toInt()
        basePaint.shader = LinearGradient(0f, top, 0f, bottom, baseTop, baseBottom, Shader.TileMode.CLAMP)
        basePaint.alpha = (246f * fade).toInt()
        canvas.drawRoundRect(capsuleRect, corner, corner, basePaint)

        borderPaint.color = Color.WHITE
        borderPaint.alpha = ((if (highlighted) 90f else 34f) * fade).toInt()
        borderPaint.strokeWidth = maxOf(1f, h * 0.030f)
        canvas.drawRoundRect(capsuleRect, corner, corner, borderPaint)

        sheenPaint.color = Color.WHITE
        sheenPaint.alpha = ((if (highlighted) 40f else 16f) * fade).toInt()
        sheenPaint.strokeWidth = maxOf(1f, h * 0.024f)
        val inset = corner * 0.55f
        canvas.drawLine(left + inset, top + h * 0.24f, right - inset, top + h * 0.24f, sheenPaint)

        // Icon + label centered along the pill's long axis.
        val textSize = h * 0.40f
        textPaint.textSize = textSize
        val labelWidth = textPaint.measureText(item.label)
        val iconSize = h * 0.50f
        val gap = if (icon != null) h * 0.16f else 0f
        val groupWidth = (if (icon != null) iconSize + gap else 0f) + labelWidth
        val groupLeft = -groupWidth / 2f
        val textColor = if (highlighted) Color.WHITE else 0xFFEDF0F7.toInt()

        if (icon != null) {
            DrawableCompat.setTint(icon, if (highlighted) Color.WHITE else lighten(item.color, 0.55f))
            val iconLeft = groupLeft.toInt()
            val iconTop = (-iconSize / 2f).toInt()
            icon.setBounds(iconLeft, iconTop, iconLeft + iconSize.toInt(), iconTop + iconSize.toInt())
            icon.alpha = (255 * fade).toInt()
            icon.draw(canvas)
        }

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.color = textColor
        textPaint.alpha = (255 * fade).toInt()
        val baseline = -(textPaint.ascent() + textPaint.descent()) / 2f
        canvas.drawText(item.label, groupLeft + (if (icon != null) iconSize + gap else 0f), baseline, textPaint)

        canvas.restore()
    }

    /** Rotate a radial pill back into (-90, 90] so its text is never upside down. */
    private fun readableAngle(deg: Float): Float {
        var a = deg % 360f
        if (a <= -180f) a += 360f
        if (a > 180f) a -= 360f
        if (a > 90f) a -= 180f
        if (a <= -90f) a += 180f
        return a
    }

    private fun easeOutCubic(x: Float): Float {
        val p = x.coerceIn(0f, 1f)
        return 1f - (1f - p) * (1f - p) * (1f - p)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    private fun lighten(color: Int, amount: Float): Int {
        val a = amount.coerceIn(0f, 1f)
        fun ch(c: Int) = (c + (255 - c) * a).toInt().coerceIn(0, 255)
        return Color.argb(255, ch(Color.red(color)), ch(Color.green(color)), ch(Color.blue(color)))
    }

    override fun onDetachedFromWindow() {
        revealAnimator?.cancel()
        super.onDetachedFromWindow()
    }

    private companion object {
        private const val STAGGER = 0.10f
    }
}
