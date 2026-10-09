package com.example.shieldcall

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

object PostCallNotes {
    fun isOn(c: Context): Boolean =
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).getBoolean("postCallNotes", true)

    fun set(c: Context, on: Boolean) {
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putBoolean("postCallNotes", on).apply()
    }
}

/** Settings row that turns the after-call note prompt on or off. */
@Composable
fun PostCallNotesSettingRow() {
    val context = LocalContext.current
    var on by remember { mutableStateOf(PostCallNotes.isOn(context)) }
    SettingRow(
        Icons.Default.EditNote,
        "Note after calls",
        "Asks for a note about a number when an answered call ends, if it has no note yet",
        on
    ) {
        on = it
        PostCallNotes.set(context, it)
    }
}

/** Shown when an answered call ends. Notes are saved per number and appear in Number Details. */
@Composable
fun NoteStep(key: String, name: String?, dryRun: Boolean = false, onNext: () -> Unit) {
    val context = LocalContext.current
    var text by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        delay(45000)
        onNext()
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ShieldCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Add a note", style = MaterialTheme.typography.titleLarge)
                Text(
                    name?.takeIf { it.isNotBlank() } ?: "+$key",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Only you can see this. It appears in Number Details.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(500) },
                    placeholder = { Text("What was this call about?") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onNext) { Text("Skip") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = text.isNotBlank(),
                        onClick = {
                            if (!dryRun) Notes.set(context, key, text.trim())
                            onNext()
                        }
                    ) { Text("Save") }
                }
            }
        }
    }
}