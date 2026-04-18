package ro.softwarechef.freshboomer

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Telephony
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import ro.softwarechef.freshboomer.models.Contact
import ro.softwarechef.freshboomer.ui.theme.LauncherTheme
import kotlinx.coroutines.flow.MutableStateFlow
import android.app.role.RoleManager
import android.content.Context
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.telephony.SmsManager
import kotlinx.coroutines.flow.update
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import kotlinx.coroutines.delay
import android.provider.ContactsContract
import ro.softwarechef.freshboomer.ui.composables.AccentGlowButton
import ro.softwarechef.freshboomer.ui.composables.GlassBackground
import ro.softwarechef.freshboomer.ui.composables.GradientAvatar
import ro.softwarechef.freshboomer.ui.composables.HideSystemBars
import ro.softwarechef.freshboomer.ui.composables.ImmersiveActivity
import ro.softwarechef.freshboomer.ui.composables.Inapoi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun requestDefaultSmsRole(context: Context, resultLauncher: ActivityResultLauncher<Intent>) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
        if (!roleManager.isRoleHeld(RoleManager.ROLE_SMS)) {
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_SMS)
            resultLauncher.launch(intent)
        }
    }
}

class SmsActivity : ImmersiveActivity() {
    private val conversations = MutableStateFlow<List<Contact>>(emptyList())

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.all { it.value }) {
            setDefaultSmsApp()
        }
    }

    private val smsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                "ro.softwarechef.freshboomer.SMS_RECEIVED" -> {
                    loadConversations(context!!, conversations)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            checkPermissions()
            registerReceiver(
                smsReceiver,
                IntentFilter("ro.softwarechef.freshboomer.SMS_RECEIVED"),
                Context.RECEIVER_NOT_EXPORTED
            )
            setContent {
                HideSystemBars()
                LauncherTheme {
                    GlassBackground {
                        SmsScreen(
                            onBackClick = { finish() },
                            conversations = conversations
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SmsActivity", "Error in onCreate", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(smsReceiver)
        } catch (e: Exception) {
            Log.e("SmsActivity", "Error unregistering receiver", e)
        }
    }

    private fun checkPermissions() {
        try {
            val permissions = arrayOf(
                Manifest.permission.READ_SMS,
                Manifest.permission.SEND_SMS,
                Manifest.permission.RECEIVE_SMS
            )

            if (permissions.all {
                    ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
                }) {
                setDefaultSmsApp()
            } else {
                requestPermissionLauncher.launch(permissions)
            }
        } catch (e: Exception) {
            Log.e("SmsActivity", "Error checking permissions", e)
        }
    }

    private fun setDefaultSmsApp() {
        try {
            if (!Telephony.Sms.getDefaultSmsPackage(this).equals(packageName)) {
                val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT)
                intent.putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, packageName)
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("SmsActivity", "Error setting default SMS app", e)
        }
    }

    public fun sendSms(context: Context, phoneNumber: String, message: String) {
        try {
            @Suppress("DEPRECATION")
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)

            conversations.update { currentList ->
                val updatedList = currentList.toMutableList()
                val contactIndex = updatedList.indexOfFirst { contact -> contact.phoneNumber == phoneNumber }

                if (contactIndex != -1) {
                    val contact = updatedList[contactIndex]
                    updatedList[contactIndex] = contact.copy(
                        lastMessage = message,
                        date = System.currentTimeMillis()
                    )
                } else {
                    updatedList.add(Contact(
                        name = phoneNumber,
                        phoneNumber = phoneNumber,
                        lastMessage = message,
                        date = System.currentTimeMillis()
                    ))
                }
                updatedList.sortedByDescending { it.date }
            }

            val updateIntent = Intent("ro.softwarechef.freshboomer.SMS_RECEIVED")
            context.sendBroadcast(updateIntent)

        } catch (e: Exception) {
            Log.e("SmsActivity", "Error sending SMS", e)
        }
    }
}

