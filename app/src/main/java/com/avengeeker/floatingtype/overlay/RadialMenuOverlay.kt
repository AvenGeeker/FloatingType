package com.avengeeker.floatingtype.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.WindowManager

/**
 * WindowManager host for [RadialMenuView] (discrete oval buttons surrounding the
 * ball). The window is NOT_TOUCHABLE so the long-press gesture that opened it keeps
 * flowing to the ball window underneath; this window only paints. Centered on the
 * ball, it may extend past the screen edge near corners (the system clips it)
 * without shifting the hit-test origin, which stays at the ball center.
 */
class RadialMenuOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: RadialMenuView? = null
    private var params: WindowManager.LayoutParams? = null

    var isVisible: Boolean = false
        private set

    fun show(
        windowX: Int,
        windowY: Int,
        windowSizePx: Int,
        originXPx: Float,
        originYPx: Float,
        items: List<RadialMenuItem>,
        radiusPx: Float,
        ovalWidthPx: Float,
        ovalHeightPx: Float,
        centerAngleDeg: Float,
        sweepDeg: Float,
    ) {
        val v = view ?: RadialMenuView(context).also { view = it }
        v.configure(
            items, radiusPx, ovalWidthPx, ovalHeightPx, centerAngleDeg, sweepDeg,
            originXPx, originYPx,
        )
        val p = params ?: WindowManager.LayoutParams().apply {
            type = if (Build.VERSION.SDK_INT >= 26) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            flags = (WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN)
            format = PixelFormat.TRANSLUCENT
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0
        }.also { params = it }
        p.width = windowSizePx
        p.height = windowSizePx
        p.x = windowX
        p.y = windowY
        try {
            if (!isVisible) {
                windowManager.addView(v, p)
                isVisible = true
            } else {
                windowManager.updateViewLayout(v, p)
            }
        } catch (_: Exception) {
            isVisible = false
        }
    }

    fun highlight(index: Int) {
        view?.highlightedIndex = index
    }

    fun hide() {
        val v = view
        if (isVisible && v != null) {
            try {
                windowManager.removeView(v)
            } catch (_: Exception) {
                // Already detached.
            }
        }
        isVisible = false
    }
}
