package com.example.shieldcall

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.telecom.TelecomManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.lazy.rememberLazyListState

data class ContactItem(
    val name: String,
    val key: String,
    val raw: String,
    val photo: String?,
    val extra: String? = null,
    val incoming: Boolean? = null
)

private fun loadContacts(context: Context): List<ContactItem> {
    val region = Reports.region(context)
    val out = LinkedHashMap<String, ContactItem>()
    context.contentResolver.query(
        Phone.CONTENT_URI,
        arrayOf(Phone.DISPLAY_NAME, Phone.NUMBER, Phone.PHOTO_THUMBNAIL_URI),
        null,
        null,
        "${Phone.DISPLAY_NAME} COLLATE NOCASE ASC"
    )?.use { c ->
        while (c.moveToNext()) {
            val name = c.getString(0).orEmpty()
            val raw = c.getString(1).orEmpty()
            val key = Reports.key(raw, region) ?: continue
            out.putIfAbsent(key, ContactItem(name, key, raw, c.getString(2)))
        }
    }
    return out.values.toList()
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ContactsScreen(onLookup: (String) -> Unit) {
    val context = LocalContext.current
    val dao = remember { SpamDb.get(context).dao() }
    val blocked by dao.blockedFlow().collectAsState(emptyList())
    val blockedKeys = remember(blocked) { blocked.map { it.number }.toSet() }
    val calls by dao.recentFlow().collectAsState(emptyList())
    val favKeys = Favorites.keys
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    var contacts by remember { mutableStateOf<List<ContactItem>?>(null) }
    var query by remember { mutableStateOf("") }
    var pendingCall by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()
    val rowState = rememberLazyListState()

    fun place(number: String) {
        context.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), null)
    }

    val callLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pendingCall?.let { place(it) }
        pendingCall = null
    }

    fun call(c: ContactItem) {
        val number = "+${c.key}"
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            place(number)
        } else {
            pendingCall = number
            callLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    LaunchedEffect(Unit) { contacts = withContext(Dispatchers.IO) { loadContacts(context) } }

    val recents = remember(calls) {
        calls.map {
            ContactItem(
                it.name.ifBlank { it.raw },
                it.number,
                it.raw,
                null,
                buildString {
                    append(it.status)
                    if (it.duration > 0) append(" · %d:%02d".format(it.duration / 60, it.duration % 60))
                    append(" · ")
                    append(DateUtils.getRelativeTimeSpanString(it.time))
                },
                it.status != "Outgoing"
            )
        }
    }
    val favItems = remember(contacts, favKeys) { contacts.orEmpty().filter { it.key in favKeys } }
    val list = when (mode) {
        0 -> contacts
        1 -> recents
        else -> favItems
    }
    val shown = remember(list, query, mode) {
        if (mode == 0) list?.filter { it.name.contains(query, true) || it.raw.contains(query) }.orEmpty()
        else list.orEmpty()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        ShieldCard(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp)) {
                Text(
                    when (mode) {
                        0 -> "Contacts"
                        1 -> "Recent Calls"
                        else -> "Favorites"
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
                Box(Modifier.align(Alignment.CenterEnd)) {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.FilterList, contentDescription = "Filter") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Contacts") }, onClick = { mode = 0; menu = false })
                        DropdownMenuItem(text = { Text("Favorites") }, onClick = { mode = 2; menu = false })
                        DropdownMenuItem(text = { Text("Recent calls") }, onClick = { mode = 1; menu = false })
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        if (mode == 0) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search contacts") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (query.isBlank() && favItems.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Favorites · tap to call, hold for details",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp)
                )
                LazyRow(state = rowState, modifier = Modifier.scrollbar(rowState, vertical = false), horizontalArrangement = Arrangement.spacedBy(16.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(favItems, key = { it.key }) { c ->
                        Column(
                            Modifier
                                .width(72.dp)
                                .combinedClickable(onClick = { call(c) }, onLongClick = { onLookup(c.key) }),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Avatar(c.name, c.photo, 56.dp)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                c.name,
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
        Text(
            "Tap on a contact to view more info",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 6.dp)
        )
        Spacer(Modifier.height(8.dp))
        when {
            list == null -> CircularProgressIndicator()
            shown.isEmpty() -> Text(
                when (mode) {
                    0 -> "No contacts found."
                    1 -> "No calls yet."
                    else -> "No favorites yet. Tap the star next to a contact to add one."
                }
            )
            else -> LazyColumn(Modifier.scrollbar(listState), state = listState, contentPadding = PaddingValues(bottom = 96.dp)) {
                items(shown) { c ->
                    val isBlocked = c.key in blockedKeys
                    Row(
                        Modifier.fillMaxWidth().clickable { onLookup(c.key) }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(c.name, c.photo)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name, style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                c.incoming?.let {
                                    Icon(
                                        if (it) Icons.AutoMirrored.Filled.CallReceived else Icons.AutoMirrored.Filled.CallMade,
                                        contentDescription = if (it) "Incoming" else "Outgoing",
                                        tint = if (it) Color(0xFF2E7D32) else Color(0xFFC62828),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(c.extra ?: c.raw, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        if (mode != 1) {
                            val fav = c.key in favKeys
                            IconButton(onClick = { Favorites.toggle(context, c.key) }) {
                                Icon(
                                    if (fav) Icons.Default.Star else Icons.Default.StarBorder,
                                    contentDescription = "Favorite",
                                    tint = if (fav) Color(0xFFF9A825) else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        IconButton(onClick = {
                            if (isBlocked) dao.unblock(c.key) else dao.block(BlockedNumber(c.key, c.name))
                        }) {
                            Icon(
                                Icons.Default.Block,
                                contentDescription = "Block",
                                tint = if (isBlocked) Color(0xFFC62828) else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun Avatar(name: String, photo: String?, size: Dp = 48.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(it))?.use { s ->
                        BitmapFactory.decodeStream(s)?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
    val shape = Modifier.size(size).clip(CircleShape)
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, modifier = shape, contentScale = ContentScale.Crop)
    } else {
        Box(
            shape.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}