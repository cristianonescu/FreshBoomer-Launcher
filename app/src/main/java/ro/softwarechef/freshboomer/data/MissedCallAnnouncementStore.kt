package ro.softwarechef.freshboomer.data

import android.content.Context

/**
 * Persisted state for [MissedCallAnnouncer] — which caller was last announced
 * and how many times. Mirrors the [MissedCallStore] pattern of colocating
 * persistence with the logic it serves, keeping `ImmersiveActivity` free of
 * inline SharedPreferences access.
 */
object MissedCallAnnouncementStore {

    private const val PREFS_NAME = "LauncherPrefs"
    private const val KEY_LAST_ANNOUNCED_NUMBER = "last_announced_number"
    private const val KEY_ANNOUNCEMENT_COUNT = "announcement_count"

    data class State(val lastAnnouncedNumber: String?, val count: Int)

    fun read(context: Context): State {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return State(
            lastAnnouncedNumber = prefs.getString(KEY_LAST_ANNOUNCED_NUMBER, null),
            count = prefs.getInt(KEY_ANNOUNCEMENT_COUNT, 0)
        )
    }

    fun write(context: Context, lastAnnouncedNumber: String?, count: Int) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_LAST_ANNOUNCED_NUMBER, lastAnnouncedNumber)
            .putInt(KEY_ANNOUNCEMENT_COUNT, count)
            .apply()
    }
}
