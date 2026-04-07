package ro.softwarechef.freshboomer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.res.stringResource
import ro.softwarechef.freshboomer.R
import ro.softwarechef.freshboomer.data.AppConfig
import ro.softwarechef.freshboomer.data.ConfigUrlPreference
import ro.softwarechef.freshboomer.data.FeatureTogglePreference
import ro.softwarechef.freshboomer.data.NicknamePreference
import ro.softwarechef.freshboomer.data.QuickContactRepository
import ro.softwarechef.freshboomer.onboarding.OnboardingChecker
import ro.softwarechef.freshboomer.onboarding.OnboardingScreen
import ro.softwarechef.freshboomer.onboarding.SetupWizardPreference
import ro.softwarechef.freshboomer.onboarding.SetupWizardScreen
import ro.softwarechef.freshboomer.models.QuickContact
import ro.softwarechef.freshboomer.services.InactivityMonitorWorker
import ro.softwarechef.freshboomer.tts.PiperTtsEngine
import ro.softwarechef.freshboomer.tts.TtsStatusFooter
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ro.softwarechef.freshboomer.ui.composables.ConfirmCallDialog
import ro.softwarechef.freshboomer.ui.composables.HideSystemBars
import ro.softwarechef.freshboomer.ui.composables.ImmersiveActivity
import ro.softwarechef.freshboomer.ui.composables.QuickContactSettingsScreen
import ro.softwarechef.freshboomer.ui.theme.LauncherTheme
import kotlinx.coroutines.delay
import java.io.File
import java.text.SimpleDateFormat
import java.time.Year
import java.util.Date
import java.util.Locale

class MainActivity : ImmersiveActivity() {
    // run in terminal:
    // adb shell
    // then run the following to disabled notification bar:
    // settings put global policy_control immersive.status=*
    // settings put global policy_control immersive.full=*
    // settings put global policy_control immersive.navigation=*

    // to disable run the following:
    // settings put global policy_control null

