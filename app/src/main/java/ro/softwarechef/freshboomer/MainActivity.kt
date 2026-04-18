package ro.softwarechef.freshboomer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.draw.clip
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
import androidx.compose.ui.text.font.FontWeight
import ro.softwarechef.freshboomer.ui.composables.AccentGlowButton
import ro.softwarechef.freshboomer.ui.composables.GlassBackground
import ro.softwarechef.freshboomer.ui.composables.GlassButton
import ro.softwarechef.freshboomer.ui.composables.GradientAvatar
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
                    GlassBackground(
                        modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
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
                GlassBackground(
                    modifier = Modifier.windowInsetsPadding(WindowInsets.systemBars)
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
            AccentGlowButton(
                onClick = onGalleryClick,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }

        if (toggles.whatsapp) {
            val activity = LocalActivity.current as? ImmersiveActivity
            AccentGlowButton(
                onClick = { activity?.launchWhatsApp() },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                color = Color(0xFF25D366),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
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

    if (!visible) {
        Box(modifier = modifier.aspectRatio(1f)) {}
        return
    }

    if (roundedSquare) {
        // Utility-row button — use the shared GlassButton primitive so it
        // matches the v2 prototype's glass treatment everywhere else in the
        // app.
        GlassButton(
            onClick = onClick,
            modifier = modifier.fillMaxHeight(),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = text,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
        return
    }

    // Quick-contact button path (circular avatar, supports real photos / gradient fallback).
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight().aspectRatio(1f),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 12.dp,
            focusedElevation = 8.dp,
            hoveredElevation = 8.dp
        ),
        shape = RoundedCornerShape(200.dp),
        colors = if (!hasImage) {
            ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.gray))
        } else {
            ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        },
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.25f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.35f)
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
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = text,
                        style = resolvedTextStyle,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            } else if (!hasImage && icon == null) {
                // Quick contact without photo — theme-accented gradient avatar + name.
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    GradientAvatar(
                        name = text,
                        size = 100.dp,
                        textStyle = resolvedTextStyle.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 8.dp, start = 6.dp, end = 6.dp)
                    ) {
                        Text(
                            text = text,
                            style = resolvedTextStyle.copy(
                                fontSize = resolvedTextStyle.fontSize * 0.7,
                                color = Color.White.copy(alpha = 0.95f),
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    offset = Offset(1f, 1f),
                                    blurRadius = 3f
                                )
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
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

        // Glass banner with a red-dot indicator. No shadows/blur — uses
        // gradient + border to draw the "glass" edge so it renders the
        // same on every device.
        val red = Color(0xFFD32F2F)
        val bannerShape = RoundedCornerShape(16.dp)
        val bannerBg = MaterialTheme.colorScheme.background
        val isDark = (0.299f * bannerBg.red + 0.587f * bannerBg.green + 0.114f * bannerBg.blue) < 0.5f
        Row(
            modifier = modifier
                .fillMaxWidth()
                .clip(bannerShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = if (isDark) 0.35f else 0.85f))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.08f else 0.30f),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    ),
                    bannerShape
                )
                .clickable {
                    onQuickCall(
                        displayName,
                        caller.number,
                        null,
                        Icons.Default.Call
                    )
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Red dot indicator — solid red with a subtle outer ring for
            // emphasis. No `Modifier.shadow` (unreliable colored glow on
            // some OEM skins).
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(red.copy(alpha = 0.25f))
                    .padding(3.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(red)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Apel pierdut de la $displayName",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.missed_call_tap_to_return, caller.time ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
            Spacer(Modifier.width(12.dp))
            AccentGlowButton(
                onClick = {
                    onQuickCall(
                        displayName,
                        caller.number,
                        null,
                        Icons.Default.Call
                    )
                },
                color = Color(0xFF4CAF50),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(44.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.call),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
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
        Icon(
            imageVector = Icons.Default.DateRange,
            contentDescription = null,
            tint = clockColor,
            modifier = Modifier.size(28.dp)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = currentDate,
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

    val accent = MaterialTheme.colorScheme.primary
    val darkMode = androidx.compose.foundation.isSystemInDarkTheme()

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val radius = size.minDimension / 2f

        // Dark-mode glow: wider dim accent halo behind the face
        if (darkMode) {
            drawCircle(
                color = accent.copy(alpha = 0.12f),
                radius = radius * 1.1f,
                center = Offset(cx, cy)
            )
        }

        // Clock face (glass fill)
        drawCircle(
            color = color.copy(alpha = 0.25f),
            radius = radius,
            center = Offset(cx, cy)
        )
        // Accent outer ring
        drawCircle(
            color = accent.copy(alpha = 0.6f),
            radius = radius,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )

        // Hour tick marks — 12/3/6/9 thicker + accent, others subtle
        for (i in 0 until 12) {
            val angle = Math.toRadians(i * 30.0 - 90.0).toFloat()
            val isMajor = i % 3 == 0
            val innerR = radius * (if (isMajor) 0.78f else 0.82f)
            val outerR = radius * 0.95f
            drawLine(
                color = if (isMajor) accent else color.copy(alpha = 0.5f),
                start = Offset(cx + innerR * kotlin.math.cos(angle), cy + innerR * kotlin.math.sin(angle)),
                end = Offset(cx + outerR * kotlin.math.cos(angle), cy + outerR * kotlin.math.sin(angle)),
                strokeWidth = if (isMajor) 2.5f else 1.5f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
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

        // Second hand (accent)
        val secondLen = radius * 0.8f
        drawLine(
            color = accent.copy(alpha = 0.85f),
            start = Offset(cx, cy),
            end = Offset(cx + secondLen * kotlin.math.cos(secondAngle), cy + secondLen * kotlin.math.sin(secondAngle)),
            strokeWidth = 1f,
            cap = androidx.compose.ui.graphics.StrokeCap.Round
        )

        // Center pin — accent ring + white dot
        drawCircle(
            color = accent,
            radius = 3f,
            center = Offset(cx, cy),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5f)
        )
        drawCircle(
            color = Color.White,
            radius = 1.5f,
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Puneți telefonul la încărcat. Baterie scăzută ($batteryLevel%)",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Se încarcă… ($level%)",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        }
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "Bateria este complet încărcată",
                color = Color.White,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
        }
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