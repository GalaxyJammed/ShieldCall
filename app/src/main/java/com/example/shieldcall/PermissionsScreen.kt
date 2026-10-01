package com.example.shieldcall

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PermissionsScreen(p: Perms, onPhone: () -> Unit, onOverlay: () -> Unit, onRole: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        ShieldCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text("Welcome to ShieldCall", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("ShieldCall needs a few permissions to work properly. Grant all three to continue.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        PermissionRow("Contacts & phone state", "Recognise numbers saved in your contacts.", p.phone, onPhone)
        PermissionRow("Display over other apps", "Show the caller info card while your phone rings.", p.overlay, onOverlay)
        PermissionRow("Caller ID & spam app", "Lets ShieldCall see incoming numbers.", p.role, onRole)
    }
}

@Composable
private fun PermissionRow(title: String, text: String, done: Boolean, onClick: () -> Unit) {
    ShieldCard(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(text, style = MaterialTheme.typography.bodySmall)
            }
            if (done) Text("✓ Done") else Button(onClick = onClick) { Text("Allow") }
        }
    }
}