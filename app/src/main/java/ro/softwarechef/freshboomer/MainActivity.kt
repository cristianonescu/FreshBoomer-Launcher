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
import ro.softwarechef.freshboomer.data.LauncherNavigator
import ro.softwarechef.freshboomer.data.NicknamePreference
import ro.softwarechef.freshboomer.data.QuickContactRepository
import ro.softwarechef.freshboomer.home.SystemClock
import ro.softwarechef.freshboomer.home.LowBatteryOverlay
import ro.softwarechef.freshboomer.home.ChargingOverlay
import ro.softwarechef.freshboomer.home.FullyChargedOverlay
import ro.softwarechef.freshboomer.home.rememberBatteryState
import ro.softwarechef.freshboomer.home.LastCallerBanner
import ro.softwarechef.freshboomer.home.GridLayout
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
                                onSmsClick      = { announceAndGo(LauncherNavigator.Screen.SMS,      R.string.tts_open_sms) },
                                onPhoneClick    = { announceAndGo(LauncherNavigator.Screen.PHONE,    R.string.tts_open_phone) },
                                onContactsClick = { announceAndGo(LauncherNavigator.Screen.CONTACTS, R.string.tts_open_contacts) },
                                onGalleryClick  = { announceAndGo(LauncherNavigator.Screen.GALLERY,  R.string.tts_open_gallery) },
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
