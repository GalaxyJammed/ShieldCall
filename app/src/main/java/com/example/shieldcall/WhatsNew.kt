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
            "2.6.0",
            listOf(
                "In \"Settings\" you can now enable estimate tracking of your minutes left so if you are on SIM it will (in estimate) warn you if your Carrier Balance is getting too low. You can also add the carrier's website for easy access to it straight from the app",
                "Added a TTS option in \"Settings\" that lets a TTS voice speak when receiving a call saying \"Incoming Call from {Contact Name}\"",
                "Added Quiet Hours/Dialer accessible from the Android Quick Tiles so you can access it in a flash",
                "Added \"Favorites\" widget so you can access yoru favorite contacts (upto 6) from the widget in the home screen and immediately call them",
                "Added a Contacts clean-up button so you can immediately merge and remove contacts with missing numbers or missing names and reformats numbers that are missing their country code",

            )
        ),
        Release(
            "2.5.0",
            listOf(
                "Added the ability to add a custom background that overrides the settings background for individual contacts \"Number Details\" -> \"Edit Icon\"",
                "Added \"Decline With Reason\" when you get a call that declines and lets you write an SMS to the user immediately (or pick from default lines already given)",
                "Added a \"Call Later\" on missed call notifications that reminds you to call the number an hour later",
                "Added a \"Set Birthday\" in 'Edit Contact' inside of \"Number Details\" that lets you assign a contact's birthday (syncs with Google) and when that user's birthday arrives it gives you a reminder",
                "Added Flash on call in Settings where your phone's flashlight goes on when a call is ongoing so you don't miss it",
                "On the \"Dialer\" added \"Speed-dial\" where you can assign each number to a contact and easily call them by holding down said number",
                "Added \"Mute when face down\" setting where if you put your phone face down the call and microphone are muted incase you are in the middle of a conversation but dont want to hang up",
                "Added a 'Looping Audio' field in \"Fake Call\" where you can add an audio file that constantly loops to simulate talking so you can test features that have to do with audio (Hold, Phone Down to mute etc)"
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