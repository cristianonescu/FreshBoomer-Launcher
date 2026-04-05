package ro.softwarechef.freshboomer.data

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import org.json.JSONObject
import java.io.File
import java.util.Locale

object LocaleHelper {

    /**
     * Wraps the base context with the locale from the persisted config.
     * Call from Activity.attachBaseContext().
     *
     * This runs BEFORE onCreate/AppConfig.init(), so we read the language
     * directly from the config JSON file rather than from AppConfig.current.
     */
    fun wrap(context: Context): Context {
        val lang = readLanguageFromDisk(context)
        val locale = Locale(lang)
        Locale.setDefault(locale)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * Read app_language directly from config.json on disk.
     * Falls back to "ro" if the file doesn't exist or can't be parsed.
     */
    private fun readLanguageFromDisk(context: Context): String {
        return try {
            val file = File(context.filesDir, "config.json")
            if (file.exists()) {
                val json = JSONObject(file.readText())
                json.optString("app_language", "ro")
            } else {
                "ro"
            }
        } catch (_: Exception) {
            "ro"
        }
    }

    /**
     * Returns the Locale matching the current app language setting.
     */
    fun getLocale(): Locale {
        return when (AppConfig.current.appLanguage) {
            "en" -> Locale("en")
            else -> Locale("ro")
        }
    }

    /**
     * Apply a new language and recreate the activity so the UI updates.
     */
    fun applyLanguage(activity: Activity, lang: String) {
        AppConfig.save(activity, AppConfig.current.copy(appLanguage = lang))
        activity.recreate()
    }
}
