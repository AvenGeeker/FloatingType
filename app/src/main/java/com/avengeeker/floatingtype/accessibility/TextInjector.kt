package com.avengeeker.floatingtype.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Writes transcribed text into the currently focused input field via the
 * accessibility service.
 * Primary path: ACTION_SET_TEXT spliced at the current selection.
 * Fallback: clipboard + ACTION_PASTE.
 */
object TextInjector {

    private const val TAG = "FloatingType.Inject"

    data class SplicedText(val text: String, val cursor: Int)

    /**
     * Pure splice logic. Negative selection indices mean "no selection" and
     * append at the end. Indices are clamped into the existing text bounds.
     */
    fun splice(existing: CharSequence?, selectionStart: Int, selectionEnd: Int, insert: String): SplicedText {
        val base = existing?.toString() ?: ""
        if (selectionStart < 0 || selectionEnd < 0) {
            return SplicedText(base + insert, base.length + insert.length)
        }
        val start = selectionStart.coerceIn(0, base.length)
        val end = selectionEnd.coerceIn(start, base.length)
        val text = base.substring(0, start) + insert + base.substring(end)
        return SplicedText(text, start + insert.length)
    }

    fun isEditableNode(node: AccessibilityNodeInfo): Boolean {
        if (node.isPassword) return false
        if (node.isEditable) return true
        val className = node.className?.toString() ?: return false
        return EDITABLE_CLASS_HINTS.any { className.contains(it) }
    }

    /**
     * @param preferred node the accessibility service already resolved for the
     *                  current input state; refreshed before use.
     */
    fun findEditableNode(
        service: AccessibilityService,
        preferred: AccessibilityNodeInfo? = null,
    ): AccessibilityNodeInfo? {
        val direct = try {
            service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        } catch (e: Exception) {
            Log.w(TAG, "findFocus(FOCUS_INPUT) threw", e)
            null
        }
        val root = try {
            service.rootInActiveWindow
        } catch (e: Exception) {
            Log.w(TAG, "rootInActiveWindow threw", e)
            null
        }
        return InputFocusFinder.find(direct, listOf(root), preferred)
    }

    /** Returns true when the text was written into the focused field. */
    fun inject(
        service: AccessibilityService,
        text: String,
        preferred: AccessibilityNodeInfo? = null,
    ): Boolean {
        if (text.isEmpty()) {
            Log.w(TAG, "inject skipped: text is empty")
            return false
        }
        val node = findEditableNode(service, preferred)
        if (node == null) {
            Log.w(TAG, "inject failed: no editable node found (focused input lost or not editable)")
            return false
        }
        Log.d(
            TAG,
            "inject target: class=${node.className} pkg=${node.packageName} " +
                "editable=${node.isEditable} chars=${text.length}"
        )

        val spliced = splice(node.text, node.textSelectionStart, node.textSelectionEnd, text)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, spliced.text)
        }
        val setOk = try {
            node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        } catch (e: Exception) {
            Log.w(TAG, "ACTION_SET_TEXT threw; will try clipboard paste fallback", e)
            false
        }
        if (setOk) {
            val selectionArgs = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, spliced.cursor)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, spliced.cursor)
            }
            try {
                node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectionArgs)
            } catch (e: Exception) {
                // Cursor placement is best-effort; the text is already written.
                Log.w(TAG, "cursor placement failed (text already written)", e)
            }
            Log.i(TAG, "injected via ACTION_SET_TEXT: chars=${text.length}")
            return true
        }

        // Fallback for fields that do not support ACTION_SET_TEXT.
        Log.w(TAG, "ACTION_SET_TEXT returned false; trying clipboard paste fallback")
        return try {
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("FloatingType", text))
            val pasteOk = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            if (pasteOk) {
                Log.i(TAG, "injected via clipboard paste: chars=${text.length}")
            } else {
                Log.e(TAG, "clipboard paste fallback failed: ACTION_PASTE returned false")
            }
            pasteOk
        } catch (e: Exception) {
            Log.e(TAG, "clipboard paste fallback threw", e)
            false
        }
    }

    private val EDITABLE_CLASS_HINTS = listOf(
        "EditText",
        "AutoCompleteTextView",
        "ComposeText",
        "TextField",
    )
}
