package ro.softwarechef.freshboomer.onboarding

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ro.softwarechef.freshboomer.R
import ro.softwarechef.freshboomer.data.AppConfig
import ro.softwarechef.freshboomer.data.AppThemeMode
import ro.softwarechef.freshboomer.data.EmergencyContact
import ro.softwarechef.freshboomer.data.FeatureToggles
import ro.softwarechef.freshboomer.data.FeatureTogglePreference
import ro.softwarechef.freshboomer.data.MedicationReminder
import ro.softwarechef.freshboomer.data.NicknamePreference
import ro.softwarechef.freshboomer.data.QuickContactRepository
import ro.softwarechef.freshboomer.data.ThemePreference
import ro.softwarechef.freshboomer.models.QuickContact
import ro.softwarechef.freshboomer.ui.composables.AccentGlowButton
import ro.softwarechef.freshboomer.ui.composables.GlassButton
import java.io.File
import java.util.Locale
import java.util.UUID

@Composable
internal fun WelcomePage(
    appLanguage: String,
    onLanguageChanged: (String) -> Unit,
    privacyAccepted: Boolean,
    onPrivacyAccepted: () -> Unit,
    onPrivacyDeclined: () -> Unit
) {
    val context = LocalContext.current

    // Show privacy policy dialog automatically on first display
    var showPrivacyDialog by remember { mutableStateOf(!privacyAccepted) }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(
            onAccept = {
                showPrivacyDialog = false
                onPrivacyAccepted()
            },
            onDecline = {
                showPrivacyDialog = false
                onPrivacyDeclined()
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Home,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.wizard_welcome_title),
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.wizard_welcome_description),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.wizard_welcome_instructions),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))

        // Language selection
        Text(
            text = stringResource(R.string.wizard_language_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilterChip(
                selected = appLanguage == "ro",
                onClick = { if (appLanguage != "ro") onLanguageChanged("ro") },
                label = { Text("Romana", style = MaterialTheme.typography.titleMedium) },
                leadingIcon = if (appLanguage == "ro") {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null
            )
            FilterChip(
                selected = appLanguage == "en",
                onClick = { if (appLanguage != "en") onLanguageChanged("en") },
                label = { Text("English", style = MaterialTheme.typography.titleMedium) },
                leadingIcon = if (appLanguage == "en") {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                } else null
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Privacy policy status + re-read option
        if (privacyAccepted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.clickable { showPrivacyDialog = true }
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color(0xFF4CAF50),
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(R.string.privacy_policy_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
internal fun PrivacyPolicyDialog(
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { /* prevent dismiss without choice */ },
        modifier = Modifier.fillMaxSize(),
        title = {
            Text(
                text = stringResource(R.string.privacy_policy_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
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
                onClick = onAccept,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(R.string.privacy_policy_accept),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = androidx.compose.ui.graphics.Color.White
                )
            }
        },
        dismissButton = {
            GlassButton(
                onClick = onDecline,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    stringResource(R.string.privacy_policy_decline),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    )
}

// ─── Page 2: Nickname ───

@Composable
internal fun NicknamePage(
    nickname: String,
    onNicknameChanged: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))
        Icon(
            imageVector = Icons.Default.Person,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.wizard_nickname_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.wizard_nickname_description),
            style = MaterialTheme.typography.titleSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChanged,
            label = { Text(stringResource(R.string.wizard_nickname_label), style = MaterialTheme.typography.titleMedium) },
            placeholder = { Text(stringResource(R.string.wizard_nickname_placeholder)) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textStyle = MaterialTheme.typography.headlineMedium,
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ─── Page 3: Quick Contacts ───

@Composable
internal fun QuickContactsPage(
    contacts: List<QuickContact>,
    onContactsChanged: (List<QuickContact>) -> Unit
) {
    val context = LocalContext.current
    var pickingPhotoForIndex by remember { mutableIntStateOf(-1) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null && pickingPhotoForIndex >= 0 && pickingPhotoForIndex < contacts.size) {
            val internalPath = QuickContactRepository.savePhotoToInternal(context, uri)
            val updated = contacts.toMutableList()
            updated[pickingPhotoForIndex] = contacts[pickingPhotoForIndex].copy(
                photoUri = internalPath,
                drawableResName = null
            )
            onContactsChanged(updated)
        }
        pickingPhotoForIndex = -1
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Text(
            text = stringResource(R.string.wizard_contacts_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wizard_contacts_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            contacts.forEachIndexed { index, contact ->
                QuickContactRow(
                    contact = contact,
                    onNameChanged = { name ->
                        val updated = contacts.toMutableList()
                        updated[index] = contact.copy(name = name)
                        onContactsChanged(updated)
                    },
                    onPhoneChanged = { phone ->
                        val updated = contacts.toMutableList()
                        updated[index] = contact.copy(phoneNumber = phone)
                        onContactsChanged(updated)
                    },
                    onPickPhoto = {
                        pickingPhotoForIndex = index
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onRemove = {
                        val updated = contacts.toMutableList()
                        updated.removeAt(index)
                        onContactsChanged(updated)
                    }
                )
            }

            // Add contact button
            GlassButton(
                onClick = {
                    val newId = UUID.randomUUID().toString()
                    val updated = contacts + QuickContact(
                        id = newId,
                        name = "",
                        phoneNumber = "",
                        sortOrder = contacts.size
                    )
                    onContactsChanged(updated)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.wizard_contacts_add),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            if (contacts.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.wizard_contacts_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
internal fun QuickContactRow(
    contact: QuickContact,
    onNameChanged: (String) -> Unit,
    onPhoneChanged: (String) -> Unit,
    onPickPhoto: () -> Unit,
    onRemove: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Photo avatar / picker
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                    .clickable { onPickPhoto() },
                contentAlignment = Alignment.Center
            ) {
                if (contact.photoUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(contact.photoUri))
                            .crossfade(true)
                            .build(),
                        contentDescription = contact.name,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Adauga poza",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = contact.name,
                    onValueChange = onNameChanged,
                    label = { Text(stringResource(R.string.wizard_contact_name_label)) },
                    placeholder = { Text(stringResource(R.string.wizard_contact_name_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = contact.phoneNumber,
                    onValueChange = onPhoneChanged,
                    label = { Text(stringResource(R.string.wizard_contact_phone_label)) },
                    placeholder = { Text(stringResource(R.string.wizard_contact_phone_placeholder)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = onRemove,
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Sterge",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

// ─── Page 4: Language ───

@Composable
internal fun LanguagePage(
    appLanguage: String,
    onLanguageChanged: (String) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.wizard_language_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wizard_language_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(20.dp))

        FilterChip(
            selected = appLanguage == "ro",
            onClick = { if (appLanguage != "ro") onLanguageChanged("ro") },
            label = { Text("Romana", style = MaterialTheme.typography.titleMedium) },
            leadingIcon = if (appLanguage == "ro") {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        FilterChip(
            selected = appLanguage == "en",
            onClick = { if (appLanguage != "en") onLanguageChanged("en") },
            label = { Text("English", style = MaterialTheme.typography.titleMedium) },
            leadingIcon = if (appLanguage == "en") {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─── Page 5: Emergency Contacts ───

@Composable
internal fun EmergencyContactsPage(
    emergencyContacts: List<EmergencyContact>,
    onContactsChanged: (List<EmergencyContact>) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.wizard_emergency_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wizard_emergency_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(20.dp))

        emergencyContacts.forEachIndexed { index, contact ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
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
                    IconButton(
                        onClick = {
                            val updated = emergencyContacts.toMutableList()
                            updated.removeAt(index)
                            onContactsChanged(updated)
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.1f))
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Sterge",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        GlassButton(
            onClick = {
                onContactsChanged(emergencyContacts + EmergencyContact())
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
                    stringResource(R.string.wizard_emergency_add),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        if (emergencyContacts.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.wizard_emergency_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── Page 6: Features & Theme ───

@Composable
internal fun FeaturesPage(
    toggles: FeatureToggles,
    onTogglesChanged: (FeatureToggles) -> Unit,
    themeMode: AppThemeMode,
    onThemeModeChanged: (AppThemeMode) -> Unit,
    inactivityThresholdHours: Int,
    onThresholdHoursChanged: (Int) -> Unit,
    hasEmergencyContacts: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = stringResource(R.string.wizard_customize_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.wizard_customize_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Theme
        Text(
            text = stringResource(R.string.wizard_section_theme),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeChip(stringResource(R.string.wizard_theme_system), AppThemeMode.SYSTEM, themeMode, onThemeModeChanged, Modifier.weight(1f))
            ThemeChip(stringResource(R.string.wizard_theme_light), AppThemeMode.LIGHT, themeMode, onThemeModeChanged, Modifier.weight(1f))
            ThemeChip(stringResource(R.string.wizard_theme_dark), AppThemeMode.DARK, themeMode, onThemeModeChanged, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Feature toggles
        Text(
            text = stringResource(R.string.wizard_section_features),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FeatureToggleRow(stringResource(R.string.wizard_feature_quick_contacts), stringResource(R.string.wizard_feature_quick_contacts_desc), Icons.Default.Call, toggles.quickContacts) {
            onTogglesChanged(toggles.copy(quickContacts = it))
        }
        FeatureToggleRow(stringResource(R.string.wizard_feature_dial_pad), stringResource(R.string.wizard_feature_dial_pad_desc), Icons.Default.Phone, toggles.dialPad) {
            onTogglesChanged(toggles.copy(dialPad = it))
        }
        FeatureToggleRow(stringResource(R.string.wizard_feature_contacts), stringResource(R.string.wizard_feature_contacts_desc), Icons.Default.Person, toggles.contacts) {
            onTogglesChanged(toggles.copy(contacts = it))
        }
        FeatureToggleRow(stringResource(R.string.wizard_feature_sms), stringResource(R.string.wizard_feature_sms_desc), Icons.Default.Email, toggles.messages) {
            onTogglesChanged(toggles.copy(messages = it))
        }
        FeatureToggleRow(stringResource(R.string.wizard_feature_gallery), stringResource(R.string.wizard_feature_gallery_desc), Icons.Default.Face, toggles.gallery) {
            onTogglesChanged(toggles.copy(gallery = it))
        }
        FeatureToggleRow("WhatsApp", stringResource(R.string.wizard_feature_whatsapp_desc), Icons.Default.Call, toggles.whatsapp) {
            onTogglesChanged(toggles.copy(whatsapp = it))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Behavior
        Text(
            text = stringResource(R.string.wizard_section_behavior),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FeatureToggleRow(
            stringResource(R.string.wizard_feature_auto_volume),
            stringResource(R.string.wizard_feature_auto_volume_desc),
            Icons.Default.Notifications,
            toggles.autoMaxVolume
        ) {
            onTogglesChanged(toggles.copy(autoMaxVolume = it))
        }
        FeatureToggleRow(
            stringResource(R.string.wizard_feature_inactivity_monitor),
            stringResource(R.string.wizard_feature_inactivity_monitor_desc),
            Icons.Default.Warning,
            toggles.inactivityMonitor
        ) {
            onTogglesChanged(toggles.copy(inactivityMonitor = it))
        }

        if (toggles.inactivityMonitor) {
            if (!hasEmergencyContacts) {
                Text(
                    text = stringResource(R.string.wizard_feature_inactivity_no_contacts),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 40.dp, top = 4.dp, end = 8.dp)
                )
            }
            Row(
                modifier = Modifier
                    .padding(start = 40.dp, top = 8.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.wizard_feature_alert_after),
                    style = MaterialTheme.typography.bodyLarge,
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
                    textStyle = MaterialTheme.typography.titleMedium.copy(textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp)
                )
                Text(
                    text = stringResource(R.string.wizard_feature_hours),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }

        FeatureToggleRow(
            stringResource(R.string.wizard_feature_medication_reminders),
            stringResource(R.string.wizard_feature_medication_reminders_desc),
            Icons.Default.Notifications,
            toggles.medicationReminders
        ) {
            onTogglesChanged(toggles.copy(medicationReminders = it))
        }
        FeatureToggleRow(
            stringResource(R.string.wizard_feature_tts_sms),
            stringResource(R.string.wizard_feature_tts_sms_desc),
            Icons.Default.Email,
            toggles.ttsSms
        ) {
            onTogglesChanged(toggles.copy(ttsSms = it))
        }
    }
}

// ─── Page 5: Settings Info ───

@Composable
internal fun SettingsInfoPage() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.wizard_before_start_title),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        // How to access settings
        InfoCard(
            icon = Icons.Default.Lock,
            title = stringResource(R.string.wizard_settings_info_access_title)
        ) {
            Text(
                text = stringResource(R.string.wizard_settings_info_lock),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.wizard_settings_info_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // What's in settings
        InfoCard(
            icon = Icons.Default.Settings,
            title = stringResource(R.string.wizard_settings_info_contents)
        ) {
            val items = listOf(
                stringResource(R.string.wizard_settings_info_item_contacts),
                stringResource(R.string.wizard_settings_info_item_theme),
                stringResource(R.string.wizard_settings_info_item_tts),
                stringResource(R.string.wizard_settings_info_item_features),
                stringResource(R.string.wizard_settings_info_item_permissions),
                stringResource(R.string.wizard_settings_info_item_advanced)
            )
            items.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("•", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        text = item,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Remote config
        InfoCard(
            icon = Icons.Default.Share,
            title = stringResource(R.string.wizard_settings_info_remote_title)
        ) {
            Text(
                text = stringResource(R.string.wizard_settings_info_config_editor),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wizard_settings_info_how),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            val steps = listOf(
                stringResource(R.string.wizard_settings_info_step1),
                stringResource(R.string.wizard_settings_info_step2),
                stringResource(R.string.wizard_settings_info_step3),
                stringResource(R.string.wizard_settings_info_step4),
                stringResource(R.string.wizard_settings_info_step5)
            )
            steps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.wizard_settings_info_remote_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
internal fun InfoCard(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
internal fun ThemeChip(
    label: String,
    mode: AppThemeMode,
    selected: AppThemeMode,
    onSelected: (AppThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val isSelected = mode == selected
    FilterChip(
        selected = isSelected,
        onClick = { onSelected(mode) },
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
internal fun FeatureToggleRow(
    title: String,
    description: String,
    icon: ImageVector,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(28.dp),
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
    }
}
