package ro.softwarechef.freshboomer

import android.app.NotificationManager
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ro.softwarechef.freshboomer.data.AppConfig
import ro.softwarechef.freshboomer.services.MedicationReminderScheduler
import ro.softwarechef.freshboomer.ui.composables.AccentGlowButton
import ro.softwarechef.freshboomer.ui.composables.GlassBackground
import ro.softwarechef.freshboomer.ui.composables.GlassButton
import ro.softwarechef.freshboomer.ui.composables.ImmersiveActivity
import ro.softwarechef.freshboomer.ui.theme.LauncherTheme

class MedicationAlertActivity : ImmersiveActivity() {

    override val disableInactivityTimeout: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val reminderId = intent.getStringExtra("reminder_id") ?: ""
        val reminderName = intent.getStringExtra("reminder_name") ?: ""
        val snoozeDuration = intent.getIntExtra("snooze_duration", 5)
        val nickname = AppConfig.current.userNickname

        // Speak the reminder
        val announcement = getString(R.string.tts_medication_reminder, nickname, reminderName)
        speakOutLoud(announcement)

        setContent {
            LauncherTheme {
                MedicationAlertScreen(
                    reminderName = reminderName,
                    snoozeDuration = snoozeDuration,
                    onDismiss = {
                        // Clear snooze state and notification
                        MedicationReminderScheduler.clearSnoozeState(this, reminderId)
                        dismissNotification(reminderId)
                        finish()
                    },
                    onSnooze = {
                        val reminder = AppConfig.current.medicationReminders.find { it.id == reminderId }
                        if (reminder != null) {
                            MedicationReminderScheduler.scheduleSnooze(this, reminder)
                        }
                        dismissNotification(reminderId)
                        finish()
                    }
                )
            }
        }
    }

    private fun dismissNotification(reminderId: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(5000 + reminderId.hashCode().and(0xFFF))
    }
}

@Composable
private fun MedicationAlertScreen(
    reminderName: String,
    snoozeDuration: Int,
    onDismiss: () -> Unit,
    onSnooze: () -> Unit
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
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                modifier = Modifier.size(100.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.medication_alert_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Medication name card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    text = reminderName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            // "I took it" dismiss — primary green glow
            AccentGlowButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(80.dp),
                color = Color(0xFF4CAF50),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.medication_alert_dismiss),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // "Later" snooze — glass (neutral)
            GlassButton(
                onClick = onSnooze,
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.medication_alert_snooze),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.medication_alert_snooze_info, snoozeDuration),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
