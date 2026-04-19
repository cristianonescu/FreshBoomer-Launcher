package ro.softwarechef.freshboomer.onboarding

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ro.softwarechef.freshboomer.R
import ro.softwarechef.freshboomer.data.*
import ro.softwarechef.freshboomer.models.QuickContact
import ro.softwarechef.freshboomer.services.InactivityMonitorWorker
import ro.softwarechef.freshboomer.ui.composables.AccentGlowButton
import ro.softwarechef.freshboomer.ui.composables.GlassButton
import java.io.File
import java.util.UUID

private const val PREFS_NAME = "LauncherPrefs"
private const val KEY_SETUP_COMPLETED = "setup_wizard_completed"

object SetupWizardPreference {
    fun isCompleted(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_SETUP_COMPLETED, false)
    }

    fun setCompleted(context: Context, completed: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_SETUP_COMPLETED, completed)
            .apply()
    }
}

private const val TOTAL_PAGES = 6

@Composable
fun SetupWizardScreen(
    onThemeChanged: () -> Unit = {},
    onComplete: () -> Unit
) {
    val baseContext = LocalContext.current
    var currentPage by remember { mutableIntStateOf(0) }

    // Wizard state
    var nickname by remember { mutableStateOf(NicknamePreference.getNickname(baseContext)) }
    var quickContacts by remember { mutableStateOf(listOf<QuickContact>()) }
    var featureToggles by remember { mutableStateOf(FeatureTogglePreference.getToggles(baseContext)) }
    var themeMode by remember { mutableStateOf(ThemePreference.getThemeMode(baseContext)) }
    var appLanguage by remember { mutableStateOf(AppConfig.current.appLanguage) }
    var emergencyContacts by remember { mutableStateOf(AppConfig.current.emergencyContacts) }
    var privacyAccepted by remember { mutableStateOf(false) }
    var inactivityThresholdHours by remember { mutableIntStateOf(AppConfig.current.inactivityMonitorThresholdHours) }

    // Create a locale-aware context so stringResource() resolves to the selected language.
    // We also explicitly re-provide Activity-dependent composition locals because
    // createConfigurationContext() returns a plain Context, not an Activity.
    val activity = baseContext as androidx.activity.ComponentActivity
    val localeContext = remember(appLanguage) {
        val locale = java.util.Locale.forLanguageTag(appLanguage)
        java.util.Locale.setDefault(locale)
        val config = android.content.res.Configuration(baseContext.resources.configuration)
        config.setLocale(locale)
        baseContext.createConfigurationContext(config)
    }

    fun saveAndFinish() {
        // Save everything via AppConfig (which syncs contacts to repo)
        val config = AppConfig.current.copy(
            userNickname = nickname,
            featureQuickContacts = featureToggles.quickContacts,
            featureDialPad = featureToggles.dialPad,
            featureContacts = featureToggles.contacts,
            featureMessages = featureToggles.messages,
            featureGallery = featureToggles.gallery,
            featureWhatsapp = featureToggles.whatsapp,
            autoMaxVolume = featureToggles.autoMaxVolume,
            inactivityMonitorEnabled = featureToggles.inactivityMonitor,
            inactivityMonitorThresholdHours = inactivityThresholdHours,
            featureMedicationReminders = featureToggles.medicationReminders,
            featureTtsSms = featureToggles.ttsSms,
            themeMode = themeMode.name,
            appLanguage = appLanguage,
            emergencyContacts = emergencyContacts,
            quickContacts = quickContacts
        )
        AppConfig.save(baseContext, config)

        // Schedule or cancel inactivity monitor based on new config
        InactivityMonitorWorker.reschedule(baseContext)

        // Schedule medication reminders if enabled
        ro.softwarechef.freshboomer.services.MedicationReminderScheduler.scheduleAll(baseContext)

        // Mark setup as completed
        SetupWizardPreference.setCompleted(baseContext, true)
        onComplete()
    }

    CompositionLocalProvider(
        LocalContext provides localeContext,
        LocalActivityResultRegistryOwner provides activity,
        LocalOnBackPressedDispatcherOwner provides activity,
        LocalLifecycleOwner provides activity,
        LocalSavedStateRegistryOwner provides activity,
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // Progress bar
        LinearProgressIndicator(
            progress = { (currentPage + 1).toFloat() / TOTAL_PAGES },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
        Text(
            text = stringResource(R.string.wizard_step_progress, currentPage + 1, TOTAL_PAGES),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Page content
        Box(
            modifier = Modifier.weight(1f)
        ) {
            when (currentPage) {
                0 -> WelcomePage(
                    appLanguage = appLanguage,
                    onLanguageChanged = {
                        appLanguage = it
                        AppConfig.save(baseContext, AppConfig.current.copy(appLanguage = it))
                    },
                    privacyAccepted = privacyAccepted,
                    onPrivacyAccepted = { privacyAccepted = true },
                    onPrivacyDeclined = {
                        // Exit the app if user declines the privacy policy
                        (baseContext as? android.app.Activity)?.finishAffinity()
                    }
                )
                1 -> NicknamePage(
                    nickname = nickname,
                    onNicknameChanged = { nickname = it }
                )
                2 -> QuickContactsPage(
                    contacts = quickContacts,
                    onContactsChanged = { quickContacts = it }
                )
                3 -> EmergencyContactsPage(
                    emergencyContacts = emergencyContacts,
                    onContactsChanged = { emergencyContacts = it }
                )
                4 -> FeaturesPage(
                    toggles = featureToggles,
                    onTogglesChanged = { featureToggles = it },
                    themeMode = themeMode,
                    onThemeModeChanged = {
                        themeMode = it
                        ThemePreference.setThemeMode(baseContext, it)
                        onThemeChanged()
                    },
                    inactivityThresholdHours = inactivityThresholdHours,
                    onThresholdHoursChanged = { inactivityThresholdHours = it },
                    hasEmergencyContacts = emergencyContacts.any { it.phoneNumber.isNotBlank() }
                )
                5 -> SettingsInfoPage()
            }
        }

        // Navigation buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (currentPage > 0) {
                GlassButton(
                    onClick = { currentPage-- },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.wizard_back),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            } else {
                // Skip button on first page
                TextButton(
                    onClick = { saveAndFinish() },
                    modifier = Modifier.height(52.dp),
                    enabled = privacyAccepted
                ) {
                    Text(
                        stringResource(R.string.wizard_skip),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (privacyAccepted) 0.5f else 0.2f)
                    )
                }
            }

            if (currentPage < TOTAL_PAGES - 1) {
                AccentGlowButton(
                    onClick = { currentPage++ },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(16.dp),
                    enabled = currentPage != 0 || privacyAccepted
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.wizard_next),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                AccentGlowButton(
                    onClick = { saveAndFinish() },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.wizard_finish),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
    } // CompositionLocalProvider
}

// ─── Page 1: Welcome ───

