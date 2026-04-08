package ro.softwarechef.freshboomer.data

import org.json.JSONArray
import org.json.JSONObject
import ro.softwarechef.freshboomer.models.QuickContact
import java.util.UUID

data class ConfigData(
    // General
    val userNickname: String = "Mamaie",
    val themeMode: String = "SYSTEM",

    // TTS
    val ttsEnabled: Boolean = true,
    val ttsEngine: String = "PIPER_SANDA",
    val ttsSpeechRate: Float = 0.85f,

    // Features
    val featureQuickContacts: Boolean = true,
    val featureDialPad: Boolean = true,
    val featureContacts: Boolean = true,
    val featureMessages: Boolean = true,
    val featureGallery: Boolean = true,
    val featureWhatsapp: Boolean = false,

    // Language
    val appLanguage: String = "ro",

    // Emergency Contacts
    val emergencyContacts: List<EmergencyContact> = emptyList(),

    // Behavior
    val autoMaxVolume: Boolean = true,
    val inactivityTimeoutMs: Long = 20000,
    val maxMissedCallAnnouncements: Int = 3,
    val callSpeakerDelayMs: Long = 3000,

    // Inactivity Monitor
    val inactivityMonitorEnabled: Boolean = false,
    val inactivityMonitorThresholdHours: Int = 12,

    // Medication Reminders
    val featureMedicationReminders: Boolean = false,
    val medicationReminders: List<MedicationReminder> = emptyList(),

    // Remote TTS SMS
    val featureTtsSms: Boolean = false,
    val ttsSmsPrefix: String = "CITESTE:",
    val featureTtsSmsTrustedOnly: Boolean = true,

    // Quick Contacts
    val quickContacts: List<QuickContact> = DEFAULT_QUICK_CONTACTS,

    // Config versioning (for remote config change detection)
    val configUpdatedAt: String? = null,
    val configVersion: Int = 0
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("user_nickname", userNickname)
        put("theme_mode", themeMode)
        put("tts_enabled", ttsEnabled)
        put("tts_engine", ttsEngine)
        put("tts_speech_rate", ttsSpeechRate.toDouble())
        put("feature_quick_contacts", featureQuickContacts)
        put("feature_dial_pad", featureDialPad)
        put("feature_contacts", featureContacts)
        put("feature_messages", featureMessages)
        put("feature_gallery", featureGallery)
        put("feature_whatsapp", featureWhatsapp)
        put("app_language", appLanguage)
        put("emergency_contacts", JSONArray().apply {
            emergencyContacts.forEach { c ->
                put(JSONObject().apply {
                    put("name", c.name)
                    put("phone", c.phoneNumber)
                })
            }
        })
        put("auto_max_volume", autoMaxVolume)
        put("inactivity_timeout_ms", inactivityTimeoutMs)
        put("max_missed_call_announcements", maxMissedCallAnnouncements)
        put("call_speaker_delay_ms", callSpeakerDelayMs)
        put("inactivity_monitor_enabled", inactivityMonitorEnabled)
        put("inactivity_monitor_threshold_hours", inactivityMonitorThresholdHours)
        put("feature_medication_reminders", featureMedicationReminders)
        put("medication_reminders", JSONArray().apply {
            medicationReminders.forEach { r ->
                put(JSONObject().apply {
                    put("id", r.id)
                    put("name", r.name)
                    put("time", r.time)
                    put("days_of_week", JSONArray(r.daysOfWeek))
                    put("enabled", r.enabled)
                    put("snooze_duration_minutes", r.snoozeDurationMinutes)
                })
            }
        })
        put("feature_tts_sms", featureTtsSms)
        put("tts_sms_prefix", ttsSmsPrefix)
        put("feature_tts_sms_trusted_only", featureTtsSmsTrustedOnly)
        put("quick_contacts", JSONArray().apply {
            quickContacts.forEach { c ->
                put(JSONObject().apply {
                    put("name", c.name)
                    put("phone", c.phoneNumber)
                    if (c.photoBase64 != null) put("photo_base64", c.photoBase64)
                    if (c.photoMime != null) put("photo_mime", c.photoMime)
                })
            }
        })
        if (configUpdatedAt != null) put("config_updated_at", configUpdatedAt)
        put("config_version", configVersion)
    }

    companion object {
        // Helper: only use JSON value if the key is present, otherwise keep fallback
        private fun JSONObject.boolOrFallback(key: String, fallback: Boolean): Boolean =
            if (has(key)) optBoolean(key) else fallback
        private fun JSONObject.stringOrFallback(key: String, fallback: String): String =
            if (has(key)) optString(key) else fallback
        private fun JSONObject.intOrFallback(key: String, fallback: Int): Int =
            if (has(key)) optInt(key) else fallback
        private fun JSONObject.longOrFallback(key: String, fallback: Long): Long =
            if (has(key)) optLong(key) else fallback
        private fun JSONObject.doubleOrFallback(key: String, fallback: Double): Double =
            if (has(key)) optDouble(key) else fallback

        fun fromJson(json: JSONObject, fallback: ConfigData = ConfigData()): ConfigData {
            val d = fallback
            return ConfigData(
                userNickname = json.stringOrFallback("user_nickname", d.userNickname),
                themeMode = json.stringOrFallback("theme_mode", d.themeMode),
                ttsEnabled = json.boolOrFallback("tts_enabled", d.ttsEnabled),
                ttsEngine = json.stringOrFallback("tts_engine", d.ttsEngine),
                ttsSpeechRate = json.doubleOrFallback("tts_speech_rate", d.ttsSpeechRate.toDouble()).toFloat(),
                featureQuickContacts = json.boolOrFallback("feature_quick_contacts", d.featureQuickContacts),
                featureDialPad = json.boolOrFallback("feature_dial_pad", d.featureDialPad),
                featureContacts = json.boolOrFallback("feature_contacts", d.featureContacts),
                featureMessages = json.boolOrFallback("feature_messages", d.featureMessages),
                featureGallery = json.boolOrFallback("feature_gallery", d.featureGallery),
                featureWhatsapp = json.boolOrFallback("feature_whatsapp", d.featureWhatsapp),
                appLanguage = json.stringOrFallback("app_language", d.appLanguage),
                emergencyContacts = if (json.has("emergency_contacts")) {
                    json.optJSONArray("emergency_contacts")?.let { arr ->
                        (0 until arr.length()).map { i ->
                            val obj = arr.getJSONObject(i)
                            EmergencyContact(
                                name = obj.optString("name", ""),
                                phoneNumber = obj.optString("phone", "")
                            )
                        }
                    } ?: d.emergencyContacts
                } else d.emergencyContacts,
                autoMaxVolume = json.boolOrFallback("auto_max_volume", d.autoMaxVolume),
                inactivityTimeoutMs = json.longOrFallback("inactivity_timeout_ms", d.inactivityTimeoutMs),
                maxMissedCallAnnouncements = json.intOrFallback("max_missed_call_announcements", d.maxMissedCallAnnouncements),
                callSpeakerDelayMs = json.longOrFallback("call_speaker_delay_ms", d.callSpeakerDelayMs),
                inactivityMonitorEnabled = json.boolOrFallback("inactivity_monitor_enabled", d.inactivityMonitorEnabled),
                inactivityMonitorThresholdHours = json.intOrFallback("inactivity_monitor_threshold_hours", d.inactivityMonitorThresholdHours),
                featureMedicationReminders = json.boolOrFallback("feature_medication_reminders", d.featureMedicationReminders),
                medicationReminders = if (json.has("medication_reminders")) {
                    json.optJSONArray("medication_reminders")?.let { arr ->
                        (0 until arr.length()).map { i ->
                            val obj = arr.getJSONObject(i)
                            MedicationReminder(
                                id = obj.optString("id", UUID.randomUUID().toString()),
                                name = obj.optString("name", ""),
                                time = obj.optString("time", "08:00"),
                                daysOfWeek = obj.optJSONArray("days_of_week")?.let { da ->
                                    (0 until da.length()).map { da.getInt(it) }
                                } ?: listOf(1, 2, 3, 4, 5, 6, 7),
                                enabled = obj.optBoolean("enabled", true),
                                snoozeDurationMinutes = obj.optInt("snooze_duration_minutes", 5)
                            )
                        }
                    } ?: d.medicationReminders
                } else d.medicationReminders,
                featureTtsSms = json.boolOrFallback("feature_tts_sms", d.featureTtsSms),
                ttsSmsPrefix = json.stringOrFallback("tts_sms_prefix", d.ttsSmsPrefix),
                featureTtsSmsTrustedOnly = json.boolOrFallback("feature_tts_sms_trusted_only", d.featureTtsSmsTrustedOnly),
                quickContacts = if (json.has("quick_contacts")) {
                    json.optJSONArray("quick_contacts")?.let { arr ->
                        (0 until arr.length()).map { i ->
                            val obj = arr.getJSONObject(i)
                            QuickContact(
                                id = (i + 1).toString(),
                                name = obj.optString("name", ""),
                                phoneNumber = obj.optString("phone", ""),
                                sortOrder = i,
                                photoBase64 = obj.optString("photo_base64", "").takeIf { it.isNotEmpty() },
                                photoMime = obj.optString("photo_mime", "").takeIf { it.isNotEmpty() }
                            )
                        }
                    } ?: d.quickContacts
                } else d.quickContacts,
                configUpdatedAt = if (json.has("config_updated_at")) json.optString("config_updated_at", "").takeIf { it.isNotEmpty() } else d.configUpdatedAt,
                configVersion = json.intOrFallback("config_version", d.configVersion)
            )
        }
    }
}