    // Mutable state to trigger recomposition when returning from settings/permission dialogs
    private var resumeVersion = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize centralized config (migrates from SharedPreferences on first run)
        ro.softwarechef.freshboomer.data.AppConfig.init(this)
        // Seed default contacts on first launch only
        QuickContactRepository.ensureDefaults(this)
        // Schedule inactivity monitor if enabled
        InactivityMonitorWorker.schedule(this)
        setContent {
            var showSettings by remember { mutableStateOf(false) }
            var showOnboarding by remember { mutableStateOf(false) }
            var setupCompleted by remember { mutableStateOf(SetupWizardPreference.isCompleted(this@MainActivity)) }
            var contactsVersion by remember { mutableIntStateOf(0) }
            var themeVersion by remember { mutableIntStateOf(0) }
            var ttsReady by remember { mutableStateOf(PiperTtsEngine.isReady) }
            val coroutineScope = rememberCoroutineScope()

            // Re-check onboarding state whenever resumeVersion changes
            val currentResumeVersion by resumeVersion
            val onboardingState = remember(currentResumeVersion) {
                OnboardingChecker.check(this@MainActivity)
            }

            HideSystemBars()
            if (!setupCompleted) {
                LauncherTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize()
                            .windowInsetsPadding(WindowInsets.systemBars),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        SetupWizardScreen(
                            onThemeChanged = { /* theme applied via configFlow */ },
                            onComplete = {
                                setupCompleted = true
                                QuickContactRepository.invalidateCache()
                                contactsVersion++
                                themeVersion++
                                // Recreate so attachBaseContext() applies the selected language
                                recreate()
                            }
                        )
                    }
                }
            } else {
            key(themeVersion) {
            LauncherTheme {
                Surface(
                    modifier = Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.systemBars),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!onboardingState.allGranted || showOnboarding) {
                        OnboardingScreen(
                            state = onboardingState,
                            onAllGranted = {
                                showOnboarding = false
                                resumeVersion.intValue++
                            },
                            onStateChanged = { resumeVersion.intValue++ }
                        )
                    } else if (showSettings) {
                        SettingsScreen(
                            onBackClick = {
                                QuickContactRepository.invalidateCache()
                                contactsVersion++
                                showSettings = false
                            },
                            onThemeChanged = { themeVersion++ },
                            onOpenOnboarding = {
                                showSettings = false
                                showOnboarding = true
                            }
                        )
                    } else {
                        // Force recomposition when contactsVersion changes
                        key(contactsVersion) {
                            LauncherScreen(
                                onSmsClick = {
                                    val nick = NicknamePreference.getNickname(this@MainActivity)
                                    speakOutLoud(getString(R.string.tts_open_sms, nick))
                                    startActivity(Intent(this, SmsActivity::class.java))
                                },
                                onPhoneClick = {
                                    val nick = NicknamePreference.getNickname(this@MainActivity)
                                    speakOutLoud(getString(R.string.tts_open_phone, nick))
                                    startActivity(Intent(this, PhoneActivity::class.java))
                                },
                                onContactsClick = {
                                    val nick = NicknamePreference.getNickname(this@MainActivity)
                                    speakOutLoud(getString(R.string.tts_open_contacts, nick))
                                    startActivity(Intent(this, ContactsActivity::class.java))
                                },
                                onGalleryClick = {
                                    val nick = NicknamePreference.getNickname(this@MainActivity)
                                    speakOutLoud(getString(R.string.tts_open_gallery, nick))
                                    startActivity(Intent(this, GalleryActivity::class.java))
                                },
                                onSettingsClick = { showSettings = true },
                                showSettingsButton = false,
                                footerContent = {
                                    TtsStatusFooter(
                                        onComplete = { voice ->
                                            coroutineScope.launch(Dispatchers.IO) {
                                                PiperTtsEngine.initialize(this@MainActivity, voice)
                                                withContext(Dispatchers.Main) {
                                                    ttsReady = true
                                                }
                                            }
                                        },
                                        copyrightText = run {
                                            val nick = NicknamePreference.getNickname(this@MainActivity)
                                            val pkgInfo = packageManager.getPackageInfo(packageName, 0)
                                            val versionName = pkgInfo.versionName ?: "?"
                                            val versionCode = pkgInfo.longVersionCode
                                            getString(R.string.footer_copyright, nick, versionName, versionCode, Year.now().value)
                                        },
                                        onSettingsClick = { showSettings = true }
                                    )
                                }
                            )
                        }
                    }
                }
            }
            }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-check onboarding state every time we resume
        resumeVersion.intValue++

        // If resuming from a call, do NOT speak
        if (resumedFromCall) {
            resumedFromCall = false
            return
        }

        // Also protect against edge cases
        if (isInCall()) return

        setMaxVolume()
        refreshLastCall()
        fetchRemoteConfigIfNeeded()
    }

    private fun fetchRemoteConfigIfNeeded() {
        val url = ConfigUrlPreference.getUrl(this)
        if (url.isBlank()) return
        val langBefore = AppConfig.current.appLanguage
        CoroutineScope(Dispatchers.IO).launch {
            val result = AppConfig.importFromUrl(this@MainActivity, url)
            if (result.isSuccess && AppConfig.current.appLanguage != langBefore) {
                withContext(Dispatchers.Main) {
                    recreate()
                }
            }
        }
    }

    fun sunaRapid(number: String): () -> Unit = {
        // Make the phone call
        makePhoneCall(number)
    }
}

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onThemeChanged: () -> Unit = {},
    onOpenOnboarding: () -> Unit = {}
) {
    QuickContactSettingsScreen(
        onBackClick = onBackClick,
        onThemeChanged = onThemeChanged,
        onOpenOnboarding = onOpenOnboarding
    )
}

