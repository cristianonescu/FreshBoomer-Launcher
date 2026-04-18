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

@Composable
private fun TipsSection() {
    var showWhatsAppGuide by remember { mutableStateOf(false) }

    if (showWhatsAppGuide) {
        AlertDialog(
            onDismissRequest = { showWhatsAppGuide = false },
            title = {
                Text(
                    stringResource(R.string.settings_whatsapp_guide_title),
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        stringResource(R.string.settings_whatsapp_guide_intro),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    GuideStep("1", stringResource(R.string.settings_whatsapp_guide_step1))
                    GuideStep("2", stringResource(R.string.settings_whatsapp_guide_step2))
                    GuideStep("3", stringResource(R.string.settings_whatsapp_guide_step3))
                    GuideStep("4", stringResource(R.string.settings_whatsapp_guide_step4))
                    GuideStep("5", stringResource(R.string.settings_whatsapp_guide_step5))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.settings_whatsapp_guide_done),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                AccentGlowButton(onClick = { showWhatsAppGuide = false }) {
                    Text(stringResource(R.string.settings_understood), fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_whatsapp_photos),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_whatsapp_photos_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                AccentGlowButton(
                    onClick = { showWhatsAppGuide = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(stringResource(R.string.settings_see), fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun GuideStep(number: String, text: String) {
    Row(
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$number.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        if (expandable) {
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (expanded) "Ascunde" else "Arata",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun AppsSection() {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            AppRow(
                name = "WhatsApp",
                icon = Icons.Default.Call,
                onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.whatsapp")
                    if (intent != null) {
                        context.startActivity(intent)
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            AppRow(
                name = stringResource(R.string.settings_app_google_play),
                icon = Icons.Default.ShoppingCart,
                onClick = {
                    val intent = context.packageManager.getLaunchIntentForPackage("com.android.vending")
                    if (intent != null) {
                        context.startActivity(intent)
                    }
                }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            AppRow(
                name = stringResource(R.string.settings_app_device_settings),
                icon = Icons.Default.Settings,
                onClick = {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            )
        }
    }
}

@Composable
private fun AppRow(
    name: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        AccentGlowButton(onClick = onClick) {
            Text(stringResource(R.string.settings_open), fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}

@Composable
private fun TtsSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    selectedEngine: TtsEngine,
    onEngineChange: (TtsEngine) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_tts_enabled),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_tts_enabled_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onEnabledChange
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedEngine == TtsEngine.PIPER_SANDA,
                    onClick = { onEngineChange(TtsEngine.PIPER_SANDA) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_tts_sanda),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_tts_sanda_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedEngine == TtsEngine.PIPER_LILI,
                    onClick = { onEngineChange(TtsEngine.PIPER_LILI) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_tts_piper),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_tts_piper_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = selectedEngine == TtsEngine.DEVICE_DEFAULT,
                    onClick = { onEngineChange(TtsEngine.DEVICE_DEFAULT) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_tts_device),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_tts_device_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // ─── Model update check ───
            TtsUpdateRow()
        }
    }
}

@Composable
private fun TtsUpdateRow() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val selectedVoice = when (TtsPreference.getEngine(context)) {
        TtsEngine.PIPER_SANDA -> PiperVoice.SANDA
        else -> PiperVoice.LILI
    }

    var checkState by remember { mutableStateOf<UpdateCheckState>(UpdateCheckState.Idle) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadedMb by remember { mutableFloatStateOf(0f) }
    var totalMb by remember { mutableFloatStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.settings_tts_update_model),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = when (checkState) {
                    UpdateCheckState.Idle -> stringResource(R.string.settings_tts_update_idle)
                    UpdateCheckState.Checking -> stringResource(R.string.settings_tts_update_checking)
                    UpdateCheckState.UpToDate -> stringResource(R.string.settings_tts_update_up_to_date)
                    UpdateCheckState.Downloading -> {
                        if (totalMb > 0)
                            stringResource(R.string.settings_tts_update_downloading, downloadedMb, totalMb)
                        else
                            stringResource(R.string.settings_tts_update_downloading_unknown, downloadedMb)
                    }
                    UpdateCheckState.Done -> stringResource(R.string.settings_tts_update_done)
                    UpdateCheckState.Error -> stringResource(R.string.settings_tts_update_error)
                    UpdateCheckState.NoModel -> stringResource(R.string.settings_tts_update_no_model)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = when (checkState) {
                    UpdateCheckState.Error -> MaterialTheme.colorScheme.error
                    UpdateCheckState.Done, UpdateCheckState.UpToDate -> Color(0xFF4CAF50)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                }
            )
            if (checkState == UpdateCheckState.Downloading) {
                Spacer(modifier = Modifier.height(4.dp))
                LinearProgressIndicator(
                    progress = { if (totalMb > 0) downloadProgress else 0f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        AccentGlowButton(
            onClick = {
                scope.launch {
                    checkState = UpdateCheckState.Checking
                    val modelExists = withContext(Dispatchers.IO) {
                        TtsModelManager.isModelDownloaded(context, selectedVoice)
                    }
                    if (!modelExists) {
                        checkState = UpdateCheckState.NoModel
                        val success = TtsModelManager.downloadModel(context, selectedVoice) { downloaded, total ->
                            downloadedMb = downloaded / (1024f * 1024f)
                            if (total > 0) {
                                totalMb = total / (1024f * 1024f)
                                downloadProgress = downloaded.toFloat() / total.toFloat()
                            }
                            checkState = UpdateCheckState.Downloading
                        }
                        checkState = if (success) UpdateCheckState.Done else UpdateCheckState.Error
                        return@launch
                    }
                    val updateAvailable = TtsModelManager.isUpdateAvailable(context, selectedVoice)
                    if (!updateAvailable) {
                        checkState = UpdateCheckState.UpToDate
                        return@launch
                    }
                    checkState = UpdateCheckState.Downloading
                    downloadProgress = 0f
                    downloadedMb = 0f
                    totalMb = 0f
                    val success = TtsModelManager.downloadModel(context, selectedVoice) { downloaded, total ->
                        downloadedMb = downloaded / (1024f * 1024f)
                        if (total > 0) {
                            totalMb = total / (1024f * 1024f)
                            downloadProgress = downloaded.toFloat() / total.toFloat()
                        }
                    }
                    checkState = if (success) UpdateCheckState.Done else UpdateCheckState.Error
                }
            },
            enabled = checkState != UpdateCheckState.Checking
                    && checkState != UpdateCheckState.Downloading
                    && checkState != UpdateCheckState.NoModel
        ) {
            Text(stringResource(R.string.settings_tts_update_check), fontWeight = FontWeight.ExtraBold, color = Color.White)
        }
    }
}

@Composable
private fun TtsSmsSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    trustedOnly: Boolean,
    onTrustedOnlyChange: (Boolean) -> Unit,
    prefix: String,
    onPrefixChange: (String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            FeatureToggleRow(
                label = stringResource(R.string.settings_tts_sms_enabled),
                description = stringResource(R.string.settings_tts_sms_enabled_desc),
                checked = enabled,
                onCheckedChange = onEnabledChange
            )

            if (enabled) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                FeatureToggleRow(
                    label = stringResource(R.string.settings_tts_sms_trusted_only),
                    description = stringResource(R.string.settings_tts_sms_trusted_only_desc),
                    checked = trustedOnly,
                    onCheckedChange = onTrustedOnlyChange
                )

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text(
                    text = stringResource(R.string.settings_tts_sms_prefix),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Text(
                    text = stringResource(R.string.settings_tts_sms_prefix_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                OutlinedTextField(
                    value = prefix,
                    onValueChange = { if (it.length <= 20) onPrefixChange(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.settings_tts_sms_info, prefix.ifEmpty { "CITESTE:" }),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MedicationRemindersSection(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    reminders: List<ro.softwarechef.freshboomer.data.MedicationReminder>,
    onRemindersChanged: (List<ro.softwarechef.freshboomer.data.MedicationReminder>) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            FeatureToggleRow(
                label = stringResource(R.string.settings_medication_enabled),
                description = stringResource(R.string.settings_medication_enabled_desc),
                checked = enabled,
                onCheckedChange = onEnabledChange
            )

            if (enabled) {
                // Exact alarm permission check (Android 12+)
                if (!ro.softwarechef.freshboomer.services.MedicationReminderScheduler.canScheduleExactAlarms(context)) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = stringResource(R.string.medication_exact_alarm_needed),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(4.dp)
                    )
                    AccentGlowButton(
                        onClick = {
                            context.startActivity(
                                ro.softwarechef.freshboomer.services.MedicationReminderScheduler.getExactAlarmSettingsIntent()
                            )
                        },
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        Text(stringResource(R.string.medication_exact_alarm_grant), fontWeight = FontWeight.ExtraBold, color = Color.White)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (reminders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_medication_none),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.padding(8.dp)
                    )
                }

                reminders.forEachIndexed { index, reminder ->
                    if (index > 0) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }
                    MedicationReminderItem(
                        reminder = reminder,
                        onChanged = { updated ->
                            val list = reminders.toMutableList()
                            list[index] = updated
                            onRemindersChanged(list)
                        },
                        onDelete = {
                            val list = reminders.toMutableList()
                            list.removeAt(index)
                            onRemindersChanged(list)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                GlassButton(
                    onClick = {
                        val updated = reminders + ro.softwarechef.freshboomer.data.MedicationReminder()
                        onRemindersChanged(updated)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
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
                            stringResource(R.string.settings_medication_add),
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MedicationReminderItem(
    reminder: ro.softwarechef.freshboomer.data.MedicationReminder,
    onChanged: (ro.softwarechef.freshboomer.data.MedicationReminder) -> Unit,
    onDelete: () -> Unit
) {
    val dayLabels = listOf(
        stringResource(R.string.day_mon),
        stringResource(R.string.day_tue),
        stringResource(R.string.day_wed),
        stringResource(R.string.day_thu),
        stringResource(R.string.day_fri),
        stringResource(R.string.day_sat),
        stringResource(R.string.day_sun)
    )

    var showTimePicker by remember { mutableStateOf(false) }

    if (showTimePicker) {
        val parts = reminder.time.split(":")
        val initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val timePickerState = rememberTimePickerState(
            initialHour = initialHour,
            initialMinute = initialMinute,
            is24Hour = true
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.settings_medication_time)) },
            text = {
                TimePicker(state = timePickerState)
            },
            confirmButton = {
                AccentGlowButton(onClick = {
                    val newTime = String.format("%02d:%02d", timePickerState.hour, timePickerState.minute)
                    onChanged(reminder.copy(time = newTime))
                    showTimePicker = false
                }) {
                    Text("OK", fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            },
            dismissButton = {
                GlassButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        )
    }

    Column {
        // Name field + enabled toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = reminder.name,
                onValueChange = { onChanged(reminder.copy(name = it)) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text(stringResource(R.string.settings_medication_name_hint)) },
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = reminder.enabled,
                onCheckedChange = { onChanged(reminder.copy(enabled = it)) }
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Time selector
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_medication_time),
                style = MaterialTheme.typography.bodyMedium
            )
            GlassButton(
                onClick = { showTimePicker = true },
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = reminder.time,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day of week selector
        Text(
            text = stringResource(R.string.settings_medication_days),
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            (1..7).forEach { day ->
                val isSelected = day in reminder.daysOfWeek
                FilterChip(
                    selected = isSelected,
                    onClick = {
                        val newDays = if (isSelected) {
                            reminder.daysOfWeek - day
                        } else {
                            reminder.daysOfWeek + day
                        }
                        onChanged(reminder.copy(daysOfWeek = newDays.sorted()))
                    },
                    label = {
                        Text(
                            text = dayLabels[day - 1],
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Delete button
        TextButton(
            onClick = onDelete,
            colors = ButtonDefaults.textButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            )
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.settings_medication_delete))
        }
    }
}

@Composable
private fun FeatureToggleSection(
    toggles: FeatureToggles,
    onToggleChange: (key: String, enabled: Boolean) -> Unit,
    inactivityThresholdHours: Int,
    onThresholdHoursChanged: (Int) -> Unit,
    hasEmergencyContacts: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            FeatureToggleRow(
                label = stringResource(R.string.wizard_feature_quick_contacts),
                description = stringResource(R.string.wizard_feature_quick_contacts_desc),
                checked = toggles.quickContacts,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.QUICK_CONTACTS, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = stringResource(R.string.wizard_feature_dial_pad),
                description = stringResource(R.string.wizard_feature_dial_pad_desc),
                checked = toggles.dialPad,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.DIAL_PAD, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = stringResource(R.string.wizard_feature_contacts),
                description = stringResource(R.string.wizard_feature_contacts_desc),
                checked = toggles.contacts,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.CONTACTS, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = stringResource(R.string.wizard_feature_sms),
                description = stringResource(R.string.wizard_feature_sms_desc),
                checked = toggles.messages,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.MESSAGES, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = stringResource(R.string.wizard_feature_gallery),
                description = stringResource(R.string.wizard_feature_gallery_desc),
                checked = toggles.gallery,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.GALLERY, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = "WhatsApp",
                description = stringResource(R.string.wizard_feature_whatsapp_desc),
                checked = toggles.whatsapp,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.WHATSAPP, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = stringResource(R.string.wizard_feature_auto_volume),
                description = stringResource(R.string.wizard_feature_auto_volume_desc),
                checked = toggles.autoMaxVolume,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.AUTO_MAX_VOLUME, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = stringResource(R.string.wizard_feature_inactivity_monitor),
                description = stringResource(R.string.wizard_feature_inactivity_monitor_desc),
                checked = toggles.inactivityMonitor,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.INACTIVITY_MONITOR, it) }
            )

            if (toggles.inactivityMonitor) {
                if (!hasEmergencyContacts) {
                    Text(
                        text = stringResource(R.string.settings_inactivity_no_contacts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, end = 8.dp)
                    )
                }
                Row(
                    modifier = Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.settings_inactivity_alert_after),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    OutlinedTextField(
                        value = inactivityThresholdHours.toString(),
                        onValueChange = { text ->
                            val hours = text.filter { it.isDigit() }.toIntOrNull()
                            if (hours != null && hours in 1..72) {
                                onThresholdHoursChanged(hours)
                            } else if (text.isEmpty()) {
                                onThresholdHoursChanged(1)
                            }
                        },
                        modifier = Modifier.width(80.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        ),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_inactivity_hours),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun FeatureToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

private enum class UpdateCheckState {
    Idle, Checking, UpToDate, Downloading, Done, Error, NoModel
}

@Composable
private fun NicknameSection(
    nickname: String,
    onNicknameChange: (String) -> Unit
) {
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.settings_nickname_title),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_nickname_desc, nickname),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = nickname,
                    onValueChange = onNicknameChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_nickname_placeholder)) },
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        (context as? ImmersiveActivity)?.speakOutLoud(
                            "$nickname, te-a sunat cineva!"
                        )
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Asculta",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun LanguageSection(
    appLanguage: String,
    onLanguageChange: (String) -> Unit
) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.settings_language_title),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_language_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = appLanguage == "ro",
                    onClick = {
                        if (appLanguage != "ro") {
                            onLanguageChange("ro")
                            LocaleHelper.applyLanguage(context as android.app.Activity, "ro")
                        }
                    },
                    label = { Text("Romana") },
                    leadingIcon = if (appLanguage == "ro") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = appLanguage == "en",
                    onClick = {
                        if (appLanguage != "en") {
                            onLanguageChange("en")
                            LocaleHelper.applyLanguage(context as android.app.Activity, "en")
                        }
                    },
                    label = { Text("English") },
                    leadingIcon = if (appLanguage == "en") {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                    } else null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun EmergencyContactsSection(
    emergencyContacts: List<EmergencyContact>,
    onContactsChanged: (List<EmergencyContact>) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.settings_emergency_title),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = stringResource(R.string.settings_emergency_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (emergencyContacts.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_emergency_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            } else {
                emergencyContacts.forEachIndexed { index, contact ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = contact.name,
                                    onValueChange = { newName ->
                                        val updated = emergencyContacts.toMutableList()
                                        updated[index] = contact.copy(name = newName)
                                        onContactsChanged(updated)
                                    },
                                    label = { Text(stringResource(R.string.settings_emergency_name_label)) },
                                    placeholder = { Text(stringResource(R.string.settings_emergency_name_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = contact.phoneNumber,
                                    onValueChange = { newPhone ->
                                        val updated = emergencyContacts.toMutableList()
                                        updated[index] = contact.copy(phoneNumber = newPhone)
                                        onContactsChanged(updated)
                                    },
                                    label = { Text(stringResource(R.string.settings_emergency_phone_label)) },
                                    placeholder = { Text(stringResource(R.string.settings_emergency_phone_placeholder)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    shape = RoundedCornerShape(8.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = {
                                val updated = emergencyContacts.toMutableList()
                                updated.removeAt(index)
                                onContactsChanged(updated)
                            }) {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = "Sterge",
                                    tint = Color.Red
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            GlassButton(
                onClick = {
                    val updated = emergencyContacts + EmergencyContact()
                    onContactsChanged(updated)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
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
                        stringResource(R.string.settings_emergency_add),
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeSection(
    selectedTheme: AppThemeMode,
    onThemeChange: (AppThemeMode) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_visual_title),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = stringResource(R.string.settings_visual_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box {
                    GlassButton(
                        onClick = { expanded = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = when (selectedTheme) {
                                AppThemeMode.SYSTEM -> stringResource(R.string.settings_theme_auto_short)
                                AppThemeMode.LIGHT -> stringResource(R.string.settings_theme_light)
                                AppThemeMode.DARK -> stringResource(R.string.settings_theme_dark)
                            },
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        }
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.settings_theme_auto), style = MaterialTheme.typography.bodyLarge)
                            },
                            onClick = {
                                onThemeChange(AppThemeMode.SYSTEM)
                                expanded = false
                            },
                            trailingIcon = {
                                if (selectedTheme == AppThemeMode.SYSTEM) {
                                    Icon(Icons.Default.Check, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.settings_theme_light), style = MaterialTheme.typography.bodyLarge)
                            },
                            onClick = {
                                onThemeChange(AppThemeMode.LIGHT)
                                expanded = false
                            },
                            trailingIcon = {
                                if (selectedTheme == AppThemeMode.LIGHT) {
                                    Icon(Icons.Default.Check, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(stringResource(R.string.settings_theme_dark), style = MaterialTheme.typography.bodyLarge)
                            },
                            onClick = {
                                onThemeChange(AppThemeMode.DARK)
                                expanded = false
                            },
                            trailingIcon = {
                                if (selectedTheme == AppThemeMode.DARK) {
                                    Icon(Icons.Default.Check, contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactCard(
    contact: QuickContact,
    isFirst: Boolean,
    isLast: Boolean,
    onNameChange: (String) -> Unit,
    onPhoneChange: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current
    var editingName by remember(contact.id) { mutableStateOf(contact.name) }
    var editingPhone by remember(contact.id) { mutableStateOf(contact.phoneNumber) }

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
            // Photo avatar — tap to pick photo
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .clickable { onPickPhoto() },
                contentAlignment = Alignment.Center
            ) {
                when {
                    contact.photoUri != null -> {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(File(contact.photoUri))
                                .crossfade(true)
                                .build(),
                            contentDescription = contact.name,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    }
                    contact.drawableResName != null -> {
                        val resId = context.resources.getIdentifier(
                            contact.drawableResName, "drawable", context.packageName
                        )
                        if (resId != 0) {
                            Image(
                                painter = painterResource(resId),
                                contentDescription = contact.name,
                                modifier = Modifier.fillMaxSize().clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Add, contentDescription = "Adauga poza",
                                modifier = Modifier.size(28.dp),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    else -> {
                        Icon(Icons.Default.Add, contentDescription = "Adauga poza",
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Name and phone fields
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = editingName,
                    onValueChange = { editingName = it },
                    label = { Text(stringResource(R.string.settings_contact_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = editingPhone,
                    onValueChange = { editingPhone = it },
                    label = { Text(stringResource(R.string.settings_contact_phone)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Save button (only visible if changes were made)
                    if (editingName != contact.name || editingPhone != contact.phoneNumber) {
                        AccentGlowButton(
                            onClick = {
                                if (editingName != contact.name) onNameChange(editingName)
                                if (editingPhone != contact.phoneNumber) onPhoneChange(editingPhone)
                            }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.settings_contact_save),
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Reorder and delete column
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = !isFirst,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Muta sus")
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = !isLast,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Muta jos")
                }
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Sterge", tint = Color.Red)
                }
            }
        }
    }
}

private data class LicenseInfo(
    val name: String,
    val license: String,
    val url: String,
    val description: String
)

private val LICENSES = listOf(
    LicenseInfo(
        "Jetpack Compose",
        "Apache License 2.0",
        "https://developer.android.com/jetpack/compose",
        "UI toolkit modern pentru Android"
    ),
    LicenseInfo(
        "AndroidX Core / AppCompat / Lifecycle",
        "Apache License 2.0",
        "https://developer.android.com/jetpack/androidx",
        "Librarii fundamentale Android Jetpack"
    ),
    LicenseInfo(
        "Material Design 3",
        "Apache License 2.0",
        "https://m3.material.io",
        "Componente Material Design pentru Compose"
    ),
    LicenseInfo(
        "Coil",
        "Apache License 2.0",
        "https://coil-kt.github.io/coil/",
        "Incarcare imagini pentru Compose"
    ),
    LicenseInfo(
        "Kotlin Coroutines",
        "Apache License 2.0",
        "https://github.com/Kotlin/kotlinx.coroutines",
        "Programare asincrona pentru Kotlin"
    ),
    LicenseInfo(
        "Sherpa ONNX",
        "Apache License 2.0",
        "https://github.com/k2-fsa/sherpa-onnx",
        "Motor text-to-speech offline (vocile Piper Lili si Sanda)"
    ),
    LicenseInfo(
        "Piper TTS",
        "MIT License",
        "https://github.com/rhasspy/piper",
        "Sistem de sinteza vocala folosit pentru limba romana"
    ),
)

private const val APACHE_2_TEXT = """Apache License, Version 2.0

Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the License. You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the specific language governing permissions and limitations under the License."""

private const val MIT_TEXT = """MIT License

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT."""

@Composable
private fun LicensesSection() {
    var expandedLicense by remember { mutableStateOf<String?>(null) }
    var showFullLicense by remember { mutableStateOf<String?>(null) }

    if (showFullLicense != null) {
        val text = when (showFullLicense) {
            "Apache License 2.0" -> APACHE_2_TEXT
            "MIT License" -> MIT_TEXT
            else -> ""
        }
        AlertDialog(
            onDismissRequest = { showFullLicense = null },
            title = { Text(showFullLicense!!, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = text.trimIndent(),
                        style = MaterialTheme.typography.bodyMedium,
                        lineHeight = 20.sp
                    )
                }
            },
            confirmButton = {
                AccentGlowButton(onClick = { showFullLicense = null }) {
                    Text("Inchide", fontWeight = FontWeight.ExtraBold, color = Color.White)
                }
            }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Aceasta aplicatie foloseste urmatoarele librarii open source:",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LICENSES.forEachIndexed { index, lib ->
                val isExpanded = expandedLicense == lib.name

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expandedLicense = if (isExpanded) null else lib.name
                        }
                        .padding(vertical = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = lib.name,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = lib.license,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }

                    androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                        Column(modifier = Modifier.padding(top = 8.dp)) {
                            Text(
                                text = lib.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                            Text(
                                text = lib.url,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                            GlassButton(
                                onClick = { showFullLicense = lib.license },
                                modifier = Modifier.padding(top = 8.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    "Citeste licenta",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                if (index < LICENSES.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                }
            }
        }
    }
}

@Composable
private fun AboutScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Inapoi(
            modifier = Modifier.padding(start = 16.dp),
            onClicked = onBack
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Licente si Multumiri",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Licenses
            Text(
                text = "Licente Open Source",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LicensesSection()

            Spacer(modifier = Modifier.height(20.dp))

            // Credits
            Text(
                text = "Multumiri",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            CreditsSection()

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CreditsSection() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Piper TTS Romanian
            Text(
                text = "Piper TTS Romanian",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Modelul de voce romaneasca folosit in aceasta aplicatie este creat de eduardem si este disponibil pe Hugging Face.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
            Text(
                text = "https://huggingface.co/eduardem/piper-tts-romanian",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp)
            )
            Text(
                text = "Multumim pentru contributia la sinteza vocala in limba romana!",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                modifier = Modifier.padding(top = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Developer
            Text(
                text = "Dezvoltator",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Cristian Onescu",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Aceasta aplicatie a fost creata cu drag pentru a ajuta persoanele varstnice sa foloseasca telefonul mai usor.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun ImportUrlDialog(
    onDismiss: () -> Unit,
    onImported: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf(ro.softwarechef.freshboomer.data.ConfigUrlPreference.getUrl(context)) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        title = {
            Text("Importa Configurare din URL", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        },
        text = {
            Column {
                Text(
                    "Introdu adresa URL catre fisierul JSON de configurare:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it
                        error = null
                    },
                    label = { Text("URL") },
                    placeholder = { Text("https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isLoading
                )
                if (error != null) {
                    Text(
                        text = error!!,
                        color = Color.Red,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    )
                }
            }
        },
        confirmButton = {
            AccentGlowButton(
                onClick = {
                    if (url.isBlank()) {
                        error = "URL-ul nu poate fi gol"
                        return@AccentGlowButton
                    }
                    isLoading = true
                    error = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ro.softwarechef.freshboomer.data.AppConfig.forceImportFromUrl(context, url.trim())
                        }
                        isLoading = false
                        if (result.isSuccess) {
                            // Persist the URL so the app auto-fetches config on every main screen resume
                            ro.softwarechef.freshboomer.data.ConfigUrlPreference.setUrl(context, url.trim())
                            android.widget.Toast.makeText(context, "Configurare importata cu succes", android.widget.Toast.LENGTH_SHORT).show()
                            onImported()
                            // Recreate activity to apply locale/theme changes from imported config
                            (context as? android.app.Activity)?.recreate()
                        } else {
                            error = "Eroare: ${result.exceptionOrNull()?.localizedMessage ?: "necunoscuta"}"
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text("Importa", fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
        },
        dismissButton = {
            GlassButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Anuleaza", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    )
}
