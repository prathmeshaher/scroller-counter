package com.scrollercounter

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.provider.Settings
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Button
import android.widget.Toast
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {

    private lateinit var store: CounterStore
    private lateinit var today: TextView
    private lateinit var total: TextView
    private lateinit var breakdown: TextView
    private lateinit var status: TextView

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = CounterStore(this)
        today = findViewById(R.id.today)
        total = findViewById(R.id.total)
        breakdown = findViewById(R.id.breakdown)
        status = findViewById(R.id.status)

        findViewById<Button>(R.id.enable).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        findViewById<Button>(R.id.copyDebug).setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("debug", DebugLog.dump()))
            Toast.makeText(this, "Copied ${DebugLog.size()} lines", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.reset).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Reset all counts?")
                .setPositiveButton("Reset") { _, _ -> store.reset(); refresh() }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter(ReelScrollService.ACTION_COUNT_CHANGED)
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        refresh()
    }

    override fun onStop() {
        unregisterReceiver(receiver)
        super.onStop()
    }

    private fun refresh() {
        today.text = store.today().toString()
        total.text = "All time: ${store.total()}"
        breakdown.text = ReelScrollService.APP_NAMES.entries.joinToString("\n") { (pkg, name) ->
            "$name: ${store.forApp(pkg)}"
        }
        findViewById<TextView>(R.id.debugInfo).text = "Debug events logged: ${DebugLog.size()}"
        status.text = if (serviceEnabled()) "Tracking is ON" else
            "Tracking is OFF — enable \"Scroller Counter\" in Accessibility settings"
    }

    private fun serviceEnabled(): Boolean {
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabled.contains("$packageName/${ReelScrollService::class.java.name}")
    }
}
