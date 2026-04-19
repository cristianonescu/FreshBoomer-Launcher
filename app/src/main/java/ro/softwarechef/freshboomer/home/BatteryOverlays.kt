package ro.softwarechef.freshboomer.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

data class BatteryUiState(
    val level: Int,
    val isCharging: Boolean,
    val isFull: Boolean
)

/**
 * Shared glass-style banner for battery status overlays. Matches the visual
 * language of [ro.softwarechef.freshboomer.home.LastCallerBanner] (translucent
 * surface + top highlight gradient + 1dp accent border + 16dp rounded
 * corners) so battery state reads as part of the app rather than an OS-style
 * system toast.
 */
@Composable
private fun GlassStatusBanner(
    accent: Color,
    icon: ImageVector,
    text: String
) {
    val shape = RoundedCornerShape(16.dp)
    val bg = MaterialTheme.colorScheme.background
    val isDark = (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) < 0.5f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(shape)
            .background(accent.copy(alpha = if (isDark) 0.20f else 0.18f))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (isDark) 0.08f else 0.25f),
                        Color.Transparent
                    )
                )
            )
            .border(BorderStroke(1.dp, accent.copy(alpha = 0.45f)), shape)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun LowBatteryOverlay(batteryLevel: Int) {
    GlassStatusBanner(
        accent = Color(0xFFE53935),
        icon = Icons.Default.Warning,
        text = "Puneți telefonul la încărcat. Baterie scăzută ($batteryLevel%)"
    )
}

@Composable
fun ChargingOverlay(level: Int) {
    GlassStatusBanner(
        accent = Color(0xFF4CAF50),
        icon = Icons.Default.BatteryChargingFull,
        text = "Se încarcă… ($level%)"
    )
}

@Composable
fun FullyChargedOverlay() {
    GlassStatusBanner(
        accent = MaterialTheme.colorScheme.primary,
        icon = Icons.Default.CheckCircle,
        text = "Bateria este complet încărcată"
    )
}

@Composable
fun rememberBatteryState(): State<BatteryUiState> {
    val context = LocalContext.current
    val state = remember {
        mutableStateOf(
            BatteryUiState(level = 100, isCharging = false, isFull = false)
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
                val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING
                val isFull = status == BatteryManager.BATTERY_STATUS_FULL

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

        onDispose { context.unregisterReceiver(receiver) }
    }

    return state
}