@Composable
fun SmsScreen(
    onBackClick: () -> Unit,
    conversations: MutableStateFlow<List<Contact>>
) {
    var showConversation by remember { mutableStateOf(false) }
    var selectedContact by remember { mutableStateOf<Contact?>(null) }
    val context = LocalContext.current
    val conversationsState by conversations.collectAsState()

    LaunchedEffect(Unit) {
        try {
            loadConversations(context, conversations)
        } catch (e: Exception) {
            Log.e("SmsScreen", "Error loading conversations", e)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Inapoi(onClicked = {
            if (showConversation) {
                showConversation = false
                selectedContact = null
            }
        })

        if (!showConversation) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Email,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = stringResource(R.string.sms_header),
                    style = MaterialTheme.typography.displayLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
                )
            }

            // Conversation count
            Text(
                text = stringResource(R.string.sms_conversations_count, conversationsState.size),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
            )

            if (conversationsState.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Email,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.sms_empty),
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                ConversationList(
                    conversations = conversationsState,
                    onConversationClick = { contact ->
                        selectedContact = contact
                        showConversation = true
                    }
                )
            }
        } else {
            selectedContact?.let { contact ->
                // Conversation header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GradientAvatar(
                        name = contact.name,
                        size = 44.dp,
                        textStyle = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = contact.name,
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = contact.phoneNumber,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(bottom = 8.dp),
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                )

                ConversationScreen(
                    contact = contact,
                    onBackClick = onBackClick,
                    onSendMessage = { message ->
                        (context as? SmsActivity)?.sendSms(context, contact.phoneNumber, message)
                    }
                )
            }
        }
    }
}

@Composable
fun ConversationList(
    conversations: List<Contact>,
    onConversationClick: (Contact) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = PaddingValues(bottom = 16.dp)
    ) {
        items(conversations) { contact ->
            ConversationItem(
                contact = contact,
                onClick = { onConversationClick(contact) }
            )
        }
    }
}

