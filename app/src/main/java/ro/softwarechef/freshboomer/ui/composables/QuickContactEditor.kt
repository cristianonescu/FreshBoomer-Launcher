package ro.softwarechef.freshboomer.ui.composables

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ro.softwarechef.freshboomer.data.AppConfig
import ro.softwarechef.freshboomer.data.EmergencyContact
import ro.softwarechef.freshboomer.data.QuickContactRepository
import ro.softwarechef.freshboomer.data.AppThemeMode
import androidx.compose.ui.res.stringResource
import ro.softwarechef.freshboomer.R
import ro.softwarechef.freshboomer.data.FeatureTogglePreference
import ro.softwarechef.freshboomer.data.LocaleHelper
import ro.softwarechef.freshboomer.data.FeatureToggles
import ro.softwarechef.freshboomer.data.NicknamePreference
import ro.softwarechef.freshboomer.data.ThemePreference
import ro.softwarechef.freshboomer.data.TtsEngine
import ro.softwarechef.freshboomer.data.TtsPreference
import ro.softwarechef.freshboomer.models.QuickContact
import androidx.compose.ui.text.font.FontWeight
import ro.softwarechef.freshboomer.tts.PiperVoice
import ro.softwarechef.freshboomer.tts.TtsModelManager
// AccentGlowButton and GlassButton are in the same package so no import needed.
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

