package ro.softwarechef.freshboomer.ui.composables

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ro.softwarechef.freshboomer.MainActivity
import ro.softwarechef.freshboomer.R
import android.media.AudioAttributes
import android.provider.CallLog
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ro.softwarechef.freshboomer.data.InactivityTracker
import ro.softwarechef.freshboomer.data.NicknamePreference
import ro.softwarechef.freshboomer.tts.PiperTtsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

abstract class ImmersiveActivity : ComponentActivity() {
    private var fallbackTts: TextToSpeech? = null
    private var fallbackTtsInitialized = false
    private val ttsScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    var resumedFromCall = false

    /** Override to true in call-related activities to prevent auto-navigation to MainActivity. */
    protected open val disableInactivityTimeout: Boolean = false

    var lastCalledNumber by mutableStateOf<LastCaller?>(null)
        private set
    private var lastCallAnnouncementCount = 0

    private lateinit var sharedPrefs: SharedPreferences
    private val PREFS_NAME = "LauncherPrefs"
    private val KEY_LAST_ANNOUNCED_NUMBER = "last_announced_number"
    private val KEY_ANNOUNCEMENT_COUNT = "announcement_count"

    private val inactivityTimeoutMs get() = ro.softwarechef.freshboomer.data.AppConfig.current.inactivityTimeoutMs
    private val inactivityHandler = Handler(Looper.getMainLooper())
    private val inactivityRunnable = Runnable {
        navigateToMainActivity()
    }

    private val chargingReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_CONNECTED) {
                InactivityTracker.recordInteraction(context)
                Log.d("ImmersiveActivity", "Charger connected — interaction recorded")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Show over lock screen and dismiss keyguard
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        keyguardManager.requestDismissKeyguard(this, null)

        sharedPrefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        startInactivityTimer()

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
        // Fetch the most recent missed call
        val newLastCall = getLastCall()
        Log.d("LastCaller", "New last call: $newLastCall")

        // Get previously announced number and count
        val lastAnnouncedNumber = sharedPrefs.getString(KEY_LAST_ANNOUNCED_NUMBER, null)
        val announcementCount = sharedPrefs.getInt(KEY_ANNOUNCEMENT_COUNT, 0)

        if (newLastCall != null) {
            // Check if this is a different missed call than before
            if (newLastCall.number != lastAnnouncedNumber) {
                // New missed call - reset counter
                lastCallAnnouncementCount = 0
                with(sharedPrefs.edit()) {
                    putString(KEY_LAST_ANNOUNCED_NUMBER, newLastCall.number)
                    putInt(KEY_ANNOUNCEMENT_COUNT, 0)
                    apply()
                }
                Log.d("LastCaller", "New missed call detected, resetting counter")
            } else {
                // Same missed call - get count from preferences
                lastCallAnnouncementCount = announcementCount
            }

            lastCalledNumber = newLastCall
        } else {
            // No missed calls
            lastCalledNumber = null
            lastCallAnnouncementCount = 0
        }
        // Announce only if we have a missed call and haven't announced 3 times yet
        if (lastCalledNumber != null && lastCallAnnouncementCount < ro.softwarechef.freshboomer.data.AppConfig.current.maxMissedCallAnnouncements) {
            lastCallAnnouncementCount++

            // Save updated count
            with(sharedPrefs.edit()) {
                putInt(KEY_ANNOUNCEMENT_COUNT, lastCallAnnouncementCount)
                apply()
            }

            val callerInfo = lastCalledNumber!!.name ?: lastCalledNumber!!.number
            val nickname = NicknamePreference.getNickname(this)
            speakOutLoud(getString(R.string.tts_missed_call, nickname, callerInfo, lastCalledNumber!!.time))
            Log.d("LastCaller", "Announced call #$lastCallAnnouncementCount for ${lastCalledNumber!!.number}")
        } else if (lastCalledNumber != null) {
//            lastCalledNumber = LastCaller("", null, null)
            Log.d("LastCaller", "Call already announced 3 times, skipping announcement")
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
        inactivityHandler.postDelayed(inactivityRunnable, inactivityTimeoutMs)
    }

    private fun stopInactivityTimer() {
        inactivityHandler.removeCallbacks(inactivityRunnable)
    }

    private fun resetInactivityTimer() {
        stopInactivityTimer()
        startInactivityTimer()
    }

    private fun navigateToMainActivity() {
        if (this is MainActivity) return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
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

//    fun getLastCall(): LastCaller? {
//        return LastCaller(number = CallLog.Calls.getLastOutgoingCall(this), name = "", time = "")
//    }

    fun getLastCall(): LastCaller? {
        return try {
            // Check permissions first
            if (checkSelfPermission(android.Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(android.Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                return null
            }

            // Query the last MISSED call from CallLog
            val projection = arrayOf(
                CallLog.Calls.NUMBER,
                CallLog.Calls.DATE,
                CallLog.Calls.TYPE,
                CallLog.Calls.DURATION
            )

            val cursor = contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                "${CallLog.Calls.TYPE} = ?",  // Only missed calls
                arrayOf(CallLog.Calls.MISSED_TYPE.toString()),
                "${CallLog.Calls.DATE} DESC"  // Sort by date DESCENDING (newest first)
            ) ?: return null

            var lastNumber: String? = null
            var lastDate: Long? = null

            cursor.use {
                if (it.moveToFirst()) { // This should now be the most recent missed call
                    lastNumber = it.getString(it.getColumnIndexOrThrow(CallLog.Calls.NUMBER))
                    lastDate = it.getLong(it.getColumnIndexOrThrow(CallLog.Calls.DATE))

                    // Log for debugging
                    Log.d("LAST_CALL", "Found missed call: $lastNumber at $lastDate")
                }
            }

            if (lastNumber == null) {
                Log.d("LAST_CALL", "No missed calls found")
                return null
            }

            // Lookup contact name from phone number
            var contactName: String? = null
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(lastNumber)
            )

            contentResolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { contactCursor ->
                if (contactCursor.moveToFirst()) {
                    contactName = contactCursor.getString(
                        contactCursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    )
                }
            }

            // Format time as HH:mm
            val timeStr = lastDate?.let {
                java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(it)
            }

            val result = LastCaller(number = lastNumber!!, name = contactName, time = timeStr, timestamp = lastDate ?: 0L)
            Log.d("LAST_CALL", "Returning: $result")
            result
        } catch (e: Exception) {
            Log.e("ELDER_APP", "Failed to get last call", e)
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
                Log.e("ELDER_APP", "setCommunicationDevice failed: ${e.localizedMessage}")
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
            Log.w("ELDER_APP", "No DND policy access — skipping setMaxVolume. Grant via Settings > Apps > Special access > Do Not Disturb.")
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
                Log.w("ELDER_APP", "Cannot set volume for stream $stream: ${e.message}")
            }
        }
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
            Log.d("ELDER_APP", "TTS skipped: disabled by user")
            return
        }
        if (isInCall()) {
            Log.d("ELDER_APP", "TTS skipped: in call")
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
            Log.w("ELDER_APP", "Cannot set TTS volume: ${e.message}")
        }

        val preferredEngine = ro.softwarechef.freshboomer.data.TtsPreference.getEngine(this)
        if (preferredEngine == ro.softwarechef.freshboomer.data.TtsEngine.PIPER_LILI && PiperTtsEngine.isReady) {
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
                    Log.e("ELDER_APP", "Fallback TTS init failed")
                }
            }
        } else if (fallbackTtsInitialized) {
            fallbackTts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "elder_tts")
        }
    }

    private fun configureFallbackTts() {
        fallbackTts?.apply {
            language = Locale("ro", "RO")
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