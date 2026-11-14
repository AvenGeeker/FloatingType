package com.avengeeker.floatingtype.accessibility

import android.view.accessibility.AccessibilityNodeInfo

/**
 * Locates the editable node that really holds input focus.
 *
 * Why not just AccessibilityService.findFocus(FOCUS_INPUT)? Virtual view
 * hierarchies break it: Jetpack Compose answers with a non-focused
 * `android.view.View` stub, so every Compose text field (including this app's
 * own settings and test fields) used to be reported as "no input" and the ball
 * never appeared. When the fast path yields nothing editable we search the
 * window tree for the node that reports input focus, and cache it so later
 * evaluations only need a cheap refresh().
 *
 * Traversal is bounded: every getChild() is an IPC round trip into the app.
 */
object InputFocusFinder {

    /** Hard cap on visited nodes for one tree search. */
    const val MAX_VISITED_NODES = 400

    /**
     * @param direct  result of AccessibilityService.findFocus(FOCUS_INPUT)
     * @param roots   window roots to inspect, most likely first
     * @param cached  node found by a previous evaluation, if any
     */
    fun find(
        direct: AccessibilityNodeInfo?,
        roots: List<AccessibilityNodeInfo?>,
        cached: AccessibilityNodeInfo?,
        allowTreeSearch: Boolean = true,
    ): AccessibilityNodeInfo? {
        if (cached != null && refreshesToFocusedEditable(cached)) return cached
        if (direct != null && TextInjector.isEditableNode(direct)) return direct
        for (root in roots) {
            if (root == null) continue
            val focused = try {
                root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            } catch (_: Exception) {
                null
            }
            if (focused != null && TextInjector.isEditableNode(focused)) return focused
            if (allowTreeSearch) {
                val found = searchFocusedEditable(root)
                if (found != null) return found
            }
        }
        return null
    }

    /** True when the cached node is still the focused editable field. */
    fun refreshesToFocusedEditable(node: AccessibilityNodeInfo): Boolean = try {
        node.refresh() && node.isFocused && TextInjector.isEditableNode(node)
    } catch (_: Exception) {
        false
    }

    /** Breadth-first search for the node that reports input focus and is editable. */
    fun searchFocusedEditable(root: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var visited = 0
        while (queue.isNotEmpty() && visited < MAX_VISITED_NODES) {
            val node = queue.removeFirst()
            visited++
            // isFocused/isEditable/className are cached on the node: no IPC.
            if (node.isFocused && TextInjector.isEditableNode(node)) return node
            for (i in 0 until node.childCount) {
                val child = try {
                    node.getChild(i)
                } catch (_: Exception) {
                    null
                } ?: continue
                queue.add(child)
            }
        }
        return null
    }
}
