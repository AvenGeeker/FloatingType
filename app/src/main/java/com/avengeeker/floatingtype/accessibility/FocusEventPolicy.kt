package com.avengeeker.floatingtype.accessibility

import android.view.accessibility.AccessibilityEvent

/**
 * Pure event-type policy.
 *
 * Visibility is decided by the *current state* (keyboard on screen + editable
 * field focused), never by the event type alone. The previous policy mapped
 * every text/window event to HIDE, which killed the ball right after a tap:
 * opening the IME emits TYPE_WINDOWS_CHANGED / TYPE_WINDOW_STATE_CHANGED and a
 * burst of TYPE_WINDOW_CONTENT_CHANGED while the field is still focused, and
 * typing emits TYPE_VIEW_TEXT_CHANGED / TYPE_VIEW_TEXT_SELECTION_CHANGED.
 *
 *  - VIEW_CLICKED / VIEW_FOCUSED -> evaluate now (user picked a field)
 *  - text/window/IME events      -> re-evaluate, throttled (layout may have
 *                                   changed: keyboard opened/closed, text was
 *                                   injected, app window switched)
 *  - everything else             -> ignore
 */
object FocusEventPolicy {

    enum class Action { EVALUATE, RECHECK, IGNORE }

    fun actionFor(eventType: Int): Action = when (eventType) {
        AccessibilityEvent.TYPE_VIEW_CLICKED,
        AccessibilityEvent.TYPE_VIEW_FOCUSED -> Action.EVALUATE

        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> Action.RECHECK

        else -> Action.IGNORE
    }
}
