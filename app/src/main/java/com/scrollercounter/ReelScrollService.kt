package com.scrollercounter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Counts one reel per swipe in a short-video feed.
 *
 * A swipe fires a burst of TYPE_VIEW_SCROLLED events, so events are debounced.
 * To avoid counting ordinary feed scrolling, a count only happens while the
 * screen shows a reels/shorts viewer (detected via view-id hints, which may
 * need updating when the apps change their layouts).
 */
class ReelScrollService : AccessibilityService() {

    private lateinit var store: CounterStore
    private var lastCountAt = 0L
    private var lastOtherLogAt = 0L

    override fun onServiceConnected() {
        store = CounterStore(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        val hints = VIEWER_HINTS[pkg] ?: return
        val now = SystemClock.elapsedRealtime()

        if (event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            // Content-change events are noisy; dump the visible screen at most every 700 ms.
            if (now - lastOtherLogAt >= 700) {
                lastOtherLogAt = now
                DebugLog.add(
                    "other ${AccessibilityEvent.eventTypeToString(event.eventType)} " +
                        "${event.className} tree=[${dumpVisible(rootInActiveWindow)}]"
                )
            }
            return
        }

        val root = rootInActiveWindow
        val ids = LinkedHashSet<String>()
        val onViewer = if (root != null) showsReelViewer(root, hints, ids) else false
        val horizontalOnly = event.scrollDeltaX != 0 && event.scrollDeltaY == 0
        val debounced = now - lastCountAt < DEBOUNCE_MS
        val counted = onViewer && !horizontalOnly && !debounced

        DebugLog.add(
            "SCROLL ${pkg.substringAfterLast('.')} cls=${event.className} dX=${event.scrollDeltaX} " +
                "dY=${event.scrollDeltaY} idx=${event.fromIndex}->${event.toIndex} " +
                "viewer=$onViewer counted=$counted ids=${ids.joinToString(",")}"
        )

        if (!counted) return
        lastCountAt = now
        store.increment(pkg)
        sendBroadcast(Intent(ACTION_COUNT_CHANGED).setPackage(packageName))
    }

    /** Debug only: compact list of visible nodes as id|class|text. Stays in memory until copied. */
    private fun dumpVisible(root: AccessibilityNodeInfo?): String {
        if (root == null) return "no root"
        val out = ArrayList<String>()
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 14 || out.size >= 40) return
            if (node.isVisibleToUser) {
                val id = node.viewIdResourceName?.substringAfter(":id/").orEmpty()
                val text = (node.text ?: node.contentDescription)?.toString().orEmpty().take(25)
                if (id.isNotEmpty() || text.isNotEmpty()) {
                    out.add("$id|${node.className?.substringAfterLast('.')}|$text")
                }
            }
            for (i in 0 until node.childCount) walk(node.getChild(i) ?: continue, depth + 1)
        }
        walk(root, 0)
        return out.joinToString("; ")
    }

    private fun showsReelViewer(
        root: AccessibilityNodeInfo,
        hints: List<String>,
        ids: MutableSet<String>,
    ): Boolean {
        var found = false
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 14 || ids.size >= 60) return
            node.viewIdResourceName?.let { id ->
                ids.add(id.substringAfter(":id/"))
                if (hints.any { id.contains(it, ignoreCase = true) }) found = true
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                walk(child, depth + 1)
            }
        }
        walk(root, 0)
        return found
    }

    override fun onInterrupt() = Unit

    companion object {
        const val ACTION_COUNT_CHANGED = "com.scrollercounter.COUNT_CHANGED"
        private const val DEBOUNCE_MS = 700L

        val APP_NAMES = mapOf(
            "com.instagram.android" to "Instagram Reels",
            "com.google.android.youtube" to "YouTube Shorts",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.facebook.katana" to "Facebook Reels",
        )

        private val VIEWER_HINTS = mapOf(
            "com.instagram.android" to listOf("clips_viewer", "clips_video"),
            "com.google.android.youtube" to listOf("reel_recycler", "reel_player", "shorts"),
            "com.zhiliaoapp.musically" to listOf("viewpager", "feed"),
            "com.facebook.katana" to listOf("reels", "video_home_viewer"),
        )
    }
}
