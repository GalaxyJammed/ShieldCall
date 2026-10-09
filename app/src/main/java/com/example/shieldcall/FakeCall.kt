package com.example.shieldcall

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.delay
import java.io.File

private val TestGreen = Color(0xFF2E7D32)
private val TestAmber = Color(0xFFF9A825)
private val TestRed = Color(0xFFC62828)

/** Stores the looping audio picked for test calls. */
object TestLoop {
    fun file(c: Context) = File(c.filesDir, "test_loop.audio")

    fun save(c: Context, uri: Uri): Boolean {
        val target = file(c)
        return try {
            c.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            if (target.length() in 1..30_000_000L) {
                true
            } else {
                target.delete()
                false
            }
        } catch (e: Exception) {
            target.delete()
            false
        }
    }

    fun path(c: Context): String? = file(c).takeIf { it.exists() }?.path

    fun clear(c: Context) {
        file(c).delete()
    }
}

/**
 * Mirrors the real call flow without touching Telecom: ringing (Decline, Reason, Accept),
 * active (keypad, mute, speaker, hold, end) with optional looping audio, then the
 * note, feedback and decline prompts. Nothing is dialed, saved or reported.
 */
@Composable
fun FakeCallScreen(name: String, number: String, loopPath: String? = null, onFinish: () -> Unit) {
    val context = LocalContext.current
    val region = remember { Reports.region(context) }
    var phase by remember { mutableIntStateOf(0) } // 0 ringing, 1 active, 2 ended, 3 declined
    var step by remember { mutableStateOf<String?>(null) } // "note", "feedback", "decline"
    var seconds by remember { mutableLongStateOf(0L) }
    var muted by remember { mutableStateOf(false) }
    var speaker by remember { mutableStateOf(false) }
    var hold by remember { mutableStateOf(false) }
    var faceMuted by remember { mutableStateOf(false) }
    var keypad by remember { mutableStateOf(false) }
    var showReplies by remember { mutableStateOf(false) }
    val sound = remember { CallSound(context) }

    val loop = remember(loopPath) {
        loopPath?.let { path ->
            try {
                MediaPlayer().apply {
                    setDataSource(path)
                    isLooping = true
                    prepare()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
    DisposableEffect(loop) {
        onDispose { loop?.release() }
    }

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
        score >= 60 -> TestGreen
        score >= 30 -> TestAmber
        else -> TestRed
    }
    val rankText = when {
        key == null -> "Safety Ranking: Unknown"
        !Reports.signedIn() -> "Sign in to see the safety ranking"
        score == null -> "Safety Ranking: Unknown"
        else -> "Safety Ranking: $score%"
    }

    fun endCall() {
        phase = 2
        step = if (PostCallNotes.isOn(context) && key != null && Notes.get(context, key).isBlank()) "note" else "feedback"
    }

    DisposableEffect(phase) {
        if (phase == 0) sound.start()
        if (phase == 1) {
            FaceDown.testSink = { faceMuted = it }
            FaceDown.start(context)
        }
        onDispose {
            sound.stop()
            if (phase == 1) {
                FaceDown.stop()
                FaceDown.testSink = null
            }
        }
    }
    LaunchedEffect(phase, hold, muted, faceMuted, loop) {
        val m = loop ?: return@LaunchedEffect
        val play = phase == 1 && !hold && !muted && !faceMuted
        try {
            if (play && !m.isPlaying) m.start()
            else if (!play && m.isPlaying) m.pause()
        } catch (e: Exception) {
        }
    }
    LaunchedEffect(phase, hold) {
        if (phase == 1 && !hold) {
            while (true) {
                delay(1000)
                seconds++
            }
        }
    }
    BackHandler {
        when {
            step != null -> onFinish()
            phase == 1 -> endCall()
            else -> {
                phase = 3
                step = "decline"
            }
        }
    }

    when (step) {
        "note" -> {
            NoteStep(key = key ?: number, name = name, dryRun = true) { step = "feedback" }
            return
        }
        "feedback" -> {
            TestStep {
                CallFeedbackPrompt { onFinish() }
            }
            return
        }
        "decline" -> {
            TestStep {
                DeclineReasonPrompt(existingVote = null, onSelect = { onFinish() })
            }
            return
        }
    }

    val stateText = when (phase) {
        0 -> "Incoming call"
        1 -> if (hold) "On hold" else "%02d:%02d".format(seconds / 60, seconds % 60)
        2 -> "Call ended"
        else -> "Call declined"
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
            Surface(
                color = rankColor.copy(alpha = 0.2f).compositeOver(MaterialTheme.colorScheme.surface),
                shape = MaterialTheme.shapes.medium
            ) {
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
            0 -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    LabeledButton("Decline", Icons.Default.CallEnd, TestRed) {
                        phase = 3
                        step = "decline"
                    }
                    Spacer(Modifier.height(10.dp))
                    SmallCircleButton("Reason", Icons.AutoMirrored.Filled.Message, MaterialTheme.colorScheme.secondary) {
                        showReplies = true
                    }
                }
                LabeledButton("Accept", Icons.Default.Call, TestGreen) { phase = 1 }
            }
            1 -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ToggleButton("Mute", if (muted) Icons.Default.MicOff else Icons.Default.Mic, muted) { muted = !muted }
                    ToggleButton("Keypad", Icons.Default.Dialpad, keypad) { keypad = !keypad }
                    ToggleButton("Speaker", Icons.AutoMirrored.Filled.VolumeUp, speaker) { speaker = !speaker }
                    ToggleButton("Hold", if (hold) Icons.Default.PlayArrow else Icons.Default.Pause, hold) { hold = !hold }
                }
                LabeledButton("End", Icons.Default.CallEnd, TestRed) { endCall() }
            }
            else -> Spacer(Modifier.height(72.dp))
        }
    }

    if (phase == 0 && showReplies) {
        ReplySheet(
            replies = listOf(
                "I'm busy right now",
                "I'm at work",
                "I can't talk right now",
                "I'll call you back later",
                "Please send a text instead"
            ),
            onPick = {
                showReplies = false
                phase = 3
                step = "decline"
            },
            onDismiss = { showReplies = false }
        )
    }
}

@Composable
private fun TestStep(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ShieldCard(Modifier.fillMaxWidth()) { content() }
        Spacer(Modifier.height(12.dp))
        Text(
            "Test only: nothing is saved or reported.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}