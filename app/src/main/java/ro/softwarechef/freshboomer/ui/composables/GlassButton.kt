package ro.softwarechef.freshboomer.ui.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Translucent "glass" surface button. Built from pure Compose primitives
 * (background + gradient highlight + border) so it renders identically
 * on every device.
 *
 * **No `Modifier.shadow`** — colored ambient/spot shadows render
 * inconsistently on several OEM skins (missing or muddy). The border
 * stroke + translucent gradient produce a visually distinct edge without
 * relying on per-device shadow support. This is equivalent to an XML
 * `<shape>` with `<gradient>` + `<stroke>`.
 *
 * Visual language:
 *   - Translucent fill: `surface` alpha 0.20 (dark) / 0.75 (light).
 *   - Top-to-bottom gradient highlight for the glass shine.
 *   - 1dp border in the accent color (alpha 0.45) — gives the edge
 *     definition that shadow normally would.
 *   - 18dp corner radius.
 *
 * Use for neutral / secondary actions. For primary actions use
 * [AccentGlowButton].
 */
@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = RoundedCornerShape(18.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 14.dp, vertical = 14.dp),
    content: @Composable () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val surfaceAlpha = if (isDark) 0.20f else 0.75f
    val surfaceColor = MaterialTheme.colorScheme.surface.copy(alpha = surfaceAlpha)
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
    val highlightAlpha = if (isDark) 0.08f else 0.35f

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(shape)
            .background(surfaceColor)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = highlightAlpha),
                        Color.Transparent
                    )
                )
            )
            .border(BorderStroke(1.dp, borderColor), shape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = MaterialTheme.colorScheme.primary),
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * Solid accent "glow" button. Matches the prototype's `glowOf()` helper
 * but without `Modifier.shadow` (which renders inconsistently on some
 * devices). The saturated accent background + top-to-bottom highlight
 * gradient + 1dp inner-edge tint provide the same visual weight at zero
 * render cost.
 *
 * Use for primary positive actions (call, send, confirm). For destructive
 * actions, pass [color] = `MaterialTheme.colorScheme.error` or a red.
 *
 * Visual language:
 *   - Background: [color] (defaults to `primary`).
 *   - Top-to-bottom highlight gradient (white alpha 0.18 → transparent).
 *   - 18dp corner radius, 14dp vertical / 20dp horizontal padding.
 */
@Composable
fun AccentGlowButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = MaterialTheme.colorScheme.primary,
    shape: Shape = RoundedCornerShape(18.dp),
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .clip(shape)
            .background(color)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.18f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.08f)
                    )
                )
            )
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = Color.White.copy(alpha = 0.3f)),
                enabled = enabled,
                onClick = onClick
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/** Rough luminance for deciding light vs dark theme without importing Palette. */
private fun Color.luminance(): Float =
    0.299f * red + 0.587f * green + 0.114f * blue
