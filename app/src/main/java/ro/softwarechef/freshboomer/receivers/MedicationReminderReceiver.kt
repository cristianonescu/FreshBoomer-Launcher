package ro.softwarechef.freshboomer.receivers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.util.Log
import androidx.core.app.NotificationCompat
import ro.softwarechef.freshboomer.R
import ro.softwarechef.freshboomer.data.AppConfig
import ro.softwarechef.freshboomer.data.LauncherNavigator
import ro.softwarechef.freshboomer.services.MedicationReminderScheduler

class MedicationReminderReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MedReminderReceiver"
        private const val CHANNEL_ID = "medication_reminders"
        private const val NOTIFICATION_ID_BASE = 5000
    }

    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getStringExtra("reminder_id") ?: return
        val reminderName = intent.getStringExtra("reminder_name") ?: return
        val snoozeDuration = intent.getIntExtra("snooze_duration", 5)
        val isSnooze = intent.getBooleanExtra("is_snooze", false)

        Log.d(TAG, "Medication reminder fired: $reminderName (snooze=$isSnooze)")

        // Clear snooze state when the user dismissed (non-snooze alarm fires)
        if (!isSnooze) {
            MedicationReminderScheduler.clearSnoozeState(context, reminderId)
        }

        // Create notification channel
        createNotificationChannel(context)

        // Show notification with full-screen intent
        showNotification(context, reminderId, reminderName, snoozeDuration)

        // Launch full-screen alert activity
        LauncherNavigator.launch(context, LauncherNavigator.Screen.MEDICATION_ALERT) {
            putExtra("reminder_id", reminderId)
            putExtra("reminder_name", reminderName)
            putExtra("snooze_duration", snoozeDuration)
        }

        // Schedule next occurrence (for non-snooze alarms only)
        if (!isSnooze) {
            val config = AppConfig.current
            val reminder = config.medicationReminders.find { it.id == reminderId }
            if (reminder != null && reminder.enabled) {
                MedicationReminderScheduler.scheduleAll(context)
            }
        }
    }

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.medication_notification_channel),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.medication_notification_title)
            enableVibration(true)
            setSound(
                android.provider.Settings.System.DEFAULT_ALARM_ALERT_URI,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun showNotification(
        context: Context,
        reminderId: String,
        reminderName: String,
        snoozeDuration: Int
    ) {
        val fullScreenIntent = LauncherNavigator.intentFor(
            context,
            LauncherNavigator.Screen.MEDICATION_ALERT
        ) {
            putExtra("reminder_id", reminderId)
            putExtra("reminder_name", reminderName)
            putExtra("snooze_duration", snoozeDuration)
        }
        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            reminderId.hashCode() + 100,
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(context.getString(R.string.medication_notification_title))
            .setContentText(context.getString(R.string.medication_notification_text, reminderName))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .setAutoCancel(true)
            .setOngoing(true)
            .build()

        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            NOTIFICATION_ID_BASE + reminderId.hashCode().and(0xFFF),
            notification
        )
    }
}
