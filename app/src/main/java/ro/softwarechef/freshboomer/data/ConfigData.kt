package ro.softwarechef.freshboomer.data

import org.json.JSONArray
import org.json.JSONObject
import ro.softwarechef.freshboomer.models.QuickContact

data class ConfigData(
    // General
    val userNickname: String = "Mamaie",
    val themeMode: String = "SYSTEM",

    // TTS
    val ttsEnabled: Boolean = true,
    val ttsEngine: String = "PIPER_LILI",
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

    // Quick Contacts
    val quickContacts: List<QuickContact> = DEFAULT_QUICK_CONTACTS
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
        put("quick_contacts", JSONArray().apply {
            quickContacts.forEach { c ->
                put(JSONObject().apply {
                    put("name", c.name)
                    put("phone", c.phoneNumber)
                })
            }
        })
    }

    companion object {
        fun fromJson(json: JSONObject): ConfigData {
            val defaults = ConfigData()
            return ConfigData(
                userNickname = json.optString("user_nickname", defaults.userNickname),
                themeMode = json.optString("theme_mode", defaults.themeMode),
                ttsEnabled = json.optBoolean("tts_enabled", defaults.ttsEnabled),
                ttsEngine = json.optString("tts_engine", defaults.ttsEngine),
                ttsSpeechRate = json.optDouble("tts_speech_rate", defaults.ttsSpeechRate.toDouble()).toFloat(),
                featureQuickContacts = json.optBoolean("feature_quick_contacts", defaults.featureQuickContacts),
                featureDialPad = json.optBoolean("feature_dial_pad", defaults.featureDialPad),
                featureContacts = json.optBoolean("feature_contacts", defaults.featureContacts),
                featureMessages = json.optBoolean("feature_messages", defaults.featureMessages),
                featureGallery = json.optBoolean("feature_gallery", defaults.featureGallery),
                featureWhatsapp = json.optBoolean("feature_whatsapp", defaults.featureWhatsapp),
                appLanguage = json.optString("app_language", defaults.appLanguage),
                emergencyContacts = json.optJSONArray("emergency_contacts")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        EmergencyContact(
                            name = obj.optString("name", ""),
                            phoneNumber = obj.optString("phone", "")
                        )
                    }
                } ?: defaults.emergencyContacts,
                autoMaxVolume = json.optBoolean("auto_max_volume", defaults.autoMaxVolume),
                inactivityTimeoutMs = json.optLong("inactivity_timeout_ms", defaults.inactivityTimeoutMs),
                maxMissedCallAnnouncements = json.optInt("max_missed_call_announcements", defaults.maxMissedCallAnnouncements),
                callSpeakerDelayMs = json.optLong("call_speaker_delay_ms", defaults.callSpeakerDelayMs),
                inactivityMonitorEnabled = json.optBoolean("inactivity_monitor_enabled", defaults.inactivityMonitorEnabled),
                inactivityMonitorThresholdHours = json.optInt("inactivity_monitor_threshold_hours", defaults.inactivityMonitorThresholdHours),
                quickContacts = json.optJSONArray("quick_contacts")?.let { arr ->
                    (0 until arr.length()).map { i ->
                        val obj = arr.getJSONObject(i)
                        QuickContact(
                            id = (i + 1).toString(),
                            name = obj.optString("name", ""),
                            phoneNumber = obj.optString("phone", ""),
                            sortOrder = i
                        )
                    }
                } ?: defaults.quickContacts
            )
        }
    }
}

data class EmergencyContact(
    val name: String = "",
    val phoneNumber: String = ""
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
    ConfigFieldMeta("app_language", "app_language", "Limba aplicatiei", ConfigGroup.GENERAL, FieldType.ENUM, listOf("ro")),

    // TTS
    ConfigFieldMeta("tts_enabled", "tts_enabled", "Activeaza/dezactiveaza anunturile vocale", ConfigGroup.TTS, FieldType.BOOLEAN),
    ConfigFieldMeta("tts_engine", "tts_engine", "Motorul de voce folosit", ConfigGroup.TTS, FieldType.ENUM, listOf("PIPER_LILI", "DEVICE_DEFAULT")),
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
    ConfigFieldMeta("inactivity_monitor_threshold_hours", "inactivity_monitor_threshold_hours", "Ore de inactivitate dupa care se trimite alerta SMS. Implicit: 12", ConfigGroup.BEHAVIOR, FieldType.INT)
)
