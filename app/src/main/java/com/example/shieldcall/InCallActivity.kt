package com.example.shieldcall

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.PhoneAccount
import android.telecom.VideoProfile
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CallMerge
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Headset
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapCalls
import androidx.compose.material.icons.automirrored.filled.VolumeUp
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.os.PowerManager

private val CallGreen = Color(0xFF2E7D32)
private val CallAmber = Color(0xFFF9A825)
private val CallRed = Color(0xFFC62828)

object InCallUi {
    var visible by mutableStateOf(false)
}

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
    override fun onStop() {
        super.onStop()
        Widgets.refresh(this)
    }
    override fun onResume() {
        super.onResume()
        InCallUi.visible = true
    }
    override fun onPause() {
        super.onPause()
        InCallUi.visible = false
    }
}

@Suppress("DEPRECATION")
@Composable
private fun InCallScreen(onFinish: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val tick = CallManager.tick
    val top = CallManager.calls.filter { it.parent == null }
    val ringing = top.firstOrNull { it.state == Call.STATE_RINGING }
    val others = top.filter { it.state != Call.STATE_RINGING }
    val call = others.firstOrNull { it.state != Call.STATE_HOLDING } ?: others.firstOrNull() ?: ringing
    val waiting = ringing?.takeIf { it != call }
    val held = others.firstOrNull { it.state == Call.STATE_HOLDING && it != call }
    val canMerge = held != null && call != null &&
            (call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) || held.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE))

    val region = remember { Reports.region(context) }
    var lastNumber by remember { mutableStateOf("") }
    var wasActive by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf<String?>(null) }
    var keypad by remember { mutableStateOf(false) }
    var showReplies by remember { mutableStateOf(false) }
    var showAudio by remember { mutableStateOf(false) }

    val btLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    val liveNumber = call?.details?.handle?.schemeSpecificPart.orEmpty()
    val number = liveNumber.ifEmpty { lastNumber }
    val isVoicemail = call?.details?.handle?.scheme == PhoneAccount.SCHEME_VOICEMAIL
    val isEmergency = remember(number) {
        number.isNotBlank() && try {
            context.getSystemService(TelephonyManager::class.java).isEmergencyNumber(number)
        } catch (e: Exception) {
            false
        }
    }
    val state = remember(tick, call) { call?.state ?: Call.STATE_DISCONNECTED }

    LaunchedEffect(liveNumber) { if (liveNumber.isNotEmpty()) lastNumber = liveNumber }
    LaunchedEffect(state) {
        if (state == Call.STATE_ACTIVE) wasActive = true
        if (state != Call.STATE_RINGING) context.getSystemService(NotificationManager::class.java).cancel(1)
    }
    val nearEar = InCallUi.visible && !keypad && CallManager.route == CallAudioState.ROUTE_EARPIECE &&
            (state == Call.STATE_ACTIVE || state == Call.STATE_DIALING || state == Call.STATE_CONNECTING)
    DisposableEffect(nearEar) {
        val pm = context.getSystemService(PowerManager::class.java)
        val lock = if (nearEar && pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
            pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "ShieldCall:proximity").also { it.acquire(4 * 60 * 60 * 1000L) }
        } else null
        onDispose { lock?.let { if (it.isHeld) it.release() } }
    }

    val key = remember(number) { Reports.key(number, region) }
    val contact by produceState(ContactInfo(null, null, null), number, ContactsVersion.n) {
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
            info?.myVote == null && localVote == null && contact.name.isNullOrBlank() && !isEmergency

    LaunchedEffect(call == null) {
        if (call == null && prompt == null) {
            if (wasActive && canPrompt) {
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
            if (waiting != null) {
                WaitingCard(
                    waiting,
                    onDecline = { waiting.reject(false, null) },
                    onHoldAccept = {
                        call.hold()
                        waiting.answer(VideoProfile.STATE_AUDIO_ONLY)
                    },
                    onEndAccept = {
                        call.disconnect()
                        waiting.answer(VideoProfile.STATE_AUDIO_ONLY)
                    }
                )
            }
            if (held != null) {
                HeldCard(
                    held,
                    onSwap = {
                        call.hold()
                        held.unhold()
                    }
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(stateText, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CallAvatar(photoUri = contact.photo, size = 120.dp)
            Text(
                if (isVoicemail) "Voicemail" else if (isEmergency) "Emergency call" else contact.name?.takeIf { it.isNotBlank() } ?: "Unknown number",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            if (!isVoicemail) {
                Text(
                    if (key != null) "+$key" else number.ifBlank { "Hidden number" },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                if (!isEmergency && key != null && contact.name.isNullOrBlank()) {
                    TextButton(onClick = { adding = true }) { Text("Add to contacts") }
                }
            }
            country?.let { Text("${it.flag} ${it.name}", style = MaterialTheme.typography.bodyLarge) }
            val simLabel = remember(call) {
                if (SimChoice.accounts(context).size > 1) SimChoice.label(context, call.details.accountHandle) else null
            }
            simLabel?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            if (!contact.location.isNullOrBlank() && contact.location != country?.name) {
                Text(contact.location!!, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            if (!isEmergency && !isVoicemail) {
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
            val canReply = call.details.can(Call.Details.CAPABILITY_RESPOND_VIA_TEXT) && number.isNotBlank() && !isEmergency
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                LabeledButton("Decline", Icons.Default.CallEnd, CallRed) {
                    if (canPrompt) prompt = "declined"
                    call.reject(false, null)
                }
                if (canReply) LabeledButton("Message", Icons.AutoMirrored.Filled.Message, MaterialTheme.colorScheme.primary) { showReplies = true }
                LabeledButton("Accept", Icons.Default.Call, CallGreen) { call.answer(VideoProfile.STATE_AUDIO_ONLY) }
            }
            if (showReplies) {
                val replies = remember(call) {
                    call.cannedTextResponses.orEmpty().ifEmpty {
                        listOf("Can't talk now. What's up?", "I'll call you right back.", "I'll call you later.", "Can't talk now. Call me later?")
                    }
                }
                ReplySheet(
                    replies = replies,
                    onPick = { text ->
                        showReplies = false
                        if (canPrompt) prompt = "declined"
                        call.reject(true, text)
                    },
                    onDismiss = { showReplies = false }
                )
            }
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    ToggleButton("Mute", if (CallManager.muted) Icons.Default.MicOff else Icons.Default.Mic, CallManager.muted) {
                        CallManager.toggleMute()
                    }
                    ToggleButton("Keypad", Icons.Default.Dialpad, keypad) { keypad = !keypad }
                    ToggleButton("Audio", routeIcon(CallManager.route), CallManager.route != CallAudioState.ROUTE_EARPIECE) {
                        if (Build.VERSION.SDK_INT >= 31 &&
                            context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
                        ) btLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
                        showAudio = true
                    }
                    ToggleButton(
                        "Hold",
                        if (state == Call.STATE_HOLDING) Icons.Default.PlayArrow else Icons.Default.Pause,
                        state == Call.STATE_HOLDING
                    ) { if (state == Call.STATE_HOLDING) call.unhold() else call.hold() }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
                    ToggleButton("Add call", Icons.Default.Add, false) {
                        context.startActivity(
                            Intent(context, MainActivity::class.java)
                                .putExtra("dial", "")
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        )
                    }
                    LabeledButton("End", Icons.Default.CallEnd, CallRed) { call.disconnect() }
                    if (held != null && canMerge) {
                        ToggleButton("Merge", Icons.Default.CallMerge, false) {
                            when {
                                call.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) -> call.mergeConference()
                                held.details.can(Call.Details.CAPABILITY_MERGE_CONFERENCE) -> held.mergeConference()
                                else -> call.conference(held)
                            }
                        }
                    } else {
                        Spacer(Modifier.width(60.dp))
                    }
                }
            }
        }
    }

    if (showAudio) AudioSheet { showAudio = false }
    if (adding) NewContactDialog(if (key != null) "+$key" else number) { adding = false }
}

private fun routeIcon(route: Int): ImageVector = when (route) {
    CallAudioState.ROUTE_SPEAKER -> Icons.AutoMirrored.Filled.VolumeUp
    CallAudioState.ROUTE_BLUETOOTH -> Icons.Default.Bluetooth
    CallAudioState.ROUTE_WIRED_HEADSET -> Icons.Default.Headset
    else -> Icons.Default.PhoneInTalk
}

private fun btName(d: BluetoothDevice): String = try {
    d.name ?: "Bluetooth device"
} catch (e: SecurityException) {
    "Bluetooth device"
}

@Suppress("DEPRECATION")
@Composable
private fun AudioSheet(onDismiss: () -> Unit) {
    val route = CallManager.route
    val mask = CallManager.supported
    val devices = CallManager.btDevices
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ShieldCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.padding(vertical = 16.dp)) {
                Text("Audio output", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                if ((mask and CallAudioState.ROUTE_EARPIECE) != 0) {
                    AudioRow("Phone", Icons.Default.PhoneInTalk, route == CallAudioState.ROUTE_EARPIECE) {
                        CallManager.chooseRoute(CallAudioState.ROUTE_EARPIECE)
                        onDismiss()
                    }
                }
                if ((mask and CallAudioState.ROUTE_WIRED_HEADSET) != 0) {
                    AudioRow("Wired headset", Icons.Default.Headset, route == CallAudioState.ROUTE_WIRED_HEADSET) {
                        CallManager.chooseRoute(CallAudioState.ROUTE_WIRED_HEADSET)
                        onDismiss()
                    }
                }
                devices.forEach { d ->
                    AudioRow(btName(d), Icons.Default.Bluetooth, route == CallAudioState.ROUTE_BLUETOOTH && CallManager.activeBt == d) {
                        CallManager.useBluetooth(d)
                        onDismiss()
                    }
                }
                if (devices.isEmpty() && (mask and CallAudioState.ROUTE_BLUETOOTH) != 0) {
                    AudioRow("Bluetooth", Icons.Default.Bluetooth, route == CallAudioState.ROUTE_BLUETOOTH) {
                        CallManager.chooseRoute(CallAudioState.ROUTE_BLUETOOTH)
                        onDismiss()
                    }
                }
                AudioRow("Speaker", Icons.AutoMirrored.Filled.VolumeUp, route == CallAudioState.ROUTE_SPEAKER) {
                    CallManager.chooseRoute(CallAudioState.ROUTE_SPEAKER)
                    onDismiss()
                }
            }
        }
    }
}

@Composable
private fun AudioRow(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        RadioButton(selected = selected, onClick = null)
    }
}

@Composable
private fun WaitingCard(call: Call, onDecline: () -> Unit, onHoldAccept: () -> Unit, onEndAccept: () -> Unit) {
    val context = LocalContext.current
    val number = call.details.handle?.schemeSpecificPart.orEmpty()
    val contact by produceState(ContactInfo(null, null, null), number) {
        value = withContext(Dispatchers.IO) { Reports.loadContactInfo(context, number) }
    }
    ShieldCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Incoming call", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                contact.name?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Unknown number" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onDecline, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("Decline", maxLines = 1)
                }
                Button(
                    onClick = onHoldAccept,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CallGreen, contentColor = Color.White)
                ) { Text("Hold & accept", maxLines = 1) }
            }
            TextButton(onClick = onEndAccept, modifier = Modifier.fillMaxWidth()) { Text("End current call & accept") }
        }
    }
}

@Composable
private fun HeldCard(call: Call, onSwap: () -> Unit) {
    val context = LocalContext.current
    val number = call.details.handle?.schemeSpecificPart.orEmpty()
    val contact by produceState(ContactInfo(null, null, null), number) {
        value = withContext(Dispatchers.IO) { Reports.loadContactInfo(context, number) }
    }
    ShieldCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("On hold", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    contact.name?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Unknown number" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            TextButton(onClick = onSwap) {
                Icon(Icons.Default.SwapCalls, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Swap")
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
private fun ReplySheet(replies: List<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
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