package ro.softwarechef.freshboomer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import ro.softwarechef.freshboomer.TtsSmsAlertActivity
import ro.softwarechef.freshboomer.data.AppConfig
import ro.softwarechef.freshboomer.data.EmergencyContact

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Telephony.Sms.Intents.SMS_DELIVER_ACTION -> {
                handleSms(context, intent, isDefault = true)
            }
            Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                handleSms(context, intent, isDefault = false)
            }
        }
    }

    private fun handleSms(context: Context, intent: Intent, isDefault: Boolean) {
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        // Group multi-part SMS by sender and concatenate bodies
        val grouped = mutableMapOf<String, StringBuilder>()
        messages.forEach { smsMessage ->
            val sender = smsMessage.originatingAddress ?: "Unknown"
            grouped.getOrPut(sender) { StringBuilder() }.append(smsMessage.messageBody)
        }

        grouped.forEach { (sender, bodyBuilder) ->
            val messageBody = bodyBuilder.toString()

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

            Log.d("SmsReceiver", "Received SMS${if (!isDefault) " (non-default)" else ""} from $sender: $messageBody")

            // Check for TTS SMS trigger
            checkTtsSms(context, sender, messageBody)
        }
    }

    private fun checkTtsSms(context: Context, sender: String, messageBody: String) {
        val config = AppConfig.current
        if (!config.featureTtsSms || !config.ttsEnabled) return

        // Check for both the configured prefix and "READ:" as recognized prefixes
        val configuredPrefix = config.ttsSmsPrefix.trim()
        val recognizedPrefixes = mutableSetOf(configuredPrefix)
        // Always recognize both CITESTE: and READ: regardless of language setting
        recognizedPrefixes.add("CITESTE:")
        recognizedPrefixes.add("READ:")

        val matchedPrefix = recognizedPrefixes.firstOrNull { prefix ->
            prefix.isNotEmpty() && messageBody.startsWith(prefix, ignoreCase = true)
        } ?: return

        val actualMessage = messageBody.removePrefix(matchedPrefix)
            .removePrefix(matchedPrefix.lowercase())
            .removePrefix(matchedPrefix.uppercase())
            .let { messageBody.substring(matchedPrefix.length) }
            .trim()

        if (actualMessage.isBlank()) return

        // Check trusted sender if required
        if (config.featureTtsSmsTrustedOnly && !isTrustedSender(sender, config.emergencyContacts)) {
            Log.d("SmsReceiver", "TTS SMS ignored: sender $sender is not a trusted contact")
            return
        }

        // Don't trigger if in a call
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        if (audioManager.mode == AudioManager.MODE_IN_CALL ||
            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION) {
            Log.d("SmsReceiver", "TTS SMS deferred: currently in a call")
            return
        }

        // Launch full-screen TTS alert
        val alertIntent = Intent(context, TtsSmsAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("sender", sender)
            putExtra("message", actualMessage)
        }
        context.startActivity(alertIntent)

        Log.d("SmsReceiver", "TTS SMS triggered from $sender: $actualMessage")
    }

    private fun isTrustedSender(sender: String, contacts: List<EmergencyContact>): Boolean {
        val normalizedSender = sender.filter { it.isDigit() }.takeLast(10)
        if (normalizedSender.isEmpty()) return false
        return contacts.any { contact ->
            val normalizedContact = contact.phoneNumber.filter { it.isDigit() }.takeLast(10)
            normalizedContact.isNotEmpty() && normalizedContact == normalizedSender
        }
    }
}
