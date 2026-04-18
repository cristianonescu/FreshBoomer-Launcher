package ro.softwarechef.freshboomer.services

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings
import android.util.Log
import ro.softwarechef.freshboomer.data.AppConfig
import ro.softwarechef.freshboomer.data.MedicationReminder
import ro.softwarechef.freshboomer.receivers.MedicationReminderReceiver
import java.util.Calendar

object MedicationReminderScheduler {
    private const val TAG = "MedReminderScheduler"
    private const val SNOOZE_PREFS = "MedicationSnoozePrefs"

    fun scheduleAll(context: Context) {
        val config = AppConfig.current
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        if (!config.featureMedicationReminders) {
            cancelAll(context)
            return
        }

        config.medicationReminders.forEach { reminder ->
            if (reminder.enabled) {
                scheduleNext(context, alarmManager, reminder)
            } else {
                cancelReminder(context, alarmManager, reminder)
            }
        }

        // Also restore any pending snooze alarms
        restoreSnoozeAlarms(context, alarmManager)

        Log.d(TAG, "Scheduled ${config.medicationReminders.count { it.enabled }} medication reminders")
    }

    fun cancelAll(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val config = AppConfig.current
        config.medicationReminders.forEach { reminder ->
            cancelReminder(context, alarmManager, reminder)
        }
        clearAllSnoozeState(context)
        Log.d(TAG, "Cancelled all medication reminders")
    }

    fun scheduleSnooze(context: Context, reminder: MedicationReminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        // Check snooze count (max 3)
        val prefs = getSnoozePrefs(context)
        val snoozeKey = "snooze_count_${reminder.id}"
        val currentCount = prefs.getInt(snoozeKey, 0)
        if (currentCount >= 3) {
            Log.d(TAG, "Max snooze count reached for ${reminder.name}, not rescheduling")
            return
        }

        val fireTimeMs = System.currentTimeMillis() + reminder.snoozeDurationMinutes * 60_000L

        // Persist snooze state for reboot survival
        prefs.edit()
            .putInt(snoozeKey, currentCount + 1)
            .putLong("snooze_time_${reminder.id}", fireTimeMs)
            .apply()

        val pendingIntent = createPendingIntent(context, reminder, isSnooze = true)
        scheduleExact(alarmManager, fireTimeMs, pendingIntent)

        Log.d(TAG, "Snoozed reminder '${reminder.name}' for ${reminder.snoozeDurationMinutes} min (attempt ${currentCount + 1}/3)")
    }

    fun clearSnoozeState(context: Context, reminderId: String) {
        getSnoozePrefs(context).edit()
            .remove("snooze_count_$reminderId")
            .remove("snooze_time_$reminderId")
            .apply()
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        return alarmManager.canScheduleExactAlarms()
    }

    fun getExactAlarmSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        }
    }

    private fun scheduleNext(context: Context, alarmManager: AlarmManager, reminder: MedicationReminder) {
        val fireTimeMs = calculateNextFireTime(reminder.time, reminder.daysOfWeek) ?: return
        val pendingIntent = createPendingIntent(context, reminder, isSnooze = false)
        scheduleExact(alarmManager, fireTimeMs, pendingIntent)
        Log.d(TAG, "Scheduled reminder '${reminder.name}' at ${reminder.time}")
    }

    private fun scheduleExact(alarmManager: AlarmManager, fireTimeMs: Long, pendingIntent: PendingIntent) {
        try {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                fireTimeMs,
                pendingIntent
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Cannot schedule exact alarm: ${e.message}")
        }
    }

    private fun cancelReminder(context: Context, alarmManager: AlarmManager, reminder: MedicationReminder) {
        val pendingIntent = createPendingIntent(context, reminder, isSnooze = false)
        alarmManager.cancel(pendingIntent)
        val snoozePendingIntent = createPendingIntent(context, reminder, isSnooze = true)
        alarmManager.cancel(snoozePendingIntent)
    }

    private fun createPendingIntent(context: Context, reminder: MedicationReminder, isSnooze: Boolean): PendingIntent {
        val intent = Intent(context, MedicationReminderReceiver::class.java).apply {
            action = "ro.softwarechef.freshboomer.MEDICATION_REMINDER"
            putExtra("reminder_id", reminder.id)
            putExtra("reminder_name", reminder.name)
            putExtra("snooze_duration", reminder.snoozeDurationMinutes)
            putExtra("is_snooze", isSnooze)
        }
        // Use different request codes for regular vs snooze to avoid overwriting
        val requestCode = if (isSnooze) {
            reminder.id.hashCode() + 1
        } else {
            reminder.id.hashCode()
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun restoreSnoozeAlarms(context: Context, alarmManager: AlarmManager) {
        val prefs = getSnoozePrefs(context)
        val config = AppConfig.current
        config.medicationReminders.forEach { reminder ->
            val snoozeTime = prefs.getLong("snooze_time_${reminder.id}", 0)
            if (snoozeTime > System.currentTimeMillis()) {
                val pendingIntent = createPendingIntent(context, reminder, isSnooze = true)
                scheduleExact(alarmManager, snoozeTime, pendingIntent)
                Log.d(TAG, "Restored snooze alarm for '${reminder.name}'")
            } else if (snoozeTime > 0) {
                // Expired snooze, clear it
                clearSnoozeState(context, reminder.id)
            }
        }
    }

    private fun clearAllSnoozeState(context: Context) {
        getSnoozePrefs(context).edit().clear().apply()
    }

    private fun getSnoozePrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(SNOOZE_PREFS, Context.MODE_PRIVATE)
    }

    /**
     * @param nowMs "current time" baseline; defaults to the wall clock.
     *   Injectable for deterministic tests.
     */
    internal fun calculateNextFireTime(
        time: String,
        daysOfWeek: List<Int>,
        nowMs: Long = System.currentTimeMillis()
    ): Long? {
        if (daysOfWeek.isEmpty()) return null

        val parts = time.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null

        val candidate = Calendar.getInstance()

        // Try today and the next 7 days
        for (offset in 0..7) {
            candidate.timeInMillis = nowMs
            candidate.set(Calendar.HOUR_OF_DAY, hour)
            candidate.set(Calendar.MINUTE, minute)
            candidate.set(Calendar.SECOND, 0)
            candidate.set(Calendar.MILLISECOND, 0)
            candidate.add(Calendar.DAY_OF_YEAR, offset)

            // Calendar uses 1=Sunday..7=Saturday, ISO uses 1=Monday..7=Sunday
            val calDow = candidate.get(Calendar.DAY_OF_WEEK)
            val isoDow = when (calDow) {
                Calendar.MONDAY -> 1
                Calendar.TUESDAY -> 2
                Calendar.WEDNESDAY -> 3
                Calendar.THURSDAY -> 4
                Calendar.FRIDAY -> 5
                Calendar.SATURDAY -> 6
                Calendar.SUNDAY -> 7
                else -> 0
            }

            if (isoDow in daysOfWeek && candidate.timeInMillis > nowMs) {
                return candidate.timeInMillis
            }
        }

        return null
    }
}
