package ro.softwarechef.freshboomer.onboarding

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.app.role.RoleManager
import android.provider.Settings
import android.app.KeyguardManager
import android.telecom.TelecomManager
import androidx.core.content.ContextCompat

data class OnboardingState(
    val phonePermission: Boolean = false,
    val contactsPermission: Boolean = false,
    val callLogPermission: Boolean = false,
    val phoneStatePermission: Boolean = false,
    val smsPermissions: Boolean = false,
    val notificationPermission: Boolean = false,
    val mediaPermission: Boolean = false,
    val answerCallsPermission: Boolean = false,
    val audioPermission: Boolean = false,
    val notificationListenerAccess: Boolean = false,
    val dndAccess: Boolean = false,
    val isDefaultDialer: Boolean = false,
    val isDefaultSms: Boolean = false,
    val isDefaultLauncher: Boolean = false,
    val screenLockDisabled: Boolean = false,
) {
    val allGranted: Boolean
        get() = phonePermission && contactsPermission && callLogPermission &&
                phoneStatePermission && smsPermissions && notificationPermission &&
                mediaPermission && answerCallsPermission && audioPermission &&
                notificationListenerAccess && dndAccess &&
                isDefaultDialer && isDefaultSms && isDefaultLauncher &&
                screenLockDisabled
}

object OnboardingChecker {

    fun check(context: Context): OnboardingState {
        return OnboardingState(
            phonePermission = hasPermission(context, Manifest.permission.CALL_PHONE),
            contactsPermission = hasPermission(context, Manifest.permission.READ_CONTACTS),
            callLogPermission = hasPermission(context, Manifest.permission.READ_CALL_LOG),
            phoneStatePermission = hasPermission(context, Manifest.permission.READ_PHONE_STATE),
            smsPermissions = hasPermission(context, Manifest.permission.READ_SMS) &&
                    hasPermission(context, Manifest.permission.SEND_SMS) &&
                    hasPermission(context, Manifest.permission.RECEIVE_SMS),
            notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            } else true,
            mediaPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                hasPermission(context, Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                hasPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
            },
            answerCallsPermission = hasPermission(context, Manifest.permission.ANSWER_PHONE_CALLS),
            audioPermission = hasPermission(context, Manifest.permission.MODIFY_AUDIO_SETTINGS),
            notificationListenerAccess = isNotificationListenerEnabled(context),
            dndAccess = isDndAccessGranted(context),
            isDefaultDialer = isDefaultDialer(context),
            isDefaultSms = isDefaultSmsApp(context),
            isDefaultLauncher = isDefaultLauncher(context),
            screenLockDisabled = isScreenLockDisabled(context),
        )
    }

    private fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun isNotificationListenerEnabled(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            ?: return false
        val componentName = ComponentName(context, "ro.softwarechef.freshboomer.services.WhatsAppCallListenerService")
        return flat.contains(componentName.flattenToString())
    }

    private fun isDndAccessGranted(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        return nm.isNotificationPolicyAccessGranted
    }

    private fun isDefaultDialer(context: Context): Boolean {
        val tm = context.getSystemService(Context.TELECOM_SERVICE) as TelecomManager
        return tm.defaultDialerPackage == context.packageName
    }

    private fun isDefaultSmsApp(context: Context): Boolean {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        return roleManager.isRoleHeld(RoleManager.ROLE_SMS)
    }

    private fun isScreenLockDisabled(context: Context): Boolean {
        val keyguardManager = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        return !keyguardManager.isKeyguardSecure
    }

    private fun isDefaultLauncher(context: Context): Boolean {
        val pm = context.packageManager
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN).apply {
            addCategory(android.content.Intent.CATEGORY_HOME)
        }
        val resolveInfo = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo?.activityInfo?.packageName == context.packageName
    }
}
