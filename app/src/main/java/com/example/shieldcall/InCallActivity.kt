package com.example.shieldcall

import android.app.NotificationManager
import android.content.Context
import android.os.Bundle
import android.telecom.Call
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CallGreen = Color(0xFF2E7D32)
private val CallAmber = Color(0xFFF9A825)
private val CallRed = Color(0xFFC62828)

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        Prefs.load(this)
        val fake = intent.getStringExtra("fakeName")
        setContent {
            ShieldTheme(Prefs.dark) {
                Surface(Modifier.fillMaxSize()) {
                    CallBackdrop {
                        Box(Modifier.safeDrawingPadding()) {
                            if (fake != null) FakeCallScreen(fake, intent.getStringExtra("fakeNumber").orEmpty()) { finish() }
                            else InCallScreen { finish() }
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun InCallScreen(onFinish: () -> Unit) {
    val context = LocalContext.current
    val tick = CallManager.tick
    val calls = CallManager.calls
    val call = calls.firstOrNull { it.state == Call.STATE_RINGING } ?: calls.firstOrNull()
    val region = remember { Reports.region(context) }
    var lastNumber by remember { mutableStateOf("") }
    var wasActive by remember { mutableStateOf(false) }
    var wasIncoming by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf<String?>(null) }
    var keypad by remember { mutableStateOf(false) }

    val liveNumber = call?.details?.handle?.schemeSpecificPart.orEmpty()
    val number = liveNumber.ifEmpty { lastNumber }
    val state = remember(tick, call) { call?.state ?: Call.STATE_DISCONNECTED }

    LaunchedEffect(liveNumber) { if (liveNumber.isNotEmpty()) lastNumber = liveNumber }
    LaunchedEffect(state) {
        if (state == Call.STATE_RINGING) wasIncoming = true
        if (state == Call.STATE_ACTIVE) wasActive = true
        if (state != Call.STATE_RINGING) context.getSystemService(NotificationManager::class.java).cancel(1)
    }

    val key = remember(number) { Reports.key(number, region) }
    val contact by produceState(ContactInfo(null, null, null), number) {
        value = withContext(Dispatchers.IO) { Reports.loadContactInfo(context, number) }
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
    val localVote by produceState<String?>(null, key) {
        value = key?.let { k ->
            withContext(Dispatchers.IO) {
                SpamDb.get(context).dao().entry(k)?.takeIf { it.source == "mine" }?.type
            }
        }
    }
    val canPrompt = key != null && Reports.signedIn() && info != null &&
            info?.myVote == null && localVote == null && contact.name.isNullOrBlank()

    LaunchedEffect(call == null) {
        if (call == null && prompt == null) {
            if (wasIncoming && wasActive && canPrompt) {
                prompt = "ended"
            } else {
                delay(800)
                if (CallManager.calls.isEmpty()) onFinish()
            }
        }
    }

    val p = prompt
    if (p != null) {
        FeedbackScreen(number, contact, p == "ended", onFinish)
        return
    }
    if (call == null) return

    val country = remember(number) {
        try {
            val util = PhoneNumberUtil.getInstance()
            val r = util.getRegionCodeForNumber(util.parse(number, region))
            countries.firstOrNull { it.region == r }
        } catch (e: Exception) {
            null
        }
    }
    val seconds by produceState(0L, state) {
        while (state == Call.STATE_ACTIVE) {
            val start = call.details.connectTimeMillis
            value = if (start > 0) (System.currentTimeMillis() - start) / 1000 else 0
            delay(1000)
        }
    }

    val i = info
    val total = (i?.spam ?: 0) + (i?.scam ?: 0) + (i?.safe ?: 0)
    val score = if (i != null && total > 0) ((i.safe * 100) / total).toInt() else null
    val rankColor = when {
        score == null -> MaterialTheme.colorScheme.onSurfaceVariant
        score >= 60 -> CallGreen
        score >= 30 -> CallAmber
        else -> CallRed
    }
    val rankText = when {
        !Reports.signedIn() -> "Sign in to see the safety ranking"
        score == null -> "Safety Ranking: Unknown"
        else -> "Safety Ranking: $score%"
    }
    val stateText = when (state) {
        Call.STATE_RINGING -> "Incoming call"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Calling…"
        Call.STATE_ACTIVE -> "%02d:%02d".format(seconds / 60, seconds % 60)
        Call.STATE_HOLDING -> "On hold"
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "Call ended"
        else -> ""
    }

    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Spacer(Modifier.height(16.dp))
            Text(stateText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CallAvatar(photoUri = contact.photo, size = 120.dp)
            Text(
                contact.name?.takeIf { it.isNotBlank() } ?: "Unknown number",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                if (key != null) "+$key" else number.ifBlank { "Hidden number" },
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            country?.let { Text("${it.flag} ${it.name}", style = MaterialTheme.typography.bodyLarge) }
            if (!contact.location.isNullOrBlank() && contact.location != country?.name) {
                Text(contact.location!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

        if (keypad) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(listOf('1', '2', '3'), listOf('4', '5', '6'), listOf('7', '8', '9'), listOf('*', '0', '#')).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        row.forEach { c ->
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                                    .pointerInput(call) {
                                        detectTapGestures(onPress = {
                                            call.playDtmfTone(c)
                                            tryAwaitRelease()
                                            call.stopDtmfTone()
                                        })
                                    },
                                contentAlignment = Alignment.Center
                            ) { Text(c.toString(), style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                }
                TextButton(onClick = { keypad = false }) { Text("Hide keypad") }
            }
        }

        if (state == Call.STATE_RINGING) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                LabeledButton("Decline", Icons.Default.CallEnd, CallRed) {
                    if (canPrompt) prompt = "declined"
                    call.reject(false, null)
                }
                LabeledButton("Accept", Icons.Default.Call, CallGreen) { call.answer(VideoProfile.STATE_AUDIO_ONLY) }
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ToggleButton("Mute", if (CallManager.muted) Icons.Default.MicOff else Icons.Default.Mic, CallManager.muted) {
                        CallManager.toggleMute()
                    }
                    ToggleButton("Keypad", Icons.Default.Dialpad, keypad) { keypad = !keypad }
                    ToggleButton("Speaker", Icons.Default.VolumeUp, CallManager.speaker) { CallManager.toggleSpeaker() }
                    ToggleButton(
                        "Hold",
                        if (state == Call.STATE_HOLDING) Icons.Default.PlayArrow else Icons.Default.Pause,
                        state == Call.STATE_HOLDING
                    ) { if (state == Call.STATE_HOLDING) call.unhold() else call.hold() }
                }
                LabeledButton("End", Icons.Default.CallEnd, CallRed) { call.disconnect() }
            }
        }
    }
}

private fun submitReport(context: Context, number: String, type: String) {
    val tail = Reports.key(number, Reports.region(context)) ?: return
    val dao = SpamDb.get(context).dao()
    dao.insert(SpamNumber(tail, type))
    dao.deleteIdentifications(tail)
    dao.addIdentification(IdentificationEntry(number = tail, type = type, time = System.currentTimeMillis()))
    CoroutineScope(Dispatchers.IO).launch {
        try {
            Reports.report(context, tail, type)
        } catch (e: Exception) {
        }
    }
}

@Composable
private fun FeedbackScreen(number: String, contact: ContactInfo, ended: Boolean, onFinish: () -> Unit) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        delay(30000)
        onFinish()
    }
    val onSelect: (String?) -> Unit = { type ->
        if (type != null) submitReport(context, number, type)
        onFinish()
    }
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CallAvatar(photoUri = contact.photo, size = 96.dp)
        Spacer(Modifier.height(16.dp))
        Text(
            contact.name?.takeIf { it.isNotBlank() } ?: "Unknown number",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            if (number.startsWith("+")) number else "+$number",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(24.dp))
        ShieldCard(Modifier.fillMaxWidth()) {
            if (ended) CallFeedbackPrompt(onSelect) else DeclineReasonPrompt(existingVote = null, onSelect = onSelect)
        }
    }
}

@Composable
private fun CallFeedbackPrompt(onSelect: (String?) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("How did the call go?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        listOf("safe" to CallGreen, "spam" to CallAmber, "scam" to CallRed).forEach { (type, color) ->
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
fun ToggleButton(label: String, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconToggleButton(
            checked = on,
            onCheckedChange = { onClick() },
            modifier = Modifier.size(60.dp)
        ) { Icon(icon, contentDescription = label) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}