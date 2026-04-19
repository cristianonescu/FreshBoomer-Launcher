package ro.softwarechef.freshboomer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

private const val TAG = "FB/MmsReceiver"

class MmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "MMS received: ${intent.action}")
        // MMS handling is not critical for this app — just acknowledge receipt
    }
}
