package ro.softwarechef.freshboomer.onboarding

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ro.softwarechef.freshboomer.R
import ro.softwarechef.freshboomer.ui.composables.AccentGlowButton
import ro.softwarechef.freshboomer.ui.composables.GlassButton

@Composable
fun OnboardingScreen(
    state: OnboardingState,
    onAllGranted: () -> Unit,
    onStateChanged: () -> Unit
) {
    val context = LocalContext.current
    var showSmsGuideDialog by remember { mutableStateOf(false) }

    // Permission launchers
    val phonePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onStateChanged() }

    val contactsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onStateChanged() }

    val phoneStatePermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onStateChanged() }

    val smsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onStateChanged() }

    val notifPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onStateChanged() }

    val mediaPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onStateChanged() }

    val answerCallsPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onStateChanged() }

    val audioPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { onStateChanged() }

    // Activity result launchers for settings intents
    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { onStateChanged() }

    // SMS default app guide dialog
    if (showSmsGuideDialog) {
        AlertDialog(
            onDismissRequest = { showSmsGuideDialog = false },
            title = {
                Text(
                    stringResource(R.string.sms_default_dialog_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.sms_default_dialog_text),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Text(stringResource(R.string.sms_default_step1), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.sms_default_step2), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.sms_default_step3, stringResource(R.string.app_name)), style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(stringResource(R.string.sms_default_step4), style = MaterialTheme.typography.titleSmall)
                }
            },
            confirmButton = {
                AccentGlowButton(
                    onClick = {
                        showSmsGuideDialog = false
                        val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
                        settingsLauncher.launch(intent)
                    }
                ) {
                    Text(
                        stringResource(R.string.onboarding_open_settings),
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            },
            dismissButton = {
                GlassButton(onClick = { showSmsGuideDialog = false }) {
                    Text(
                        stringResource(R.string.cancel),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Header
        Text(
            text = stringResource(R.string.onboarding_header),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = stringResource(R.string.onboarding_description),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Progress indicator
        val totalSteps = 14
        val completedSteps = listOf(
            state.isDefaultDialer, state.isDefaultSms, state.isDefaultLauncher,
            state.phonePermission, state.contactsPermission,
            state.phoneStatePermission, state.smsPermissions, state.notificationPermission,
            state.mediaPermission, state.answerCallsPermission, state.audioPermission,
            state.notificationListenerAccess, state.dndAccess,
            state.screenLockDisabled
        ).count { it }

        LinearProgressIndicator(
            progress = { completedSteps.toFloat() / totalSteps },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = stringResource(R.string.onboarding_progress, completedSteps, totalSteps),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
        )

        // Steps list
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Section: Default Apps ──
            // Per Google Play policy: the default-handler role prompt MUST appear before
            // any SMS/Call Log/Phone runtime permission prompt. So this section comes first,
            // and the phone/SMS runtime permissions below are gated until the roles are held.
            SectionLabel(stringResource(R.string.onboarding_section_default_app))

            OnboardingStep(
                title = stringResource(R.string.default_phone_app),
                description = stringResource(R.string.default_phone_app_desc),
                icon = Icons.Default.Phone,
                granted = state.isDefaultDialer,
                buttonLabel = stringResource(R.string.onboarding_set),
                onRequest = {
                    val roleManager = context.getSystemService(android.app.role.RoleManager::class.java)
                    val intent = roleManager.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER)
                    settingsLauncher.launch(intent)
                }
            )

            OnboardingStep(
                title = stringResource(R.string.default_sms_app),
                description = stringResource(R.string.default_sms_app_desc),
                icon = Icons.Default.Email,
                granted = state.isDefaultSms,
                buttonLabel = stringResource(R.string.onboarding_set),
                onRequest = { showSmsGuideDialog = true }
            )

            OnboardingStep(
                title = stringResource(R.string.default_launcher),
                description = stringResource(R.string.default_launcher_desc),
                icon = Icons.Default.Home,
                granted = state.isDefaultLauncher,
                buttonLabel = stringResource(R.string.onboarding_set),
                onRequest = {
                    val intent = Intent(Settings.ACTION_HOME_SETTINGS)
                    settingsLauncher.launch(intent)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Section: Permissions ──
            SectionLabel(stringResource(R.string.onboarding_section_permissions))

            // Phone/SMS runtime permissions are gated behind the dialer/SMS roles above.
            // Once those roles are granted, several of these permissions are auto-granted
            // by Android and the cards will simply show as completed.
            val phoneRoleReady = state.isDefaultDialer
            val smsRoleReady = state.isDefaultSms

            OnboardingStep(
                title = stringResource(R.string.perm_phone_calls),
                description = stringResource(R.string.perm_phone_calls_desc),
                icon = Icons.Default.Call,
                granted = state.phonePermission,
                enabled = phoneRoleReady,
                onRequest = { phonePermLauncher.launch(Manifest.permission.CALL_PHONE) }
            )

            OnboardingStep(
                title = stringResource(R.string.perm_contacts),
                description = stringResource(R.string.perm_contacts_desc),
                icon = Icons.Default.Person,
                granted = state.contactsPermission,
                onRequest = { contactsPermLauncher.launch(Manifest.permission.READ_CONTACTS) }
            )

            OnboardingStep(
                title = stringResource(R.string.perm_phone_state),
                description = stringResource(R.string.perm_phone_state_desc),
                icon = Icons.Default.Phone,
                granted = state.phoneStatePermission,
                enabled = phoneRoleReady,
                onRequest = { phoneStatePermLauncher.launch(Manifest.permission.READ_PHONE_STATE) }
            )

            OnboardingStep(
                title = stringResource(R.string.perm_sms),
                description = stringResource(R.string.perm_sms_desc),
                icon = Icons.Default.Email,
                granted = state.smsPermissions,
                enabled = smsRoleReady,
                onRequest = {
                    smsPermLauncher.launch(arrayOf(
                        Manifest.permission.READ_SMS,
                        Manifest.permission.SEND_SMS,
                        Manifest.permission.RECEIVE_SMS
                    ))
                }
            )

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                OnboardingStep(
                    title = stringResource(R.string.perm_notifications),
                    description = stringResource(R.string.perm_notifications_desc),
                    icon = Icons.Default.Notifications,
                    granted = state.notificationPermission,
                    onRequest = { notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                )
            }

            OnboardingStep(
                title = stringResource(R.string.perm_photos),
                description = stringResource(R.string.perm_photos_desc),
                icon = Icons.Default.Face,
                granted = state.mediaPermission,
                onRequest = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        mediaPermLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                    } else {
                        mediaPermLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
            )

            OnboardingStep(
                title = stringResource(R.string.perm_answer_calls),
                description = stringResource(R.string.perm_answer_calls_desc),
                icon = Icons.Default.Call,
                granted = state.answerCallsPermission,
                enabled = phoneRoleReady,
                onRequest = { answerCallsPermLauncher.launch(Manifest.permission.ANSWER_PHONE_CALLS) }
            )

            OnboardingStep(
                title = stringResource(R.string.perm_audio),
                description = stringResource(R.string.perm_audio_desc),
                icon = Icons.Default.PlayArrow,
                granted = state.audioPermission,
                onRequest = { audioPermLauncher.launch(Manifest.permission.MODIFY_AUDIO_SETTINGS) }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Section: Special Access ──
            SectionLabel(stringResource(R.string.onboarding_section_special_access))

            OnboardingStep(
                title = stringResource(R.string.special_notification_access),
                description = stringResource(R.string.special_notification_access_desc),
                icon = Icons.Default.Notifications,
                granted = state.notificationListenerAccess,
                buttonLabel = stringResource(R.string.onboarding_open_settings),
                onRequest = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                    settingsLauncher.launch(intent)
                }
            )

            OnboardingStep(
                title = stringResource(R.string.special_dnd),
                description = stringResource(R.string.special_dnd_desc),
                icon = Icons.Default.Settings,
                granted = state.dndAccess,
                buttonLabel = stringResource(R.string.onboarding_open_settings),
                onRequest = {
                    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    settingsLauncher.launch(intent)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            // ── Section: Screen Lock ──
            SectionLabel(stringResource(R.string.onboarding_section_lock_screen))

            OnboardingStep(
                title = stringResource(R.string.lock_screen_disable),
                description = stringResource(R.string.lock_screen_disable_desc),
                icon = Icons.Default.Lock,
                granted = state.screenLockDisabled,
                buttonLabel = stringResource(R.string.onboarding_open_settings),
                onRequest = {
                    val intent = Intent(Settings.ACTION_SECURITY_SETTINGS)
                    settingsLauncher.launch(intent)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Bottom button
        if (state.allGranted) {
            AccentGlowButton(
                onClick = onAllGranted,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    stringResource(R.string.onboarding_continue),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun OnboardingStep(
    title: String,
    description: String,
    icon: ImageVector,
    granted: Boolean,
    buttonLabel: String = stringResource(R.string.onboarding_allow),
    enabled: Boolean = true,
    onRequest: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (granted)
                MaterialTheme.colorScheme.background
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (granted) null else CardDefaults.outlinedCardBorder(),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (granted) 0.dp else 1.dp
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (granted) Icons.Default.CheckCircle else icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (granted) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (granted)
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            if (granted) {
                Text(
                    text = stringResource(R.string.enabled),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF4CAF50),
                    fontWeight = FontWeight.SemiBold
                )
            } else {
                AccentGlowButton(
                    onClick = onRequest,
                    enabled = enabled,
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        buttonLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}
