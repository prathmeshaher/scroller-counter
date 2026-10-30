package com.scrollercounter

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Counts one reel per swipe in a short-video feed.
 *
 * YouTube is handled separately (see handleYouTube). For the others, a swipe fires a burst of TYPE_VIEW_SCROLLED events, so events are debounced.
 * To avoid counting ordinary feed scrolling, a count only happens while the
 * screen shows a reels/shorts viewer (detected via view-id hints, which may
 * need updating when the apps change their layouts).
 */
class ReelScrollService : AccessibilityService() {

    private lateinit var store: CounterStore
    private var lastCountAt = 0L
    private var lastOtherLogAt = 0L
    private var lastYtCheckAt = 0L
    private var ytInShorts = false
    private val ytSeen = LinkedHashSet<Int>()

    override fun onServiceConnected() {
        store = CounterStore(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val pkg = event.packageName?.toString() ?: return
        if (pkg == YOUTUBE) {
            handleYouTube(event)
            return
        }
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

    /**
     * YouTube Shorts sends no scroll events, only content-change events. Instead, look at the
     * texts inside the Shorts pager (channel handle, caption, ad name) and count a reel when
     * texts appear that were not seen before. Only text hashes are kept, in memory.
     */
    private fun handleYouTube(event: AccessibilityEvent) {
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_VIEW_SCROLLED
        ) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastYtCheckAt < YT_CHECK_MS) return
        lastYtCheckAt = now

        val root = rootInActiveWindow ?: return
        val pager = root.findAccessibilityNodeInfosByViewId("$YOUTUBE:id/reel_recycler")
            .firstOrNull()
        if (pager == null) {
            ytInShorts = false
            return
        }

        val tokens = reelTokens(pager)
        if (tokens.isEmpty()) return

        if (!ytInShorts) {
            // Entering Shorts: the first reel is the starting point, not a swipe.
            ytInShorts = true
            ytSeen.addAll(tokens)
            DebugLog.add("YT entered shorts, baseline tokens=${tokens.size}")
            return
        }

        val fresh = tokens.filter { it !in ytSeen }
        if (fresh.isEmpty()) return
        ytSeen.addAll(fresh)
        while (ytSeen.size > MAX_SEEN) ytSeen.remove(ytSeen.first())

        val counted = now - lastCountAt >= COUNT_DEBOUNCE_MS
        DebugLog.add("YT new reel text fresh=${fresh.size} of ${tokens.size} counted=$counted")
        if (!counted) return
        lastCountAt = now
        store.increment(YOUTUBE)
        sendBroadcast(Intent(ACTION_COUNT_CHANGED).setPackage(packageName))
    }

    /** Hashes of identifying texts (not buttons, counters or generic labels) under [pager]. */
    private fun reelTokens(pager: AccessibilityNodeInfo): List<Int> {
        val out = ArrayList<Int>()
        var visited = 0
        fun walk(node: AccessibilityNodeInfo, depth: Int) {
            if (depth > 20 || ++visited > 300) return
            if (node.isVisibleToUser) {
                val cls = node.className?.toString().orEmpty()
                if (cls.endsWith("ViewGroup") || cls.endsWith("ImageView")) {
                    val text = (node.text ?: node.contentDescription)?.toString()?.trim().orEmpty()
                    if (text.length >= 3 && text.lowercase() !in GENERIC_TEXTS &&
                        !text.startsWith("Subscribe", ignoreCase = true) &&
                        !NUMERIC_TEXT.matches(text)
                    ) out.add(text.hashCode())
                }
            }
            for (i in 0 until node.childCount) walk(node.getChild(i) ?: continue, depth + 1)
        }
        walk(pager, 0)
        return out
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
                    out.add("$id|${node.className?.toString()?.substringAfterLast('.')}|$text")
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
        private const val COUNT_DEBOUNCE_MS = 600L
        private const val YT_CHECK_MS = 250L
        private const val MAX_SEEN = 400
        private const val YOUTUBE = "com.google.android.youtube"

        private val GENERIC_TEXTS = setOf(
            "ad", "sponsored", "auto-dubbed", "action menu", "shop now", "install", "more actions",
        )
        private val NUMERIC_TEXT = Regex("^[\\d.,/]+(\\s\\w+)?$")

        val APP_NAMES = mapOf(
            "com.instagram.android" to "Instagram Reels",
            "com.google.android.youtube" to "YouTube Shorts",
            "com.zhiliaoapp.musically" to "TikTok",
            "com.facebook.katana" to "Facebook Reels",
        )

        private val VIEWER_HINTS = mapOf(
            "com.instagram.android" to listOf("clips_viewer", "clips_video"),
            "com.zhiliaoapp.musically" to listOf("viewpager", "feed"),
            "com.facebook.katana" to listOf("reels", "video_home_viewer"),
        )
    }
}
