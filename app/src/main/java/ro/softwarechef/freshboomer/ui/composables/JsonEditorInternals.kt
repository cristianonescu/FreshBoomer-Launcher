package ro.softwarechef.freshboomer.ui.composables

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.softwarechef.freshboomer.data.ConfigData
import ro.softwarechef.freshboomer.data.ConfigFieldMeta
import ro.softwarechef.freshboomer.data.FieldType

// -------------------------------------------------------------------------
// Code editor color palette
// -------------------------------------------------------------------------

internal val EditorBg = Color(0xFF1E1E1E)
internal val KeyColor = Color(0xFF569CD6)
internal val StringColor = Color(0xFF6A9955)
internal val NumberColor = Color(0xFFCE9178)
internal val BooleanColor = Color(0xFFC586C0)
internal val PunctuationColor = Color(0xFFD4D4D4)
internal val CommentColor = Color(0xFF6A9955)
internal val LineNumColor = Color(0xFF858585)
internal val TooltipBg = Color(0xFF2D2D2D)
internal val TooltipBorder = Color(0xFF454545)

// -------------------------------------------------------------------------
// Syntax-highlighting composables
// -------------------------------------------------------------------------

@Composable
internal fun JsonLine(lineNum: Int, content: AnnotatedString) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%3d".format(lineNum),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = LineNumColor,
            modifier = Modifier.width(30.dp)
        )
        Text(
            text = content,
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )
    }
}

@Composable
internal fun JsonFieldRow(
    lineNum: Int,
    field: ConfigFieldMeta,
    value: Any?,
    isLast: Boolean,
    tooltipExpanded: Boolean,
    onToggleTooltip: () -> Unit,
    onValueChange: (Any) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember { mutableStateOf("") }
    var showDropdown by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "%3d".format(lineNum),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = LineNumColor,
                modifier = Modifier.width(30.dp)
            )

            if (isEditing && (field.type == FieldType.STRING || field.type == FieldType.INT ||
                        field.type == FieldType.LONG || field.type == FieldType.FLOAT)
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = PunctuationColor)) { append("  ") }
                        withStyle(SpanStyle(color = KeyColor)) { append("\"${field.jsonKey}\"") }
                        withStyle(SpanStyle(color = PunctuationColor)) { append(": ") }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp
                )
                TextField(
                    value = editText,
                    onValueChange = { editText = it },
                    modifier = Modifier.weight(1f).height(40.dp),
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = if (field.type == FieldType.STRING) StringColor else NumberColor
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color(0xFF2D2D2D),
                        unfocusedContainerColor = Color(0xFF2D2D2D),
                        cursorColor = Color.White,
                        focusedIndicatorColor = KeyColor,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (field.type) {
                            FieldType.INT, FieldType.LONG -> KeyboardType.Number
                            FieldType.FLOAT -> KeyboardType.Decimal
                            else -> KeyboardType.Text
                        },
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val parsed = parseValue(field, editText)
                            if (parsed != null) {
                                onValueChange(parsed)
                            }
                            isEditing = false
                            focusManager.clearFocus()
                        }
                    )
                )
            } else {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = PunctuationColor)) { append("  ") }
                        withStyle(SpanStyle(color = KeyColor)) { append("\"${field.jsonKey}\"") }
                        withStyle(SpanStyle(color = PunctuationColor)) { append(": ") }
                        appendValue(value, field.type)
                        if (!isLast) {
                            withStyle(SpanStyle(color = PunctuationColor)) { append(",") }
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            when (field.type) {
                                FieldType.BOOLEAN -> onValueChange(!(value as Boolean))
                                FieldType.ENUM -> showDropdown = true
                                else -> {
                                    editText = when (value) {
                                        is Float -> if (value == value.toLong().toFloat()) value.toLong().toString() else value.toString()
                                        else -> value.toString()
                                    }
                                    isEditing = true
                                }
                            }
                        }
                )
            }

            IconButton(
                onClick = onToggleTooltip,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = "Info",
                    tint = if (tooltipExpanded) KeyColor else LineNumColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        if (showDropdown && field.options != null) {
            DropdownMenu(
                expanded = showDropdown,
                onDismissRequest = { showDropdown = false },
                containerColor = TooltipBg
            ) {
                field.options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                option,
                                fontFamily = FontFamily.Monospace,
                                color = StringColor,
                                fontSize = 14.sp
                            )
                        },
                        onClick = {
                            onValueChange(option)
                            showDropdown = false
                        }
                    )
                }
            }
        }

        AnimatedVisibility(visible = tooltipExpanded) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 30.dp, top = 2.dp, bottom = 4.dp, end = 28.dp)
                    .background(TooltipBg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) {
                            append("// ${field.tooltip}")
                        }
                        if (field.options != null) {
                            withStyle(SpanStyle(color = PunctuationColor)) {
                                append("\n// Optiuni: ")
                            }
                            field.options.forEachIndexed { i, opt ->
                                withStyle(SpanStyle(color = StringColor)) { append("\"$opt\"") }
                                if (i < field.options.lastIndex) {
                                    withStyle(SpanStyle(color = PunctuationColor)) { append(" | ") }
                                }
                            }
                        }
                    },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
