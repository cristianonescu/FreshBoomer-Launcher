package ro.softwarechef.freshboomer.ui.composables

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ro.softwarechef.freshboomer.data.*

// Code editor color palette
private val EditorBg = Color(0xFF1E1E1E)
private val KeyColor = Color(0xFF569CD6)      // blue
private val StringColor = Color(0xFF6A9955)    // green
private val NumberColor = Color(0xFFCE9178)    // orange
private val BooleanColor = Color(0xFFC586C0)   // purple
private val PunctuationColor = Color(0xFFD4D4D4)
private val CommentColor = Color(0xFF6A9955)
private val LineNumColor = Color(0xFF858585)
private val TooltipBg = Color(0xFF2D2D2D)
private val TooltipBorder = Color(0xFF454545)

@Composable
fun JsonConfigEditorScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var editedConfig by remember { mutableStateOf(AppConfig.current) }
    var expandedTooltip by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Inapoi(
            modifier = Modifier.padding(start = 16.dp),
            onClicked = onBack
        )

        Text(
            text = "Configurare JSON",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )

        // JSON editor card
        Card(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = EditorBg)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                var lineNum = 1

                // Opening brace
                JsonLine(lineNum++, buildAnnotatedString {
                    withStyle(SpanStyle(color = PunctuationColor)) { append("{") }
                })

                val groups = ALL_CONFIG_FIELDS.groupBy { it.group }
                val groupEntries = groups.entries.toList()

                groupEntries.forEachIndexed { groupIdx, (group, fields) ->
                    // Group comment
                    JsonLine(lineNum++, buildAnnotatedString {
                        withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) {
                            append("  // ${group.displayName}")
                        }
                    })

                    fields.forEachIndexed { fieldIdx, field ->
                        val value = getFieldValue(editedConfig, field.jsonKey)

                        // Field row — never last since contacts array follows
                        JsonFieldRow(
                            lineNum = lineNum++,
                            field = field,
                            value = value,
                            isLast = false,
                            tooltipExpanded = expandedTooltip == field.jsonKey,
                            onToggleTooltip = {
                                expandedTooltip = if (expandedTooltip == field.jsonKey) null else field.jsonKey
                            },
                            onValueChange = { newValue ->
                                editedConfig = setFieldValue(editedConfig, field.jsonKey, newValue)
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                }

                // ── Quick Contacts array ──
                JsonLine(lineNum++, buildAnnotatedString {
                    withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) {
                        append("  // Contacte Rapide")
                    }
                })

                // "quick_contacts": [
                JsonLine(lineNum++, buildAnnotatedString {
                    withStyle(SpanStyle(color = PunctuationColor)) { append("  ") }
                    withStyle(SpanStyle(color = KeyColor)) { append("\"quick_contacts\"") }
                    withStyle(SpanStyle(color = PunctuationColor)) { append(": [") }
                })

                // Tooltip for the array
                val contactsTooltipKey = "quick_contacts_array"
                val contactsTooltipExpanded = expandedTooltip == contactsTooltipKey
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            expandedTooltip = if (contactsTooltipExpanded) null else contactsTooltipKey
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            tint = if (contactsTooltipExpanded) KeyColor else LineNumColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                AnimatedVisibility(visible = contactsTooltipExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 30.dp, top = 2.dp, bottom = 4.dp, end = 28.dp)
                            .background(TooltipBg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) {
                                    append("// Lista de contacte rapide afisate pe ecranul principal.\n")
                                    append("// Fiecare contact are: \"name\" (nume) si \"phone\" (numar telefon).\n")
                                    append("// Poti adauga, sterge sau modifica contacte.")
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                editedConfig.quickContacts.forEachIndexed { idx, contact ->
                    val isLastContact = idx == editedConfig.quickContacts.lastIndex

                    // {
                    JsonLine(lineNum++, buildAnnotatedString {
                        withStyle(SpanStyle(color = PunctuationColor)) { append("    {") }
                    })

                    // "name": "..."
                    ContactFieldRow(
                        lineNum = lineNum++,
                        key = "name",
                        value = contact.name,
                        onValueChange = { newName ->
                            val updated = editedConfig.quickContacts.toMutableList()
                            updated[idx] = contact.copy(name = newName)
                            editedConfig = editedConfig.copy(quickContacts = updated)
                        }
                    )

                    // "phone": "..."
                    ContactFieldRow(
                        lineNum = lineNum++,
                        key = "phone",
                        value = contact.phoneNumber,
                        isLast = true,
                        onValueChange = { newPhone ->
                            val updated = editedConfig.quickContacts.toMutableList()
                            updated[idx] = contact.copy(phoneNumber = newPhone)
                            editedConfig = editedConfig.copy(quickContacts = updated)
                        }
                    )

                    // } or },
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "%3d".format(lineNum++),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = LineNumColor,
                            modifier = Modifier.width(30.dp)
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = PunctuationColor)) {
                                    append(if (isLastContact) "    }" else "    },")
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        // Delete contact button
                        if (editedConfig.quickContacts.size > 1) {
                            IconButton(
                                onClick = {
                                    val updated = editedConfig.quickContacts.toMutableList()
                                    updated.removeAt(idx)
                                    editedConfig = editedConfig.copy(quickContacts = updated)
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Sterge",
                                    tint = Color(0xFFCF6679),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                // ]
                JsonLine(lineNum++, buildAnnotatedString {
                    withStyle(SpanStyle(color = PunctuationColor)) { append("  ]") }
                })

                // Add contact button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 30.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    TextButton(
                        onClick = {
                            val updated = editedConfig.quickContacts.toMutableList()
                            updated.add(
                                ro.softwarechef.freshboomer.models.QuickContact(
                                    id = (updated.size + 1).toString(),
                                    name = "",
                                    phoneNumber = "",
                                    sortOrder = updated.size
                                )
                            )
                            editedConfig = editedConfig.copy(quickContacts = updated)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = KeyColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adauga contact", color = KeyColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }

                // ── Emergency Contacts array ──
                JsonLine(lineNum++, buildAnnotatedString {
                    withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) {
                        append("  // Contacte de Urgenta")
                    }
                })

                // "emergency_contacts": [
                JsonLine(lineNum++, buildAnnotatedString {
                    withStyle(SpanStyle(color = PunctuationColor)) { append("  ") }
                    withStyle(SpanStyle(color = KeyColor)) { append("\"emergency_contacts\"") }
                    withStyle(SpanStyle(color = PunctuationColor)) { append(": [") }
                })

                // Tooltip for emergency contacts
                val emergencyTooltipKey = "emergency_contacts_array"
                val emergencyTooltipExpanded = expandedTooltip == emergencyTooltipKey
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 30.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = {
                            expandedTooltip = if (emergencyTooltipExpanded) null else emergencyTooltipKey
                        },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            Icons.Default.Info,
                            contentDescription = "Info",
                            tint = if (emergencyTooltipExpanded) KeyColor else LineNumColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                AnimatedVisibility(visible = emergencyTooltipExpanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 30.dp, top = 2.dp, bottom = 4.dp, end = 28.dp)
                            .background(TooltipBg, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) {
                                    append("// Contacte de urgenta care vor fi notificate daca utilizatorul are probleme.\n")
                                    append("// Fiecare contact are: \"name\" (nume) si \"phone\" (numar telefon).")
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }

                editedConfig.emergencyContacts.forEachIndexed { idx, contact ->
                    val isLastEmergency = idx == editedConfig.emergencyContacts.lastIndex

                    // {
                    JsonLine(lineNum++, buildAnnotatedString {
                        withStyle(SpanStyle(color = PunctuationColor)) { append("    {") }
                    })

                    // "name": "..."
                    ContactFieldRow(
                        lineNum = lineNum++,
                        key = "name",
                        value = contact.name,
                        onValueChange = { newName ->
                            val updated = editedConfig.emergencyContacts.toMutableList()
                            updated[idx] = contact.copy(name = newName)
                            editedConfig = editedConfig.copy(emergencyContacts = updated)
                        }
                    )

                    // "phone": "..."
                    ContactFieldRow(
                        lineNum = lineNum++,
                        key = "phone",
                        value = contact.phoneNumber,
                        isLast = true,
                        onValueChange = { newPhone ->
                            val updated = editedConfig.emergencyContacts.toMutableList()
                            updated[idx] = contact.copy(phoneNumber = newPhone)
                            editedConfig = editedConfig.copy(emergencyContacts = updated)
                        }
                    )

                    // } or },
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "%3d".format(lineNum++),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            color = LineNumColor,
                            modifier = Modifier.width(30.dp)
                        )
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(color = PunctuationColor)) {
                                    append(if (isLastEmergency) "    }" else "    },")
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val updated = editedConfig.emergencyContacts.toMutableList()
                                updated.removeAt(idx)
                                editedConfig = editedConfig.copy(emergencyContacts = updated)
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Sterge",
                                tint = Color(0xFFCF6679),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // ]
                JsonLine(lineNum++, buildAnnotatedString {
                    withStyle(SpanStyle(color = PunctuationColor)) { append("  ]") }
                })

                // Add emergency contact button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 30.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    TextButton(
                        onClick = {
                            val updated = editedConfig.emergencyContacts.toMutableList()
                            updated.add(EmergencyContact())
                            editedConfig = editedConfig.copy(emergencyContacts = updated)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = KeyColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adauga contact de urgenta", color = KeyColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }

                // Closing brace
                JsonLine(lineNum, buildAnnotatedString {
                    withStyle(SpanStyle(color = PunctuationColor)) { append("}") }
                })
            }
        }

        // Bottom buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = {
                    editedConfig = ConfigData()
                    Toast.makeText(context, "Resetat la valori implicite", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Reseteaza", fontSize = 16.sp)
            }
            Button(
                onClick = {
                    AppConfig.save(context, editedConfig)
                    Toast.makeText(context, "Configurare salvata", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Salveaza", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun JsonLine(lineNum: Int, content: androidx.compose.ui.text.AnnotatedString) {
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
private fun JsonFieldRow(
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
            // Line number
            Text(
                text = "%3d".format(lineNum),
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = LineNumColor,
                modifier = Modifier.width(30.dp)
            )

            // JSON content
            if (isEditing && (field.type == FieldType.STRING || field.type == FieldType.INT ||
                        field.type == FieldType.LONG || field.type == FieldType.FLOAT)
            ) {
                // Inline editing mode
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
                    textStyle = androidx.compose.ui.text.TextStyle(
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
                // Display mode — tap value to edit
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
                                FieldType.BOOLEAN -> {
                                    onValueChange(!(value as Boolean))
                                }
                                FieldType.ENUM -> {
                                    showDropdown = true
                                }
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

            // Info icon
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

        // Enum dropdown
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

        // Tooltip
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

private fun androidx.compose.ui.text.AnnotatedString.Builder.appendValue(value: Any?, type: FieldType) {
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

private fun getFieldValue(config: ConfigData, key: String): Any? = when (key) {
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
    else -> null
}

private fun setFieldValue(config: ConfigData, key: String, value: Any): ConfigData = when (key) {
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
    else -> config
}

private fun parseValue(field: ConfigFieldMeta, text: String): Any? {
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

@Composable
private fun ContactFieldRow(
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
                textStyle = androidx.compose.ui.text.TextStyle(
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