@Composable
fun ConversationItem(
    contact: Contact,
    onClick: () -> Unit
) {
    val timeText = remember(contact.date) {
        if (contact.date > 0) {
            val now = System.currentTimeMillis()
            val diff = now - contact.date
            when {
                diff < 60_000 -> "acum"
                diff < 3_600_000 -> "${diff / 60_000} min"
                diff < 86_400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(contact.date))
                else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(contact.date))
            }
        } else ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GradientAvatar(
                name = contact.name,
                size = 52.dp,
                textStyle = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.width(14.dp))

            // Name, message, time
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = contact.name,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = if (contact.unreadCount > 0) FontWeight.Bold else FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (timeText.isNotEmpty()) {
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (contact.unreadCount > 0)
                                MaterialTheme.colorScheme.primary
                            else
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = contact.lastMessage,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (contact.unreadCount > 0) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Badge(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) {
                            Text(
                                text = contact.unreadCount.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConversationScreen(
    contact: Contact,
    onBackClick: () -> Unit,
    onSendMessage: (String) -> Unit
) {
    var messageText by remember { mutableStateOf("") }
    val messages = remember { MutableStateFlow<List<Message>>(emptyList()) }
    val context = LocalContext.current
    val messagesState by messages.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(contact) {
        loadMessages(context, contact.phoneNumber, messages)
        while (true) {
            delay(1000)
            loadMessages(context, contact.phoneNumber, messages)
        }
    }

    // Auto-scroll to bottom when messages change
    LaunchedEffect(messagesState.size) {
        if (messagesState.isNotEmpty()) {
            listState.animateScrollToItem(messagesState.size - 1)
        }
    }

    DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "ro.softwarechef.freshboomer.SMS_RECEIVED") {
                    loadMessages(context!!, contact.phoneNumber, messages)
                }
            }
        }
        context.registerReceiver(
            receiver,
            IntentFilter("ro.softwarechef.freshboomer.SMS_RECEIVED"),
            Context.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (e: Exception) {
                Log.e("ConversationScreen", "Error unregistering receiver", e)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(messagesState) { message ->
                MessageItem(message = message)
            }
        }

        // Message input area
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { messageText = it },
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    placeholder = {
                        Text(
                            "Scrie un mesaj...",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    )
                )

                AccentGlowButton(
                    onClick = {
                        if (messageText.isNotBlank()) {
                            onSendMessage(messageText)
                            messageText = ""
                        }
                    },
                    modifier = Modifier.height(56.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "Trimite",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: Message) {
    val isFromMe = message.isFromMe
    val timeText = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = if (isFromMe) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isFromMe) 16.dp else 4.dp,
                bottomEnd = if (isFromMe) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isFromMe)
                    MaterialTheme.colorScheme.primary
                else
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                if (!isFromMe && message.sender.isNotEmpty()) {
                    Text(
                        text = message.sender,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Text(
                    text = message.content,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isFromMe)
                        MaterialTheme.colorScheme.onPrimary
                    else
                        MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isFromMe)
                        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                    else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

private fun loadConversations(context: android.content.Context, conversations: MutableStateFlow<List<Contact>>) {
    try {
        val cursor = context.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY,
                Telephony.Sms.DATE,
                Telephony.Sms.READ
            ),
            null,
            null,
            "${Telephony.Sms.DATE} DESC"
        )

        val contactMap = mutableMapOf<String, Contact>()

        cursor?.use {
            while (it.moveToNext()) {
                try {
                    val address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
                    val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY))
                    val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
                    val read = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.READ)) == 1

                    val contactName = getContactName(context, address)

                    val contact = contactMap.getOrPut(address) {
                        Contact(
                            name = contactName ?: address,
                            phoneNumber = address,
                            lastMessage = body,
                            date = date,
                            unreadCount = 0
                        )
                    }

                    if (!read) {
                        contact.unreadCount++
                    }
                } catch (e: Exception) {
                    Log.e("SmsActivity", "Error processing message", e)
                }
            }
        }

        conversations.value = contactMap.values.sortedByDescending { it.date }
    } catch (e: Exception) {
        Log.e("SmsActivity", "Error loading conversations", e)
        conversations.value = emptyList()
    }
}

private fun loadMessages(context: android.content.Context, phoneNumber: String, messages: MutableStateFlow<List<Message>>) {
    val cursor = context.contentResolver.query(
        Telephony.Sms.CONTENT_URI,
        arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        ),
        "${Telephony.Sms.ADDRESS} = ?",
        arrayOf(phoneNumber),
        "${Telephony.Sms.DATE} ASC"
    )

    val messageList = mutableListOf<Message>()
    val contactName = getContactName(context, phoneNumber)

    cursor?.use {
        while (it.moveToNext()) {
            val address = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.ADDRESS))
            val body = it.getString(it.getColumnIndexOrThrow(Telephony.Sms.BODY))
            val date = it.getLong(it.getColumnIndexOrThrow(Telephony.Sms.DATE))
            val type = it.getInt(it.getColumnIndexOrThrow(Telephony.Sms.TYPE))

            messageList.add(
                Message(
                    sender = if (type == Telephony.Sms.MESSAGE_TYPE_SENT) "" else (contactName ?: address),
                    content = body,
                    isFromMe = type == Telephony.Sms.MESSAGE_TYPE_SENT,
                    timestamp = date
                )
            )
        }
    }

    messages.value = messageList
}

private fun getContactName(context: android.content.Context, phoneNumber: String): String? {
    val uri = android.net.Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        android.net.Uri.encode(phoneNumber)
    )
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.PhoneLookup.DISPLAY_NAME))
            } else null
        }
    } catch (e: Exception) {
        Log.e("SmsActivity", "Error looking up contact name", e)
        null
    }
}

data class Contact(
    val name: String,
    val phoneNumber: String,
    val lastMessage: String,
    val date: Long,
    var unreadCount: Int = 0
)

data class Message(
    val sender: String,
    val content: String,
    val isFromMe: Boolean,
    val timestamp: Long
)
