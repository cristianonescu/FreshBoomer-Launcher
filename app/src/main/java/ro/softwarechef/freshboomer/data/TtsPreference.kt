package ro.softwarechef.freshboomer.data

import android.content.Context

enum class TtsEngine {
    PIPER_LILI,
    PIPER_SANDA,
    DEVICE_DEFAULT
}

object TtsPreference {
    fun isEnabled(context: Context): Boolean = AppConfig.current.ttsEnabled

    fun setEnabled(context: Context, enabled: Boolean) {
        AppConfig.save(context, AppConfig.current.copy(ttsEnabled = enabled))
    }

    fun getEngine(context: Context): TtsEngine {
        return try {
            TtsEngine.valueOf(AppConfig.current.ttsEngine)
        } catch (_: IllegalArgumentException) {
            TtsEngine.PIPER_SANDA
        }
    }

    fun setEngine(context: Context, engine: TtsEngine) {
        AppConfig.save(context, AppConfig.current.copy(ttsEngine = engine.name))
    }
}
