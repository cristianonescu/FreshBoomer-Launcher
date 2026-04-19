package ro.softwarechef.freshboomer.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ro.softwarechef.freshboomer.R
import ro.softwarechef.freshboomer.ui.composables.AccentGlowButton
import ro.softwarechef.freshboomer.ui.composables.ImmersiveActivity

@Composable
fun LastCallerBanner(
    modifier: Modifier = Modifier,
    lastCaller: ImmersiveActivity.LastCaller?,
    onQuickCall: (name: String, number: String, profile: Int?, icon: ImageVector?) -> Unit
) {
    lastCaller?.let { caller ->
        val ageMs = System.currentTimeMillis() - caller.timestamp
        if (caller.timestamp > 0L && ageMs > 4 * 60 * 60 * 1000L) return

        val displayName = caller.name ?: caller.number

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
                    BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                    ),
                    bannerShape
                )
                .clickable {
                    onQuickCall(displayName, caller.number, null, Icons.Default.Call)
                }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(red.copy(alpha = 0.25f))
                    .padding(3.dp)
                    .clip(CircleShape)
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
                    onQuickCall(displayName, caller.number, null, Icons.Default.Call)
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