data class EmergencyContact(
    val name: String = "",
    val phoneNumber: String = ""
)

data class MedicationReminder(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val time: String = "08:00",
    val daysOfWeek: List<Int> = listOf(1, 2, 3, 4, 5, 6, 7),
    val enabled: Boolean = true,
    val snoozeDurationMinutes: Int = 5
)

val DEFAULT_QUICK_CONTACTS = emptyList<QuickContact>()

enum class ConfigGroup(val displayName: String) {
    GENERAL("General"),
    TTS("Text-to-Speech"),
    FEATURES("Functionalitati"),
    BEHAVIOR("Comportament")
}

enum class FieldType { BOOLEAN, STRING, INT, LONG, FLOAT, ENUM }

data class ConfigFieldMeta(
    val jsonKey: String,
    val label: String,
    val tooltip: String,
    val group: ConfigGroup,
    val type: FieldType,
    val options: List<String>? = null
)

val ALL_CONFIG_FIELDS: List<ConfigFieldMeta> = listOf(
    // General
    ConfigFieldMeta("user_nickname", "user_nickname", "Numele folosit in anunturile vocale (inlocuieste 'Mamaie')", ConfigGroup.GENERAL, FieldType.STRING),
    ConfigFieldMeta("theme_mode", "theme_mode", "Tema aplicatiei", ConfigGroup.GENERAL, FieldType.ENUM, listOf("SYSTEM", "LIGHT", "DARK")),
    ConfigFieldMeta("app_language", "app_language", "Limba aplicatiei", ConfigGroup.GENERAL, FieldType.ENUM, listOf("ro", "en")),

    // TTS
    ConfigFieldMeta("tts_enabled", "tts_enabled", "Activeaza/dezactiveaza anunturile vocale", ConfigGroup.TTS, FieldType.BOOLEAN),
    ConfigFieldMeta("tts_engine", "tts_engine", "Motorul de voce folosit", ConfigGroup.TTS, FieldType.ENUM, listOf("PIPER_SANDA", "PIPER_LILI", "DEVICE_DEFAULT")),
    ConfigFieldMeta("tts_speech_rate", "tts_speech_rate", "Viteza vorbirii: 0.5 (lent) - 2.0 (rapid). Implicit: 0.85", ConfigGroup.TTS, FieldType.FLOAT),

    // Features
    ConfigFieldMeta("feature_quick_contacts", "feature_quick_contacts", "Arata butoanele de apel rapid pe ecranul principal", ConfigGroup.FEATURES, FieldType.BOOLEAN),
    ConfigFieldMeta("feature_dial_pad", "feature_dial_pad", "Arata tastatura de apel", ConfigGroup.FEATURES, FieldType.BOOLEAN),
    ConfigFieldMeta("feature_contacts", "feature_contacts", "Arata agenda de contacte", ConfigGroup.FEATURES, FieldType.BOOLEAN),
    ConfigFieldMeta("feature_messages", "feature_messages", "Arata mesajele SMS", ConfigGroup.FEATURES, FieldType.BOOLEAN),
    ConfigFieldMeta("feature_gallery", "feature_gallery", "Arata galeria foto", ConfigGroup.FEATURES, FieldType.BOOLEAN),
    ConfigFieldMeta("feature_whatsapp", "feature_whatsapp", "Arata butonul WhatsApp pe ecranul principal", ConfigGroup.FEATURES, FieldType.BOOLEAN),

    // Behavior
    ConfigFieldMeta("auto_max_volume", "auto_max_volume", "Seteaza automat toate volumele la maxim pentru a preveni situatiile in care utilizatorul reduce volumul accidental", ConfigGroup.BEHAVIOR, FieldType.BOOLEAN),
    ConfigFieldMeta("inactivity_timeout_ms", "inactivity_timeout_ms", "Milisecunde pana la revenirea automata pe ecranul principal. Implicit: 20000", ConfigGroup.BEHAVIOR, FieldType.LONG),
    ConfigFieldMeta("max_missed_call_announcements", "max_missed_call_announcements", "De cate ori se anunta un apel pierdut. Implicit: 3", ConfigGroup.BEHAVIOR, FieldType.INT),
    ConfigFieldMeta("call_speaker_delay_ms", "call_speaker_delay_ms", "Intarziere (ms) inainte de activarea difuzorului. Implicit: 3000", ConfigGroup.BEHAVIOR, FieldType.LONG),
    ConfigFieldMeta("inactivity_monitor_enabled", "inactivity_monitor_enabled", "Trimite SMS contactelor de urgenta daca utilizatorul nu interactioneaza cu telefonul pentru o perioada lunga", ConfigGroup.BEHAVIOR, FieldType.BOOLEAN),
    ConfigFieldMeta("inactivity_monitor_threshold_hours", "inactivity_monitor_threshold_hours", "Ore de inactivitate dupa care se trimite alerta SMS. Implicit: 12", ConfigGroup.BEHAVIOR, FieldType.INT),

    // Medication Reminders
    ConfigFieldMeta("feature_medication_reminders", "feature_medication_reminders", "Activeaza memento-urile pentru medicamente", ConfigGroup.FEATURES, FieldType.BOOLEAN),

    // Remote TTS SMS
    ConfigFieldMeta("feature_tts_sms", "feature_tts_sms", "Citeste cu voce tare mesajele SMS care incep cu un prefix special", ConfigGroup.FEATURES, FieldType.BOOLEAN),
    ConfigFieldMeta("tts_sms_prefix", "tts_sms_prefix", "Prefixul care declanseaza citirea cu voce tare. Implicit: CITESTE:", ConfigGroup.TTS, FieldType.STRING),
    ConfigFieldMeta("feature_tts_sms_trusted_only", "feature_tts_sms_trusted_only", "Doar mesajele de la contactele de urgenta sunt citite cu voce tare", ConfigGroup.FEATURES, FieldType.BOOLEAN)
)
