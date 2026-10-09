package com.example.shieldcall

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.unit.Dp

private val PromptGreen = Color(0xFF2E7D32)
private val PromptAmber = Color(0xFFF9A825)
private val PromptRed = Color(0xFFC62828)

@Composable
fun CallFeedbackPrompt(onSelect: (String?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How did the call go?", style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        listOf("safe" to PromptGreen, "spam" to PromptAmber, "scam" to PromptRed).forEach { (type, color) ->
            Button(
                onClick = { onSelect(type) },
                colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
                modifier = Modifier.fillMaxWidth()
            ) { Text(type.replaceFirstChar { it.uppercase() }) }
        }
        OutlinedButton(onClick = { onSelect(null) }, modifier = Modifier.fillMaxWidth()) { Text("Skip") }
    }
}

@Composable
fun ReplySheet(replies: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var custom by remember { mutableStateOf("") }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ShieldCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Decline with a message", style = MaterialTheme.typography.titleLarge)
                replies.forEach { r ->
                    OutlinedButton(onClick = { onPick(r) }, modifier = Modifier.fillMaxWidth()) { Text(r) }
                }
                OutlinedTextField(
                    value = custom,
                    onValueChange = { custom = it.take(160) },
                    label = { Text("Custom message") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(enabled = custom.isNotBlank(), onClick = { onPick(custom.trim()) }) { Text("Send") }
                }
            }
        }
    }
}

@Composable
fun SmallCircleButton(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(52.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White)
        ) { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LabeledButton(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = color, contentColor = Color.White)
        ) { Icon(icon, contentDescription = label, modifier = Modifier.size(32.dp)) }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ToggleButton(
    label: String,
    icon: ImageVector,
    on: Boolean,
    modifier: Modifier = Modifier,
    labelGap: Dp = 4.dp,
    onClick: () -> Unit
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconToggleButton(
            checked = on,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(60.dp)
        ) { Icon(icon, contentDescription = label) }
        Spacer(Modifier.height(labelGap))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}