@Composable
fun LauncherScreen(
    onSmsClick: () -> Unit,
    onPhoneClick: () -> Unit,
    onContactsClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onSettingsClick: () -> Unit,
    showSettingsButton: Boolean = false,
    footerContent: @Composable () -> Unit = {}
) {
    val activity = LocalActivity.current as? MainActivity ?: return
    val context = LocalContext.current
    val batteryState by rememberBatteryState()

    val quickContacts = remember { QuickContactRepository.getContacts(context) }

    var confirmCallName by remember { mutableStateOf<String?>(null) }
    var confirmCallNumber by remember { mutableStateOf<String?>(null) }
    var confirmProfile by remember {mutableStateOf<Int?>(null)}
    var confirmCallPhotoUri by remember { mutableStateOf<String?>(null) }
    var confirmIcon by remember {mutableStateOf<ImageVector?>(null)}

    // Observe activity's lastCalledNumber
    val lastCalledNumber by produceState<ImmersiveActivity.LastCaller?>(initialValue = null) {
        activity.lastCalledNumber?.let { value = it }
        // You might need to add a way to observe changes
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // ✅ Confirmation dialog
        if (confirmCallName != null && confirmCallNumber != null) {
            ConfirmCallDialog(
                profile = confirmProfile,
                photoUri = confirmCallPhotoUri,
                icon = confirmIcon,
                name = confirmCallName!!,
                number = confirmCallNumber!!,
                onPhoneCall = {
                    activity.makePhoneCall(confirmCallNumber!!)
                    confirmCallName = null
                    confirmCallNumber = null
                    confirmProfile = null
                    confirmCallPhotoUri = null
                    confirmIcon = null
                },
                onDismiss = {
                    confirmCallName = null
                    confirmCallNumber = null
                    confirmProfile = null
                    confirmCallPhotoUri = null
                    confirmIcon = null
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
//            Row(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .padding(bottom = 32.dp),
//                horizontalArrangement = Arrangement.Center,
//                verticalAlignment = Alignment.CenterVertically
//            ) {
//                Text(
//                    text = "",
//                    style = MaterialTheme.typography.displayLarge,
//                    textAlign = TextAlign.Center,
//                    modifier = Modifier.weight(1f)
//                )
//                if (showSettingsButton) {
//                    IconButton(onClick = onSettingsClick) {
//                        Icon(
//                            Icons.Default.Settings,
//                            contentDescription = "Settings",
//                            modifier = Modifier.size(32.dp)
//                        )
//                    }
//                }
//            }

            val nick = NicknamePreference.getNickname(context)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (batteryState.level < 35 && !batteryState.isCharging) {
                    activity.speakOutLoud(context.getString(R.string.tts_battery_low, nick))
                    LowBatteryOverlay(batteryState.level)
                }

                if (batteryState.isCharging && !batteryState.isFull) {
                    activity.speakOutLoud(context.getString(R.string.tts_battery_charging, nick))
                    ChargingOverlay(batteryState.level)
                }

                if (batteryState.isFull) {
                    activity.speakOutLoud(context.getString(R.string.tts_battery_full, nick))
                    FullyChargedOverlay()
                }
                SystemClock(modifier = Modifier.padding(top = 16.dp))
                if(!lastCalledNumber?.number.isNullOrEmpty()) {
                    LastCallerBanner(
                        modifier = Modifier.padding(top = 16.dp),
                        lastCaller = lastCalledNumber,  // Pass it as parameter
                        onQuickCall = { name, number, profile, icon ->
                            confirmCallName = name
                            confirmCallNumber = number
                            confirmProfile = profile
                            confirmIcon = icon
                        })
                }
            }

            GridLayout(
                contacts = quickContacts,
                onSmsClick = onSmsClick,
                onPhoneClick = onPhoneClick,
                onContactsClick = onContactsClick,
                onGalleryClick = onGalleryClick,
                onQuickCall = { name, number, profile, photoUri, icon ->
                    confirmCallName = name
                    confirmCallNumber = number
                    confirmProfile = profile
                    confirmCallPhotoUri = photoUri
                    confirmIcon = icon
                },
                modifier = Modifier.weight(1f)
            )

            footerContent()
        }
    }
}

@Composable
fun GridLayout(
    contacts: List<QuickContact>,
    onSmsClick: () -> Unit,
    onPhoneClick: () -> Unit,
    onContactsClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onQuickCall: (name: String, number: String, profile: Int?, photoUri: String?, icon: ImageVector?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val toggles = remember { FeatureTogglePreference.getToggles(context) }
    val contactRows = contacts.chunked(3)
    val numContactRows = if (toggles.quickContacts) contactRows.size else 0
    val scrollable = numContactRows > 3 // more than 9 contacts

    // Scale text style based on number of rows
    val contactTextStyle = when {
        numContactRows <= 2 -> MaterialTheme.typography.titleLarge
        numContactRows == 3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.bodyLarge
    }

    val utilityButtonCount = listOf(toggles.dialPad, toggles.contacts, toggles.messages).count { it }
    val hasUtilityRow = utilityButtonCount > 0
    val hasGallery = toggles.gallery
    val hasContacts = toggles.quickContacts && numContactRows > 0

    // Count total weighted rows to distribute space proportionally
    val totalWeightedRows = (if (hasContacts && !scrollable) numContactRows else 0) +
            (if (hasUtilityRow) 1 else 0)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasContacts) {
            if (scrollable) {
                // Scrollable contact grid for >9 contacts
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (row in contactRows) {
                        ContactRow(row, context, onQuickCall, contactTextStyle)
                    }
                }
            } else {
                // Fixed contact rows that share space proportionally
                for (row in contactRows) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (contact in row) {
                            val drawableResId = contact.drawableResName?.let {
                                context.resources.getIdentifier(it, "drawable", context.packageName)
                            }?.takeIf { it != 0 }

                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                GridButton(
                                    text = contact.name,
                                    profile = if (contact.photoUri == null) drawableResId else null,
                                    photoUri = contact.photoUri,
                                    onClick = {
                                        onQuickCall(
                                            contact.name,
                                            contact.phoneNumber,
                                            if (contact.photoUri == null) drawableResId else null,
                                            contact.photoUri,
                                            null
                                        )
                                    },
                                    modifier = Modifier,
                                    textStyle = contactTextStyle,
                                )
                            }
                        }
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        // Utility buttons
        if (hasUtilityRow) {
            if (!hasContacts && utilityButtonCount <= 2) {
                // Without quick contacts and few buttons: stack vertically, each ~15% screen height
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (toggles.dialPad) {
                        GridButton(
                            text = stringResource(R.string.main_dial_number),
                            icon = Icons.Default.Call,
                            onClick = onPhoneClick,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f),
                            roundedSquare = true,
                        )
                    }
                    if (toggles.contacts) {
                        GridButton(
                            text = stringResource(R.string.main_phone_book),
                            icon = Icons.Default.Person,
                            onClick = onContactsClick,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f),
                            roundedSquare = true,
                        )
                    }
                    if (toggles.messages) {
                        GridButton(
                            text = stringResource(R.string.main_messages),
                            icon = Icons.Default.Email,
                            onClick = onSmsClick,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f),
                            roundedSquare = true,
                        )
                    }
                }
            } else if (!hasContacts) {
                // Without quick contacts but all 3 buttons: horizontal row, ~15% screen height
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (toggles.dialPad) {
                        GridButton(
                            text = stringResource(R.string.main_dial_number),
                            icon = Icons.Default.Call,
                            onClick = onPhoneClick,
                            modifier = Modifier.weight(1f),
                            roundedSquare = true,
                        )
                    }
                    if (toggles.contacts) {
                        GridButton(
                            text = stringResource(R.string.main_phone_book),
                            icon = Icons.Default.Person,
                            onClick = onContactsClick,
                            modifier = Modifier.weight(1f),
                            roundedSquare = true,
                        )
                    }
                    if (toggles.messages) {
                        GridButton(
                            text = stringResource(R.string.main_messages),
                            icon = Icons.Default.Email,
                            onClick = onSmsClick,
                            modifier = Modifier.weight(1f),
                            roundedSquare = true,
                        )
                    }
                }
            } else {
                // With quick contacts: fixed height row
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (toggles.dialPad) {
                        GridButton(
                            text = stringResource(R.string.main_dial_number),
                            icon = Icons.Default.Call,
                            onClick = onPhoneClick,
                            modifier = Modifier.weight(1f),
                            roundedSquare = true,
                        )
                    }
                    if (toggles.contacts) {
                        GridButton(
                            text = stringResource(R.string.main_phone_book),
                            icon = Icons.Default.Person,
                            onClick = onContactsClick,
                            modifier = Modifier.weight(1f),
                            roundedSquare = true,
                        )
                    }
                    if (toggles.messages) {
                        GridButton(
                            text = stringResource(R.string.main_messages),
                            icon = Icons.Default.Email,
                            onClick = onSmsClick,
                            modifier = Modifier.weight(1f),
                            roundedSquare = true,
                        )
                    }
                }
            }
        }

        // Gallery and WhatsApp are fixed-height action bars, not weighted
        if (hasGallery) {
            Button(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                onClick = onGalleryClick,
                shape = RoundedCornerShape(20.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.Face,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.main_gallery),
                    style = MaterialTheme.typography.titleLarge,
                    color = colorResource(id = R.color.white)
                )
            }
        }

        if (toggles.whatsapp) {
            val activity = LocalActivity.current as? ImmersiveActivity
            Button(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                onClick = { activity?.launchWhatsApp() },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF25D366))
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = "WhatsApp",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "WhatsApp",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White
                )
            }
        }
    }
}