@Composable
fun QuickContactSettingsScreen(
    onBackClick: () -> Unit,
    onThemeChanged: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {}
) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf(QuickContactRepository.getContacts(context)) }
    var deleteConfirmId by remember { mutableStateOf<String?>(null) }
    var contactsExpanded by remember { mutableStateOf(false) }
    var showJsonEditor by remember { mutableStateOf(false) }
    var showImportUrlDialog by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var ttsEnabled by remember { mutableStateOf(TtsPreference.isEnabled(context)) }
    var selectedTts by remember { mutableStateOf(TtsPreference.getEngine(context)) }
    var selectedTheme by remember { mutableStateOf(ThemePreference.getThemeMode(context)) }
    var featureToggles by remember { mutableStateOf(FeatureTogglePreference.getToggles(context)) }
    var nickname by remember { mutableStateOf(NicknamePreference.getNickname(context)) }
    var appLanguage by remember { mutableStateOf(AppConfig.current.appLanguage) }
    var emergencyContacts by remember { mutableStateOf(AppConfig.current.emergencyContacts) }
    var inactivityThresholdHours by remember { mutableIntStateOf(AppConfig.current.inactivityMonitorThresholdHours) }
    // Reactively derive state from config flow — always reflects latest saved config
    val currentConfig by AppConfig.configFlow.collectAsState()
    val featureTtsSms = currentConfig.featureTtsSms
    val featureTtsSmsTrustedOnly = currentConfig.featureTtsSmsTrustedOnly
    val featureMedicationReminders = currentConfig.featureMedicationReminders
    val medicationReminders = currentConfig.medicationReminders
    // Text fields need local state for responsive typing
    var ttsSmsPrefix by remember(currentConfig.ttsSmsPrefix) { mutableStateOf(currentConfig.ttsSmsPrefix) }

    // Photo picker state
    var pickingPhotoForId by remember { mutableStateOf<String?>(null) }
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && pickingPhotoForId != null) {
            val internalPath = QuickContactRepository.savePhotoToInternal(context, uri)
            val contact = contacts.find { it.id == pickingPhotoForId }
            if (contact != null) {
                val updated = contact.copy(photoUri = internalPath, drawableResName = null)
                QuickContactRepository.updateContact(context, updated)
                contacts = QuickContactRepository.getContacts(context)
            }
        }
        pickingPhotoForId = null
    }

    // Delete confirmation dialog
    if (deleteConfirmId != null) {
        val contactToDelete = contacts.find { it.id == deleteConfirmId }
        if (contactToDelete != null) {
            AlertDialog(
                onDismissRequest = { deleteConfirmId = null },
                title = { Text(stringResource(R.string.settings_delete_contact_title)) },
                text = { Text(stringResource(R.string.settings_delete_contact_message, contactToDelete.name)) },
                confirmButton = {
                    AccentGlowButton(
                        onClick = {
                            QuickContactRepository.removeContact(context, deleteConfirmId!!)
                            contacts = QuickContactRepository.getContacts(context)
                            deleteConfirmId = null
                        },
                        color = Color(0xFFD32F2F)
                    ) {
                        Text(stringResource(R.string.delete), fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                },
                dismissButton = {
                    GlassButton(onClick = { deleteConfirmId = null }) {
                        Text(stringResource(R.string.cancel), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            )
        }
    }

    // Import from URL dialog
    if (showImportUrlDialog) {
        ImportUrlDialog(
            onDismiss = { showImportUrlDialog = false },
            onImported = {
                showImportUrlDialog = false
                contacts = QuickContactRepository.getContacts(context)
                ttsEnabled = TtsPreference.isEnabled(context)
                selectedTts = TtsPreference.getEngine(context)
                selectedTheme = ThemePreference.getThemeMode(context)
                featureToggles = FeatureTogglePreference.getToggles(context)
                nickname = NicknamePreference.getNickname(context)
                appLanguage = AppConfig.current.appLanguage
                emergencyContacts = AppConfig.current.emergencyContacts
                onThemeChanged()
            }
        )
    }

    if (showAboutScreen) {
        AboutScreen(onBack = { showAboutScreen = false })
        return
    }

    if (showJsonEditor) {
        JsonConfigEditorScreen(onBack = {
            showJsonEditor = false
            // Refresh all state from AppConfig after JSON editor changes
            contacts = QuickContactRepository.getContacts(context)
            ttsEnabled = TtsPreference.isEnabled(context)
            selectedTts = TtsPreference.getEngine(context)
            selectedTheme = ThemePreference.getThemeMode(context)
            featureToggles = FeatureTogglePreference.getToggles(context)
            nickname = NicknamePreference.getNickname(context)
            appLanguage = AppConfig.current.appLanguage
            emergencyContacts = AppConfig.current.emergencyContacts
            onThemeChanged()
        })
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Inapoi(
            modifier = Modifier.padding(start = 16.dp),
            onClicked = { onBackClick() }
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
        ) {
            // ─── Group 1: Quick Contacts (collapsible) ───
            item {
                SectionHeader(
                    title = stringResource(R.string.settings_section_quick_contacts, contacts.size),
                    icon = Icons.Default.Person,
                    expandable = true,
                    expanded = contactsExpanded,
                    onClick = { contactsExpanded = !contactsExpanded }
                )
            }

            if (contactsExpanded) {
                itemsIndexed(contacts, key = { _, c -> c.id }) { index, contact ->
                    ContactCard(
                        contact = contact,
                        isFirst = index == 0,
                        isLast = index == contacts.lastIndex,
                        onNameChange = { newName ->
                            val updated = contact.copy(name = newName)
                            QuickContactRepository.updateContact(context, updated)
                            contacts = QuickContactRepository.getContacts(context)
                        },
                        onPhoneChange = { newPhone ->
                            val updated = contact.copy(phoneNumber = newPhone)
                            QuickContactRepository.updateContact(context, updated)
                            contacts = QuickContactRepository.getContacts(context)
                        },
                        onPickPhoto = {
                            pickingPhotoForId = contact.id
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        onMoveUp = {
                            QuickContactRepository.moveContact(context, contact.id, -1)
                            contacts = QuickContactRepository.getContacts(context)
                        },
                        onMoveDown = {
                            QuickContactRepository.moveContact(context, contact.id, 1)
                            contacts = QuickContactRepository.getContacts(context)
                        },
                        onDelete = {
                            deleteConfirmId = contact.id
                        }
                    )
                }

                item {
                    GlassButton(
                        onClick = {
                            val newContact = QuickContact(
                                id = UUID.randomUUID().toString(),
                                name = "",
                                phoneNumber = "",
                                sortOrder = contacts.size
                            )
                            QuickContactRepository.addContact(context, newContact)
                            contacts = QuickContactRepository.getContacts(context)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.settings_add_contact),
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // ─── Group 2: Apps ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_apps),
                    icon = Icons.Default.Favorite
                )
            }

            item {
                AppsSection()
            }

            // ─── Group 3: TTS ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_tts),
                    icon = Icons.Default.PlayArrow
                )
            }

            item {
                TtsSection(
                    enabled = ttsEnabled,
                    onEnabledChange = { enabled ->
                        ttsEnabled = enabled
                        TtsPreference.setEnabled(context, enabled)
                    },
                    selectedEngine = selectedTts,
                    onEngineChange = { engine ->
                        selectedTts = engine
                        TtsPreference.setEngine(context, engine)
                    }
                )
            }

            // ─── Group: TTS SMS ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_tts_sms),
                    icon = Icons.Default.Email
                )
            }

            item {
                TtsSmsSection(
                    enabled = featureTtsSms,
                    onEnabledChange = { enabled ->
                        FeatureTogglePreference.setToggle(context, FeatureTogglePreference.TTS_SMS, enabled)
                    },
                    trustedOnly = featureTtsSmsTrustedOnly,
                    onTrustedOnlyChange = { enabled ->
                        AppConfig.save(context, AppConfig.current.copy(featureTtsSmsTrustedOnly = enabled))
                    },
                    prefix = ttsSmsPrefix,
                    onPrefixChange = { prefix ->
                        ttsSmsPrefix = prefix
                        AppConfig.save(context, AppConfig.current.copy(ttsSmsPrefix = prefix))
                    }
                )
            }

            // ─── Group: Nickname ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_customize),
                    icon = Icons.Default.Person
                )
            }

            item {
                NicknameSection(
                    nickname = nickname,
                    onNicknameChange = { newNickname ->
                        nickname = newNickname
                        NicknamePreference.setNickname(context, newNickname)
                    }
                )
            }

            // ─── Group: Language ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_language),
                    icon = Icons.Default.Place
                )
            }

            item {
                LanguageSection(
                    appLanguage = appLanguage,
                    onLanguageChange = { lang ->
                        appLanguage = lang
                        AppConfig.save(context, AppConfig.current.copy(appLanguage = lang))
                    }
                )
            }

            // ─── Group: Emergency Contacts ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_emergency),
                    icon = Icons.Default.Warning
                )
            }

            item {
                EmergencyContactsSection(
                    emergencyContacts = emergencyContacts,
                    onContactsChanged = { updated ->
                        emergencyContacts = updated
                        AppConfig.save(context, AppConfig.current.copy(emergencyContacts = updated))
                    }
                )
            }

            // ─── Group: Medication Reminders ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_medication),
                    icon = Icons.Default.Notifications
                )
            }

            item {
                MedicationRemindersSection(
                    enabled = featureMedicationReminders,
                    onEnabledChange = { enabled ->
                        FeatureTogglePreference.setToggle(context, FeatureTogglePreference.MEDICATION_REMINDERS, enabled)
                        if (!enabled) {
                            ro.softwarechef.freshboomer.services.MedicationReminderScheduler.cancelAll(context)
                        }
                    },
                    reminders = medicationReminders,
                    onRemindersChanged = { updated ->
                        AppConfig.save(context, AppConfig.current.copy(medicationReminders = updated))
                    }
                )
            }

            // ─── Group 4: Theme ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_theme),
                    icon = Icons.Default.Settings
                )
            }

            item {
                ThemeSection(
                    selectedTheme = selectedTheme,
                    onThemeChange = { mode ->
                        selectedTheme = mode
                        ThemePreference.setThemeMode(context, mode)
                        onThemeChanged()
                    }
                )
            }

            // ─── Group 5: Feature Toggles ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_home_screen),
                    icon = Icons.Default.Home
                )
            }

            item {
                FeatureToggleSection(
                    toggles = featureToggles,
                    onToggleChange = { key, enabled ->
                        FeatureTogglePreference.setToggle(context, key, enabled)
                        featureToggles = FeatureTogglePreference.getToggles(context)
                        if (key == FeatureTogglePreference.INACTIVITY_MONITOR) {
                            ro.softwarechef.freshboomer.services.InactivityMonitorWorker.reschedule(context)
                        }
                    },
                    inactivityThresholdHours = inactivityThresholdHours,
                    onThresholdHoursChanged = { hours ->
                        inactivityThresholdHours = hours
                        val updated = AppConfig.current.copy(inactivityMonitorThresholdHours = hours)
                        AppConfig.save(context, updated)
                        ro.softwarechef.freshboomer.services.InactivityMonitorWorker.reschedule(context)
                    },
                    hasEmergencyContacts = emergencyContacts.any { it.phoneNumber.isNotBlank() }
                )
            }

            // ─── Group 6: Permissions ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_permissions),
                    icon = Icons.Default.Lock
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_check_permissions),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.settings_check_permissions_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        AccentGlowButton(
                            onClick = onOpenOnboarding,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.settings_open), fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }

            // ─── Group 7: Advanced Config ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_advanced),
                    icon = Icons.Default.Build
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_json_editor),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.settings_json_editor_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        AccentGlowButton(
                            onClick = { showJsonEditor = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.settings_open), fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_import_config),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.settings_import_config_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        AccentGlowButton(
                            onClick = { showImportUrlDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.settings_import), fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }

            item {
                ConfigEditorHint()
            }

            // ─── Group 8: Tips ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_tips),
                    icon = Icons.Default.Info
                )
            }

            item {
                TipsSection()
            }

            // ─── Group 9: About / Licenses ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = stringResource(R.string.settings_section_about),
                    icon = Icons.Default.Info
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_licenses),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.settings_licenses_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        AccentGlowButton(
                            onClick = { showAboutScreen = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.settings_open), fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }

            // Privacy Policy
            item {
                var showPrivacyPolicy by remember { mutableStateOf(false) }

                if (showPrivacyPolicy) {
                    AlertDialog(
                        onDismissRequest = { showPrivacyPolicy = false },
                        modifier = Modifier.fillMaxSize(),
                        title = {
                            Text(
                                text = stringResource(R.string.settings_privacy_policy),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        },
                        text = {
                            AndroidView(
                                factory = { ctx ->
                                    android.webkit.WebView(ctx).apply {
                                        settings.javaScriptEnabled = true

                                        loadUrl("file:///android_asset/privacy-policy.html")
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp)
                            )
                        },
                        confirmButton = {
                            AccentGlowButton(
                                onClick = { showPrivacyPolicy = false },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(stringResource(R.string.settings_understood), fontWeight = FontWeight.ExtraBold, color = Color.White)
                            }
                        }
                    )
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_privacy_policy),
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = stringResource(R.string.settings_privacy_policy_desc),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        AccentGlowButton(
                            onClick = { showPrivacyPolicy = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(stringResource(R.string.settings_open), fontWeight = FontWeight.ExtraBold, color = Color.White)
                        }
                    }
                }
            }
        }
    }
}

