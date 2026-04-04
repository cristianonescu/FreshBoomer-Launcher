package ro.softwarechef.freshboomer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.softwarechef.freshboomer.ui.composables.HideSystemBars
import ro.softwarechef.freshboomer.ui.composables.ImmersiveActivity
import ro.softwarechef.freshboomer.ui.theme.LauncherTheme

class WhatsAppCallActivity : ImmersiveActivity() {

    companion object {
        const val EXTRA_CALLER_NAME = "caller_name"
        const val EXTRA_CALLER_TEXT = "caller_text"
        const val EXTRA_NOTIFICATION_KEY = "notification_key"
        const val ACTION_WHATSAPP_CALL_ENDED = "ro.softwarechef.freshboomer.WHATSAPP_CALL_ENDED"
    }

    override val disableInactivityTimeout: Boolean = false

    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d("WhatsAppCall", "Call ended broadcast received")
            speakOutLoud(getString(R.string.tts_whatsapp_ended))
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val callerName = intent.getStringExtra(EXTRA_CALLER_NAME) ?: "cineva"
        val callerText = intent.getStringExtra(EXTRA_CALLER_TEXT)

        val nickname = ro.softwarechef.freshboomer.data.NicknamePreference.getNickname(this)
        speakOutLoud(getString(R.string.tts_whatsapp_incoming, nickname, callerName))

        registerReceiver(
            callEndedReceiver,
            IntentFilter(ACTION_WHATSAPP_CALL_ENDED),
            RECEIVER_NOT_EXPORTED
        )

        setContent {
            HideSystemBars()
            LauncherTheme {
                WhatsAppCallScreen(
                    callerName = callerName,
                    callerText = callerText,
                    onAnswerClick = { openWhatsAppCall() },
                    onRejectClick = { moveTaskToBack(true) }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(callEndedReceiver)
        } catch (_: Exception) { }
    }

    private fun openWhatsAppCall() {
        // Move our entire task to background so WhatsApp's call screen stays visible.
        // Don't call finish() here — that would bring MainActivity (the launcher) to the front.
        moveTaskToBack(true)
    }
}

@Composable
fun WhatsAppCallScreen(
    callerName: String,
    callerText: String?,
    onAnswerClick: () -> Unit,
    onRejectClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF075E54)), // WhatsApp green
        contentAlignment = Alignment.Center
    ) {
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
                Text(
                    text = "WhatsApp",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Te sună...",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = callerName,
                    fontSize = 48.sp,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                if (callerText != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = callerText,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            // Accept / Reject buttons
            /*Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 48.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Reject button
                Button(
                    onClick = onRejectClick,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFD32F2F)
                    )
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
                            color = Color.White
                        )
                    }
                }

                // Accept button
                Button(
                    onClick = onAnswerClick,
                    modifier = Modifier
                        .weight(1f)
                        .aspectRatio(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF388E3C)
                    )
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
                            color = Color.White
                        )
                    }
                }
            }*/
        }
    }
}
