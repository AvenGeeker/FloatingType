package com.avengeeker.floatingtype.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.pm.ApplicationInfo
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.avengeeker.floatingtype.core.ServiceLocator
import kotlin.math.min

/**
 * Always-on backbone. The ball follows the *input state*, not the event type:
 *
 *  - keyboard on screen + editable field focused -> show
 *  - keyboard collapsed (or focus moved away)    -> hide
 *  - voice session running                       -> always keep the ball
 *
 * Focus/click events evaluate immediately; text/window/IME events re-evaluate
 * through a coalescing throttle, because they are exactly the events that fire
 * when the keyboard opens, when the user types, and when text is injected.
 * Hiding is delayed by a short grace period so window transitions cannot make
 * the ball flicker away while the user is still typing.
 *
 * Detection: walk getWindows(), find the focused application window and the
 * IME window (type INPUT_METHOD, height > 0). The IME window is only present
 * while the keyboard is shown, which makes it a reliable "user is inputting"
 * signal; its top edge is used to place the ball above the keyboard.
 */
class FloatingAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lastEvaluateAt = 0L
    private var pendingEvaluateAt = 0L
    private var unavailableSince = -1L
    private var lastImeTop = -1
    private var inputFocused = false
    private var focusedPackageName: String? = null
    private var cachedInputNode: AccessibilityNodeInfo? = null
    private var lastTreeSearchAt = 0L

    private val evaluateRunnable = Runnable { evaluateAndApply(forceTreeSearch = false) }

    private val settingsListener: () -> Unit = { handler.post { refresh() } }

    private val traceEnabled: Boolean by lazy {
        (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        isConnected = true
        instance = this
        ServiceLocator.pipeline.onServiceConnected(this)
        ServiceLocator.settings.addChangeListener(settingsListener)
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        when (FocusEventPolicy.actionFor(event.eventType)) {
            FocusEventPolicy.Action.EVALUATE -> refresh()
            FocusEventPolicy.Action.RECHECK -> {
                // Our own UI and the overlay itself emit content changes;
                // reacting to them would feed back into our own layout updates.
                if (event.packageName?.toString() == packageName) return
                requestEvaluation(
                    BallVisibilityPolicy.recheckDelayMs(
                        lastEvaluateAt,
                        SystemClock.elapsedRealtime(),
                        RECHECK_MIN_INTERVAL_MS,
                    )
                )
            }
            FocusEventPolicy.Action.IGNORE -> Unit
        }
    }

    /** Re-evaluate the current input state right now. */
    fun refresh() {
        handler.removeCallbacks(evaluateRunnable)
        pendingEvaluateAt = 0L
        evaluateAndApply(forceTreeSearch = true)
    }

    /**
     * Whether an editable field is focused with the keyboard up. Re-checks
     * once when the cached answer is negative, so a stale flag from a window
     * transition cannot block a recording the user just started.
     */
    fun hasInputFocus(): Boolean {
        if (!inputFocused) refresh()
        return inputFocused
    }

    /** Package name of the app that currently owns the focused input, if known. */
    fun focusedPackageName(): String? = focusedPackageName

    fun injectText(text: String): Boolean = TextInjector.inject(this, text, cachedInputNode)

    /** Schedule one coalesced evaluation, never later than an already pending one. */
    private fun requestEvaluation(delayMs: Long) {
        val target = SystemClock.elapsedRealtime() + delayMs
        if (pendingEvaluateAt in 1..target) return
        handler.removeCallbacks(evaluateRunnable)
        pendingEvaluateAt = target
        handler.postDelayed(evaluateRunnable, delayMs)
    }

    private fun evaluateAndApply(forceTreeSearch: Boolean) {
        handler.removeCallbacks(evaluateRunnable)
        pendingEvaluateAt = 0L

        val now = SystemClock.elapsedRealtime()
        lastEvaluateAt = now
        // A tree search costs one IPC per node; only pay for it on a real
        // focus/click event, or at a low rate while re-checking.
        val allowTreeSearch = forceTreeSearch ||
            now - lastTreeSearchAt >= TREE_SEARCH_MIN_INTERVAL_MS
        if (allowTreeSearch) lastTreeSearchAt = now
        val focus = evaluateInputFocus(allowTreeSearch)
        inputFocused = focus.hasInput
        lastImeTop = focus.imeTop
        focusedPackageName = focus.packageName

        val overlay = ServiceLocator.overlay
        val shouldShow = BallVisibilityPolicy.shouldShow(
            imeVisible = focus.imeVisible,
            hasEditableFocus = focus.hasInput,
            sessionBusy = ServiceLocator.pipeline.isBusy(),
        )
        if (shouldShow) {
            unavailableSince = -1L
        } else if (unavailableSince < 0L) {
            unavailableSince = now
        }
        val unavailableFor = if (unavailableSince < 0L) 0L else now - unavailableSince

        val decision = BallVisibilityPolicy.decide(
            shouldShow = shouldShow,
            currentlyVisible = overlay.isVisible,
            unavailableForMs = unavailableFor,
            graceMs = HIDE_GRACE_MS,
        )
        when (decision) {
            BallVisibilityPolicy.Decision.SHOW -> {
                val wasVisible = overlay.isVisible
                applyVisibility(true)
                // The keyboard may still be animating into place; re-check once
                // after the ball really appeared so it settles above the IME
                // even if no further accessibility event arrives.
                if (!wasVisible && overlay.isVisible) requestEvaluation(IME_SETTLE_MS)
            }
            BallVisibilityPolicy.Decision.RETRY ->
                requestEvaluation((HIDE_GRACE_MS - unavailableFor).coerceAtLeast(RECHECK_MIN_INTERVAL_MS))
            BallVisibilityPolicy.Decision.HIDE -> applyVisibility(false)
        }
        trace(
            "ime=${focus.imeVisible}@${focus.imeTop} editable=${focus.hasInput} " +
                "busy=${ServiceLocator.pipeline.isBusy()} -> $decision (visible=${overlay.isVisible})"
        )
    }

    private fun applyVisibility(show: Boolean) {
        val allowed = show &&
            ServiceLocator.settings.ballEnabled &&
            Settings.canDrawOverlays(this)
        if (allowed) ServiceLocator.overlay.show(lastImeTop) else ServiceLocator.overlay.hide()
    }

    private fun evaluateInputFocus(allowTreeSearch: Boolean): FocusResult {
        var imeTop = -1
        var focusedAppWindow: AccessibilityWindowInfo? = null
        var topAppWindow: AccessibilityWindowInfo? = null
        try {
            for (window in windows) {
                when (window.type) {
                    AccessibilityWindowInfo.TYPE_APPLICATION -> {
                        if (window.isFocused) {
                            focusedAppWindow = window
                        } else {
                            val top = topAppWindow
                            if (top == null || window.layer > top.layer) topAppWindow = window
                        }
                    }
                    AccessibilityWindowInfo.TYPE_INPUT_METHOD -> {
                        val bounds = Rect()
                        window.getBoundsInScreen(bounds)
                        if (bounds.height() > 0) {
                            imeTop = if (imeTop < 0) bounds.top else min(imeTop, bounds.top)
                        }
                    }
                    else -> Unit
                }
            }
        } catch (_: Exception) {
            // Window inspection is best-effort; fall through to focus lookup.
        }

        if (imeTop < 0) {
            // No keyboard on screen -> the user is not in a text-input state.
            // Skip the node lookup entirely: this is the common case while
            // browsing, and it keeps noisy content-change events cheap.
            cachedInputNode = null
            return FocusResult(hasInput = false, imeVisible = false, imeTop = -1, packageName = null)
        }

        val direct = try {
            findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } catch (_: Exception) {
            null
        }
        // During IME/app window transitions the app window can briefly report
        // "not focused"; fall back to the active window / topmost other window.
        val roots = if (focusedAppWindow != null) {
            listOf(rootOf(focusedAppWindow), rootOf(topAppWindow))
        } else {
            listOf(activeRoot(), rootOf(topAppWindow))
        }
        val node = InputFocusFinder.find(direct, roots, cachedInputNode, allowTreeSearch)
        cachedInputNode = node

        val hasInput = node != null
        val packageName = node?.packageName?.toString()
            ?: focusedAppWindow?.root?.packageName?.toString()
        return FocusResult(hasInput, imeVisible = true, imeTop = imeTop, packageName = packageName)
    }

    private fun rootOf(window: AccessibilityWindowInfo?): AccessibilityNodeInfo? {
        if (window == null) return null
        return try {
            window.root
        } catch (_: Exception) {
            null
        }
    }

    private fun activeRoot(): AccessibilityNodeInfo? = try {
        rootInActiveWindow
    } catch (_: Exception) {
        null
    }

    private fun trace(message: String) {
        if (traceEnabled) Log.d(TAG, message)
    }

    override fun onInterrupt() {
        // No interrupt feedback required for this service.
    }

    override fun onDestroy() {
        isConnected = false
        instance = null
        ServiceLocator.settings.removeChangeListener(settingsListener)
        handler.removeCallbacksAndMessages(null)
        ServiceLocator.overlay.hide()
        ServiceLocator.pipeline.onServiceDisconnected()
        super.onDestroy()
    }

    private data class FocusResult(
        val hasInput: Boolean,
        val imeVisible: Boolean,
        val imeTop: Int,
        val packageName: String?,
    )

    companion object {
        /** At most one focus evaluation per this interval while events burst. */
        private const val RECHECK_MIN_INTERVAL_MS = 80L

        /** Keep the ball this long after the input state ends before hiding. */
        private const val HIDE_GRACE_MS = 300L

        /** One delayed re-check after showing, to settle above the keyboard. */
        private const val IME_SETTLE_MS = 320L

        /** Minimum gap between two full node-tree searches. */
        private const val TREE_SEARCH_MIN_INTERVAL_MS = 400L

        private const val TAG = "FloatingTypeBall"

        @Volatile
        var isConnected: Boolean = false
            private set

        @Volatile
        var instance: FloatingAccessibilityService? = null
            private set
    }
}
