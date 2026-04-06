package ro.softwarechef.freshboomer.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object AppConfig {
    private const val FILE_NAME = "config.json"
    private const val PREFS_NAME = "LauncherPrefs"
    private const val TAG = "AppConfig"
    private const val KEY_LAST_CONFIG_UPDATED_AT = "last_config_updated_at"
    private const val KEY_LAST_CONFIG_VERSION = "last_config_version"

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
        // Clear remote version tracking so the next remote fetch always applies.
        // Import methods call saveLastAppliedVersion() after save() to restore it.
        clearRemoteVersionTracking(context)
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
     * Skips the import if the remote config is not newer than what's already stored
     * (based on config_version and config_updated_at).
     * Decodes any base64-encoded contact photos into internal storage files.
     * Must be called from a background thread / coroutine.
     */
    fun importFromUrl(context: Context, url: String): Result<Unit> {
        return try {
            val text = URL(url).readText()
            val json = JSONObject(text)
            val config = ConfigData.fromJson(json)

            // Check if remote config is newer than what we last applied
            if (!isRemoteConfigNewer(context, config)) {
                Log.d(TAG, "Remote config is not newer, skipping import")
                return Result.success(Unit)
            }

            // Decode base64 photos into internal storage files
            val configWithPhotos = decodeBase64Photos(context, config)

            save(context, configWithPhotos)
            saveLastAppliedVersion(context, configWithPhotos)
            Log.d(TAG, "Imported config from URL: $url")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import config from URL: $url", e)
            Result.failure(e)
        }
    }

    /**
     * Force-imports config from a URL, ignoring version checks.
     * Used when the user manually triggers an import from Settings.
     */
    fun forceImportFromUrl(context: Context, url: String): Result<Unit> {
        return try {
            val text = URL(url).readText()
            val json = JSONObject(text)
            val config = ConfigData.fromJson(json)
            val configWithPhotos = decodeBase64Photos(context, config)
            save(context, configWithPhotos)
            saveLastAppliedVersion(context, configWithPhotos)
            Log.d(TAG, "Force-imported config from URL: $url")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to force-import config from URL: $url", e)
            Result.failure(e)
        }
    }

    private fun isRemoteConfigNewer(context: Context, remoteConfig: ConfigData): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        // First check config_version (integer, foolproof)
        val storedVersion = prefs.getInt(KEY_LAST_CONFIG_VERSION, 0)
        if (remoteConfig.configVersion > storedVersion) return true
        if (remoteConfig.configVersion < storedVersion) return false

        // Same version — check timestamp as tiebreaker
        val storedTimestamp = prefs.getString(KEY_LAST_CONFIG_UPDATED_AT, null)
        val remoteTimestamp = remoteConfig.configUpdatedAt

        if (remoteTimestamp.isNullOrEmpty()) return false
        if (storedTimestamp.isNullOrEmpty()) return true

        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US)
            fmt.timeZone = TimeZone.getTimeZone("UTC")
            val remoteDate = fmt.parse(remoteTimestamp)
            val storedDate = fmt.parse(storedTimestamp)
            if (remoteDate != null && storedDate != null) {
                remoteDate.after(storedDate)
            } else {
                true // If parsing fails, apply to be safe
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse config timestamps, applying config", e)
            true
        }
    }

    private fun saveLastAppliedVersion(context: Context, config: ConfigData) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putInt(KEY_LAST_CONFIG_VERSION, config.configVersion)
            .putString(KEY_LAST_CONFIG_UPDATED_AT, config.configUpdatedAt ?: "")
            .apply()
    }

    /**
     * Clears stored version info so the next remote fetch always applies.
     * Call this when the user makes local edits.
     */
    fun clearRemoteVersionTracking(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_LAST_CONFIG_VERSION)
            .remove(KEY_LAST_CONFIG_UPDATED_AT)
            .apply()
    }

    private fun decodeBase64Photos(context: Context, config: ConfigData): ConfigData {
        val updatedContacts = config.quickContacts.map { contact ->
            if (contact.photoBase64 != null) {
                val filePath = QuickContactRepository.saveBase64PhotoToInternal(
                    context, contact.photoBase64, contact.photoMime
                )
                if (filePath != null) {
                    // Set photoUri to the decoded file, clear base64 (not stored locally)
                    contact.copy(photoUri = filePath, photoBase64 = null, photoMime = null)
                } else {
                    contact.copy(photoBase64 = null, photoMime = null)
                }
            } else {
                contact
            }
        }
        return config.copy(quickContacts = updatedContacts)
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
        // Preserve photoUri/drawableResName from existing contacts when syncing,
        // but prefer the incoming photoUri if it was decoded from base64
        val existing = QuickContactRepository.getContacts(context).associateBy { it.name to it.phoneNumber }
        val merged = config.quickContacts.mapIndexed { index, c ->
            val match = existing[c.name to c.phoneNumber]
            c.copy(
                id = match?.id ?: (index + 1).toString(),
                photoUri = c.photoUri ?: match?.photoUri,
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
