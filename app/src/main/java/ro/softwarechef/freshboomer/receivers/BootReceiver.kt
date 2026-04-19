package ro.softwarechef.freshboomer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ro.softwarechef.freshboomer.data.AppConfig
import ro.softwarechef.freshboomer.data.LauncherNavigator
import ro.softwarechef.freshboomer.services.InactivityMonitorWorker
import ro.softwarechef.freshboomer.services.MedicationReminderScheduler

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Initialize config so the worker can read it
            AppConfig.init(context)

            // Schedule inactivity monitor if enabled
            InactivityMonitorWorker.schedule(context)

            // Reschedule medication reminders
            MedicationReminderScheduler.scheduleAll(context)

            LauncherNavigator.launch(context, LauncherNavigator.Screen.HOME)
        }
    }
}
