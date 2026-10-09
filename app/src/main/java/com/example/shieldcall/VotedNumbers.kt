package com.example.shieldcall

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun VotedNumbersScreen(type: String, onBack: () -> Unit, onOpen: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val dao = remember { SpamDb.get(context).dao() }
    val rows by dao.identificationsFlow().collectAsState(emptyList())
    val list = remember(rows, type) {
        rows.filter { it.type.equals(type, ignoreCase = true) }
            .distinctBy { it.number }
            .sortedByDescending { it.time }
    }
    val title = when (type) {
        "spam" -> "Marked as spam"
        "scam" -> "Marked as scam"
        else -> "Marked as safe"
    }
    val listState = rememberLazyListState()

    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
        ShieldCard(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(28.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tap a number to view or change your vote.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        if (list.isEmpty()) {
            Text("No numbers yet.", Modifier.padding(top = 8.dp))
        } else {
            LazyColumn(
                Modifier.fillMaxSize().scrollbar(listState),
                state = listState
            ) {
                items(list, key = { it.number }) { entry ->
                    VotedNumberRow(entry.number, entry.time, onOpen)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun VotedNumberRow(number: String, time: Long, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    val contact by produceState(ContactInfo(null, null, null), number, ContactsVersion.n) {
        value = withContext(Dispatchers.IO) { Reports.loadContactInfo(context, number) }
    }
    val name = contact.name?.takeIf { it.isNotBlank() }
    Row(
        Modifier.fillMaxWidth().clickable { onOpen(number) }.padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CallAvatar(photoUri = contact.photo, size = 48.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                name ?: "Unknown number",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "+$number · ${DateUtils.getRelativeTimeSpanString(time)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Open")
    }
}