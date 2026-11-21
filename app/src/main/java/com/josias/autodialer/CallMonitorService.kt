package com.josias.autodialer

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log

/**
 * Android binds this service only while the app is the default phone app.
 * It gets real call states: an outgoing call goes DIALING -> ACTIVE, and
 * ACTIVE means the other side picked up (a person or their voicemail).
 */
class CallMonitorService : InCallService() {

    private val outgoing = mutableSetOf<Call>()

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            Log.d(TAG, "state -> ${stateName(state)}")
            if (state == Call.STATE_ACTIVE && call in outgoing && CallManager.hangUpOnAnswer) {
                Log.i(TAG, "Outgoing call answered, hanging up")
                call.disconnect()
            }
            CallManager.notifyChanged()
        }
    }

    override fun onCallAdded(call: Call) {
        // Incoming calls arrive RINGING; everything else here is one we placed.
        if (call.state != Call.STATE_RINGING) outgoing += call
        call.registerCallback(callback)
        CallManager.calls += call
        CallManager.notifyChanged()

        // As the default phone app we must show the in-call screen ourselves.
        startActivity(
            Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    override fun onCallRemoved(call: Call) {
        call.unregisterCallback(callback)
        outgoing -= call
        CallManager.calls -= call
        CallManager.notifyChanged()
    }

    companion object {
        private const val TAG = "CallMonitor"

        fun stateName(state: Int) = when (state) {
            Call.STATE_NEW -> "New"
            Call.STATE_DIALING -> "Dialing"
            Call.STATE_RINGING -> "Incoming"
            Call.STATE_HOLDING -> "On hold"
            Call.STATE_ACTIVE -> "Answered"
            Call.STATE_DISCONNECTED -> "Ended"
            Call.STATE_CONNECTING -> "Connecting"
            Call.STATE_DISCONNECTING -> "Ending"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "Select SIM"
            else -> "State $state"
        }
    }
}
