package com.example.shieldcall

import android.app.role.RoleManager
import android.telephony.TelephonyManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun EmergencyCheck() {
    val context = LocalContext.current
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<Boolean?>(null) }
    val isDefault = remember {
        context.getSystemService(RoleManager::class.java)?.isRoleHeld(RoleManager.ROLE_DIALER) == true
    }

    ShieldCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalHospital, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                Column {
                    Text("Emergency number check", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Type a number to see if it's recognized as an emergency number. Nothing is ever dialed.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.filter(Char::isDigit).take(6); result = null },
                    label = { Text("e.g. 112") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    enabled = input.isNotBlank(),
                    onClick = {
                        result = try {
                            context.getSystemService(TelephonyManager::class.java).isEmergencyNumber(input)
                        } catch (e: Exception) {
                            false
                        }
                    }
                ) { Text("Check") }
            }
            result?.let {
                Text(
                    if (it) "✓ $input is an emergency number" else "✗ $input is not an emergency number",
                    color = if (it) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Text(
                if (isDefault) "ShieldCall is your default phone app, so calls to emergency numbers are placed by ShieldCall."
                else "ShieldCall isn't your default phone app, so your normal phone app places emergency calls.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "Never place a real emergency call as a test.",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}