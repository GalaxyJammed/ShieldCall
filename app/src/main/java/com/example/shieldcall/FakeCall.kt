package com.example.shieldcall

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.delay
import androidx.compose.ui.graphics.compositeOver

private val FakeGreen = Color(0xFF2E7D32)
private val FakeAmber = Color(0xFFF9A825)
private val FakeRed = Color(0xFFC62828)

@Composable
fun FakeCallScreen(name: String, number: String, onFinish: () -> Unit) {
    val context = LocalContext.current
    val region = remember { Reports.region(context) }
    var phase by remember { mutableIntStateOf(0) }
    var seconds by remember { mutableStateOf(0L) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var hold by remember { mutableStateOf(false) }
    val sound = remember { CallSound(context) }
    var keypad by remember { mutableStateOf(false) }

    val key = remember(number) { if (number.isBlank()) null else Reports.key(number, region) }
    val country = remember(number) {
        try {
            val util = PhoneNumberUtil.getInstance()
            val r = util.getRegionCodeForNumber(util.parse(number, region))
            countries.firstOrNull { it.region == r }
        } catch (e: Exception) {
            null
        }
    }
    val info by produceState<Info?>(null, key) {
        value = if (key != null && Reports.signedIn()) {
            try {
                Reports.load(key)
            } catch (e: Exception) {
                null
            }
        } else null
    }
    val i = info
    val total = (i?.spam ?: 0) + (i?.scam ?: 0) + (i?.safe ?: 0)
    val score = if (i != null && total > 0) ((i.safe * 100) / total).toInt() else null
    val rankColor = when {
        score == null -> MaterialTheme.colorScheme.onSurfaceVariant
        score >= 60 -> FakeGreen
        score >= 30 -> FakeAmber
        else -> FakeRed
    }
    val rankText = when {
        key == null -> "Safety Ranking: Unknown"
        !Reports.signedIn() -> "Sign in to see the safety ranking"
        score == null -> "Safety Ranking: Unknown"
        else -> "Safety Ranking: $score%"
    }

    DisposableEffect(phase) {
        if (phase == 0) sound.start()
        onDispose { sound.stop() }
    }

    LaunchedEffect(phase, hold) {
        if (phase == 1 && !hold) {
            while (true) {
                delay(1000)
                seconds++
            }
        }
        if (phase == 2) {
            delay(800)
            onFinish()
        }
    }
    BackHandler { if (phase == 2) onFinish() else phase = 2 }

    val stateText = when {
        phase == 0 -> "Incoming call"
        phase == 1 && hold -> "On hold"
        phase == 1 -> "%02d:%02d".format(seconds / 60, seconds % 60)
        phase == 3 -> "Call declined"
        else -> "Call ended"
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(stateText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CallAvatar(photoUri = null, size = 120.dp)
            Text(name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            if (number.isNotBlank()) {
                Text(
                    if (key != null) "+$key" else number,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            country?.let { Text("${it.flag} ${it.name}", style = MaterialTheme.typography.bodyLarge) }
            Text("Test call", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Surface(color = rankColor.copy(alpha = 0.2f).compositeOver(MaterialTheme.colorScheme.surface), shape = MaterialTheme.shapes.medium) {
                Text(
                    rankText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = rankColor,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        if (keypad && phase == 1) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('*', '0', '#')).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { c ->
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                contentAlignment = Alignment.Center
                            ) { Text(c.toString(), style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                }
                TextButton(onClick = { keypad = false }) { Text("Hide keypad") }
            }
        }

        when (phase) {
            0 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LabeledButton("Decline", Icons.Default.CallEnd, FakeRed) { phase = 3 }
                LabeledButton("Accept", Icons.Default.Call, FakeGreen) { phase = 1 }
            }
            1 -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ToggleButton("Mute", if (muted) Icons.Default.MicOff else Icons.Default.Mic, muted) { muted = !muted }
                    ToggleButton("Keypad", Icons.Default.Dialpad, keypad) { keypad = !keypad }
                    ToggleButton("Speaker", Icons.AutoMirrored.Filled.VolumeUp, speaker) { speaker = !speaker }
                    ToggleButton("Hold", if (hold) Icons.Default.PlayArrow else Icons.Default.Pause, hold) { hold = !hold }
                }
                LabeledButton("End", Icons.Default.CallEnd, FakeRed) { phase = 2 }
            }
            3 -> ShieldCard(Modifier.fillMaxWidth()) { DeclineReasonPrompt(null) { phase = 2 } }
            else -> Spacer(Modifier.height(72.dp))
        }
    }
}