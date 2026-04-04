package ro.softwarechef.freshboomer.data

import android.content.Context

enum class TtsEngine {
    PIPER_LILI,
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
            TtsEngine.PIPER_LILI
        }
    }

    fun setEngine(context: Context, engine: TtsEngine) {
        AppConfig.save(context, AppConfig.current.copy(ttsEngine = engine.name))
    }
}
