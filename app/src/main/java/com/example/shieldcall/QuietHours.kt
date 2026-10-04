package com.example.shieldcall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuietHoursCard() {
    val context = LocalContext.current
    var editing by remember { mutableStateOf<String?>(null) }

    ShieldCard(Modifier.fillMaxWidth()) {
        Column {
            SettingRow(
                Icons.Default.Bedtime,
                "Quiet hours",
                "Reject calls from unsaved numbers during these hours. Saved contacts still ring.",
                Prefs.quiet
            ) { Prefs.setQuiet(context, it) }
            if (Prefs.quiet) {
                HorizontalDivider()
                TimeRow("From", Prefs.quietStart) { editing = "start" }
                HorizontalDivider()
                TimeRow("To", Prefs.quietEnd) { editing = "end" }
            }
        }
    }

    editing?.let { which ->
        val initial = if (which == "start") Prefs.quietStart else Prefs.quietEnd
        val state = rememberTimePickerState(initial / 60, initial % 60, true)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (which == "start") "Start time" else "End time") },
            text = { TimePicker(state) },
            confirmButton = {
                Button(onClick = {
                    val minutes = state.hour * 60 + state.minute
                    if (which == "start") Prefs.setQuietTimes(context, minutes, Prefs.quietEnd)
                    else Prefs.setQuietTimes(context, Prefs.quietStart, minutes)
                    editing = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TimeRow(label: String, minutes: Int, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(
            "%02d:%02d".format(minutes / 60, minutes % 60),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}