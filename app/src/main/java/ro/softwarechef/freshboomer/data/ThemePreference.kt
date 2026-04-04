package ro.softwarechef.freshboomer.data

import android.content.Context

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

object ThemePreference {
    fun getThemeMode(context: Context): AppThemeMode {
        return try {
            AppThemeMode.valueOf(AppConfig.current.themeMode)
        } catch (_: IllegalArgumentException) {
            AppThemeMode.SYSTEM
        }
    }

    fun setThemeMode(context: Context, mode: AppThemeMode) {
        AppConfig.save(context, AppConfig.current.copy(themeMode = mode.name))
    }
}
