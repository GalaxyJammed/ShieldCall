package com.example.shieldcall

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.telecom.TelecomManager
import android.telephony.PhoneNumberUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil

private val Green = Color(0xFF2E7D32)

@Composable
fun LookupWithDialer(onNumber: (String) -> Unit, onSettings: () -> Unit) {
    var dialing by rememberSaveable { mutableStateOf(false) }
    if (dialing) {
        DialerScreen { dialing = false }
    } else {
        Box(Modifier.fillMaxSize()) {
            LookupScreen(onNumber, onSettings)
            FloatingActionButton(
                onClick = { dialing = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(24.dp)
            ) { Icon(Icons.Default.Dialpad, contentDescription = "Dial") }
        }
    }
}

@Composable
fun DialerScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val util = remember { PhoneNumberUtil.getInstance() }
    val region = remember { Reports.region(context) }
    var digits by rememberSaveable { mutableStateOf("") }
    val parsed = remember(digits) {
        try {
            util.parse(digits, region).takeIf { util.isValidNumber(it) }
        } catch (e: NumberParseException) {
            null
        }
    }

    fun place() {
        val number = parsed?.let { "+${it.countryCode}${it.nationalNumber}" } ?: digits
        context.getSystemService(TelecomManager::class.java).placeCall(Uri.fromParts("tel", number, null), null)
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) place() }
    BackHandler { onClose() }

    fun start() {
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) place()
        else launcher.launch(Manifest.permission.CALL_PHONE)
    }

    val canCall = parsed != null || PhoneNumberUtils.isEmergencyNumber(digits)
    val p = parsed

    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Default.Close, contentDescription = "Close")
            }
            Text(
                "Dialer",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center)
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (p != null) util.format(p, PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL) else digits.ifEmpty { "Enter a number" },
            style = MaterialTheme.typography.headlineMedium,
            maxLines = 1,
            color = if (digits.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
        Text(
            "Hold 0 for +, start with + for other countries",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        listOf(listOf("1", "2", "3"), listOf("4", "5", "6"), listOf("7", "8", "9"), listOf("*", "0", "#")).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { k ->
                    Key(
                        onClick = { if (digits.length < 20) digits += k },
                        onLong = if (k == "0") ({ if (digits.length < 20) digits += "+" }) else null
                    ) { Text(k, style = MaterialTheme.typography.headlineMedium) }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.size(72.dp))
            Key(onClick = { if (canCall) start() }, color = if (canCall) Green else Green.copy(alpha = 0.3f)) {
                Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
            }
            Key(onClick = { digits = digits.dropLast(1) }, onLong = { digits = "" }) {
                Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "Delete")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun Key(onClick: () -> Unit, onLong: (() -> Unit)? = null, color: Color? = null, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceContainerHighest)
            .combinedClickable(onClick = onClick, onLongClick = onLong),
        contentAlignment = Alignment.Center
    ) { content() }
}