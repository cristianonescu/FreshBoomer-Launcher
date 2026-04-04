package ro.softwarechef.freshboomer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Telephony.Sms.Intents.SMS_DELIVER_ACTION -> {
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                messages?.forEach { smsMessage ->
                    val sender = smsMessage.originatingAddress ?: "Unknown"
                    val messageBody = smsMessage.messageBody
                    
                    // Store the message in the system SMS database
                    val contentValues = android.content.ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, sender)
                        put(Telephony.Sms.BODY, messageBody)
                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    }
                    
                    context.contentResolver.insert(Telephony.Sms.CONTENT_URI, contentValues)
                    
                    // Broadcast the new message to update the UI
                    val updateIntent = Intent("ro.softwarechef.freshboomer.SMS_RECEIVED").apply {
                        putExtra("sender", sender)
                        putExtra("message", messageBody)
                        putExtra("timestamp", System.currentTimeMillis())
                    }
                    context.sendBroadcast(updateIntent)
                    
                    Log.d("SmsReceiver", "Received SMS from $sender: $messageBody")
                }
            }
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                // This is a fallback for non-default SMS apps
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                messages?.forEach { smsMessage ->
                    val sender = smsMessage.originatingAddress ?: "Unknown"
                    val messageBody = smsMessage.messageBody
                    
                    // Store the message in the system SMS database
                    val contentValues = android.content.ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, sender)
                        put(Telephony.Sms.BODY, messageBody)
                        put(Telephony.Sms.DATE, System.currentTimeMillis())
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    }
                    
                    context.contentResolver.insert(Telephony.Sms.CONTENT_URI, contentValues)
                    
                    // Broadcast the new message to update the UI
                    val updateIntent = Intent("ro.softwarechef.freshboomer.SMS_RECEIVED").apply {
                        putExtra("sender", sender)
                        putExtra("message", messageBody)
                        putExtra("timestamp", System.currentTimeMillis())
                    }
                    context.sendBroadcast(updateIntent)
                    
                    Log.d("SmsReceiver", "Received SMS (non-default) from $sender: $messageBody")
                }
            }
        }
    }
} 