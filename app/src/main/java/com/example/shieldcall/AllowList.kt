package com.example.shieldcall

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AllowList {
    var keys by mutableStateOf<Set<String>>(emptySet())
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(c: Context) {
        keys = prefs(c).getStringSet("allow", emptySet()).orEmpty().toSet()
    }

    fun contains(c: Context, key: String): Boolean =
        prefs(c).getStringSet("allow", emptySet()).orEmpty().contains(key)

    fun add(c: Context, key: String) {
        keys = keys + key
        prefs(c).edit().putStringSet("allow", keys).apply()
    }

    fun remove(c: Context, key: String) {
        keys = keys - key
        prefs(c).edit().putStringSet("allow", keys).apply()
    }
}

@Composable
fun AllowListPage(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var adding by remember { mutableStateOf(false) }
    val items = AllowList.keys.toList().sorted()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        ShieldCard(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(28.dp))
                }
                Text(
                    "Always Allowed",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(onClick = { adding = true }, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Default.Add, contentDescription = "Add number")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            "These numbers always get through. Blocking rules, your blocklist and quiet hours never apply to them.",
            style = MaterialTheme.typography.bodySmall
        )
        Spacer(Modifier.height(8.dp))
        ShieldCard(Modifier.fillMaxWidth()) {
            if (items.isEmpty()) Text("No numbers yet.", Modifier.padding(16.dp))
            items.forEachIndexed { i, k ->
                if (i > 0) HorizontalDivider()
                AllowRow(k) { AllowList.remove(context, k) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (adding) AllowAddDialog { adding = false }
}

@Composable
private fun AllowAddDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    val key = Reports.key(input, Reports.region(context))
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ShieldCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Always allow a number", style = MaterialTheme.typography.titleLarge)
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter { c -> c.isDigit() || c == '+' }.take(20) },
                    label = { Text("Phone number") },
                    supportingText = { Text("Include the country code, for example +30...") },
                    isError = input.length >= 5 && key == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = key != null,
                        onClick = {
                            key?.let { AllowList.add(context, it) }
                            onDismiss()
                        }
                    ) { Text("Add") }
                }
            }
        }
    }
}

@Composable
private fun AllowRow(key: String, onRemove: () -> Unit) {
    val context = LocalContext.current
    val contact by produceState(ContactInfo(null, null, null), key, ContactsVersion.n) {
        value = withContext(Dispatchers.IO) { Reports.loadContactInfo(context, key) }
    }
    Row(
        Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CallAvatar(photoUri = contact.photo, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                contact.name?.takeIf { it.isNotBlank() } ?: "Unknown number",
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "+$key",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        TextButton(onClick = onRemove) { Text("Remove") }
    }
}