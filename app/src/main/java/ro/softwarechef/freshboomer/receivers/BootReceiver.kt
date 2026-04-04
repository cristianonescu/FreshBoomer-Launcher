package ro.softwarechef.freshboomer.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ro.softwarechef.freshboomer.MainActivity
import ro.softwarechef.freshboomer.data.AppConfig
import ro.softwarechef.freshboomer.services.InactivityMonitorWorker

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            // Initialize config so the worker can read it
            AppConfig.init(context)

            // Schedule inactivity monitor if enabled
            InactivityMonitorWorker.schedule(context)

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(launchIntent)
        }
    }
}
