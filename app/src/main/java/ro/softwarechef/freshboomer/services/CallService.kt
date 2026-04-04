package ro.softwarechef.freshboomer.services

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import ro.softwarechef.freshboomer.InCallActivity
import ro.softwarechef.freshboomer.IncomingCallActivity
import ro.softwarechef.freshboomer.call.CallManager

class CallService : InCallService() {

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d("CallService", "onCallAdded: state=${call.state}")

        CallManager.updateCall(call, applicationContext)

        when (call.state) {
            Call.STATE_RINGING -> {
                // Incoming call
                val intent = Intent(this, IncomingCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                            Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                }
                startActivity(intent)
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_ACTIVE -> {
                // Outgoing or active call
                val intent = Intent(this, InCallActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(intent)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d("CallService", "onCallRemoved")
        CallManager.updateCall(null)
    }
}
