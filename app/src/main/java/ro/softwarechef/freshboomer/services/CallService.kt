package ro.softwarechef.freshboomer.services

import android.telecom.Call
import android.telecom.InCallService
import android.util.Log
import ro.softwarechef.freshboomer.call.CallManager
import ro.softwarechef.freshboomer.data.LauncherNavigator
import ro.softwarechef.freshboomer.data.MissedCallStore

private const val TAG = "FB/CallService"

class CallService : InCallService() {

    /**
     * Tracks calls that started in RINGING state and whether they were ever
     * answered (transitioned to ACTIVE). When such a call is removed without
     * ever being answered, it is recorded as a missed call.
     *
     * Replaces CallLog-based detection so we don't need READ_CALL_LOG.
     */
    private data class RingingCallState(var answered: Boolean)

    private val ringingCalls = mutableMapOf<Call, RingingCallState>()
    private val ringingCallbacks = mutableMapOf<Call, Call.Callback>()

    @Suppress("DEPRECATION")
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        Log.d(TAG, "onCallAdded: state=${call.state}")

        CallManager.updateCall(call, applicationContext)

        when (call.state) {
            Call.STATE_RINGING -> {
                // Track this call so we can detect if the user never answers it
                trackRingingCall(call)

                // Incoming call
                LauncherNavigator.launch(
                    this,
                    LauncherNavigator.Screen.INCOMING_CALL,
                    excludeFromRecents = true
                )
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_ACTIVE -> {
                // Outgoing or active call
                LauncherNavigator.launch(this, LauncherNavigator.Screen.IN_CALL)
            }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        Log.d(TAG, "onCallRemoved")

        // If this was a ringing call that was never answered, persist as missed.
        val state = ringingCalls.remove(call)
        ringingCallbacks.remove(call)?.let { call.unregisterCallback(it) }
        if (state != null && !state.answered) {
            val number = call.details?.handle?.schemeSpecificPart
            Log.d(TAG, "Missed call recorded: $number")
            MissedCallStore.record(applicationContext, number)
        }

        CallManager.updateCall(null)
    }

    @Suppress("DEPRECATION")
    private fun trackRingingCall(call: Call) {
        if (ringingCalls.containsKey(call)) return
        ringingCalls[call] = RingingCallState(answered = false)

        val callback = object : Call.Callback() {
            override fun onStateChanged(call: Call, newState: Int) {
                if (newState == Call.STATE_ACTIVE) {
                    ringingCalls[call]?.answered = true
                }
            }
        }
        ringingCallbacks[call] = callback
        call.registerCallback(callback)
    }
}