internal fun ContactFieldRow(
    lineNum: Int,
    key: String,
    value: String,
    isLast: Boolean = false,
    onValueChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    var isEditing by remember { mutableStateOf(false) }
    var editText by remember(value) { mutableStateOf(value) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "%3d".format(lineNum),
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = LineNumColor,
            modifier = Modifier.width(30.dp)
        )

        if (isEditing) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = PunctuationColor)) { append("      ") }
                    withStyle(SpanStyle(color = KeyColor)) { append("\"$key\"") }
                    withStyle(SpanStyle(color = PunctuationColor)) { append(": ") }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
            TextField(
                value = editText,
                onValueChange = { editText = it },
                modifier = Modifier.weight(1f).height(40.dp),
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = StringColor
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFF2D2D2D),
                    unfocusedContainerColor = Color(0xFF2D2D2D),
                    cursorColor = Color.White,
                    focusedIndicatorColor = KeyColor,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (key == "phone") KeyboardType.Phone else KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onValueChange(editText)
                        isEditing = false
                        focusManager.clearFocus()
                    }
                )
            )
        } else {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = PunctuationColor)) { append("      ") }
                    withStyle(SpanStyle(color = KeyColor)) { append("\"$key\"") }
                    withStyle(SpanStyle(color = PunctuationColor)) { append(": ") }
                    withStyle(SpanStyle(color = StringColor)) { append("\"$value\"") }
                    if (!isLast) {
                        withStyle(SpanStyle(color = PunctuationColor)) { append(",") }
                    }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        editText = value
                        isEditing = true
                    }
            )
        }
    }
}

// -------------------------------------------------------------------------
// Syntax-value span builder
// -------------------------------------------------------------------------

internal fun AnnotatedString.Builder.appendValue(value: Any?, type: FieldType) {
    when (type) {
        FieldType.BOOLEAN -> {
            withStyle(SpanStyle(color = BooleanColor, fontWeight = FontWeight.Bold)) {
                append(value.toString())
            }
        }
        FieldType.STRING, FieldType.ENUM -> {
            withStyle(SpanStyle(color = StringColor)) {
                append("\"$value\"")
            }
        }
        FieldType.INT, FieldType.LONG -> {
            withStyle(SpanStyle(color = NumberColor)) {
                append(value.toString())
            }
        }
        FieldType.FLOAT -> {
            withStyle(SpanStyle(color = NumberColor)) {
                val f = value as? Float ?: 0f
                append(if (f == f.toLong().toFloat()) "${f.toLong()}.0" else f.toString())
            }
        }
    }
}

// -------------------------------------------------------------------------
// Field reflection (pure data mapping between ConfigData and JSON keys)
// -------------------------------------------------------------------------

