package com.example.shieldcall

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object SpeedDial {
    private fun p(c: Context) = c.getSharedPreferences("speed", Context.MODE_PRIVATE)
    fun get(c: Context, digit: Int): String? = p(c).getString("d$digit", null)
    fun set(c: Context, digit: Int, key: String?) {
        val e = p(c).edit()
        if (key == null) e.remove("d$digit") else e.putString("d$digit", key)
        e.apply()
    }
}

@Composable
fun SpeedDialEditor(onClose: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var assigning by remember { mutableStateOf<Int?>(null) }

    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ShieldCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Speed dial", style = MaterialTheme.typography.titleLarge)
                Text("On the dial pad, hold a key to call its contact.", style = MaterialTheme.typography.bodySmall)
                (1..9).forEach { d ->
                    val key = remember(version, d) { SpeedDial.get(context, d) }
                    val name by produceState<String?>(null, key) {
                        value = key?.let { k -> withContext(Dispatchers.IO) { Reports.loadContactInfo(context, k).name } }
                    }
                    Row(
                        Modifier.fillMaxWidth().clickable { assigning = d }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("$d", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(28.dp))
                        Text(
                            name ?: key?.let { "+$it" } ?: "Not set",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (key != null) {
                            TextButton(onClick = {
                                SpeedDial.set(context, d, null)
                                version++
                            }) { Text("Clear") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }

    assigning?.let { slot ->
        SpeedContactPicker(
            onPick = { key ->
                SpeedDial.set(context, slot, key)
                version++
                assigning = null
            },
            onDismiss = { assigning = null }
        )
    }
}

@Composable
private fun SpeedContactPicker(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val region = remember { Reports.region(context) }
    val contacts by produceState<List<ContactItem>?>(null) {
        value = withContext(Dispatchers.IO) { loadContacts(context) }
    }
    var query by remember { mutableStateOf("") }
    val manualKey = remember(query) {
        if (query.count { it.isDigit() } >= 5) Reports.key(query, region) else null
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ShieldCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Name or phone number") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                manualKey?.let { key ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(key) }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CallAvatar(photoUri = null, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text("Use this number", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "+$key",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    HorizontalDivider()
                }
                val list = contacts
                if (list == null) {
                    CircularProgressIndicator()
                } else {
                    val shown = list.filter { it.name.contains(query, true) || it.raw.contains(query) }
                    LazyColumn(Modifier.heightIn(max = 380.dp)) {
                        items(shown, key = { it.key }) { c ->
                            Row(
                                Modifier.fillMaxWidth().clickable { onPick(c.key) }.padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CallAvatar(photoUri = c.photo, size = 40.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name.ifBlank { c.raw }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                    Text(
                                        c.raw,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}