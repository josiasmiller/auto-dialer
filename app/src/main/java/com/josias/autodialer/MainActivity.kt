package com.josias.autodialer

import android.Manifest
import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import android.telecom.TelecomManager
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged

class MainActivity : AppCompatActivity() {

    private lateinit var number: EditText
    private lateinit var contactName: TextView
    private lateinit var dialerStatus: TextView
    private lateinit var makeDefault: Button
    private lateinit var restoreStock: Button

    // The contact picker hands back a single phone number row. No READ_CONTACTS
    // permission needed: Android grants access to just the row the user picked.
    private val pickContact =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uri = result.data?.data ?: return@registerForActivityResult
            contentResolver.query(
                uri, arrayOf(Phone.NUMBER, Phone.DISPLAY_NAME), null, null, null
            )?.use { c ->
                if (c.moveToFirst()) {
                    number.setText(c.getString(0))
                    contactName.text = "Contact: ${c.getString(1)}"
                    contactName.visibility = View.VISIBLE
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val perms = mutableListOf(Manifest.permission.CALL_PHONE)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        requestPermissions(perms.toTypedArray(), REQ_PERMISSIONS)

        number = findViewById(R.id.number)
        contactName = findViewById(R.id.contactName)
        dialerStatus = findViewById(R.id.dialerStatus)
        makeDefault = findViewById(R.id.makeDefault)
        restoreStock = findViewById(R.id.restoreStock)
        val interval = findViewById<EditText>(R.id.interval)
        val maxCalls = findViewById<EditText>(R.id.maxCalls)
        val hangUpOnAnswer = findViewById<CheckBox>(R.id.hangUpOnAnswer)
        val hideCallerId = findViewById<CheckBox>(R.id.hideCallerId)

        fillNumberFrom(intent)

        // Typing a number by hand clears the picked contact's name
        number.doOnTextChanged { _, _, _, _ ->
            if (number.hasFocus()) contactName.visibility = View.GONE
        }
        findViewById<Button>(R.id.pickContact).setOnClickListener {
            pickContact.launch(Intent(Intent.ACTION_PICK, Phone.CONTENT_URI))
        }
        findViewById<Button>(R.id.callerIdSettings).setOnClickListener { openCallerIdSettings() }
        makeDefault.setOnClickListener { requestDefaultDialer() }
        restoreStock.setOnClickListener {
            DialerService.stop(this)
            restoreStockDialer()
        }

        findViewById<Button>(R.id.start).setOnClickListener {
            if (number.text.isBlank()) {
                toast("Enter a number or pick a contact")
                return@setOnClickListener
            }
            if (hangUpOnAnswer.isChecked && !isDefaultDialer()) {
                toast("Make this the default phone app first, or it can't see when they answer")
                return@setOnClickListener
            }
            val i = Intent(this, DialerService::class.java)
                .putExtra(DialerService.EXTRA_NUMBER, number.text.toString())
                .putExtra(DialerService.EXTRA_INTERVAL_SEC, interval.text.toString().toLongOrNull() ?: 60L)
                .putExtra(DialerService.EXTRA_MAX_CALLS, maxCalls.text.toString().toIntOrNull() ?: 0)
                .putExtra(DialerService.EXTRA_HANG_UP_ON_ANSWER, hangUpOnAnswer.isChecked)
                .putExtra(DialerService.EXTRA_HIDE_CALLER_ID, hideCallerId.isChecked)
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(i) else startService(i)
        }

        findViewById<Button>(R.id.stop).setOnClickListener { DialerService.stop(this) }
    }

    override fun onResume() {
        super.onResume()
        val isDefault = isDefaultDialer()
        dialerStatus.text =
            if (isDefault) "Default phone app: yes (can detect answer)"
            else "Default phone app: no (can't detect answer)"
        makeDefault.visibility = if (isDefault) View.GONE else View.VISIBLE
        restoreStock.visibility = if (isDefault) View.VISIBLE else View.GONE
    }

    // Other apps' "call this number" links land here because we handle DIAL.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        fillNumberFrom(intent)
    }

    private fun fillNumberFrom(intent: Intent?) {
        if (intent?.action == Intent.ACTION_DIAL) {
            intent.data?.schemeSpecificPart?.let { number.setText(it) }
        }
    }

    private fun isDefaultDialer(): Boolean {
        val telecom = getSystemService(TELECOM_SERVICE) as TelecomManager
        return telecom.defaultDialerPackage == packageName
    }

    private fun requestDefaultDialer() {
        val intent = if (Build.VERSION.SDK_INT >= 29) {
            // Android 10+: the old intent is ignored, use RoleManager
            getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_DIALER)
        } else {
            // Android 6-9
            Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(intent, REQ_DEFAULT_DIALER)
    }

    /**
     * Apps can't change the phone's "Hide number" setting (system apps only),
     * but it applies to every call, ours included. So just take the user there.
     */
    private fun openCallerIdSettings() {
        try {
            startActivity(Intent(TelecomManager.ACTION_SHOW_CALL_SETTINGS))
            toast("Go to Additional settings → Caller ID → Hide number")
        } catch (e: ActivityNotFoundException) {
            toast("Open your Phone app → Settings → Caller ID")
        }
    }

    /**
     * The phone's built-in dialer: a pre-installed app (not us) that handles DIAL.
     * TelecomManager.getSystemDialerPackage() would be simpler but is Android 10+ only.
     */
    private fun findStockDialer(): String? =
        packageManager.queryIntentActivities(Intent(Intent.ACTION_DIAL, Uri.parse("tel:")), 0)
            .map { it.activityInfo.applicationInfo }
            .firstOrNull {
                it.packageName != packageName && (it.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            }
            ?.packageName

    private fun restoreStockDialer() {
        val stock = if (Build.VERSION.SDK_INT < 29) findStockDialer() else null
        if (stock != null) {
            // Android 6-9: the same system prompt, but naming the stock dialer
            val intent = Intent(TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
                .putExtra(TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, stock)
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(intent, REQ_RESTORE_DIALER)
                return
            } catch (e: ActivityNotFoundException) {
                // fall through to Settings
            }
        }
        // Android 10+ (or no prompt available): apps can't hand the role to
        // another app, so open the Default apps screen for the user to pick.
        try {
            startActivity(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        } catch (e: ActivityNotFoundException) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
        toast("Choose your normal Phone app")
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    private companion object {
        const val REQ_PERMISSIONS = 1
        const val REQ_DEFAULT_DIALER = 2
        const val REQ_RESTORE_DIALER = 3
    }
}
