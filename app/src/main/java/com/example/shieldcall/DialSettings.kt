package com.example.shieldcall

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.RecordVoiceOver

@Composable
fun DialSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var hasBg by remember { mutableStateOf(CallStyle.bgFile(context).exists()) }
    var flash by remember { mutableStateOf(CallPrefs.flash(context)) }
    var faceDown by remember { mutableStateOf(CallPrefs.faceDown(context)) }
    var testing by remember { mutableStateOf(false) }
    var ttsName by remember { mutableStateOf(CallPrefs.ttsName(context)) }

    val bgPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                hasBg = CallStyle.saveBg(context, uri)
                if (!hasBg) Toast.makeText(context, "Couldn't use that image (max 20 MB)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ShieldCard(Modifier.fillMaxWidth()) {
        Column {
            DialRow(
                Icons.Default.Image,
                "Call screen background",
                if (hasBg) "Custom image set" else "Use an image instead of the theme color",
                { bgPicker.launch(arrayOf("image/*")) }
            ) {
                if (hasBg) {
                    TextButton(onClick = {
                        CallStyle.bgFile(context).delete()
                        hasBg = false
                    }) { Text("Remove") }
                }
            }
            HorizontalDivider()
            SettingRow(Icons.Default.FlashOn, "Flash on call", "Blinks the torch while a call rings", flash) {
                flash = it
                CallPrefs.setFlash(context, it)
            }
            HorizontalDivider()
            SettingRow(
                Icons.Default.ScreenRotation,
                "Mute when face down",
                "Mutes the call and mic while the phone lies face down",
                faceDown
            ) {
                faceDown = it
                CallPrefs.setFaceDown(context, it)
            }
            HorizontalDivider()
            SettingRow(
                Icons.Default.RecordVoiceOver,
                "Say caller name",
                "Reads \"Incoming call from\" and the name aloud when a call rings",
                ttsName
            ) {
                ttsName = it
                CallPrefs.setTtsName(context, it)
            }
            HorizontalDivider()
            PostCallNotesSettingRow()
            HorizontalDivider()
            DialRow(Icons.Default.Call, "Test call with fake name", "Preview the call screen with a made-up caller", { testing = true })
        }
    }

    if (testing) {
        var name by remember { mutableStateOf("") }
        var number by remember { mutableStateOf("") }
        var loopSet by remember { mutableStateOf(TestLoop.path(context) != null) }
        val loopPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                scope.launch {
                    loopSet = withContext(Dispatchers.IO) { TestLoop.save(context, uri) }
                    if (!loopSet) Toast.makeText(context, "Couldn't use that audio (max 30 MB)", Toast.LENGTH_SHORT).show()
                }
            }
        }

        Dialog(onDismissRequest = { testing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            ShieldCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Test call", style = MaterialTheme.typography.titleLarge)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(40) },
                        label = { Text("Fake name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = number,
                        onValueChange = { number = it.filter { c -> c.isDigit() || c == '+' }.take(20) },
                        label = { Text("Number (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Looping audio", style = MaterialTheme.typography.titleSmall)
                            Text(
                                if (loopSet) "Plays while the caller talks. Hold, mute and face-down pause it."
                                else "Optional. Plays as if the caller is talking.",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedButton(onClick = { loopPicker.launch(arrayOf("audio/*")) }) {
                            Text(if (loopSet) "Change" else "Choose")
                        }
                        if (loopSet) {
                            TextButton(onClick = {
                                TestLoop.clear(context)
                                loopSet = false
                            }) { Text("Remove") }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { testing = false }) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            enabled = name.isNotBlank(),
                            onClick = {
                                testing = false
                                val loop = if (loopSet) TestLoop.path(context) else null
                                if (!Prefs.fullScreen && Settings.canDrawOverlays(context)) {
                                    context.startService(
                                        Intent(context, OverlayService::class.java)
                                            .putExtra("number", number)
                                            .putExtra("contactName", name.trim())
                                            .putExtra("fake", true)
                                    )
                                } else {
                                    val intent = Intent(context, InCallActivity::class.java)
                                        .putExtra("fakeName", name.trim())
                                        .putExtra("fakeNumber", number)
                                    if (loop != null) intent.putExtra("loopAudio", loop)
                                    context.startActivity(intent)
                                }
                            }
                        ) { Text("Start") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DialRow(
    icon: ImageVector,
    title: String,
    text: String,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
        trailing()
    }
}