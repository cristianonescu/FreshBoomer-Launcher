package ro.softwarechef.freshboomer.home

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import ro.softwarechef.freshboomer.R
import ro.softwarechef.freshboomer.data.FeatureTogglePreference
import ro.softwarechef.freshboomer.models.QuickContact
import ro.softwarechef.freshboomer.ui.composables.AccentGlowButton
import ro.softwarechef.freshboomer.ui.composables.GlassButton
import ro.softwarechef.freshboomer.ui.composables.GradientAvatar
import ro.softwarechef.freshboomer.ui.composables.ImmersiveActivity
import java.io.File

@Composable
fun GridLayout(
    contacts: List<QuickContact>,
    onSmsClick: () -> Unit,
    onPhoneClick: () -> Unit,
    onContactsClick: () -> Unit,
    onGalleryClick: () -> Unit,
    onQuickCall: (name: String, number: String, profile: Int?, photoUri: String?, icon: ImageVector?) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val toggles = remember { FeatureTogglePreference.getToggles(context) }
    val contactRows = contacts.chunked(3)
    val numContactRows = if (toggles.quickContacts) contactRows.size else 0
    val scrollable = numContactRows > 3

    val contactTextStyle = when {
        numContactRows <= 2 -> MaterialTheme.typography.titleLarge
        numContactRows == 3 -> MaterialTheme.typography.titleMedium
        else -> MaterialTheme.typography.bodyLarge
    }

    val utilityButtonCount = listOf(toggles.dialPad, toggles.contacts, toggles.messages).count { it }
    val hasUtilityRow = utilityButtonCount > 0
    val hasGallery = toggles.gallery
    val hasContacts = toggles.quickContacts && numContactRows > 0

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (hasContacts) {
            if (scrollable) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (row in contactRows) {
                        ContactRow(row, context, onQuickCall, contactTextStyle)
                    }
                }
            } else {
                for (row in contactRows) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        for (contact in row) {
                            val drawableResId = contact.drawableResName?.let {
                                context.resources.getIdentifier(it, "drawable", context.packageName)
                            }?.takeIf { it != 0 }

                            Box(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                GridButton(
                                    text = contact.name,
                                    profile = if (contact.photoUri == null) drawableResId else null,
                                    photoUri = contact.photoUri,
                                    onClick = {
                                        onQuickCall(
                                            contact.name,
                                            contact.phoneNumber,
                                            if (contact.photoUri == null) drawableResId else null,
                                            contact.photoUri,
                                            null
                                        )
                                    },
                                    modifier = Modifier,
                                    textStyle = contactTextStyle,
                                )
                            }
                        }
                        repeat(3 - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        if (hasUtilityRow) {
            if (!hasContacts && utilityButtonCount <= 2) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (toggles.dialPad) {
                        GridButton(
                            text = stringResource(R.string.main_dial_number),
                            icon = Icons.Default.Call,
                            onClick = onPhoneClick,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f),
                            roundedSquare = true,
                        )
                    }
                    if (toggles.contacts) {
                        GridButton(
                            text = stringResource(R.string.main_phone_book),
                            icon = Icons.Default.Person,
                            onClick = onContactsClick,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f),
                            roundedSquare = true,
                        )
                    }
                    if (toggles.messages) {
                        GridButton(
                            text = stringResource(R.string.main_messages),
                            icon = Icons.Default.Email,
                            onClick = onSmsClick,
                            modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f),
                            roundedSquare = true,
                        )
                    }
                }
            } else if (!hasContacts) {
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (toggles.dialPad) {
                        GridButton(
                            text = stringResource(R.string.main_dial_number),
                            icon = Icons.Default.Call,
                            onClick = onPhoneClick,
                            modifier = Modifier.weight(1f),
                            roundedSquare = true,
                        )
                    }
                    if (toggles.contacts) {
                        GridButton(
                            text = stringResource(R.string.main_phone_book),
                            icon = Icons.Default.Person,
                            onClick = onContactsClick,
                            modifier = Modifier.weight(1f),
                            roundedSquare = true,
                        )
                    }
                    if (toggles.messages) {
                        GridButton(
                            text = stringResource(R.string.main_messages),
                            icon = Icons.Default.Email,
                            onClick = onSmsClick,
                            modifier = Modifier.weight(1f),
                            roundedSquare = true,
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth().fillMaxHeight(0.15f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (toggles.dialPad) {
                        GridButton(
                            text = stringResource(R.string.main_dial_number),
                            icon = Icons.Default.Call,
                            onClick = onPhoneClick,
                            modifier = Modifier.weight(1f),
                            roundedSquare = true,
                        )
                    }
                    if (toggles.contacts) {
                        GridButton(
                            text = stringResource(R.string.main_phone_book),
                            icon = Icons.Default.Person,
                            onClick = onContactsClick,
                            modifier = Modifier.weight(1f),
                            roundedSquare = true,
                        )
                    }
                    if (toggles.messages) {
                        GridButton(
                            text = stringResource(R.string.main_messages),
                            icon = Icons.Default.Email,
                            onClick = onSmsClick,
                            modifier = Modifier.weight(1f),
                            roundedSquare = true,
                        )
                    }
                }
            }
        }

        if (hasGallery) {
            AccentGlowButton(
                onClick = onGalleryClick,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.main_gallery),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }

        if (toggles.whatsapp) {
            val activity = LocalActivity.current as? ImmersiveActivity
            AccentGlowButton(
                onClick = { activity?.launchWhatsApp() },
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 48.dp),
                color = Color(0xFF25D366),
                shape = RoundedCornerShape(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Call,
                        contentDescription = "WhatsApp",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "WhatsApp",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ContactRow(
    row: List<QuickContact>,
    context: android.content.Context,
    onQuickCall: (name: String, number: String, profile: Int?, photoUri: String?, icon: ImageVector?) -> Unit,
    textStyle: TextStyle
) {
    val buttonSize = 100.dp
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        for (contact in row) {
            val drawableResId = contact.drawableResName?.let {
                context.resources.getIdentifier(it, "drawable", context.packageName)
            }?.takeIf { it != 0 }

            GridButton(
                text = contact.name,
                profile = if (contact.photoUri == null) drawableResId else null,
                photoUri = contact.photoUri,
                onClick = {
                    onQuickCall(
                        contact.name,
                        contact.phoneNumber,
                        if (contact.photoUri == null) drawableResId else null,
                        contact.photoUri,
                        null
                    )
                },
                modifier = Modifier.size(buttonSize),
                textStyle = textStyle,
            )
        }
    }
}

@Composable
fun GridButton(
    text: String,
    icon: ImageVector? = null,
    profile: Int? = null,
    photoUri: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    roundedSquare: Boolean = false,
    textStyle: TextStyle? = null,
) {
    val resolvedTextStyle = textStyle ?: MaterialTheme.typography.titleLarge
    val hasImage = profile != null || photoUri != null

    if (!visible) {
        Box(modifier = modifier.aspectRatio(1f)) {}
        return
    }

    if (roundedSquare) {
        GlassButton(
            onClick = onClick,
            modifier = modifier.fillMaxHeight(),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = text,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2
                )
            }
        }
        return
    }

    Button(
        onClick = onClick,
        modifier = modifier.fillMaxHeight().aspectRatio(1f),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 6.dp,
            pressedElevation = 12.dp,
            focusedElevation = 8.dp,
            hoveredElevation = 8.dp
        ),
        shape = RoundedCornerShape(200.dp),
        colors = if (!hasImage) {
            ButtonDefaults.buttonColors(containerColor = colorResource(id = R.color.gray))
        } else {
            ButtonDefaults.buttonColors(containerColor = Color.Transparent)
        },
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.25f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.35f)
                    )
                )
            ),
            contentAlignment = Alignment.Center
        ) {
            if (!hasImage && icon != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = text,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = text,
                        style = resolvedTextStyle,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        maxLines = 2
                    )
                }
            } else if (!hasImage && icon == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    GradientAvatar(
                        name = text,
                        size = 100.dp,
                        textStyle = resolvedTextStyle.copy(fontWeight = FontWeight.Bold)
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = 8.dp, start = 6.dp, end = 6.dp)
                    ) {
                        Text(
                            text = text,
                            style = resolvedTextStyle.copy(
                                fontSize = resolvedTextStyle.fontSize * 0.7,
                                color = Color.White.copy(alpha = 0.95f),
                                shadow = Shadow(
                                    color = Color.Black.copy(alpha = 0.6f),
                                    offset = Offset(1f, 1f),
                                    blurRadius = 3f
                                )
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else if (photoUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(photoUri))
                        .crossfade(true)
                        .build(),
                    contentDescription = text,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = text,
                    style = resolvedTextStyle.copy(
                        color = Color.White,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(2f, 2f),
                            blurRadius = 6f
                        )
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            } else if (profile != null) {
                Image(
                    painter = painterResource(profile),
                    contentDescription = text,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Text(
                    text = text,
                    style = resolvedTextStyle.copy(
                        color = Color.White,
                        shadow = Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            offset = Offset(2f, 2f),
                            blurRadius = 6f
                        )
                    ),
                    modifier = Modifier.align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                )
            }
        }
    }
}
