package com.example.shieldcall

import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

@Composable
fun LockScreen(onUnlocked: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? FragmentActivity
    var usePin by remember { mutableStateOf(!Prefs.fingerprint && Prefs.pinLock) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    fun triggerBiometric() {
        if (activity == null || !Prefs.fingerprint) return
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onUnlocked()
            }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                if (errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON || errorCode == BiometricPrompt.ERROR_USER_CANCELED) {
                    if (Prefs.pinLock) usePin = true
                }
            }
        })
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock ShieldCall")
            .setSubtitle("Touch the fingerprint sensor to unlock")
            .setNegativeButtonText(if (Prefs.pinLock) "Use PIN" else "Cancel")
            .build()
        try {
            prompt.authenticate(info)
        } catch (_: Exception) {
            if (Prefs.pinLock) usePin = true
        }
    }

    LaunchedEffect(Unit) {
        if (Prefs.fingerprint) {
            triggerBiometric()
        }
    }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            ShieldCard(Modifier.fillMaxWidth()) {
                Column(
                    Modifier.padding(24.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        imageVector = if (usePin) Icons.Default.Lock else Icons.Default.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        if (usePin) "Enter PIN" else "Unlock ShieldCall",
                        style = MaterialTheme.typography.headlineSmall
                    )

                    if (usePin) {
                        // PIN dots indicator
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            repeat(4) { i ->
                                val filled = i < pin.length
                                Box(
                                    Modifier
                                        .size(16.dp)
                                        .background(
                                            if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                            CircleShape
                                        )
                                )
                            }
                        }
                        if (error) {
                            Text("Incorrect PIN. Try again.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(Modifier.height(8.dp))
                        PinKeypad(
                            onDigit = { digit ->
                                if (pin.length < 6) {
                                    pin += digit
                                    error = false
                                    if (pin.length == 4) {
                                        if (pin == Prefs.pinCode) {
                                            onUnlocked()
                                        } else {
                                            error = true
                                            pin = ""
                                        }
                                    }
                                }
                            },
                            onDelete = {
                                if (pin.isNotEmpty()) {
                                    pin = pin.dropLast(1)
                                    error = false
                                }
                            }
                        )

                        if (Prefs.fingerprint) {
                            Spacer(Modifier.height(8.dp))
                            TextButton(onClick = { usePin = false; pin = ""; error = false; triggerBiometric() }) {
                                Text("Use Fingerprint instead")
                            }
                        }
                    } else {
                        Button(
                            onClick = { triggerBiometric() },
                            modifier = Modifier.fillMaxWidth().height(50.dp)
                        ) {
                            Text("Scan Fingerprint")
                        }
                        if (Prefs.pinLock) {
                            TextButton(onClick = { usePin = true }) {
                                Text("Use PIN instead")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PinKeypad(onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    val buttons = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("", "0", "⌫")
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        buttons.forEach { row ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth()
            ) {
                row.forEach { label ->
                    if (label.isEmpty()) {
                        Spacer(Modifier.size(64.dp))
                    } else {
                        Button(
                            onClick = {
                                if (label == "⌫") onDelete() else label.firstOrNull()?.let(onDigit)
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                            modifier = Modifier.size(64.dp)
                        ) {
                            Text(label, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
