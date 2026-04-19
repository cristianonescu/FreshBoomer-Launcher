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
            style = MaterialTheme.typography.headlineMedium,
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

                // ── Medication Reminders array ──
                JsonLine(lineNum++, buildAnnotatedString {
                    withStyle(SpanStyle(color = CommentColor, fontStyle = FontStyle.Italic)) {
                        append("  // Memento Medicamente")
                    }
                })

                // "medication_reminders": [
                JsonLine(lineNum++, buildAnnotatedString {
                    withStyle(SpanStyle(color = PunctuationColor)) { append("  ") }
                    withStyle(SpanStyle(color = KeyColor)) { append("\"medication_reminders\"") }
                    withStyle(SpanStyle(color = PunctuationColor)) { append(": [") }
                })

                editedConfig.medicationReminders.forEachIndexed { idx, reminder ->
                    val isLastReminder = idx == editedConfig.medicationReminders.lastIndex

                    // {
                    JsonLine(lineNum++, buildAnnotatedString {
                        withStyle(SpanStyle(color = PunctuationColor)) { append("    {") }
                    })

                    // "id": "..."
                    ContactFieldRow(
                        lineNum = lineNum++,
                        key = "id",
                        value = reminder.id,
                        onValueChange = { newId ->
                            val updated = editedConfig.medicationReminders.toMutableList()
                            updated[idx] = reminder.copy(id = newId)
                            editedConfig = editedConfig.copy(medicationReminders = updated)
                        }
                    )

                    // "name": "..."
                    ContactFieldRow(
                        lineNum = lineNum++,
                        key = "name",
                        value = reminder.name,
                        onValueChange = { newName ->
                            val updated = editedConfig.medicationReminders.toMutableList()
                            updated[idx] = reminder.copy(name = newName)
                            editedConfig = editedConfig.copy(medicationReminders = updated)
                        }
                    )

                    // "time": "..."
                    ContactFieldRow(
                        lineNum = lineNum++,
                        key = "time",
                        value = reminder.time,
                        onValueChange = { newTime ->
                            val updated = editedConfig.medicationReminders.toMutableList()
                            updated[idx] = reminder.copy(time = newTime)
                            editedConfig = editedConfig.copy(medicationReminders = updated)
                        }
                    )

                    // "enabled": true/false
                    JsonLine(lineNum++, buildAnnotatedString {
                        withStyle(SpanStyle(color = PunctuationColor)) { append("      ") }
                        withStyle(SpanStyle(color = KeyColor)) { append("\"enabled\"") }
                        withStyle(SpanStyle(color = PunctuationColor)) { append(": ") }
                        withStyle(SpanStyle(color = BooleanColor)) {
                            append(reminder.enabled.toString())
                        }
                    })

                    // "snooze_duration_minutes": N
                    JsonLine(lineNum++, buildAnnotatedString {
                        withStyle(SpanStyle(color = PunctuationColor)) { append("      ") }
                        withStyle(SpanStyle(color = KeyColor)) { append("\"snooze_duration_minutes\"") }
                        withStyle(SpanStyle(color = PunctuationColor)) { append(": ") }
                        withStyle(SpanStyle(color = NumberColor)) { append(reminder.snoozeDurationMinutes.toString()) }
                    })

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
                                    append(if (isLastReminder) "    }" else "    },")
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = {
                                val updated = editedConfig.medicationReminders.toMutableList()
                                updated.removeAt(idx)
                                editedConfig = editedConfig.copy(medicationReminders = updated)
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

                // Add medication reminder button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 30.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    TextButton(
                        onClick = {
                            val updated = editedConfig.medicationReminders.toMutableList()
                            updated.add(ro.softwarechef.freshboomer.data.MedicationReminder())
                            editedConfig = editedConfig.copy(medicationReminders = updated)
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, tint = KeyColor, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adauga memento", color = KeyColor, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    }
                }

                // Closing brace
                JsonLine(lineNum, buildAnnotatedString {
                    withStyle(SpanStyle(color = PunctuationColor)) { append("}") }
                })
            }
        }

        // Online editor hint
        ConfigEditorHint(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        // Bottom buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassButton(
                onClick = {
                    editedConfig = ConfigData()
                    Toast.makeText(context, "Resetat la valori implicite", Toast.LENGTH_SHORT).show()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Reseteaza",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
            AccentGlowButton(
                onClick = {
                    AppConfig.save(context, editedConfig)
                    Toast.makeText(context, "Configurare salvata", Toast.LENGTH_SHORT).show()
                    onBack()
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    "Salveaza",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
        }
    }
}

