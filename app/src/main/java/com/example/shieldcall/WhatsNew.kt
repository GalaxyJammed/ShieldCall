package com.example.shieldcall

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.runtime.remember

data class Release(val version: String, val notes: List<String>)

object Changelog {
    val releases = listOf(
        Release(
            "2.1.0",
            listOf(
                "Repurposed the \"Block\" information on \"Stats\" screen to something more relevant (including the widget)",
                "Added a 'Copy from Clipboard' function to the dialer that checks if you have a valid phone number in your clipboard allowing you to immediately paste it",
                "Added \"Contact Us\" in the \"Settings\" to easily report Bugs/Give feedback",
                "Added \"Stats\" widget that shows your important stats at a glance",
                "Added 'Previews' to both Widgets",
                "Added 'On-going Call' notification so you can easily swap back to the call from your notifications when a call is ongoing instead of having to enter the app manually everytime",
                "Added a QOL feature where if your phone is close to your head/ears it will close the screen so you don't accidentally end the call with your head"
            )
        ),
        Release(
            "2.0.0",
            listOf(
                "Repurposed the \"About\" section to a \"What's new\" along with \"Privacy Policy\" and \"Licenses\" for more information",
                "Added Widget to easily lookup and dial numbers"
            )
        )
    )
}

object WhatsNew {
    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun shouldShow(c: Context): Boolean {
        val code = Reports.versionCode(c)
        val info = c.packageManager.getPackageInfo(c.packageName, 0)
        val seen = prefs(c).getLong("seenVersionCode", -1L)
        if (seen == -1L && info.firstInstallTime == info.lastUpdateTime) {
            prefs(c).edit().putLong("seenVersionCode", code).apply()
            return false
        }
        return seen < code && Changelog.releases.any { it.version == info.versionName }
    }

    fun markSeen(c: Context) {
        prefs(c).edit().putLong("seenVersionCode", Reports.versionCode(c)).apply()
    }
}

@Composable
fun WhatsNewDialog(all: Boolean, onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val current = remember { context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty() }
    val shown = if (all) Changelog.releases else Changelog.releases.filter { it.version == current }.ifEmpty { Changelog.releases.take(1) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ShieldCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (all) "What's new" else "What's new in ${shown.first().version}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(12.dp))
                val scroll = rememberScrollState()
                Column(
                    Modifier.heightIn(max = 420.dp).scrollbar(scroll).verticalScroll(scroll),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    shown.forEach { r ->
                        if (all) Text("Version ${r.version}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        r.notes.forEach { n ->
                            Row {
                                Text("•  ", style = MaterialTheme.typography.bodyMedium)
                                Text(n, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        if (all) Spacer(Modifier.height(8.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Button(onClick = onDismiss) { Text(if (all) "Close" else "Got it") }
                }
            }
        }
    }
}