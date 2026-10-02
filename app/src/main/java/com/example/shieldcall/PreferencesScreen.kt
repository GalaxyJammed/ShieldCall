package com.example.shieldcall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private fun flagValue(key: String) = when (key) {
    "spamScam" -> Prefs.spamScam
    "business" -> Prefs.business
    "foreign" -> Prefs.foreign
    "unknown" -> Prefs.unknown
    else -> Prefs.unsaved
}

@Composable
fun PreferencesScreen() {
    val context = LocalContext.current
    val dao = remember { SpamDb.get(context).dao() }
    val blocked by dao.blockedFlow().collectAsState(emptyList())

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        ScreenTitle("Preferences")
        Spacer(Modifier.height(16.dp))
        Section("Block calls from")
        ShieldCard(Modifier.fillMaxWidth()) {
            listOf(
                Triple(Icons.Default.ReportProblem, "Spam & scam", "spamScam"),
                Triple(Icons.Default.Business, "Businesses", "business"),
                Triple(Icons.Default.Public, "Other countries", "foreign"),
                Triple(Icons.Default.VisibilityOff, "Unknown numbers", "unknown"),
                Triple(Icons.Default.PersonOff, "Unsaved numbers", "unsaved")
            ).forEachIndexed { i, (icon, title, key) ->
                if (i > 0) HorizontalDivider()
                val text = when (key) {
                    "spamScam" -> "More votes in spam or scam than safe"
                    "business" -> "Toll-free, premium and VoIP numbers"
                    "foreign" -> "Calls from outside your country"
                    "unknown" -> "Hidden or withheld caller ID"
                    else -> "Numbers not in your contacts"
                }
                SettingRow(icon, title, text, flagValue(key)) { Prefs.setFlag(context, key, it) }
            }
        }
        Spacer(Modifier.height(24.dp))
        Section("When a call matches")
        ShieldCard(Modifier.fillMaxWidth()) {
            ActionRow(Icons.Default.CallEnd, "Hang up immediately", "Reject the call without ringing", "hangup")
            HorizontalDivider()
            ActionRow(Icons.Default.Block, "Block the number", "Reject it and add it to blocked numbers", "block")
        }
        Spacer(Modifier.height(24.dp))
        Section("Blocked numbers")
        ShieldCard(Modifier.fillMaxWidth()) {
            if (blocked.isEmpty()) Text("No blocked numbers", Modifier.padding(16.dp))
            blocked.forEachIndexed { i, b ->
                if (i > 0) HorizontalDivider()
                Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(b.name.ifBlank { "+${b.number}" }, style = MaterialTheme.typography.titleMedium)
                        if (b.name.isNotBlank()) Text("+${b.number}", style = MaterialTheme.typography.bodySmall)
                    }
                    TextButton(onClick = { dao.unblock(b.number) }) { Text("Unblock") }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, text: String, value: String) {
    val context = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable { Prefs.setAction(context, value) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
        RadioButton(selected = Prefs.action == value, onClick = null)
    }
}
