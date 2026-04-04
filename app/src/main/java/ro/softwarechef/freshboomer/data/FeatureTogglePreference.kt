package ro.softwarechef.freshboomer.data

import android.content.Context

data class FeatureToggles(
    val quickContacts: Boolean = true,
    val dialPad: Boolean = true,
    val contacts: Boolean = true,
    val messages: Boolean = true,
    val gallery: Boolean = true,
    val whatsapp: Boolean = false,
    val autoMaxVolume: Boolean = true,
    val inactivityMonitor: Boolean = false
)

object FeatureTogglePreference {
    const val QUICK_CONTACTS = "feature_quick_contacts"
    const val DIAL_PAD = "feature_dial_pad"
    const val CONTACTS = "feature_contacts"
    const val MESSAGES = "feature_messages"
    const val GALLERY = "feature_gallery"
    const val WHATSAPP = "feature_whatsapp"
    const val AUTO_MAX_VOLUME = "auto_max_volume"
    const val INACTIVITY_MONITOR = "inactivity_monitor_enabled"

    fun getToggles(context: Context): FeatureToggles {
        val config = AppConfig.current
        return FeatureToggles(
            quickContacts = config.featureQuickContacts,
            dialPad = config.featureDialPad,
            contacts = config.featureContacts,
            messages = config.featureMessages,
            gallery = config.featureGallery,
            whatsapp = config.featureWhatsapp,
            autoMaxVolume = config.autoMaxVolume,
            inactivityMonitor = config.inactivityMonitorEnabled
        )
    }

    fun setToggle(context: Context, key: String, enabled: Boolean) {
        val config = AppConfig.current
        val updated = when (key) {
            QUICK_CONTACTS -> config.copy(featureQuickContacts = enabled)
            DIAL_PAD -> config.copy(featureDialPad = enabled)
            CONTACTS -> config.copy(featureContacts = enabled)
            MESSAGES -> config.copy(featureMessages = enabled)
            GALLERY -> config.copy(featureGallery = enabled)
            WHATSAPP -> config.copy(featureWhatsapp = enabled)
            AUTO_MAX_VOLUME -> config.copy(autoMaxVolume = enabled)
            INACTIVITY_MONITOR -> config.copy(inactivityMonitorEnabled = enabled)
            else -> return
        }
        AppConfig.save(context, updated)
    }
}
