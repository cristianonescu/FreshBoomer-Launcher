package ro.softwarechef.freshboomer.services

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import android.util.Log

class HeadlessSmsSendService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            val recipient = it.data?.schemeSpecificPart
            val message = it.getStringExtra("sms_body")
            
            if (recipient != null && message != null) {
                try {
                    val smsManager = SmsManager.getDefault()
                    smsManager.sendTextMessage(recipient, null, message, null, null)
                    Log.d("SmsService", "Message sent to $recipient")
                } catch (e: Exception) {
                    Log.e("SmsService", "Error sending message", e)
                }
            }
        }
        return START_NOT_STICKY
    }
} 