package ro.softwarechef.freshboomer.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Tracks the last time the user interacted with the device.
 * Interactions include: screen touch, activity resume, and charger plugged in.
 */
object InactivityTracker {
    private const val PREFS_NAME = "InactivityTrackerPrefs"
    private const val KEY_LAST_INTERACTION = "last_interaction_ms"
    private const val KEY_LAST_ALERT_SENT = "last_alert_sent_ms"
    private const val TAG = "InactivityTracker"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun recordInteraction(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong(KEY_LAST_INTERACTION, now).apply()
        Log.d(TAG, "Interaction recorded at $now")
    }

    fun getLastInteractionMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_INTERACTION, 0L)

    fun getLastAlertSentMs(context: Context): Long =
        prefs(context).getLong(KEY_LAST_ALERT_SENT, 0L)

    fun recordAlertSent(context: Context) {
        val now = System.currentTimeMillis()
        prefs(context).edit().putLong(KEY_LAST_ALERT_SENT, now).apply()
        Log.d(TAG, "Alert sent recorded at $now")
    }

    /**
     * Returns the number of milliseconds since the last interaction,
     * or -1 if no interaction has ever been recorded.
     */
    fun getInactivityDurationMs(context: Context): Long {
        val last = getLastInteractionMs(context)
        if (last == 0L) return -1
        return System.currentTimeMillis() - last
    }
}
