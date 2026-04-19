package ro.softwarechef.freshboomer.ui.composables

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.net.Uri
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import ro.softwarechef.freshboomer.R
import ro.softwarechef.freshboomer.data.LocaleHelper
import ro.softwarechef.freshboomer.data.NicknamePreference
import ro.softwarechef.freshboomer.tts.PiperTtsEngine
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

private const val TAG = "FB/ConfirmCallDialog"

@Composable
fun ConfirmCallDialog(
    name: String,
    number: String,
    icon: ImageVector? = null,
    profile: Int? = null,
    photoUri: String? = null,
    onPhoneCall: () -> Unit,
    onDismiss: () -> Unit,
    timeoutSeconds: Int = 20
) {
    val context = LocalContext.current
    val nickname = NicknamePreference.getNickname(context)

    LaunchedEffect(Unit) {
        val ttsText = context.getString(R.string.confirm_call_prompt, nickname, name)
        val preferredEngine = ro.softwarechef.freshboomer.data.TtsPreference.getEngine(context)
        if ((preferredEngine == ro.softwarechef.freshboomer.data.TtsEngine.PIPER_LILI ||
             preferredEngine == ro.softwarechef.freshboomer.data.TtsEngine.PIPER_SANDA) && PiperTtsEngine.isReady) {
            launch(Dispatchers.IO) {
                PiperTtsEngine.speak(ttsText)
            }
        } else {
            // Fallback to Android TTS
            var fallbackTts: TextToSpeech? = null
            fallbackTts = TextToSpeech(context) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    fallbackTts?.language = LocaleHelper.getLocale()
                    fallbackTts?.setSpeechRate(0.85f)
                    fallbackTts?.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    fallbackTts?.speak(ttsText, TextToSpeech.QUEUE_FLUSH, null, "confirm_call")
                }
            }
        }
    }

    // Auto-dismiss after timeout
    LaunchedEffect(Unit) {
        delay(timeoutSeconds * 1000L)
        onDismiss()
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        val bg = MaterialTheme.colorScheme.background
        val isDark = (0.299f * bg.red + 0.587f * bg.green + 0.114f * bg.blue) < 0.5f
        val dialogShape = RoundedCornerShape(24.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .clip(dialogShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = if (isDark) 0.08f else 0.35f),
                            Color.Transparent
                        )
                    )
                )
                .border(
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
                    dialogShape
                )
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val avatarModifier = Modifier
                .size(140.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
            if (photoUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(photoUri))
                        .crossfade(true)
                        .build(),
                    contentDescription = name,
                    modifier = avatarModifier,
                    contentScale = ContentScale.Crop
                )
            } else if (profile != null) {
                Image(
                    painter = painterResource(profile),
                    contentDescription = name,
                    modifier = avatarModifier,
                    contentScale = ContentScale.Crop
                )
            } else if (icon != null) {
                Box(
                    modifier = avatarModifier
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = name,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(80.dp)
                    )
                }
            } else {
                GradientAvatar(name = name, size = 140.dp)
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = context.getString(R.string.confirm_call_prompt, nickname, name),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // "Da" — primary green glow (confirm call)
                AccentGlowButton(
                    onClick = onPhoneCall,
                    modifier = Modifier.height(56.dp).weight(1f),
                    color = Color(0xFF4CAF50),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Da",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }

                // "Nu" — glass (cancel)
                GlassButton(
                    onClick = onDismiss,
                    modifier = Modifier.height(56.dp).weight(1f),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Nu",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

private fun findWhatsAppContactDataId(context: Context, phoneNumber: String, mimeType: String): Long? {
    // Normalize number: strip leading 0, prepend +40 for Romanian numbers
    val normalizedNumber = when {
        phoneNumber.startsWith("+") -> phoneNumber
        phoneNumber.startsWith("0") -> "+40${phoneNumber.substring(1)}"
        else -> "+40$phoneNumber"
    }
    val strippedNumber = normalizedNumber.replace("+", "").replace(" ", "").replace("-", "")

    Log.d(TAG, "Looking up WhatsApp data for: $phoneNumber -> normalized: $normalizedNumber, stripped: $strippedNumber")

    // Approach 1: Query WhatsApp data rows directly matching the phone number in data3
    // WhatsApp stores the number as "40734490731@s.whatsapp.net" or similar in DATA1
    val dataUri = ContactsContract.Data.CONTENT_URI
    context.contentResolver.query(
        dataUri,
        arrayOf(ContactsContract.Data._ID, ContactsContract.Data.DATA1, ContactsContract.Data.MIMETYPE),
        "${ContactsContract.Data.MIMETYPE} = ?",
        arrayOf(mimeType),
        null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Data._ID))
            val data1 = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.Data.DATA1)) ?: continue
            Log.d(TAG, "  Found WhatsApp entry: id=$id, data1=$data1")

            // WhatsApp data1 is typically like "40734490731@s.whatsapp.net" or the display name
            // Check if it contains our stripped number
            val data1Stripped = data1.replace("+", "").replace(" ", "").replace("-", "")
            if (data1Stripped.contains(strippedNumber) || strippedNumber.endsWith(data1Stripped.take(10))) {
                Log.d(TAG, "  Match found! id=$id")
                return id
            }
        }
    }

    // Approach 2: Go through PhoneLookup -> contact_id -> WhatsApp data
    val lookupUri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(phoneNumber)
    )
    val contactIds = mutableListOf<Long>()
    context.contentResolver.query(
        lookupUri,
        arrayOf(ContactsContract.PhoneLookup._ID),
        null, null, null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            contactIds.add(cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID)))
        }
    }

    // Also try with normalized number
    if (normalizedNumber != phoneNumber) {
        val lookupUri2 = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(normalizedNumber)
        )
        context.contentResolver.query(
            lookupUri2,
            arrayOf(ContactsContract.PhoneLookup._ID),
            null, null, null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup._ID))
                if (id !in contactIds) contactIds.add(id)
            }
        }
    }

    Log.d(TAG, "  PhoneLookup found contact IDs: $contactIds")

    for (contactId in contactIds) {
        context.contentResolver.query(
            dataUri,
            arrayOf(ContactsContract.Data._ID),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ?",
            arrayOf(contactId.toString(), mimeType),
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(ContactsContract.Data._ID))
                Log.d(TAG, "  Match via PhoneLookup! contactId=$contactId, dataId=$id")
                return id
            }
        }
    }

    Log.w(TAG, "  No WhatsApp data found for $phoneNumber")
    return null
}

private fun startWhatsAppVideoCall(context: Context, phoneNumber: String) {
    val dataId = findWhatsAppContactDataId(
        context, phoneNumber, "vnd.android.cursor.item/vnd.com.whatsapp.video.call"
    )
    if (dataId != null) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse("content://com.android.contacts/data/$dataId")
                setPackage("com.whatsapp")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(intent)
            Log.d(TAG, "Launched WhatsApp video call intent for dataId=$dataId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch WhatsApp video call", e)
            Toast.makeText(context, "Nu s-a putut deschide WhatsApp", Toast.LENGTH_LONG).show()
        }
    } else {
        Toast.makeText(context, "Nu s-a găsit contactul pe WhatsApp", Toast.LENGTH_LONG).show()
    }
}

