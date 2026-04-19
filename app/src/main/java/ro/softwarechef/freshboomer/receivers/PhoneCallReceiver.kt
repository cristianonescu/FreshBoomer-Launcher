package ro.softwarechef.freshboomer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import ro.softwarechef.freshboomer.IncomingCallActivity
import ro.softwarechef.freshboomer.data.LauncherNavigator
import ro.softwarechef.freshboomer.data.MissedCallStore

private const val TAG = "FB/PhoneCallReceiver"

class PhoneCallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return

        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        // EXTRA_INCOMING_NUMBER was deprecated in API 29 in favour of READ_CALL_LOG
        // or CallScreeningService. We explicitly do NOT request READ_CALL_LOG
        // (Play Store privacy posture, see AI-INSTRUCTIONS). The primary path for
        // call detection is `CallService` (default-dialer InCallService using
        // `Call.Details.getHandle()`); this receiver is a fallback.
        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        Log.d(TAG, "Phone state: $state, number: $incomingNumber, lastState: $lastState, lastNumber: $lastRingingNumber")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Remember this ringing call so we can detect if it's never answered.
                if (incomingNumber != null) {
                    lastRingingNumber = incomingNumber
                }
                lastState = TelephonyManager.EXTRA_STATE_RINGING

                if (incomingNumber != null) {
                    LauncherNavigator.launch(
                        context,
                        LauncherNavigator.Screen.INCOMING_CALL,
                        excludeFromRecents = true
                    ) {
                        putExtra(IncomingCallActivity.EXTRA_INCOMING_NUMBER, incomingNumber)
                    }
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
                    Log.d(TAG, "Missed call detected via PHONE_STATE: $lastRingingNumber")
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
