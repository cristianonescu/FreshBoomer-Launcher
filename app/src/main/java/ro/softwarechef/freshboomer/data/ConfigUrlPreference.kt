package ro.softwarechef.freshboomer.data

import android.content.Context

object ConfigUrlPreference {
    private const val PREFS_NAME = "LauncherPrefs"
    private const val KEY = "config_url"

    fun getUrl(context: Context): String {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY, "") ?: ""
    }

    fun setUrl(context: Context, url: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, url)
            .apply()
    }
}
