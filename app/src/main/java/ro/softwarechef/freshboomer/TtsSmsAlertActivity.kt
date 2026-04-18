package ro.softwarechef.freshboomer

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ro.softwarechef.freshboomer.call.CallManager
import ro.softwarechef.freshboomer.data.AppConfig
import ro.softwarechef.freshboomer.ui.composables.AccentGlowButton
import ro.softwarechef.freshboomer.ui.composables.GlassBackground
import ro.softwarechef.freshboomer.ui.composables.GlassButton
import ro.softwarechef.freshboomer.ui.composables.ImmersiveActivity
import ro.softwarechef.freshboomer.ui.theme.LauncherTheme

class TtsSmsAlertActivity : ImmersiveActivity() {

    override val disableInactivityTimeout: Boolean = true

    private val autoDismissHandler = Handler(Looper.getMainLooper())
    private val autoDismissRunnable = Runnable { finish() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val message = intent.getStringExtra("message") ?: ""
        val sender = intent.getStringExtra("sender") ?: ""
        val senderName = CallManager.lookupContactName(this, sender) ?: sender
        val nickname = AppConfig.current.userNickname

        // Read the message aloud
        val announcement = getString(R.string.tts_sms_announcement, nickname, message)
        speakOutLoud(announcement)

        // Auto-dismiss after 30 seconds
        autoDismissHandler.postDelayed(autoDismissRunnable, 30_000)

        setContent {
            LauncherTheme {
                TtsSmsAlertScreen(
                    message = message,
                    senderName = senderName,
                    onDismiss = { finish() },
                    onReplay = { speakOutLoud(message) }
                )
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Dismiss if user navigates away
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
        finish()
    }

    override fun onDestroy() {
        autoDismissHandler.removeCallbacks(autoDismissRunnable)
        super.onDestroy()
    }
}

@Composable
private fun TtsSmsAlertScreen(
    message: String,
    senderName: String,
    onDismiss: () -> Unit,
    onReplay: () -> Unit
) {
    GlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Default.Email,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.tts_sms_alert_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.tts_sms_alert_from, senderName),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Message card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Replay — glass (neutral)
            GlassButton(
                onClick = onReplay,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.tts_sms_alert_replay),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Dismiss — primary accent glow
            AccentGlowButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(70.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.tts_sms_alert_dismiss),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}