internal fun getFieldValue(config: ConfigData, key: String): Any? = when (key) {
    "user_nickname" -> config.userNickname
    "theme_mode" -> config.themeMode
    "app_language" -> config.appLanguage
    "tts_enabled" -> config.ttsEnabled
    "tts_engine" -> config.ttsEngine
    "tts_speech_rate" -> config.ttsSpeechRate
    "feature_quick_contacts" -> config.featureQuickContacts
    "feature_dial_pad" -> config.featureDialPad
    "feature_contacts" -> config.featureContacts
    "feature_messages" -> config.featureMessages
    "feature_gallery" -> config.featureGallery
    "feature_whatsapp" -> config.featureWhatsapp
    "auto_max_volume" -> config.autoMaxVolume
    "inactivity_timeout_ms" -> config.inactivityTimeoutMs
    "max_missed_call_announcements" -> config.maxMissedCallAnnouncements
    "call_speaker_delay_ms" -> config.callSpeakerDelayMs
    "inactivity_monitor_enabled" -> config.inactivityMonitorEnabled
    "inactivity_monitor_threshold_hours" -> config.inactivityMonitorThresholdHours
    "feature_medication_reminders" -> config.featureMedicationReminders
    "feature_tts_sms" -> config.featureTtsSms
    "tts_sms_prefix" -> config.ttsSmsPrefix
    "feature_tts_sms_trusted_only" -> config.featureTtsSmsTrustedOnly
    else -> null
}

internal fun setFieldValue(config: ConfigData, key: String, value: Any): ConfigData = when (key) {
    "user_nickname" -> config.copy(userNickname = value as String)
    "theme_mode" -> config.copy(themeMode = value as String)
    "app_language" -> config.copy(appLanguage = value as String)
    "tts_enabled" -> config.copy(ttsEnabled = value as Boolean)
    "tts_engine" -> config.copy(ttsEngine = value as String)
    "tts_speech_rate" -> config.copy(ttsSpeechRate = (value as Number).toFloat())
    "feature_quick_contacts" -> config.copy(featureQuickContacts = value as Boolean)
    "feature_dial_pad" -> config.copy(featureDialPad = value as Boolean)
    "feature_contacts" -> config.copy(featureContacts = value as Boolean)
    "feature_messages" -> config.copy(featureMessages = value as Boolean)
    "feature_gallery" -> config.copy(featureGallery = value as Boolean)
    "feature_whatsapp" -> config.copy(featureWhatsapp = value as Boolean)
    "auto_max_volume" -> config.copy(autoMaxVolume = value as Boolean)
    "inactivity_timeout_ms" -> config.copy(inactivityTimeoutMs = (value as Number).toLong())
    "max_missed_call_announcements" -> config.copy(maxMissedCallAnnouncements = (value as Number).toInt())
    "call_speaker_delay_ms" -> config.copy(callSpeakerDelayMs = (value as Number).toLong())
    "inactivity_monitor_enabled" -> config.copy(inactivityMonitorEnabled = value as Boolean)
    "inactivity_monitor_threshold_hours" -> config.copy(inactivityMonitorThresholdHours = (value as Number).toInt())
    "feature_medication_reminders" -> config.copy(featureMedicationReminders = value as Boolean)
    "feature_tts_sms" -> config.copy(featureTtsSms = value as Boolean)
    "tts_sms_prefix" -> config.copy(ttsSmsPrefix = value as String)
    "feature_tts_sms_trusted_only" -> config.copy(featureTtsSmsTrustedOnly = value as Boolean)
    else -> config
}

internal fun parseValue(field: ConfigFieldMeta, text: String): Any? {
    return try {
        when (field.type) {
            FieldType.STRING -> text
            FieldType.INT -> text.trim().toInt()
            FieldType.LONG -> text.trim().toLong()
            FieldType.FLOAT -> text.trim().toFloat()
            else -> null
        }
    } catch (_: NumberFormatException) {
        null
    }
}
