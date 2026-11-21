package com.josias.autodialer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class DialerService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var loop: Job? = null

    companion object {
        const val CHANNEL_ID = "autodialer"
        const val NOTIF_ID = 1
        const val ACTION_STOP = "com.josias.autodialer.STOP"

        // Intent extras MainActivity passes when starting a run
        const val EXTRA_NUMBER = "number"
        const val EXTRA_INTERVAL_SEC = "interval"
        const val EXTRA_MAX_CALLS = "maxCalls"          // 0 = unlimited
        const val EXTRA_HANG_UP_ON_ANSWER = "hangUpOnAnswer"
        const val EXTRA_HIDE_CALLER_ID = "hideCallerId"

        /** Caller-ID block code dialed in front of the number (US/Canada carriers). */
        private const val HIDE_CALLER_ID_PREFIX = "*67"

        /** Every Stop button goes through here: end the current call and kill the loop. */
        fun stop(context: Context) {
            CallManager.autoDialing = false
            CallManager.endAll()
            context.stopService(Intent(context, DialerService::class.java))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {          // Stop from the notification
            stop(this)
            return START_NOT_STICKY
        }
        val rawNumber = intent?.getStringExtra(EXTRA_NUMBER)
        if (intent == null || rawNumber.isNullOrBlank()) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Drop spaces, dashes and brackets, e.g. "(555) 123-4567" from a contact
        val number = PhoneNumberUtils.stripSeparators(rawNumber)
        val intervalSec = intent.getLongExtra(EXTRA_INTERVAL_SEC, 60L).coerceAtLeast(1L)
        val maxCalls = intent.getIntExtra(EXTRA_MAX_CALLS, 0)
        val hideCallerId = intent.getBooleanExtra(EXTRA_HIDE_CALLER_ID, false)
        val dialString = if (hideCallerId) HIDE_CALLER_ID_PREFIX + number else number
        // Needs the app to be the default phone app (CallMonitorService does the work)
        CallManager.hangUpOnAnswer = intent.getBooleanExtra(EXTRA_HANG_UP_ON_ANSWER, false)
        CallManager.autoDialing = true

        createChannel()
        startForeground(
            NOTIF_ID,
            buildNotification(if (hideCallerId) "$number (ID hidden)" else number)
        )

        loop?.cancel()   // pressing Start twice restarts, instead of running two loops
        loop = scope.launch {
            var count = 0
            while (isActive) {
                placeCall(dialString)
                count++
                if (maxCalls > 0 && count >= maxCalls) break

                // Wait for this call to finish so calls never pile up.
                // (Only visible to us as the default phone app; otherwise skipped.)
                delay(3000)   // give Telecom a moment to hand us the new call
                while (isActive && CallManager.calls.isNotEmpty()) delay(500)

                delay(intervalSec * 1000)
            }
            stopSelf()
        }
        // Don't let Android revive the loop on its own after it's been killed.
        return START_NOT_STICKY
    }

    private fun placeCall(number: String) {
        if (checkSelfPermission(android.Manifest.permission.CALL_PHONE)
            != PackageManager.PERMISSION_GRANTED) return
        // Goes out over the SIM / default calling account
        val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
        telecom.placeCall(Uri.fromParts("tel", number, null), Bundle())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Auto Dialer", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(number: String): Notification {
        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val stopIntent = PendingIntent.getService(
            this, 0, Intent(this, DialerService::class.java).setAction(ACTION_STOP), piFlags
        )
        val openApp = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), piFlags
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Auto dialing")
            .setContentText("Calling $number")
            .setSmallIcon(android.R.drawable.ic_menu_call)
            .setOngoing(true)
            .setContentIntent(openApp)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopIntent)
            .build()
    }

    override fun onDestroy() {
        scope.cancel()   // kills the loop
        CallManager.autoDialing = false
        CallManager.hangUpOnAnswer = false   // normal calls behave normally again
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
