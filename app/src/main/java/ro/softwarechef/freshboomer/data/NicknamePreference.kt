package ro.softwarechef.freshboomer.data

import android.content.Context

object NicknamePreference {
    private const val DEFAULT_NICKNAME = "Mamaie"

    fun getNickname(context: Context): String {
        val value = AppConfig.current.userNickname
        return if (value.isBlank()) DEFAULT_NICKNAME else value
    }

    fun setNickname(context: Context, nickname: String) {
        AppConfig.save(context, AppConfig.current.copy(userNickname = nickname.trim()))
    }
}
