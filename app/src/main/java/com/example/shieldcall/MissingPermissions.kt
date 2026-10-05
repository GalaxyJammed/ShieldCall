package com.example.shieldcall

import android.Manifest
import android.app.NotificationManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner

private enum class Missing(val title: String, val text: String, val optional: Boolean = false) {
    DIALER("Default phone app", "Lets ShieldCall answer, place and show your calls."),
    SCREENING("Caller ID & spam app", "Lets ShieldCall see incoming numbers so it can identify and block them."),
    OVERLAY("Display over other apps", "Shows the caller info card on top of your screen."),
    CONTACTS("Contacts", "Recognises saved numbers and shows names and photos."),
    PHONE("Phone", "Places calls and reads the call state."),
    NOTIFICATIONS("Notifications", "Missed call and voicemail alerts."),
    FULLSCREEN("Full-screen calls", "Shows incoming calls over the lock screen."),
    WRITE_CONTACTS("Save contacts", "Needed to add new contacts. Optional.", true),
    BLUETOOTH("Bluetooth devices", "Shows device names for call audio. Optional.", true)
}

private fun granted(c: Context, p: String) = c.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

private fun isMissing(c: Context, item: Missing): Boolean {
    val rm = c.getSystemService(RoleManager::class.java)
    return when (item) {
        Missing.DIALER -> rm != null && rm.isRoleAvailable(RoleManager.ROLE_DIALER) && !rm.isRoleHeld(RoleManager.ROLE_DIALER)
        Missing.SCREENING -> rm != null && rm.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) && !rm.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)
        Missing.OVERLAY -> !Settings.canDrawOverlays(c)
        Missing.CONTACTS -> !granted(c, Manifest.permission.READ_CONTACTS)
        Missing.PHONE -> !granted(c, Manifest.permission.READ_PHONE_STATE) || !granted(c, Manifest.permission.CALL_PHONE)
        Missing.NOTIFICATIONS -> Build.VERSION.SDK_INT >= 33 && !granted(c, Manifest.permission.POST_NOTIFICATIONS)
        Missing.FULLSCREEN -> Build.VERSION.SDK_INT >= 34 &&
                !c.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()
        Missing.WRITE_CONTACTS -> !granted(c, Manifest.permission.WRITE_CONTACTS)
        Missing.BLUETOOTH -> Build.VERSION.SDK_INT >= 31 && !granted(c, Manifest.permission.BLUETOOTH_CONNECT)
    }
}

@Composable
fun MissingPermissionsCard() {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    val missing = remember(tick) { Missing.entries.filter { isMissing(context, it) } }

    DisposableEffect(context) {
        val owner = context as? LifecycleOwner
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) tick++ }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }

    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { tick++ }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { tick++ }

    fun start(intent: Intent) {
        try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
        }
    }

    fun appSettings() {
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    fun request(perms: List<String>, fallback: () -> Unit = { appSettings() }) {
        val prefs = context.getSharedPreferences("perm_asked", Context.MODE_PRIVATE)
        val key = perms.joinToString(",")
        if (prefs.getBoolean(key, false)) {
            fallback()
        } else {
            prefs.edit().putBoolean(key, true).apply()
            permLauncher.launch(perms.filter { !granted(context, it) }.toTypedArray())
        }
    }

    fun role(name: String) {
        val rm = context.getSystemService(RoleManager::class.java)
        try {
            roleLauncher.launch(rm.createRequestRoleIntent(name))
        } catch (e: Exception) {
            start(Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS))
        }
    }

    fun open(item: Missing) {
        val pkg = "package:${context.packageName}"
        when (item) {
            Missing.DIALER -> role(RoleManager.ROLE_DIALER)
            Missing.SCREENING -> role(RoleManager.ROLE_CALL_SCREENING)
            Missing.OVERLAY -> start(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse(pkg)))
            Missing.CONTACTS -> request(listOf(Manifest.permission.READ_CONTACTS))
            Missing.PHONE -> request(listOf(Manifest.permission.READ_PHONE_STATE, Manifest.permission.CALL_PHONE))
            Missing.NOTIFICATIONS -> request(listOf(Manifest.permission.POST_NOTIFICATIONS)) {
                start(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
            Missing.FULLSCREEN -> start(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse(pkg)))
            Missing.WRITE_CONTACTS -> request(listOf(Manifest.permission.WRITE_CONTACTS))
            Missing.BLUETOOTH -> request(listOf(Manifest.permission.BLUETOOTH_CONNECT))
        }
    }

    if (missing.isEmpty()) return

    val required = missing.filter { !it.optional }
    val tint = if (required.isNotEmpty()) Color(0xFFC62828) else Color(0xFFF9A825)

    ShieldCard(Modifier.fillMaxWidth(), containerColor = tint.copy(alpha = 0.15f)) {
        Column {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Warning, contentDescription = null, tint = tint, modifier = Modifier.padding(end = 12.dp))
                Column {
                    Text(
                        if (required.isNotEmpty()) "Permissions needed" else "Optional permissions",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = tint
                    )
                    Text("Tap an item to turn it on.", style = MaterialTheme.typography.bodySmall)
                }
            }
            (required + missing.filter { it.optional }).forEach { item ->
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { open(item) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(item.text, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
                }
            }
        }
    }
    Spacer(Modifier.height(16.dp))
}