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
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ro.softwarechef.freshboomer.data.AppConfig
import ro.softwarechef.freshboomer.data.EmergencyContact
import ro.softwarechef.freshboomer.data.QuickContactRepository
import ro.softwarechef.freshboomer.data.AppThemeMode
import ro.softwarechef.freshboomer.data.FeatureTogglePreference
import ro.softwarechef.freshboomer.data.FeatureToggles
import ro.softwarechef.freshboomer.data.NicknamePreference
import ro.softwarechef.freshboomer.data.ThemePreference
import ro.softwarechef.freshboomer.data.TtsEngine
import ro.softwarechef.freshboomer.data.TtsPreference
import ro.softwarechef.freshboomer.models.QuickContact
import ro.softwarechef.freshboomer.tts.TtsModelManager
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
                title = { Text("Sterge contact") },
                text = { Text("Sigur vrei sa stergi contactul ${contactToDelete.name}?") },
                confirmButton = {
                    Button(
                        onClick = {
                            QuickContactRepository.removeContact(context, deleteConfirmId!!)
                            contacts = QuickContactRepository.getContacts(context)
                            deleteConfirmId = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("Sterge")
                    }
                },
                dismissButton = {
                    OutlinedButton(onClick = { deleteConfirmId = null }) {
                        Text("Anuleaza")
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
                    title = "Contacte Rapide (${contacts.size})",
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
                    OutlinedButton(
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
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Adauga contact")
                    }
                }
            }

            // ─── Group 2: Apps ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "Aplicatii",
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
                    title = "Voce (Text-to-Speech)",
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

            // ─── Group: Nickname ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "Personalizare",
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
                    title = "Limba",
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
                    title = "Contacte de urgenta",
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

            // ─── Group 4: Theme ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "Tema",
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
                    title = "Ecran Principal",
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
                    title = "Permisiuni",
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
                                text = "Verifica permisiuni",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Verifica daca aplicatia are toate permisiunile necesare",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = onOpenOnboarding,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Deschide")
                        }
                    }
                }
            }

            // ─── Group 7: Advanced Config ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "Configurare Avansata",
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
                                text = "Editor JSON Configurare",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Editeaza toate setarile intr-un singur loc",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { showJsonEditor = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Deschide")
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
                                text = "Importa Configurare din URL",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Descarca si aplica o configurare de pe internet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { showImportUrlDialog = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Importa")
                        }
                    }
                }
            }

            // ─── Group 8: Tips ───
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SectionHeader(
                    title = "Sfaturi utile",
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
                    title = "Despre aplicatie",
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
                                text = "Licente si Multumiri",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                text = "Licente open source, credite si informatii",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { showAboutScreen = true },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Deschide")
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
                    "Descarcarea automata in WhatsApp",
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "Pentru ca pozele si filmele primite pe WhatsApp sa apara in \"Vezi poze\", trebuie sa activezi descarcarea automata:",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    GuideStep("1", "Deschide WhatsApp")
                    GuideStep("2", "Apasa pe cele 3 puncte din dreapta sus")
                    GuideStep("3", "Apasa pe \"Setari\" (Settings)")
                    GuideStep("4", "Apasa pe \"Stocare si date\" (Storage and data)")
                    GuideStep("5", "La sectiunea \"Descarcare automata media\", activeaza descarcarea pentru Fotografii si Video pe Wi-Fi si Date mobile")
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Dupa activare, toate pozele si filmele primite pe WhatsApp vor fi salvate automat si vor aparea in galeria \"Vezi poze\".",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showWhatsAppGuide = false }) {
                    Text("Am inteles")
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
                        text = "Poze WhatsApp in galerie",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Cum sa configurezi WhatsApp sa descarce automat poze si filme",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Button(
                    onClick = { showWhatsAppGuide = true },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Vezi")
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
                name = "Google Play",
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
                name = "Setari Dispozitiv",
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
        Button(onClick = onClick) {
            Text("Deschide")
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
                        text = "Voce activata",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Aplicatia vorbeste cand deschizi un ecran",
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
                    selected = selectedEngine == TtsEngine.PIPER_LILI,
                    onClick = { onEngineChange(TtsEngine.PIPER_LILI) }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Piper Lili (recomandat)",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Voce locala romaneasca, functioneaza fara internet",
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
                        text = "Vocea dispozitivului",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Foloseste vocea implicita de pe telefon",
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
                text = "Actualizare model vocal",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = when (checkState) {
                    UpdateCheckState.Idle -> "Apasa pentru a verifica daca exista o versiune noua"
                    UpdateCheckState.Checking -> "Se verifica..."
                    UpdateCheckState.UpToDate -> "Modelul este la zi"
                    UpdateCheckState.Downloading -> {
                        if (totalMb > 0)
                            "Se descarca: %.0f/%.0f MB".format(downloadedMb, totalMb)
                        else
                            "Se descarca: %.0f MB".format(downloadedMb)
                    }
                    UpdateCheckState.Done -> "Actualizare finalizata!"
                    UpdateCheckState.Error -> "Eroare la verificare. Verifica conexiunea la internet."
                    UpdateCheckState.NoModel -> "Modelul nu este descarcat. Se descarca..."
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
        Button(
            onClick = {
                scope.launch {
                    checkState = UpdateCheckState.Checking
                    val modelExists = withContext(Dispatchers.IO) {
                        TtsModelManager.isModelDownloaded(context)
                    }
                    if (!modelExists) {
                        checkState = UpdateCheckState.NoModel
                        val success = TtsModelManager.downloadModel(context) { downloaded, total ->
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
                    val updateAvailable = TtsModelManager.isUpdateAvailable(context)
                    if (!updateAvailable) {
                        checkState = UpdateCheckState.UpToDate
                        return@launch
                    }
                    checkState = UpdateCheckState.Downloading
                    downloadProgress = 0f
                    downloadedMb = 0f
                    totalMb = 0f
                    val success = TtsModelManager.downloadModel(context) { downloaded, total ->
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
            Text("Verifica")
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
                label = "Contacte rapide",
                description = "Butoanele mari de pe ecranul principal",
                checked = toggles.quickContacts,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.QUICK_CONTACTS, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = "Formeaza Numar",
                description = "Tastatura pentru apelat numere",
                checked = toggles.dialPad,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.DIAL_PAD, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = "Agenda Telefon",
                description = "Lista de contacte din telefon",
                checked = toggles.contacts,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.CONTACTS, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = "Mesaje",
                description = "Conversatii SMS",
                checked = toggles.messages,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.MESSAGES, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = "Vezi poze",
                description = "Galeria de fotografii",
                checked = toggles.gallery,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.GALLERY, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = "WhatsApp",
                description = "Buton WhatsApp pe ecranul principal",
                checked = toggles.whatsapp,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.WHATSAPP, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = "Volum automat la maxim",
                description = "Seteaza automat toate volumele la maxim pentru a preveni situatiile in care utilizatorul reduce volumul accidental",
                checked = toggles.autoMaxVolume,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.AUTO_MAX_VOLUME, it) }
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            FeatureToggleRow(
                label = "Monitorizare inactivitate",
                description = "Trimite SMS contactelor de urgenta daca telefonul nu e folosit. Necesita cel putin un contact de urgenta.",
                checked = toggles.inactivityMonitor,
                onCheckedChange = { onToggleChange(FeatureTogglePreference.INACTIVITY_MONITOR, it) }
            )

            if (toggles.inactivityMonitor) {
                if (!hasEmergencyContacts) {
                    Text(
                        text = "Niciun contact de urgenta configurat. Adauga cel putin un contact de urgenta pentru ca aceasta functie sa functioneze.",
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
                        text = "Alerta dupa",
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
                        text = "ore",
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
                text = "Cum sa te numeasca aplicatia",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Acest nume va fi folosit in mesajele vocale (ex: \"$nickname, te-a sunat...\")",
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
                    placeholder = { Text("Mamaie") },
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "Limba aplicatiei",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Alege limba in care este afisata aplicatia",
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
                    onClick = { onLanguageChange("ro") },
                    label = { Text("Romana") },
                    leadingIcon = if (appLanguage == "ro") {
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
                text = "Contacte de urgenta",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = "Aceste persoane vor fi notificate daca utilizatorul are probleme",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (emergencyContacts.isEmpty()) {
                Text(
                    text = "Niciun contact de urgenta adaugat.",
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
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
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
                                    label = { Text("Nume") },
                                    placeholder = { Text("ex: Maria") },
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
                                    label = { Text("Telefon") },
                                    placeholder = { Text("ex: 0712345678") },
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
            OutlinedButton(
                onClick = {
                    val updated = emergencyContacts + EmergencyContact()
                    onContactsChanged(updated)
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Adauga contact de urgenta")
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
                        text = "Aspect vizual",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = "Alege cum arata aplicatia",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = when (selectedTheme) {
                                AppThemeMode.SYSTEM -> "Automat"
                                AppThemeMode.LIGHT -> "Luminos"
                                AppThemeMode.DARK -> "Intunecat"
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("Automat (dupa sistem)", style = MaterialTheme.typography.bodyLarge)
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
                                Text("Luminos", style = MaterialTheme.typography.bodyLarge)
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
                                Text("Intunecat", style = MaterialTheme.typography.bodyLarge)
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
                    label = { Text("Nume") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedTextField(
                    value = editingPhone,
                    onValueChange = { editingPhone = it },
                    label = { Text("Numar telefon") },
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
                        FilledTonalButton(
                            onClick = {
                                if (editingName != contact.name) onNameChange(editingName)
                                if (editingPhone != contact.phoneNumber) onPhoneChange(editingPhone)
                            }
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Salveaza")
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
        "Motor text-to-speech offline (vocea Piper Lili)"
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
                Button(onClick = { showFullLicense = null }) {
                    Text("Inchide")
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
                            OutlinedButton(
                                onClick = { showFullLicense = lib.license },
                                modifier = Modifier.padding(top = 8.dp),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Citeste licenta", style = MaterialTheme.typography.bodySmall)
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
                fontSize = 28.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Licenses
            Text(
                text = "Licente Open Source",
                fontSize = 18.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LicensesSection()

            Spacer(modifier = Modifier.height(20.dp))

            // Credits
            Text(
                text = "Multumiri",
                fontSize = 18.sp,
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
            Button(
                onClick = {
                    if (url.isBlank()) {
                        error = "URL-ul nu poate fi gol"
                        return@Button
                    }
                    isLoading = true
                    error = null
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            ro.softwarechef.freshboomer.data.AppConfig.importFromUrl(context, url.trim())
                        }
                        isLoading = false
                        if (result.isSuccess) {
                            // Persist the URL so the app auto-fetches config on every main screen resume
                            ro.softwarechef.freshboomer.data.ConfigUrlPreference.setUrl(context, url.trim())
                            android.widget.Toast.makeText(context, "Configurare importata cu succes", android.widget.Toast.LENGTH_SHORT).show()
                            onImported()
                        } else {
                            error = "Eroare: ${result.exceptionOrNull()?.localizedMessage ?: "necunoscuta"}"
                        }
                    }
                },
                enabled = !isLoading
            ) {
                Text("Importa")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                enabled = !isLoading
            ) {
                Text("Anuleaza")
            }
        }
    )
}
