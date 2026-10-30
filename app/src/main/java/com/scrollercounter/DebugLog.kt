package com.scrollercounter

import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale

/** In-memory ring buffer of recent accessibility events, shown/copied from the app for troubleshooting. */
object DebugLog {
    private const val MAX_LINES = 150
    private val lines = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun add(line: String) {
        if (lines.size >= MAX_LINES) lines.removeFirst()
        lines.addLast("${fmt.format(Date())} $line")
    }

    @Synchronized fun dump(): String = lines.joinToString("\n")
    @Synchronized fun size() = lines.size
    @Synchronized fun clear() = lines.clear()
}
