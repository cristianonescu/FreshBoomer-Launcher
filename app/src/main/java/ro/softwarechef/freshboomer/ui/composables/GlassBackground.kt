package ro.softwarechef.freshboomer.ui.composables

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

/**
 * Soft "glass" wallpaper: two radial gradients tinted with the theme's
 * primary / tertiary / secondary accents, fading into the solid background.
 * Sits behind every activity root so translucent cards on top read as
 * frosted glass.
 *
 * **No `Modifier.blur`** — it renders inconsistently across OEM devices
 * (some don't honour it, some fall back to a heavy CPU pass). Pure
 * radial gradients produce the same "soft halo" feel with zero render
 * cost and identical output on every device. This is equivalent to an
 * XML `<shape>` drawable with a `<gradient type="radial">`.
 */
@Composable
fun GlassBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val secondary = MaterialTheme.colorScheme.secondary
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(background)
    ) {
        // Two offset accent halos. Alphas are tuned so they're clearly
        // visible even without blur, but never compete with foreground
        // content for attention.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primary.copy(alpha = 0.28f),
                            primary.copy(alpha = 0.10f),
                            background.copy(alpha = 0f)
                        ),
                        center = Offset(0f, 0f),
                        radius = 1100f
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            tertiary.copy(alpha = 0.18f),
                            secondary.copy(alpha = 0.10f),
                            background.copy(alpha = 0f)
                        ),
                        center = Offset(Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY),
                        radius = 1100f
                    )
                )
        )
        content()
    }
}
