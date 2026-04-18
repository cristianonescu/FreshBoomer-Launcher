package ro.softwarechef.freshboomer

import androidx.compose.ui.res.stringResource
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.ContactsContract
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ro.softwarechef.freshboomer.models.Contact
import ro.softwarechef.freshboomer.ui.composables.AccentGlowButton
import ro.softwarechef.freshboomer.ui.composables.ConfirmCallDialog
import ro.softwarechef.freshboomer.ui.composables.GlassBackground
import ro.softwarechef.freshboomer.ui.composables.GlassButton
import ro.softwarechef.freshboomer.ui.composables.GradientAvatar
import ro.softwarechef.freshboomer.ui.composables.HideSystemBars
import ro.softwarechef.freshboomer.ui.composables.ImmersiveActivity
import ro.softwarechef.freshboomer.ui.composables.Inapoi
import ro.softwarechef.freshboomer.ui.theme.LauncherTheme
import kotlinx.coroutines.delay

class ContactsActivity : ImmersiveActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, load contacts
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        checkPermission()
        setContent {
            HideSystemBars()
            LauncherTheme {
                GlassBackground {
                    ContactsScreen(
                        onBackClick = { finish() },
                        onAddContact = { showAddContactDialog() },
                        onEditContact = { contact -> showEditContactDialog(contact) },
                        onDeleteContact = { contact -> deleteContact(contact) },
                        onCallContact = { number -> makePhoneCall(number) }
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            WindowInsetsControllerCompat(window, window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
    }


    private fun checkPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission is already granted
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }

    private fun showAddContactDialog() {
        val intent = Intent(Intent.ACTION_INSERT).apply {
            type = ContactsContract.Contacts.CONTENT_TYPE
        }
        startActivity(intent)
    }

    private fun showEditContactDialog(contact: Contact) {
        val intent = Intent(Intent.ACTION_EDIT).apply {
            data = ContactsContract.Contacts.getLookupUri(
                contact.id.toLongOrNull() ?: 0L,
                contact.lookupKey
            )
        }
        startActivity(intent)
    }

    private fun deleteContact(contact: Contact) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = ContactsContract.Contacts.getLookupUri(
                contact.id.toLongOrNull() ?: 0L,
                contact.lookupKey
            )
        }
        startActivity(intent)
    }
}

@Composable
fun ContactsScreen(
    onBackClick: () -> Unit,
    onAddContact: () -> Unit,
    onEditContact: (Contact) -> Unit,
    onDeleteContact: (Contact) -> Unit,
    onCallContact: (String) -> Unit
) {
    val context = LocalContext.current
    var contacts by remember { mutableStateOf<List<Contact>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }

    var confirmCallContact by remember { mutableStateOf<Contact?>(null) }

    // Load contacts initially
    LaunchedEffect(Unit) {
        loadContacts(context) { loadedContacts ->
            contacts = loadedContacts
        }
    }

    // Set up periodic refresh
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            loadContacts(context) { loadedContacts ->
                contacts = loadedContacts
            }
        }
    }

    val filteredContacts = contacts.filter {
        it.name.contains(searchQuery, ignoreCase = true) ||
                it.phoneNumber.contains(searchQuery)
    }

    // Group contacts by first letter
    val groupedContacts = filteredContacts.groupBy {
        it.name.firstOrNull()?.uppercaseChar()?.toString() ?: "#"
    }.toSortedMap()

    // Confirm call dialog
    if (confirmCallContact != null) {
        ConfirmCallDialog(
            name = confirmCallContact!!.name,
            number = confirmCallContact!!.phoneNumber,
            onPhoneCall = {
                onCallContact(confirmCallContact!!.phoneNumber)
                confirmCallContact = null
            },
            onDismiss = { confirmCallContact = null }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Inapoi()

        // Header row with title and add button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.contacts_header),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            GlassButton(
                onClick = onAddContact,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.contacts_add),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            placeholder = {
                Text(
                    stringResource(R.string.contacts_search_placeholder),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )
            },
            leadingIcon = {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            trailingIcon = {
                AnimatedVisibility(
                    visible = searchQuery.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Sterge",
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            shape = RoundedCornerShape(16.dp),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        )

        // Contact count
        Text(
            text = "${filteredContacts.size} contacte",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp)
        )

        // Contact list grouped by letter
        if (filteredContacts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (searchQuery.isNotEmpty()) Icons.Default.Search else Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = if (searchQuery.isNotEmpty())
                            "Nu s-a gasit nimeni cu \"$searchQuery\""
                        else
                            stringResource(R.string.contacts_empty),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                groupedContacts.forEach { (letter, contactsInGroup) ->
                    // Letter header
                    item(key = "header_$letter") {
                        Text(
                            text = letter,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(
                                start = 8.dp,
                                top = if (letter == groupedContacts.keys.first()) 0.dp else 12.dp,
                                bottom = 4.dp
                            )
                        )
                    }

                    items(
                        items = contactsInGroup,
                        key = { "${it.id}_${it.phoneNumber}" }
                    ) { contact ->
                        ContactItem(
                            contact = contact,
                            onCall = { confirmCallContact = contact },
                            onEdit = { onEditContact(contact) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactItem(
    contact: Contact,
    onCall: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCall),
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
            GradientAvatar(name = contact.name, size = 56.dp)

            Spacer(modifier = Modifier.width(16.dp))

            // Name and number
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.name,
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = contact.phoneNumber,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Call button — green glow
            AccentGlowButton(
                onClick = onCall,
                modifier = Modifier.height(52.dp).widthIn(min = 100.dp),
                color = Color(0xFF4CAF50),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Call,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.contacts_call),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                }
            }
        }
    }
}

private fun loadContacts(context: android.content.Context, onContactsLoaded: (List<Contact>) -> Unit) {
    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS)
        != PackageManager.PERMISSION_GRANTED
    ) {
        onContactsLoaded(emptyList())
        return
    }

    val contacts = mutableListOf<Contact>()

    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY
        ),
        null,
        null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID))
            val name = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME))
            val phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER))
            val lookupKey = cursor.getString(cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY))

            contacts.add(Contact(
                id = id,
                name = name,
                phoneNumber = phoneNumber,
                lookupKey = lookupKey
            ))
        }
    }

    onContactsLoaded(contacts)
}