@Composable
private fun ContactRow(
    row: List<QuickContact>,
    context: android.content.Context,
    onQuickCall: (name: String, number: String, profile: Int?, photoUri: String?, icon: ImageVector?) -> Unit,
    textStyle: androidx.compose.ui.text.TextStyle
) {
    val buttonSize = 100.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        for (contact in row) {
            val drawableResId = contact.drawableResName?.let {
                context.resources.getIdentifier(it, "drawable", context.packageName)
            }?.takeIf { it != 0 }

            GridButton(
                text = contact.name,
                profile = if (contact.photoUri == null) drawableResId else null,
                photoUri = contact.photoUri,
                onClick = {
                    onQuickCall(
                        contact.name,
                        contact.phoneNumber,
                        if (contact.photoUri == null) drawableResId else null,
                        contact.photoUri,
                        null
                    )
                },
                modifier = Modifier.size(buttonSize),
                textStyle = textStyle,
            )
        }
    }
}

@Composable
fun GridButton(
    text: String,
    icon: ImageVector? = null,
    profile: Int? = null,
    photoUri: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    roundedSquare: Boolean = false,
    textStyle: androidx.compose.ui.text.TextStyle? = null,
) {
    val resolvedTextStyle = textStyle ?: MaterialTheme.typography.titleLarge
    val hasImage = profile != null || photoUri != null

    if (visible) {
        Button(
            onClick = onClick,
            modifier = if (roundedSquare) modifier.fillMaxHeight()
                else modifier.fillMaxHeight().aspectRatio(1f),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 6.dp,
                pressedElevation = 12.dp,
                focusedElevation = 8.dp,
                hoveredElevation = 8.dp
            ),
            shape = if (roundedSquare) RoundedCornerShape(20.dp) else RoundedCornerShape(200.dp),
            colors = if (roundedSquare) {
                ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3A4D))
            } else if (!hasImage) {
                ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.gray))
            } else {
                ButtonDefaults.buttonColors(containerColor = Color.Transparent)
            },
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(modifier = Modifier
                .fillMaxSize()
                .then(
                    if (roundedSquare) Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF3A4A60),
                                Color(0xFF2E3A4D)
                            )
                        )
                    ) else Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.25f),
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.35f)
                            )
                        )
                    )
                ),
                contentAlignment = Alignment.Center
            ) {
                if (!hasImage && icon != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = text,
                            tint = if (roundedSquare) Color(0xFF90CAF9) else Color.White,
                            modifier = Modifier.size(if (roundedSquare) 36.dp else 48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = text,
                            style = if (roundedSquare) MaterialTheme.typography.bodyLarge else resolvedTextStyle,
                            color = Color.White,
                            textAlign = TextAlign.Center,
                            maxLines = 2
                        )
                    }
                } else if (!hasImage && icon == null) {
                    // Quick contact without photo — show initials + name
                    val initials = text.trim().split("\\s+".toRegex())
                        .take(2)
                        .mapNotNull { it.firstOrNull()?.uppercaseChar() }
                        .joinToString("")
                        .ifEmpty { "?" }
                    val bgColors = listOf(
                        Color(0xFF5C6BC0), Color(0xFF26A69A), Color(0xFFEF5350),
                        Color(0xFFAB47BC), Color(0xFF42A5F5), Color(0xFFFF7043)
                    )
                    val bgColor = bgColors[text.hashCode().and(0x7FFFFFFF) % bgColors.size]
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        bgColor.copy(alpha = 0.9f),
                                        bgColor.copy(alpha = 0.6f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(
                                text = initials,
                                style = resolvedTextStyle.copy(
                                    fontSize = resolvedTextStyle.fontSize * 1.6,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    color = Color.White
                                ),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = text,
                                style = resolvedTextStyle.copy(
                                    fontSize = resolvedTextStyle.fontSize * 0.7,
                                    color = Color.White.copy(alpha = 0.9f),
                                    shadow = Shadow(
                                        color = Color.Black.copy(alpha = 0.5f),
                                        offset = Offset(1f, 1f),
                                        blurRadius = 3f
                                    )
                                ),
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (photoUri != null) {
                    // Load from internal file via Coil
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(photoUri))
                            .crossfade(true)
                            .build(),
                        contentDescription = text,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = text,
                        style = resolvedTextStyle.copy(
                            color = Color.White,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset(2f, 2f),
                                blurRadius = 6f
                            )
                        ),
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                } else if (profile != null) {
                    Image(
                        painter = painterResource(profile),
                        contentDescription = text,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Text(
                        text = text,
                        style = resolvedTextStyle.copy(
                            color = Color.White,
                            shadow = Shadow(
                                color = Color.Black.copy(alpha = 0.8f),
                                offset = Offset(2f, 2f),
                                blurRadius = 6f
                            )
                        ),
                        modifier = Modifier.align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    } else {
        Box(modifier = modifier.aspectRatio(1f)) {}
    }
}

@Composable
fun LastCallerBanner(
    modifier: Modifier = Modifier,
    lastCaller: ImmersiveActivity.LastCaller?,
    onQuickCall: (name: String, number: String, profile: Int?, icon: ImageVector?) -> Unit
) {
    lastCaller?.let { caller ->
        // Don't show if older than 4 hours
        val ageMs = System.currentTimeMillis() - caller.timestamp
        if (caller.timestamp > 0L && ageMs > 4 * 60 * 60 * 1000L) return

        val displayName = caller.name ?: caller.number

        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFF2E3A4D)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    tint = Color(0xFF90CAF9),
                    modifier = Modifier.size(32.dp)
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Apel pierdut",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "$displayName la ora ${caller.time}",
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                }
                Spacer(Modifier.width(12.dp))
                Button(
                    onClick = {
                        onQuickCall(
                            displayName,
                            caller.number,
                            null,
                            Icons.Default.Call
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.call), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
fun SystemClock(
    modifier: Modifier = Modifier,
    timeFormat: String = "HH:mm"
) {
    var currentTime by remember { mutableStateOf("") }
    var currentDate by remember { mutableStateOf("") }
    var hours by remember { mutableIntStateOf(0) }
    var minutes by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            val now = java.util.Calendar.getInstance()
            hours = now.get(java.util.Calendar.HOUR)
            minutes = now.get(java.util.Calendar.MINUTE)
            seconds = now.get(java.util.Calendar.SECOND)
            val sdf = SimpleDateFormat(timeFormat, Locale.getDefault())
            val sdf2 = SimpleDateFormat("EEEE, d MMMM yyyy", ro.softwarechef.freshboomer.data.LocaleHelper.getLocale())
            currentTime = sdf.format(Date())
            currentDate = sdf2.format(Date())
            delay(1000)
        }
    }

    val clockColor = MaterialTheme.colorScheme.onBackground

    val clockSize = 40.dp

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(clockSize)
            .padding(bottom = 0.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnalogClock(
            hours = hours,
            minutes = minutes,
            seconds = seconds,
            color = clockColor,
            modifier = Modifier.size(clockSize)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = currentTime,
            style = MaterialTheme.typography.titleLarge,
            color = clockColor,
            lineHeight = clockSize.value.sp
        )
        Spacer(Modifier.width(50.dp))
        Text(
            text = "\uD83D\uDDD3\uFE0F " + currentDate,
            style = MaterialTheme.typography.headlineLarge,
            color = clockColor,
            lineHeight = clockSize.value.sp
        )
    }
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun AnalogClock(
    hours: Int,
    minutes: Int,
    seconds: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    val hourAngle = Math.toRadians(((hours % 12) * 30.0 + minutes * 0.5) - 90.0).toFloat()
    val minuteAngle = Math.toRadians((minutes * 6.0 + seconds * 0.1) - 90.0).toFloat()
    val secondAngle = Math.toRadians((seconds * 6.0) - 90.0).toFloat()

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f

        // Clock face circle
        drawCircle(
            color = color.copy(alpha = 0.15f),
            radius = radius,
            center = Offset(cx, cy)
        )
        drawCircle(
            color = color.copy(alpha = 0.6f),
            radius = radius,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )

        // Hour tick marks
        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0 - 90.0).toFloat()
            val innerR = radius * 0.82f
            val outerR = radius * 0.95f
            drawLine(
                color = color.copy(alpha = 0.5f),
                start = Offset(cx + innerR * kotlin.math.cos(angle), cy + innerR * kotlin.math.sin(angle)),
                end = Offset(cx + outerR * kotlin.math.cos(angle), cy + outerR * kotlin.math.sin(angle)),
                strokeWidth = 1.5f
            )
        }

        // Hour hand
        val hourLen = radius * 0.5f
        drawLine(
            color = color,
            start = Offset(cx, cy),
            end = Offset(cx + hourLen * kotlin.math.cos(hourAngle), cy + hourLen * kotlin.math.sin(hourAngle)),
            strokeWidth = 3f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Minute hand
        val minuteLen = radius * 0.72f
        drawLine(
            color = color,
            start = Offset(cx, cy),
            end = Offset(cx + minuteLen * kotlin.math.cos(minuteAngle), cy + minuteLen * kotlin.math.sin(minuteAngle)),
            strokeWidth = 2f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Second hand
        val secondLen = radius * 0.8f
        drawLine(
            color = Color.Red.copy(alpha = 0.8f),
            start = Offset(cx, cy),
            end = Offset(cx + secondLen * kotlin.math.cos(secondAngle), cy + secondLen * kotlin.math.sin(secondAngle)),
            strokeWidth = 1f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Center dot
        drawCircle(
            color = color,
            radius = 2f,
            center = Offset(cx, cy)
        )
    }
}


@Composable
fun LowBatteryOverlay(batteryLevel: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Red)
//            .align(Alignment.TopCenter)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "⚠️ Puneți telefonul la încărcat. Baterie scăzută ($batteryLevel%)",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ChargingOverlay(level: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF2E7D32)) // green
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "🔌 Se încarcă… ($level%)",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FullyChargedOverlay() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1565C0)) // blue
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "✅ Bateria este complet încărcată",
            color = Color.White,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun rememberBatteryState(): State<BatteryUiState> {
    val context = LocalContext.current
    val state = remember {
        mutableStateOf(
            BatteryUiState(
                level = 100,
                isCharging = false,
                isFull = false
            )
        )
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                intent ?: return

                val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
                val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)

                val percent = (level * 100) / scale
                val isCharging =
                    status == BatteryManager.BATTERY_STATUS_CHARGING
                val isFull =
                    status == BatteryManager.BATTERY_STATUS_FULL

                state.value = BatteryUiState(
                    level = percent,
                    isCharging = isCharging,
                    isFull = isFull
                )
            }
        }

        context.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )

        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    return state
}

data class BatteryUiState(
    val level: Int,
    val isCharging: Boolean,
    val isFull: Boolean
)