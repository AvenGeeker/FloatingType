package com.avengeeker.floatingtype.accessibility

import android.view.accessibility.AccessibilityEvent
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusEventPolicyTest {

    @Test
    fun clickAndFocusEventsEvaluate() {
        assertEquals(
            FocusEventPolicy.Action.EVALUATE,
            FocusEventPolicy.actionFor(AccessibilityEvent.TYPE_VIEW_CLICKED)
        )
        assertEquals(
            FocusEventPolicy.Action.EVALUATE,
            FocusEventPolicy.actionFor(AccessibilityEvent.TYPE_VIEW_FOCUSED)
        )
    }

    @Test
    fun typingAndWindowEventsRecheckInsteadOfHiding() {
        // These fire while the field is still focused: keyboard opening,
        // typing, cursor moves and text injection. They must re-evaluate the
        // real input state, never hide the ball on their own.
        val recheckEvents = listOf(
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED,
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
        )
        for (event in recheckEvents) {
            assertEquals(
                "event $event should re-check focus",
                FocusEventPolicy.Action.RECHECK,
                FocusEventPolicy.actionFor(event)
            )
        }
    }

    @Test
    fun unrelatedEventsAreIgnored() {
        val ignored = listOf(
            AccessibilityEvent.TYPE_VIEW_SCROLLED,
            AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_HOVER_ENTER,
            AccessibilityEvent.TYPE_TOUCH_INTERACTION_START,
            0,
        )
        for (event in ignored) {
            assertEquals(
                "event $event should be ignored",
                FocusEventPolicy.Action.IGNORE,
                FocusEventPolicy.actionFor(event)
            )
        }
    }
}
