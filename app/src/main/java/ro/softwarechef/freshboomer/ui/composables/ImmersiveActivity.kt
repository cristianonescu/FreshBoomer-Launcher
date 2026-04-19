package ro.softwarechef.freshboomer.ui.composables

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ro.softwarechef.freshboomer.R
import android.media.AudioAttributes
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ro.softwarechef.freshboomer.data.InactivityTracker
import ro.softwarechef.freshboomer.data.LocaleHelper
import ro.softwarechef.freshboomer.data.NicknamePreference
import ro.softwarechef.freshboomer.tts.PiperTtsEngine
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ro.softwarechef.freshboomer.data.MissedCallStore
import java.util.Locale

private const val TAG = "FB/ImmersiveActivity"

abstract class ImmersiveActivity : ComponentActivity() {
    private var fallbackTts: TextToSpeech? = null
    private var fallbackTtsInitialized = false
    private val ttsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var resumedFromCall = false

    /** Override to true in call-related activities to prevent auto-navigation to MainActivity. */
    protected open val disableInactivityTimeout: Boolean = false

    /**
     * Override to true in top-level launcher screens (Phone, Contacts, Sms, Gallery) so
     * system back matches the in-screen `Inapoi` button and always returns HOME.
     * Registered at lowest priority — any `BackHandler` in the Compose tree (dialogs,
     * conversation views) still takes precedence.
     */
    protected open val backReturnsToHome: Boolean = false

    var lastCalledNumber by mutableStateOf<LastCaller?>(null)
        private set

    private val inactivityTimeoutMs get() = ro.softwarechef.freshboomer.data.AppConfig.current.inactivityTimeoutMs
    private var inactivityJob: Job? = null

