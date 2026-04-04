package ro.softwarechef.freshboomer.services

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import ro.softwarechef.freshboomer.WhatsAppCallActivity

class WhatsAppCallListenerService : NotificationListenerService() {

    companion object {
        private const val TAG = "WACallListener"
        private const val WHATSAPP_PACKAGE = "com.whatsapp"

        // WhatsApp uses category "call" for incoming/ongoing calls
        private val CALL_CATEGORIES = setOf(
            Notification.CATEGORY_CALL,
            Notification.CATEGORY_PROGRESS // some WhatsApp versions use this for ongoing calls
        )

        var isWhatsAppCallActive = false
            private set

        // Track whether we already showed our UI for this call
        private var handledCallKey: String? = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE) return

        val notification = sbn.notification
        val category = notification.category
        val isCallNotification = category in CALL_CATEGORIES
        val hasFullScreenIntent = notification.fullScreenIntent != null

        // WhatsApp incoming calls have fullScreenIntent and CATEGORY_CALL
        if (isCallNotification || hasFullScreenIntent) {
            // Only show our UI once per call — don't re-launch when notification updates
            // (e.g. ringing → ongoing)
            if (handledCallKey == sbn.key) {
                Log.d(TAG, "WhatsApp call notification updated, already handled: ${sbn.key}")
                return
            }

            val callerName = notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
            val callerText = notification.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

            Log.d(TAG, "WhatsApp call detected: caller=$callerName, text=$callerText, category=$category")

            isWhatsAppCallActive = true
            handledCallKey = sbn.key

            val intent = Intent(this, WhatsAppCallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(WhatsAppCallActivity.EXTRA_CALLER_NAME, callerName ?: "cineva")
                putExtra(WhatsAppCallActivity.EXTRA_CALLER_TEXT, callerText)
                putExtra(WhatsAppCallActivity.EXTRA_NOTIFICATION_KEY, sbn.key)
            }
            startActivity(intent)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        if (sbn.packageName != WHATSAPP_PACKAGE) return

        val notification = sbn.notification
        val category = notification.category
        val isCallNotification = category in CALL_CATEGORIES
        val hasFullScreenIntent = notification.fullScreenIntent != null

        if (isCallNotification || hasFullScreenIntent) {
            Log.d(TAG, "WhatsApp call notification removed")
            isWhatsAppCallActive = false
            handledCallKey = null

            // Broadcast so WhatsAppCallActivity can finish
            val intent = Intent(WhatsAppCallActivity.ACTION_WHATSAPP_CALL_ENDED)
            sendBroadcast(intent)
        }
    }
}
