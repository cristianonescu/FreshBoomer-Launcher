package ro.softwarechef.freshboomer.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.net.URL

object AppConfig {
    private const val FILE_NAME = "config.json"
    private const val PREFS_NAME = "LauncherPrefs"
    private const val TAG = "AppConfig"

    private val _configFlow = MutableStateFlow(ConfigData())
    val configFlow: StateFlow<ConfigData> = _configFlow.asStateFlow()

    val current: ConfigData get() = _configFlow.value

    @Synchronized
    fun init(context: Context) {
        val file = File(context.filesDir, FILE_NAME)
        if (file.exists()) {
            try {
                val json = JSONObject(file.readText())
                _configFlow.value = ConfigData.fromJson(json)
                Log.d(TAG, "Loaded config from $FILE_NAME")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load config, using defaults", e)
                _configFlow.value = ConfigData()
                writeFile(context, _configFlow.value)
            }
        } else {
            // First launch with new system — migrate from SharedPreferences
            val config = migrateFromSharedPrefs(context)
            _configFlow.value = config
            writeFile(context, config)
            Log.d(TAG, "Migrated SharedPreferences to $FILE_NAME")
        }
        syncToSharedPrefs(context, _configFlow.value)
    }

    @Synchronized
    fun save(context: Context, config: ConfigData) {
        _configFlow.value = config
        writeFile(context, config)
        syncToSharedPrefs(context, config)
        syncContactsToRepo(context, config)
    }

    @Synchronized
    fun resetToDefaults(context: Context) {
        val defaults = ConfigData()
        _configFlow.value = defaults
        writeFile(context, defaults)
        syncToSharedPrefs(context, defaults)
        syncContactsToRepo(context, defaults)
    }

    /**
     * Fetches config JSON from a URL and applies it.
     * Must be called from a background thread / coroutine.
     */
    fun importFromUrl(context: Context, url: String): Result<Unit> {
        return try {
            val text = URL(url).readText()
            val json = JSONObject(text)
            val config = ConfigData.fromJson(json)
            save(context, config)
            Log.d(TAG, "Imported config from URL: $url")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import config from URL: $url", e)
            Result.failure(e)
        }
    }

    private fun writeFile(context: Context, config: ConfigData) {
        try {
            val file = File(context.filesDir, FILE_NAME)
            file.writeText(config.toJson().toString(2))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write config file", e)
        }
    }

    private fun migrateFromSharedPrefs(context: Context): ConfigData {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val defaults = ConfigData()
        return ConfigData(
            userNickname = prefs.getString("user_nickname", defaults.userNickname) ?: defaults.userNickname,
            themeMode = prefs.getString("theme_mode", defaults.themeMode) ?: defaults.themeMode,
            ttsEnabled = prefs.getBoolean("tts_enabled", defaults.ttsEnabled),
            ttsEngine = prefs.getString("tts_engine", defaults.ttsEngine) ?: defaults.ttsEngine,
            ttsSpeechRate = defaults.ttsSpeechRate,
            featureQuickContacts = prefs.getBoolean("feature_quick_contacts", defaults.featureQuickContacts),
            featureDialPad = prefs.getBoolean("feature_dial_pad", defaults.featureDialPad),
            featureContacts = prefs.getBoolean("feature_contacts", defaults.featureContacts),
            featureMessages = prefs.getBoolean("feature_messages", defaults.featureMessages),
            featureGallery = prefs.getBoolean("feature_gallery", defaults.featureGallery),
            featureWhatsapp = prefs.getBoolean("feature_whatsapp", defaults.featureWhatsapp),
            appLanguage = prefs.getString("app_language", defaults.appLanguage) ?: defaults.appLanguage,
            inactivityTimeoutMs = defaults.inactivityTimeoutMs,
            maxMissedCallAnnouncements = defaults.maxMissedCallAnnouncements,
            callSpeakerDelayMs = defaults.callSpeakerDelayMs,
            quickContacts = QuickContactRepository.getContacts(context).ifEmpty { defaults.quickContacts }
        )
    }

    private fun syncContactsToRepo(context: Context, config: ConfigData) {
        // Preserve photoUri/drawableResName from existing contacts when syncing
        val existing = QuickContactRepository.getContacts(context).associateBy { it.name to it.phoneNumber }
        val merged = config.quickContacts.mapIndexed { index, c ->
            val match = existing[c.name to c.phoneNumber]
            c.copy(
                id = match?.id ?: (index + 1).toString(),
                photoUri = match?.photoUri ?: c.photoUri,
                drawableResName = match?.drawableResName ?: c.drawableResName,
                sortOrder = index
            )
        }
        QuickContactRepository.saveContacts(context, merged)
    }

    private fun syncToSharedPrefs(context: Context, config: ConfigData) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString("user_nickname", config.userNickname)
            .putString("theme_mode", config.themeMode)
            .putBoolean("tts_enabled", config.ttsEnabled)
            .putString("tts_engine", config.ttsEngine)
            .putBoolean("feature_quick_contacts", config.featureQuickContacts)
            .putBoolean("feature_dial_pad", config.featureDialPad)
            .putBoolean("feature_contacts", config.featureContacts)
            .putBoolean("feature_messages", config.featureMessages)
            .putBoolean("feature_gallery", config.featureGallery)
            .putBoolean("feature_whatsapp", config.featureWhatsapp)
            .putBoolean("auto_max_volume", config.autoMaxVolume)
            .putString("app_language", config.appLanguage)
            .apply()
    }
}
