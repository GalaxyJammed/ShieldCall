package com.example.shieldcall

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
        ShieldCard(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        modifier = Modifier.size(28.dp)
                    )
                }
                Text(
                    "Settings",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Section("Appearance")
        ShieldCard(Modifier.fillMaxWidth()) {
            SettingRow(Icons.Default.DarkMode, "Dark theme", "Black background instead of white", Prefs.dark) {
                Prefs.setDark(context, it)
            }
        }
        Spacer(Modifier.height(24.dp))
        Section("Automation")
        ShieldCard(Modifier.fillMaxWidth()) {
            SettingRow(Icons.Default.PhoneInTalk, "Automatic call lookup", "Show the info card when a call comes in", Prefs.lookup) {
                Prefs.setLookup(context, it)
            }
        }
        Spacer(Modifier.height(24.dp))
        Section("Security")
        ShieldCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(Icons.Default.Fingerprint, "Fingerprint lock", "Unlock app with fingerprint on entry", Prefs.fingerprint) {
                    Prefs.setFingerprint(context, it)
                }
                HorizontalDivider()
                SettingRow(Icons.Default.Lock, "PIN lock", "Unlock app with PIN on entry", Prefs.pinLock) {
                    if (it) {
                        Prefs.setPinLock(context, true)
                        showPinDialog = true
                    } else {
                        Prefs.setPinLock(context, false)
                    }
                }
                if (Prefs.pinLock) {
                    HorizontalDivider()
                    Row(
                        Modifier.fillMaxWidth().clickable { showPinDialog = true }.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("Create PIN", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text("Set or change your app PIN code", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showPinDialog) {
        PinSetupDialog(
            onDismiss = {
                showPinDialog = false
                if (Prefs.pinCode.isBlank()) {
                    Prefs.setPinLock(context, false)
                }
            },
            onConfirm = { newPin ->
                Prefs.setPinCode(context, newPin)
                Prefs.setPinLock(context, true)
                showPinDialog = false
            }
        )
    }
}

@Composable
fun PinSetupDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var pin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create PIN Code") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Enter a 4 to 6 digit PIN code to secure your app.")
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter(Char::isDigit).take(6); error = null },
                    label = { Text("New PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it.filter(Char::isDigit).take(6); error = null },
                    label = { Text("Confirm PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    when {
                        pin.length < 4 -> error = "PIN must be at least 4 digits."
                        pin != confirmPin -> error = "PINs do not match."
                        else -> onConfirm(pin)
                    }
                }
            ) { Text("Save PIN") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
fun SettingRow(icon: ImageVector, title: String, text: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
