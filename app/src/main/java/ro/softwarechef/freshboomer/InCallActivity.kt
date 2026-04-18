package ro.softwarechef.freshboomer

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import android.telecom.Call
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ro.softwarechef.freshboomer.call.CallManager
import ro.softwarechef.freshboomer.ui.composables.AccentGlowButton
import ro.softwarechef.freshboomer.ui.composables.GlassBackground
import ro.softwarechef.freshboomer.ui.composables.GradientAvatar
import ro.softwarechef.freshboomer.ui.composables.HideSystemBars
import ro.softwarechef.freshboomer.ui.composables.ImmersiveActivity
import ro.softwarechef.freshboomer.ui.theme.LauncherTheme
import kotlinx.coroutines.delay

class InCallActivity : ImmersiveActivity() {

    override val disableInactivityTimeout: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Route audio to speaker and set max volume
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        setAudioDevice(audioManager)
        setMaxVolume()

        setContent {
            HideSystemBars()
            LauncherTheme {
                val callState = CallManager.callState
                val callerNumber = CallManager.phoneNumber

                // Resolve contact name: CallManager first, then local lookup
                val callerName = CallManager.displayName
                    ?: if (callerNumber.isNotBlank()) {
                        remember(callerNumber) {
                            CallManager.lookupContactName(this@InCallActivity, callerNumber)
                        }
                    } else null

                @Suppress("DEPRECATION")
                LaunchedEffect(callState) {
                    if (callState == Call.STATE_DISCONNECTED) {
                        delay(3000)
                        navigateToMain()
                    }
                }

                InCallScreen(
                    callerName = callerName,
                    callerNumber = callerNumber,
                    callState = callState,
                    onHangup = {
                        CallManager.hangup()
                        finish()
                    },
                    onToggleSpeaker = { toggleSpeaker() },
                    onToggleMute = { toggleMute() }
                )
            }
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
        finish()
    }

    private fun toggleSpeaker(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        @Suppress("DEPRECATION")
        val newState = !audioManager.isSpeakerphoneOn
        @Suppress("DEPRECATION")
        audioManager.isSpeakerphoneOn = newState
        return newState
    }

    private fun toggleMute(): Boolean {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val newState = !audioManager.isMicrophoneMute
        audioManager.isMicrophoneMute = newState
        return newState
    }
}

@Suppress("DEPRECATION")
@Composable
fun InCallScreen(
    callerName: String?,
    callerNumber: String,
    callState: Int,
    onHangup: () -> Unit,
    onToggleSpeaker: () -> Boolean,
    onToggleMute: () -> Boolean
) {
    var elapsedSeconds by remember { mutableLongStateOf(0L) }
    var isSpeakerOn by remember { mutableStateOf(true) }
    var isMuted by remember { mutableStateOf(false) }

    // Call duration timer — only count when active
    LaunchedEffect(callState) {
        if (callState == Call.STATE_ACTIVE) {
            while (true) {
                delay(1000)
                elapsedSeconds++
            }
        }
    }

    val stateText = when (callState) {
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Se apelează..."
        Call.STATE_ACTIVE -> formatDuration(elapsedSeconds)
        Call.STATE_HOLDING -> "În așteptare"
        Call.STATE_RINGING -> "Sună..."
        Call.STATE_DISCONNECTED -> "Apel terminat"
        else -> ""
    }

    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(48.dp))

            // Caller info
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val primaryText = MaterialTheme.colorScheme.onBackground
                val secondaryText = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                GradientAvatar(
                    name = callerName ?: callerNumber,
                    size = 180.dp
                )
                Spacer(modifier = Modifier.height(24.dp))
                if (callerName != null) {
                    Text(
                        text = callerName,
                        style = MaterialTheme.typography.displayLarge,
                        color = primaryText,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = callerNumber,
                        style = MaterialTheme.typography.titleLarge,
                        color = secondaryText
                    )
                } else {
                    Text(
                        text = callerNumber,
                        style = MaterialTheme.typography.displayLarge,
                        color = primaryText,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Call state / duration
                Text(
                    text = stateText,
                    style = MaterialTheme.typography.titleLarge,
                    color = secondaryText
                )
            }

            // Call controls
            Column(
                modifier = Modifier.padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Speaker and Mute toggles
                /*Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Speaker button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = { isSpeakerOn = onToggleSpeaker() },
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSpeakerOn) Color(0xFF1565C0) else Color(0xFF424242)
                            )
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isSpeakerOn) android.R.drawable.ic_lock_silent_mode_off
                                    else android.R.drawable.ic_lock_silent_mode
                                ),
                                contentDescription = "Difuzor",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isSpeakerOn) "Difuzor ON" else "Difuzor OFF",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }

                    // Mute button
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = { isMuted = onToggleMute() },
                            modifier = Modifier.size(80.dp),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isMuted) Color(0xFFD32F2F) else Color(0xFF424242)
                            )
                        ) {
                            Icon(
                                painter = painterResource(
                                    id = if (isMuted) android.R.drawable.ic_lock_silent_mode
                                    else android.R.drawable.ic_btn_speak_now
                                ),
                                contentDescription = "Microfon",
                                tint = Color.White,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isMuted) "Microfon OFF" else "Microfon ON",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.White
                        )
                    }
                }*/

                // Hangup button — red glow
                AccentGlowButton(
                    onClick = onHangup,
                    modifier = Modifier.fillMaxWidth(0.5f).aspectRatio(1f),
                    color = Color(0xFFD32F2F),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Închide",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Închide",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%02d:%02d".format(mins, secs)
}
