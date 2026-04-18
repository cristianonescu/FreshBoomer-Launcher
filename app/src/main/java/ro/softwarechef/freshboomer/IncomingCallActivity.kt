package ro.softwarechef.freshboomer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import android.telecom.TelecomManager
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

class IncomingCallActivity : ImmersiveActivity() {

    companion object {
        const val EXTRA_INCOMING_NUMBER = "incoming_number"
    }

    override val disableInactivityTimeout: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Determine caller info: prefer CallManager (InCallService), fall back to intent extra
        val number = if (CallManager.phoneNumber.isNotBlank()) {
            CallManager.phoneNumber
        } else {
            intent.getStringExtra(EXTRA_INCOMING_NUMBER) ?: ""
        }

        val name = CallManager.displayName

        val displayName = name ?: number
        val nickname = ro.softwarechef.freshboomer.data.NicknamePreference.getNickname(this)
        speakOutLoud(getString(R.string.tts_incoming_call, nickname, displayName))

        setContent {
            HideSystemBars()
            LauncherTheme {
                val callState = CallManager.callState

                // If call was answered or disconnected, navigate appropriately
                @Suppress("DEPRECATION")
                LaunchedEffect(callState) {
                    when (callState) {
                        Call.STATE_ACTIVE -> {
                            // Call was answered — go to InCallActivity
                            val intent = Intent(this@IncomingCallActivity, InCallActivity::class.java)
                            startActivity(intent)
                            finish()
                        }
                        Call.STATE_DISCONNECTED -> {
                            // Caller gave up or call ended — go back to main screen
                            val mainIntent = Intent(this@IncomingCallActivity, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                            }
                            startActivity(mainIntent)
                            finish()
                        }
                    }
                }

                IncomingCallScreen(
                    callerName = name,
                    callerNumber = number,
                    onAccept = { acceptCall() },
                    onReject = { rejectCall() }
                )
            }
        }
    }

    private fun acceptCall() {
        if (CallManager.currentCall != null) {
            // InCallService path — proper call management
            CallManager.answer()
        } else {
            // Fallback: PhoneCallReceiver path (app is not default dialer)
            acceptCallFallback()
        }
    }

    private fun rejectCall() {
        if (CallManager.currentCall != null) {
            CallManager.reject()
        } else {
            rejectCallFallback()
        }
        finish()
    }

    @Suppress("DEPRECATION")
    private fun acceptCallFallback() {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.acceptRingingCall()
            resumedFromCall = true

            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                val audioManager = getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
                setAudioDevice(audioManager)
                setMaxVolume()
            }, 3000)
        } catch (e: Exception) {
            Log.e("IncomingCall", "Failed to accept call (fallback)", e)
        }
        finish()
    }

    @Suppress("DEPRECATION")
    private fun rejectCallFallback() {
        try {
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as TelecomManager
            telecomManager.endCall()
        } catch (e: Exception) {
            Log.e("IncomingCall", "Failed to reject call (fallback)", e)
        }
    }
}

@Composable
fun IncomingCallScreen(
    callerName: String?,
    callerNumber: String,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
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
                Text(
                    text = "Te sună...",
                    style = MaterialTheme.typography.titleLarge,
                    color = secondaryText
                )
                Spacer(modifier = Modifier.height(24.dp))
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
            }

            // Accept / Reject buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Reject — red glow
                AccentGlowButton(
                    onClick = onReject,
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    color = Color(0xFFD32F2F),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Respinge",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Respinge",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                // Accept — green glow
                AccentGlowButton(
                    onClick = onAccept,
                    modifier = Modifier.weight(1f).aspectRatio(1f),
                    color = Color(0xFF388E3C),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = "Răspunde",
                            tint = Color.White,
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Răspunde",
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
