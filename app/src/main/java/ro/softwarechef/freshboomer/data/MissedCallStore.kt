package ro.softwarechef.freshboomer.data

import android.content.Context

/**
 * Persists the most recent missed call to SharedPreferences.
 *
 * Replaces the previous CallLog-based detection so the app no longer needs the
 * READ_CALL_LOG / WRITE_CALL_LOG permissions (Google Play policy compliance).
 *
 * Missed calls are recorded by:
 *  - [ro.softwarechef.freshboomer.services.CallService] (InCallService) when
 *    a ringing call is removed without ever transitioning to ACTIVE.
 *  - [ro.softwarechef.freshboomer.receivers.PhoneCallReceiver] as a fallback
 *    that watches PHONE_STATE_CHANGED broadcasts (RINGING → IDLE without OFFHOOK).
 *
 * Only the most recent missed call is kept — that's all the home-screen banner
 * and TTS announcement need.
 */
object MissedCallStore {

    private const val PREFS_NAME = "MissedCallPrefs"
    private const val KEY_NUMBER = "number"
    private const val KEY_TIMESTAMP = "timestamp"

    data class Record(val number: String, val timestamp: Long)

    fun record(context: Context, number: String?, timestamp: Long = System.currentTimeMillis()) {
        val cleanNumber = number?.trim().orEmpty()
        if (cleanNumber.isEmpty()) return
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_NUMBER, cleanNumber)
            .putLong(KEY_TIMESTAMP, timestamp)
            .apply()
    }

    fun getLast(context: Context): Record? {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val number = prefs.getString(KEY_NUMBER, null) ?: return null
        val timestamp = prefs.getLong(KEY_TIMESTAMP, 0L)
        return Record(number = number, timestamp = timestamp)
    }

    fun clear(context: Context) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
