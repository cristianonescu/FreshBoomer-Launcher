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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ro.softwarechef.freshboomer.data.*
import ro.softwarechef.freshboomer.models.QuickContact
import ro.softwarechef.freshboomer.services.InactivityMonitorWorker
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

private const val TOTAL_PAGES = 7

@Composable
fun SetupWizardScreen(
    onThemeChanged: () -> Unit = {},
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    var currentPage by remember { mutableIntStateOf(0) }

    // Wizard state
    var nickname by remember { mutableStateOf(NicknamePreference.getNickname(context)) }
    var quickContacts by remember { mutableStateOf(listOf<QuickContact>()) }
    var featureToggles by remember { mutableStateOf(FeatureTogglePreference.getToggles(context)) }
    var themeMode by remember { mutableStateOf(ThemePreference.getThemeMode(context)) }
    var appLanguage by remember { mutableStateOf(AppConfig.current.appLanguage) }
    var emergencyContacts by remember { mutableStateOf(AppConfig.current.emergencyContacts) }
    var inactivityThresholdHours by remember { mutableIntStateOf(AppConfig.current.inactivityMonitorThresholdHours) }

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
            themeMode = themeMode.name,
            appLanguage = appLanguage,
            emergencyContacts = emergencyContacts,
            quickContacts = quickContacts
        )
        AppConfig.save(context, config)

        // Schedule or cancel inactivity monitor based on new config
        InactivityMonitorWorker.reschedule(context)

        // Mark setup as completed
        SetupWizardPreference.setCompleted(context, true)
        onComplete()
    }

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
            text = "Pasul ${currentPage + 1} din $TOTAL_PAGES",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Page content
        Box(
            modifier = Modifier.weight(1f)
        ) {
            when (currentPage) {
                0 -> WelcomePage()
                1 -> NicknamePage(
                    nickname = nickname,
                    onNicknameChanged = { nickname = it }
                )
                2 -> QuickContactsPage(
                    contacts = quickContacts,
                    onContactsChanged = { quickContacts = it }
                )
                3 -> LanguagePage(
                    appLanguage = appLanguage,
                    onLanguageChanged = { appLanguage = it }
                )
                4 -> EmergencyContactsPage(
                    emergencyContacts = emergencyContacts,
                    onContactsChanged = { emergencyContacts = it }
                )
                5 -> FeaturesPage(
                    toggles = featureToggles,
                    onTogglesChanged = { featureToggles = it },
                    themeMode = themeMode,
                    onThemeModeChanged = {
                        themeMode = it
                        ThemePreference.setThemeMode(context, it)
                        onThemeChanged()
                    },
                    inactivityThresholdHours = inactivityThresholdHours,
                    onThresholdHoursChanged = { inactivityThresholdHours = it },
                    hasEmergencyContacts = emergencyContacts.any { it.phoneNumber.isNotBlank() }
                )
                6 -> SettingsInfoPage()
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
                OutlinedButton(
                    onClick = { currentPage-- },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Inapoi", fontSize = 18.sp)
                }
            } else {
                // Skip button on first page
                TextButton(
                    onClick = { saveAndFinish() },
                    modifier = Modifier.height(52.dp)
                ) {
                    Text("Sari peste", fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            if (currentPage < TOTAL_PAGES - 1) {
                Button(
                    onClick = { currentPage++ },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Inainte", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            } else {
                Button(
                    onClick = { saveAndFinish() },
                    modifier = Modifier.height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Text("Finalizeaza", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

// ─── Page 1: Welcome ───

@Composable
private fun WelcomePage() {
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
            text = "Bine ai venit!",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "FreshBoomer este un launcher simplu care face telefonul mai usor de folosit.",
            fontSize = 20.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "In pasii urmatori poti configura aplicatia. Daca nu doresti sa faci modificari, apasa \"Sari peste\" pentru a folosi valorile implicite.",
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

// ─── Page 2: Nickname ───

@Composable
private fun NicknamePage(
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
            text = "Cum ii spui utilizatorului?",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Acest nume va fi folosit in anunturile vocale, de exemplu: \"Mamaie, ai un apel pierdut\"",
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = onNicknameChanged,
            label = { Text("Porecla", fontSize = 18.sp) },
            placeholder = { Text("ex: Mamaie, Bunica, Tata") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 24.sp),
            singleLine = true,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

// ─── Page 3: Quick Contacts ───

@Composable
private fun QuickContactsPage(
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
            text = "Contacte Rapide",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Adauga persoanele pe care utilizatorul le suna cel mai des. Vor aparea ca butoane mari pe ecranul principal.",
            fontSize = 16.sp,
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
            OutlinedButton(
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
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Adauga contact", fontSize = 18.sp)
            }

            if (contacts.isEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Niciun contact adaugat. Poti adauga contacte oricand mai tarziu din Setari.",
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun QuickContactRow(
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
                    label = { Text("Nume") },
                    placeholder = { Text("ex: Maria") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = contact.phoneNumber,
                    onValueChange = onPhoneChanged,
                    label = { Text("Telefon") },
                    placeholder = { Text("ex: 0712345678") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp),
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
private fun LanguagePage(
    appLanguage: String,
    onLanguageChanged: (String) -> Unit
) {
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
            text = "Limba aplicatiei",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Alege limba in care va fi afisata aplicatia.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(20.dp))

        FilterChip(
            selected = appLanguage == "ro",
            onClick = { onLanguageChanged("ro") },
            label = { Text("Romana", fontSize = 18.sp) },
            leadingIcon = if (appLanguage == "ro") {
                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
            } else null,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ─── Page 5: Emergency Contacts ───

@Composable
private fun EmergencyContactsPage(
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
            text = "Contacte de urgenta",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Adauga persoane care vor fi notificate daca utilizatorul are probleme.",
            fontSize = 16.sp,
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
        OutlinedButton(
            onClick = {
                onContactsChanged(emergencyContacts + EmergencyContact())
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Adauga contact de urgenta", fontSize = 16.sp)
        }

        if (emergencyContacts.isEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Niciun contact de urgenta adaugat. Poti adauga contacte de urgenta oricand mai tarziu din Setari.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

// ─── Page 6: Features & Theme ───

@Composable
private fun FeaturesPage(
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
            text = "Personalizare",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Alege ce functionalitati sa fie vizibile pe ecranul principal.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(20.dp))

        // Theme
        Text(
            text = "Tema",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeChip("Sistem", AppThemeMode.SYSTEM, themeMode, onThemeModeChanged, Modifier.weight(1f))
            ThemeChip("Luminos", AppThemeMode.LIGHT, themeMode, onThemeModeChanged, Modifier.weight(1f))
            ThemeChip("Intunecat", AppThemeMode.DARK, themeMode, onThemeModeChanged, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Feature toggles
        Text(
            text = "Functionalitati",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FeatureToggleRow("Contacte rapide", "Butoane mari de apel pe ecranul principal", Icons.Default.Call, toggles.quickContacts) {
            onTogglesChanged(toggles.copy(quickContacts = it))
        }
        FeatureToggleRow("Tastatura de apel", "Formeaza un numar de telefon", Icons.Default.Phone, toggles.dialPad) {
            onTogglesChanged(toggles.copy(dialPad = it))
        }
        FeatureToggleRow("Agenda", "Lista de contacte din telefon", Icons.Default.Person, toggles.contacts) {
            onTogglesChanged(toggles.copy(contacts = it))
        }
        FeatureToggleRow("Mesaje SMS", "Citeste si trimite mesaje", Icons.Default.Email, toggles.messages) {
            onTogglesChanged(toggles.copy(messages = it))
        }
        FeatureToggleRow("Galerie foto", "Vizualizeaza pozele din telefon", Icons.Default.Face, toggles.gallery) {
            onTogglesChanged(toggles.copy(gallery = it))
        }
        FeatureToggleRow("WhatsApp", "Buton WhatsApp pe ecranul principal", Icons.Default.Call, toggles.whatsapp) {
            onTogglesChanged(toggles.copy(whatsapp = it))
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Behavior
        Text(
            text = "Comportament",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        FeatureToggleRow(
            "Volum automat la maxim",
            "Seteaza automat toate volumele la maxim pentru a preveni situatiile in care utilizatorul reduce volumul accidental",
            Icons.Default.Notifications,
            toggles.autoMaxVolume
        ) {
            onTogglesChanged(toggles.copy(autoMaxVolume = it))
        }
        FeatureToggleRow(
            "Monitorizare inactivitate",
            "Trimite SMS contactelor de urgenta daca telefonul nu e folosit. Necesita cel putin un contact de urgenta.",
            Icons.Default.Warning,
            toggles.inactivityMonitor
        ) {
            onTogglesChanged(toggles.copy(inactivityMonitor = it))
        }

        if (toggles.inactivityMonitor) {
            if (!hasEmergencyContacts) {
                Text(
                    text = "Niciun contact de urgenta configurat. Adauga cel putin un contact de urgenta (pasul anterior) pentru ca aceasta functie sa functioneze.",
                    fontSize = 14.sp,
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
                    text = "Alerta dupa",
                    fontSize = 16.sp,
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
                    textStyle = LocalTextStyle.current.copy(fontSize = 18.sp, textAlign = TextAlign.Center),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(10.dp)
                )
                Text(
                    text = "ore",
                    fontSize = 16.sp,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

// ─── Page 5: Settings Info ───

@Composable
private fun SettingsInfoPage() {
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
            text = "Inainte de a incepe",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))

        // How to access settings
        InfoCard(
            icon = Icons.Default.Lock,
            title = "Cum accesezi Setarile"
        ) {
            Text(
                text = "Pe ecranul principal, in partea de stanga jos, vei vedea o mica iconita cu un lacat. Apasa rapid de 5 ori pe ea in mai putin de 3 secunde pentru a deschide setarile.",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Aceasta metoda previne deschiderea accidentala a setarilor de catre utilizatorul varstnic.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // What's in settings
        InfoCard(
            icon = Icons.Default.Settings,
            title = "Ce gasesti in Setari"
        ) {
            val items = listOf(
                "Contacte rapide — adauga, sterge, reordoneaza si seteaza poze",
                "Tema — schimba intre modul luminos, intunecat sau automat",
                "Voce (TTS) — activeaza/dezactiveaza anunturile vocale",
                "Functionalitati — alege ce butoane apar pe ecranul principal",
                "Permisiuni — re-verifica permisiunile aplicatiei",
                "Configurare avansata — editor JSON si import din URL"
            )
            items.forEach { item ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("•", fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))
                    Text(
                        text = item,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Remote config
        InfoCard(
            icon = Icons.Default.Share,
            title = "Configurare de la distanta"
        ) {
            Text(
                text = "Poti edita configurarea aplicatiei de pe un calculator folosind fisierul config-editor.html inclus in proiect. Acesta iti permite sa modifici toate setarile vizual si sa exporti un fisier JSON.",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cum functioneaza:",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold
            )
            val steps = listOf(
                "1. Deschide config-editor.html in browser pe calculator",
                "2. Modifica setarile si contactele",
                "3. Exporta fisierul JSON",
                "4. Incarca fisierul pe un server web (GitHub Gist, Pastebin, un server propriu etc.)",
                "5. In aplicatie: Setari → Configurare Avansata → Importa din URL → lipeste link-ul"
            )
            steps.forEach { step ->
                Text(
                    text = step,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Astfel poti actualiza setarile de la distanta fara sa ai acces fizic la telefon.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun InfoCard(
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
                    fontSize = 20.sp,
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
private fun ThemeChip(
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
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        modifier = modifier.height(44.dp),
        shape = RoundedCornerShape(12.dp)
    )
}

@Composable
private fun FeatureToggleRow(
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
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    fontSize = 14.sp,
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
