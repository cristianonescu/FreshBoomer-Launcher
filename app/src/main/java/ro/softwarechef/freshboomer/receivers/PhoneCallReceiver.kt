package ro.softwarechef.freshboomer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import ro.softwarechef.freshboomer.IncomingCallActivity
import ro.softwarechef.freshboomer.data.MissedCallStore

class PhoneCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d("PhoneCallReceiver", "Phone state: $state, number: $incomingNumber, lastState: $lastState, lastNumber: $lastRingingNumber")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Remember this ringing call so we can detect if it's never answered.
                if (incomingNumber != null) {
                    lastRingingNumber = incomingNumber
                }
                lastState = TelephonyManager.EXTRA_STATE_RINGING

                if (incomingNumber != null) {
                    val callIntent = Intent(context, IncomingCallActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                                Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        putExtra(IncomingCallActivity.EXTRA_INCOMING_NUMBER, incomingNumber)
                    }
                    context.startActivity(callIntent)
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call was answered (or it's an outgoing call) — not a miss.
                lastState = TelephonyManager.EXTRA_STATE_OFFHOOK
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // RINGING → IDLE without an intervening OFFHOOK means the call
                // ended without being answered: a missed call. Record it as a
                // fallback to CallService (which is the primary path when this
                // app holds the default-dialer role).
                if (lastState == TelephonyManager.EXTRA_STATE_RINGING && lastRingingNumber != null) {
                    Log.d("PhoneCallReceiver", "Missed call detected via PHONE_STATE: $lastRingingNumber")
                    MissedCallStore.record(context, lastRingingNumber)
                }
                lastRingingNumber = null
                lastState = TelephonyManager.EXTRA_STATE_IDLE
            }
        }
    }

    companion object {
        // Static state survives across receiver instances since BroadcastReceivers
        // are short-lived. Adequate for tracking a single in-progress call.
        @Volatile
        private var lastState: String? = null

        @Volatile
        private var lastRingingNumber: String? = null
    }
}
