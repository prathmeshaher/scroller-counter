package com.scrollercounter

import android.content.Context
import java.time.LocalDate

/** Persists reel counts locally: per day and per app. */
class CounterStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences("counts", Context.MODE_PRIVATE)

    fun increment(pkg: String) {
        val day = LocalDate.now().toString()
        prefs.edit()
            .putInt("total", total() + 1)
            .putInt("day_$day", forDay(day) + 1)
            .putInt("app_$pkg", forApp(pkg) + 1)
            .apply()
    }

    fun total() = prefs.getInt("total", 0)
    fun today() = forDay(LocalDate.now().toString())
    fun forApp(pkg: String) = prefs.getInt("app_$pkg", 0)
    private fun forDay(day: String) = prefs.getInt("day_$day", 0)

    fun reset() = prefs.edit().clear().apply()
}
