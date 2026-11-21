package com.josias.autodialer

import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.VideoProfile
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Minimal call screen. Required because the default phone app has to provide
 * the in-call UI, and it also lets you answer normal incoming calls.
 */
class InCallActivity : AppCompatActivity() {

    private lateinit var info: TextView
    private lateinit var answer: Button
    private lateinit var stopAll: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        val pad = (24 * resources.displayMetrics.density).toInt()
        info = TextView(this).apply { textSize = 24f; gravity = Gravity.CENTER }
        answer = Button(this).apply {
            text = "Answer"
            setOnClickListener {
                CallManager.calls.firstOrNull { it.state == Call.STATE_RINGING }
                    ?.answer(VideoProfile.STATE_AUDIO_ONLY)
            }
        }
        val hangUp = Button(this).apply {
            text = "Hang up"
            setOnClickListener { CallManager.endAll() }
        }
        stopAll = Button(this).apply {
            text = "Stop auto-dialing"
            setOnClickListener {
                DialerService.stop(this@InCallActivity)
                finish()
            }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(pad, pad, pad, pad)
            addView(info)
            addView(answer)
            addView(hangUp)
            addView(stopAll)
        })
    }

    override fun onStart() {
        super.onStart()
        CallManager.onChanged = ::refresh
        refresh()
    }

    override fun onStop() {
        super.onStop()
        CallManager.onChanged = null
    }

    private fun refresh() {
        val call = CallManager.calls.lastOrNull()
        if (call == null) { finish(); return }
        val number = call.details.handle?.schemeSpecificPart ?: "Unknown"
        info.text = "$number\n${CallMonitorService.stateName(call.state)}"
        answer.visibility =
            if (CallManager.calls.any { it.state == Call.STATE_RINGING }) View.VISIBLE else View.GONE
        stopAll.visibility = if (CallManager.autoDialing) View.VISIBLE else View.GONE
    }
}
