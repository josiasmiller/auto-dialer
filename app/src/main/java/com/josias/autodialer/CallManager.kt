package com.josias.autodialer

import android.telecom.Call

/**
 * Shared state between DialerService (places calls) and CallMonitorService
 * (sees call progress because we're the default phone app).
 * Everything here is touched on the main thread only.
 */
object CallManager {

    /** While true, any outgoing call that becomes ACTIVE (answered) is hung up. */
    @Volatile var hangUpOnAnswer = false

    /** True while DialerService's loop is running (shows Stop on the call screen). */
    @Volatile var autoDialing = false

    /** Calls Telecom has handed us. Empty unless we're the default phone app. */
    val calls = mutableListOf<Call>()

    /** Set by InCallActivity so its screen refreshes on state changes. */
    var onChanged: (() -> Unit)? = null

    fun notifyChanged() = onChanged?.invoke()

    /** Ends every call we know about. Returns false if we have none (not default dialer). */
    fun endAll(): Boolean {
        if (calls.isEmpty()) return false
        calls.toList().forEach { it.disconnect() }
        return true
    }
}
