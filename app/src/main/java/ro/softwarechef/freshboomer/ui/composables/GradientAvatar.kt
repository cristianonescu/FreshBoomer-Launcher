package ro.softwarechef.freshboomer.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Fallback avatar for contacts without a photo: circular gradient background
 * with the contact's first initial centered on top.
 *
 * The gradient is deterministic per-name so the same contact always gets the
 * same colors across screens. Colors come from the current theme's primary /
 * secondary / tertiary so the avatar reads as part of the app rather than
 * ad-hoc.
 */
@Composable
fun GradientAvatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp? = 72.dp,
    textStyle: TextStyle = MaterialTheme.typography.headlineMedium,
    initialFontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary to MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary to MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.secondary,
    )
    val seed = if (name.isEmpty()) 0 else (name.hashCode() and 0x7fffffff)
    val (top, bottom) = palette[seed % palette.size]

    val initial = name.trim().firstOrNull()?.uppercaseChar()?.toString().orEmpty()

    // `size == null` => rely on caller's [modifier] for sizing (e.g. fillMaxSize).
    // Otherwise apply the fixed circular size.
    val sizedModifier = if (size != null) modifier.size(size) else modifier

    Box(
        modifier = sizedModifier
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(top, bottom))),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = initial,
            color = Color.White,
            style = textStyle,
            fontWeight = FontWeight.Bold,
            fontSize = when {
                initialFontSize != androidx.compose.ui.unit.TextUnit.Unspecified -> initialFontSize
                size != null -> (size.value * 0.42f).sp
                else -> 48.sp
            }
        )
    }
}
