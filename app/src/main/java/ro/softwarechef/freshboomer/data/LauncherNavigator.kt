package ro.softwarechef.freshboomer.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import ro.softwarechef.freshboomer.ContactsActivity
import ro.softwarechef.freshboomer.GalleryActivity
import ro.softwarechef.freshboomer.InCallActivity
import ro.softwarechef.freshboomer.IncomingCallActivity
import ro.softwarechef.freshboomer.MainActivity
import ro.softwarechef.freshboomer.MedicationAlertActivity
import ro.softwarechef.freshboomer.PhoneActivity
import ro.softwarechef.freshboomer.SmsActivity
import ro.softwarechef.freshboomer.TtsSmsAlertActivity
import ro.softwarechef.freshboomer.WhatsAppCallActivity

/**
 * Single source of truth for in-app navigation. Every "go to screen X" or
 * "return home" goes through here so flags, finish() semantics, and timeout
 * behavior stay consistent across activities.
 *
 * - [go] is for Activity → Activity in-app navigation.
 * - [launch] is for receivers/services launching activities from a non-Activity
 *   context (adds NEW_TASK, accepts extras).
 * - [intentFor] returns a configured [Intent] for use with [PendingIntent].
 */
object LauncherNavigator {

    enum class Screen {
        HOME, PHONE, SMS, CONTACTS, GALLERY, IN_CALL,
        INCOMING_CALL, WHATSAPP_CALL, MEDICATION_ALERT, TTS_SMS_ALERT
    }

    fun go(from: Activity, screen: Screen, finishCaller: Boolean = false) {
        if (screen == Screen.HOME && from is MainActivity) return
        val intent = Intent(from, targetClass(screen)).apply {
            if (screen == Screen.HOME) {
                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        }
        from.startActivity(intent)
        if (finishCaller || screen == Screen.HOME) from.finish()
    }

    fun launch(
        context: Context,
        screen: Screen,
        excludeFromRecents: Boolean = false,
        configure: (Intent.() -> Unit)? = null
    ) {
        context.startActivity(intentFor(context, screen, excludeFromRecents, configure))
    }

    fun intentFor(
        context: Context,
        screen: Screen,
        excludeFromRecents: Boolean = false,
        configure: (Intent.() -> Unit)? = null
    ): Intent = Intent(context, targetClass(screen)).apply {
        var f = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        if (excludeFromRecents) f = f or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
        flags = f
        configure?.invoke(this)
    }

    private fun targetClass(screen: Screen): Class<out Activity> = when (screen) {
        Screen.HOME             -> MainActivity::class.java
        Screen.PHONE            -> PhoneActivity::class.java
        Screen.SMS              -> SmsActivity::class.java
        Screen.CONTACTS         -> ContactsActivity::class.java
        Screen.GALLERY          -> GalleryActivity::class.java
        Screen.IN_CALL          -> InCallActivity::class.java
        Screen.INCOMING_CALL    -> IncomingCallActivity::class.java
        Screen.WHATSAPP_CALL    -> WhatsAppCallActivity::class.java
        Screen.MEDICATION_ALERT -> MedicationAlertActivity::class.java
        Screen.TTS_SMS_ALERT    -> TtsSmsAlertActivity::class.java
    }
}
