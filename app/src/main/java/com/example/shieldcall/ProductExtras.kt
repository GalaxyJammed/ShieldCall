package com.example.shieldcall

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

object Links {
    const val RELEASES = "https://github.com/galaxyjammed/ShieldCall/releases"
    const val PRIVACY = "https://github.com/galaxyjammed/ShieldCall/blob/main/PRIVACY_POLICY.md"
    const val LICENSES = "https://github.com/galaxyjammed/ShieldCall/blob/main/THIRD_PARTY_LICENSES.md"
    const val EMAIL = "galaxyjammed@gmail.com"
}

object Tags {
    val all = listOf(
        "telemarketing" to "Telemarketing",
        "bank" to "Bank fraud",
        "delivery" to "Delivery scam",
        "silent" to "Silent call",
        "prize" to "Prize / lottery",
        "tech" to "Tech support",
        "investment" to "Investment",
        "other" to "Other"
    )

    val places = listOf(
        "p_restaurant" to "Restaurant",
        "p_government" to "Government office",
        "p_health" to "Clinic or hospital",
        "p_shop" to "Shop",
        "p_delivery" to "Delivery or courier",
        "p_bank" to "Bank branch",
        "p_school" to "School or university",
        "p_business" to "Other business"
    )

    fun label(key: String): String = (all + places).firstOrNull { it.first == key }?.second ?: key
}

object Notes {
    private fun prefs(c: Context) = c.getSharedPreferences("notes", Context.MODE_PRIVATE)

    fun get(c: Context, key: String): String = prefs(c).getString(key, "").orEmpty()

    fun set(c: Context, key: String, text: String) {
        if (text.isBlank()) prefs(c).edit().remove(key).apply()
        else prefs(c).edit().putString(key, text).apply()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagDialog(type: String, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val options = if (type == "safe") Tags.places else Tags.all
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ShieldCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    when (type) {
                        "scam" -> "What kind of scam?"
                        "spam" -> "What kind of call?"
                        else -> "What kind of place is it?"
                    },
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    if (type == "safe") "Optional. Only if you know who this number belongs to."
                    else "Optional. It helps others recognise the call.",
                    style = MaterialTheme.typography.bodySmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    options.forEach { (key, label) ->
                        AssistChip(onClick = { onPick(key) }, label = { Text(label) })
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    TextButton(onClick = { onPick(null) }) { Text("Skip") }
                }
            }
        }
    }
}

@Composable
fun NoteCard(key: String) {
    val context = LocalContext.current
    var text by remember(key) { mutableStateOf(Notes.get(context, key)) }
    ShieldCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("My note", style = MaterialTheme.typography.titleMedium)
            Text("Private. Stays on this phone and is never uploaded.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it.take(500)
                    Notes.set(context, key, text)
                },
                placeholder = { Text("Add a note about this number") },
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

fun shareSummary(context: Context, displayNum: String, info: Info?) {
    val lines = ArrayList<String>()
    lines.add("📞 $displayNum")
    val total = (info?.spam ?: 0) + (info?.scam ?: 0) + (info?.safe ?: 0)
    if (info != null && total > 0) {
        val score = ((info.safe * 100) / total).toInt()
        lines.add("Safety: $score% safe · $total votes (safe ${info.safe}, spam ${info.spam}, scam ${info.scam})")
        val top = info.tags.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(3)
        if (top.isNotEmpty()) lines.add("Reported as: " + top.joinToString(", ") { Tags.label(it.key) })
    } else {
        lines.add("No community reports yet")
    }
    lines.add("Checked with ShieldCall: ${Links.RELEASES}")
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, lines.joinToString("\n"))
    }
    context.startActivity(Intent.createChooser(intent, "Share summary"))
}