    private val chargingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_CONNECTED) {
                InactivityTracker.recordInteraction(context)
                Log.d(TAG, "Charger connected — interaction recorded")
            }
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Show over lock screen and dismiss keyguard
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)

        startInactivityTimer()

        if (backReturnsToHome) {
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    ro.softwarechef.freshboomer.data.LauncherNavigator.go(
                        this@ImmersiveActivity,
                        ro.softwarechef.freshboomer.data.LauncherNavigator.Screen.HOME
                    )
                }
            })
        }

        // Register charging receiver for inactivity tracking
        registerReceiver(
            chargingReceiver,
            IntentFilter(Intent.ACTION_POWER_CONNECTED),
            Context.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        try { unregisterReceiver(chargingReceiver) } catch (_: Exception) {}
    }
    fun refreshLastCall() {
        val newLastCall = getLastCall()
        Log.d(TAG, "New last call: $newLastCall")

        lastCalledNumber = newLastCall

        val stored = ro.softwarechef.freshboomer.data.MissedCallAnnouncementStore.read(this)
        val decision = ro.softwarechef.freshboomer.data.MissedCallAnnouncer.decide(
            currentCallNumber = newLastCall?.number,
            lastAnnouncedNumber = stored.lastAnnouncedNumber,
            storedCount = stored.count,
            maxAnnouncements = ro.softwarechef.freshboomer.data.AppConfig.current.maxMissedCallAnnouncements
        )
        ro.softwarechef.freshboomer.data.MissedCallAnnouncementStore.write(
            this, decision.newLastAnnouncedNumber, decision.newCount
        )

        if (decision.shouldAnnounce && newLastCall != null) {
            val callerInfo = newLastCall.name ?: newLastCall.number
            val nickname = NicknamePreference.getNickname(this)
            speakOutLoud(getString(R.string.tts_missed_call, nickname, callerInfo, newLastCall.time))
            Log.d(TAG, "Announced call #${decision.newCount} for ${newLastCall.number}")
        } else if (newLastCall != null) {
            Log.d(TAG, "Call already at max announcements, skipping")
        }
    }

    override fun onResume() {
        super.onResume()
        InactivityTracker.recordInteraction(this)
        refreshLastCall()
        startInactivityTimer()
    }

    override fun onPause() {
        super.onPause()
        stopInactivityTimer()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        InactivityTracker.recordInteraction(this)
        resetInactivityTimer()
    }

    private fun startInactivityTimer() {
        if (disableInactivityTimeout) return
        inactivityJob?.cancel()
        inactivityJob = lifecycleScope.launch {
            delay(inactivityTimeoutMs)
            navigateToMainActivity()
        }
    }

    private fun stopInactivityTimer() {
        inactivityJob?.cancel()
        inactivityJob = null
    }

    private fun resetInactivityTimer() = startInactivityTimer()

    private fun navigateToMainActivity() {
        ro.softwarechef.freshboomer.data.LauncherNavigator.go(
            this,
            ro.softwarechef.freshboomer.data.LauncherNavigator.Screen.HOME
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun hideSystemBars() {
        WindowInsetsControllerCompat(window, window.decorView)
            .hide(WindowInsetsCompat.Type.systemBars())
    }

    /**
     * Returns the most recent missed call.
     *
     * Source: [MissedCallStore], populated by [ro.softwarechef.freshboomer.services.CallService]
     * (InCallService — primary path when this app is the default dialer) and
     * [ro.softwarechef.freshboomer.receivers.PhoneCallReceiver] (PHONE_STATE fallback).
     * This replaces the previous CallLog query so the app no longer needs the
     * READ_CALL_LOG permission.
     */
    fun getLastCall(): LastCaller? {
        return try {
            val record = MissedCallStore.getLast(this) ?: return null

            // Look up the contact name from READ_CONTACTS (still permitted).
            var contactName: String? = null
            try {
                val uri = Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(record.number)
                )
                contentResolver.query(
                    uri,
                    arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        contactName = cursor.getString(
                            cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                        )
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Contact name lookup failed: ${e.localizedMessage}")
            }

            val timeStr = if (record.timestamp > 0L) {
                java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(record.timestamp)
            } else null

            LastCaller(
                number = record.number,
                name = contactName,
                time = timeStr,
                timestamp = record.timestamp
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get last missed call", e)
            null
        }
    }

    fun makePhoneCall(number: String) {
        resumedFromCall = true   // 👈 important

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Start the call
        val intent = Intent(Intent.ACTION_CALL).apply {
            data = Uri.parse("tel:$number")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        startActivity(intent)
        Handler(mainLooper).postDelayed({
            setAudioDevice(audioManager)
        }, ro.softwarechef.freshboomer.data.AppConfig.current.callSpeakerDelayMs)

        hideSystemBars()
        setMaxVolume()
    }

    @Suppress("DEPRECATION")
    fun setAudioDevice(audioManager: AudioManager) {
        audioManager.mode = AudioManager.MODE_IN_CALL
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val speakerDevice = getAudioDevice(audioManager, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER)
            try {
                if (speakerDevice != null) {
                    audioManager.setCommunicationDevice(speakerDevice)
                }
            } catch (e: Exception) {
                Log.e(TAG, "setCommunicationDevice failed: ${e.localizedMessage}")
                audioManager.isSpeakerphoneOn = true
            }
        } else {
            audioManager.isSpeakerphoneOn = true
        }
    }

    fun getAudioDevice(audioManager: AudioManager, type: Int): AudioDeviceInfo? {
        val audioDevices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        for (deviceInfo in audioDevices) {
            if (type == deviceInfo.type) return deviceInfo
        }
        return null
    }

    fun setMaxVolume() {
        if (!ro.softwarechef.freshboomer.data.AppConfig.current.autoMaxVolume) return

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        if (!notificationManager.isNotificationPolicyAccessGranted) {
            Log.w(TAG, "No DND policy access — skipping setMaxVolume. Grant via Settings > Apps > Special access > Do Not Disturb.")
            return
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val streams = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_SYSTEM,
            AudioManager.STREAM_NOTIFICATION
        )
        streams.forEach { stream ->
            try {
                audioManager.setStreamVolume(
                    stream,
                    audioManager.getStreamMaxVolume(stream),
                    AudioManager.FLAG_SHOW_UI
                )
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot set volume for stream $stream: ${e.message}")
            }
        }
    }

    /**
     * Speak the nickname-formatted [ttsResId] and navigate to [screen]. Used by
     * [MainActivity] for the four quick-launch buttons so the announce/navigate
     * pair can't drift apart.
     */
    fun announceAndGo(screen: ro.softwarechef.freshboomer.data.LauncherNavigator.Screen, ttsResId: Int) {
        val nick = NicknamePreference.getNickname(this)
        speakOutLoud(getString(ttsResId, nick))
        ro.softwarechef.freshboomer.data.LauncherNavigator.go(this, screen)
    }

    fun launchWhatsApp() {
        val pm = packageManager

        val launcherIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage("com.whatsapp")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val intent = when {
            launcherIntent.resolveActivity(pm) != null -> launcherIntent
            pm.getLaunchIntentForPackage("com.whatsapp") != null ->
                pm.getLaunchIntentForPackage("com.whatsapp")
            else -> null
        }

        if (intent != null) {
            startActivity(intent)
        } else {
            Toast.makeText(this, "WhatsApp nu este instalat", Toast.LENGTH_LONG).show()
        }
    }

    fun speakOutLoud(text: String) {
        if (!ro.softwarechef.freshboomer.data.TtsPreference.isEnabled(this)) {
            Log.d(TAG, "TTS skipped: disabled by user")
            return
        }
        if (isInCall()) {
            Log.d(TAG, "TTS skipped: in call")
            return
        }

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Ensure TTS is audible
        try {
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
                0
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot set TTS volume: ${e.message}")
        }

        val preferredEngine = ro.softwarechef.freshboomer.data.TtsPreference.getEngine(this)
        if ((preferredEngine == ro.softwarechef.freshboomer.data.TtsEngine.PIPER_LILI ||
             preferredEngine == ro.softwarechef.freshboomer.data.TtsEngine.PIPER_SANDA) && PiperTtsEngine.isReady) {
            ttsScope.launch {
                PiperTtsEngine.speak(text)
            }
        } else {
            speakWithFallbackTts(text)
        }
    }

    private fun speakWithFallbackTts(text: String) {
        if (fallbackTts == null) {
            fallbackTts = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    fallbackTtsInitialized = true
                    configureFallbackTts()
                    fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "elder_tts")
                } else {
                    Log.e(TAG, "Fallback TTS init failed")
                }
            }
        } else if (fallbackTtsInitialized) {
            fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "elder_tts")
        }
    }

    private fun configureFallbackTts() {
        fallbackTts?.apply {
            language = LocaleHelper.getLocale()
            setSpeechRate(ro.softwarechef.freshboomer.data.AppConfig.current.ttsSpeechRate)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
        }
    }

    fun isInCall(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return audioManager.mode == AudioManager.MODE_IN_CALL ||
                audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
    }

    data class LastCaller(
        val number: String,
        val name: String? = null,
        val time: String? = null,  // HH:mm format
        val timestamp: Long = 0L   // epoch millis of the call
    )
}