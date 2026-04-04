package ro.softwarechef.freshboomer.services

import android.content.Context
import android.telephony.SmsManager
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import ro.softwarechef.freshboomer.data.AppConfig
import ro.softwarechef.freshboomer.data.InactivityTracker
import java.util.concurrent.TimeUnit

class InactivityMonitorWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val config = AppConfig.current

        if (!config.inactivityMonitorEnabled) {
            Log.d(TAG, "Inactivity monitor disabled, skipping check")
            return Result.success()
        }

        if (config.emergencyContacts.isEmpty()) {
            Log.d(TAG, "No emergency contacts configured, skipping check")
            return Result.success()
        }

        val inactivityMs = InactivityTracker.getInactivityDurationMs(applicationContext)
        if (inactivityMs < 0) {
            // No interaction ever recorded — record one now so the timer starts
            InactivityTracker.recordInteraction(applicationContext)
            Log.d(TAG, "No prior interaction recorded, initializing tracker")
            return Result.success()
        }

        val thresholdMs = config.inactivityMonitorThresholdHours * 3600_000L
        Log.d(TAG, "Inactivity: ${inactivityMs / 3600_000.0}h, threshold: ${config.inactivityMonitorThresholdHours}h")

        if (inactivityMs >= thresholdMs) {
            // Don't send another alert if we already sent one within the last threshold period
            val lastAlertMs = InactivityTracker.getLastAlertSentMs(applicationContext)
            val timeSinceAlert = System.currentTimeMillis() - lastAlertMs
            if (lastAlertMs > 0 && timeSinceAlert < thresholdMs) {
                Log.d(TAG, "Alert already sent ${timeSinceAlert / 3600_000.0}h ago, skipping")
                return Result.success()
            }

            sendAlertSms(config)
            InactivityTracker.recordAlertSent(applicationContext)
        }

        return Result.success()
    }

    private fun sendAlertSms(config: ro.softwarechef.freshboomer.data.ConfigData) {
        val nickname = config.userNickname
        val hours = config.inactivityMonitorThresholdHours
        val message = "FreshBoomer: $nickname nu a folosit telefonul de peste $hours ore. Verificati daca este in regula."

        val smsManager = applicationContext.getSystemService(SmsManager::class.java)
        for (contact in config.emergencyContacts) {
            if (contact.phoneNumber.isBlank()) continue
            try {
                val parts = smsManager.divideMessage(message)
                smsManager.sendMultipartTextMessage(
                    contact.phoneNumber,
                    null,
                    parts,
                    null,
                    null
                )
                Log.d(TAG, "Alert SMS sent to ${contact.name} (${contact.phoneNumber})")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SMS to ${contact.phoneNumber}", e)
            }
        }
    }

    companion object {
        private const val TAG = "InactivityMonitor"
        const val WORK_NAME = "inactivity_monitor"

        fun schedule(context: Context) {
            val config = AppConfig.current
            if (!config.inactivityMonitorEnabled) {
                cancel(context)
                return
            }

            // Check every 1 hour
            val request = PeriodicWorkRequestBuilder<InactivityMonitorWorker>(
                1, TimeUnit.HOURS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
            Log.d(TAG, "Inactivity monitor scheduled (check every 1h, alert after ${config.inactivityMonitorThresholdHours}h)")
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            Log.d(TAG, "Inactivity monitor cancelled")
        }

        fun reschedule(context: Context) {
            cancel(context)
            if (AppConfig.current.inactivityMonitorEnabled) {
                val request = PeriodicWorkRequestBuilder<InactivityMonitorWorker>(
                    1, TimeUnit.HOURS
                ).build()
                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.REPLACE,
                    request
                )
                Log.d(TAG, "Inactivity monitor rescheduled")
            }
        }
    }
}
