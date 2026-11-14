package com.avengeeker.floatingtype.accessibility

/**
 * Pure show/hide decision for the floating ball, kept free of Android
 * dependencies so it can be unit tested.
 *
 * Rule: the ball lives exactly as long as the user is in a text-input state,
 * i.e. the soft keyboard is on screen AND an editable field holds input focus.
 * When the keyboard collapses the ball goes away, even if the EditText still
 * reports focus (Android keeps input focus after BACK dismisses the IME).
 *
 * A running voice session overrides the rule so the user can always stop
 * recording or watch progress.
 */
object BallVisibilityPolicy {

    enum class Decision { SHOW, RETRY, HIDE }

    /** Whether the ball belongs on screen for the observed state. */
    fun shouldShow(imeVisible: Boolean, hasEditableFocus: Boolean, sessionBusy: Boolean): Boolean =
        sessionBusy || (imeVisible && hasEditableFocus)

    /**
     * What to do after an evaluation.
     *
     * [unavailableForMs] is how long the show condition has been false. While
     * the ball is on screen we keep it for a short grace period and re-check,
     * so IME/app window transitions do not make it flicker. When it is already
     * hidden there is nothing to protect, so hide immediately.
     */
    fun decide(
        shouldShow: Boolean,
        currentlyVisible: Boolean,
        unavailableForMs: Long,
        graceMs: Long,
    ): Decision = when {
        shouldShow -> Decision.SHOW
        !currentlyVisible -> Decision.HIDE
        unavailableForMs < graceMs -> Decision.RETRY
        else -> Decision.HIDE
    }

    /**
     * Coalescing delay for a re-check. TYPE_WINDOW_CONTENT_CHANGED arrives in
     * bursts (typing, scrolling, keyboard animation); at most one evaluation
     * per [minIntervalMs] is enough. Returns 0 when an evaluation is due now.
     */
    fun recheckDelayMs(lastEvaluateAtMs: Long, nowMs: Long, minIntervalMs: Long): Long {
        if (lastEvaluateAtMs <= 0L || minIntervalMs <= 0L) return 0L
        val elapsed = nowMs - lastEvaluateAtMs
        return if (elapsed >= minIntervalMs) 0L else minIntervalMs - elapsed
    }
}
