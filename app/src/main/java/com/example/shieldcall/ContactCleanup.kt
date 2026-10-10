package com.example.shieldcall

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.i18n.phonenumbers.PhoneNumberUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CleanupItem(
    val lookupKey: String,
    val name: String,
    val detail: String,
    val reason: String,
    val phoneRowId: Long? = null,
    val newNumber: String? = null
)

object ContactCleanup {
    private data class Entry(val lookup: String, val name: String, val hasPhoto: Boolean)

    fun normalize(raw: String, region: String): String? {
        val t = raw.trim().replace(Regex("[\\s\\-().]"), "")
        if (t.isEmpty() || t.startsWith("+")) return null
        val candidate = if (t.startsWith("00")) "+" + t.substring(2) else t
        val util = PhoneNumberUtil.getInstance()
        val formatted: String? = try {
            val p = util.parse(candidate, region)
            if (util.isValidNumber(p)) "+${p.countryCode}${p.nationalNumber}" else null
        } catch (e: Exception) {
            null
        }
        if (formatted == null) return null
        return formatted.takeIf { it != raw }
    }

    fun reformat(c: Context, rowId: Long, number: String): Boolean = try {
        c.contentResolver.update(
            ContentUris.withAppendedId(ContactsContract.Data.CONTENT_URI, rowId),
            ContentValues().apply { put(Phone.NUMBER, number) },
            null, null
        ) > 0
    } catch (e: Exception) {
        false
    }

    fun scan(c: Context): List<CleanupItem> {
        val cr = c.contentResolver
        val region = Reports.region(c)
        val out = LinkedHashMap<String, CleanupItem>()

        cr.query(
            ContactsContract.Contacts.CONTENT_URI,
            arrayOf(
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME,
                ContactsContract.Contacts.HAS_PHONE_NUMBER
            ),
            null, null, null
        )?.use { cur ->
            while (cur.moveToNext()) {
                val lookup = cur.getString(0) ?: continue
                val name = cur.getString(1).orEmpty()
                val hasPhone = cur.getInt(2) == 1
                if (!hasPhone) {
                    out[lookup] = CleanupItem(lookup, name.ifBlank { "(no name)" }, "No phone number saved", "No phone number")
                } else if (name.isBlank()) {
                    out[lookup] = CleanupItem(lookup, "(no name)", "Phone number saved, no name", "No name")
                }
            }
        }

        val byNumber = HashMap<String, MutableList<Entry>>()
        cr.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.NUMBER, Phone.LOOKUP_KEY, Phone.DISPLAY_NAME, Phone.PHOTO_THUMBNAIL_URI, Phone._ID),
            null, null, null
        )?.use { cur ->
            while (cur.moveToNext()) {
                val raw = cur.getString(0).orEmpty()
                val lookup = cur.getString(1) ?: continue
                val name = cur.getString(2).orEmpty()
                val hasPhoto = cur.getString(3) != null
                val rowId = cur.getLong(4)

                Reports.key(raw, region)?.let { key ->
                    byNumber.getOrPut(key) { ArrayList() }.add(Entry(lookup, name, hasPhoto))
                }

                val fixed = normalize(raw, region)
                if (fixed != null && !out.containsKey(lookup)) {
                    out["fmt_$rowId"] = CleanupItem(
                        "fmt_$rowId",
                        name.ifBlank { "(no name)" },
                        "$raw → $fixed",
                        "Format number",
                        rowId,
                        fixed
                    )
                }
            }
        }

        byNumber.values.forEach { group ->
            val distinct = group.distinctBy { it.lookup }
            if (distinct.size > 1) {
                val keeper = distinct.maxBy { (if (it.hasPhoto) 2 else 0) + (if (it.name.isNotBlank()) 1 else 0) }
                distinct.filter { it.lookup != keeper.lookup }.forEach { d ->
                    out.putIfAbsent(
                        d.lookup,
                        CleanupItem(
                            d.lookup,
                            d.name.ifBlank { "(no name)" },
                            "Same number as ${keeper.name.ifBlank { "another contact" }}",
                            "Duplicate"
                        )
                    )
                }
            }
        }
        return out.values.toList()
    }
}

@Composable
fun ContactCleanupDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var found by remember { mutableStateOf<List<CleanupItem>?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        found = withContext(Dispatchers.IO) { ContactCleanup.scan(context) }
    }

    Dialog(onDismissRequest = { if (!busy) onDone() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        ShieldCard(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Clean up contacts", style = MaterialTheme.typography.titleLarge)
                val list = found
                when {
                    list == null -> CircularProgressIndicator()
                    list.isEmpty() -> Text("Nothing to clean up. Your contacts look good.")
                    else -> {
                        val formats = list.count { it.reason == "Format number" }
                        val noPhone = list.count { it.reason == "No phone number" }
                        val noName = list.count { it.reason == "No name" }
                        val dupes = list.count { it.reason == "Duplicate" }
                        Text(
                            "Changes: $formats to format · $noPhone with no phone number · $noName with no name · $dupes duplicates",
                            style = MaterialTheme.typography.bodySmall
                        )
                        LazyColumn(Modifier.heightIn(max = 340.dp)) {
                            items(list, key = { it.lookupKey }) { item ->
                                Column(Modifier.padding(vertical = 6.dp)) {
                                    Text(item.name, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                    Text(
                                        "${item.reason} · ${item.detail}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                        Text(
                            "Accepting updates or deletes these entries on your phone and any account they sync with.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(enabled = !busy, onClick = onDone) { Text("Decline") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        enabled = !busy && (found?.isNotEmpty() == true),
                        onClick = {
                            val toApply = found.orEmpty()
                            scope.launch {
                                busy = true
                                val done = withContext(Dispatchers.IO) {
                                    val formatted = toApply.count { item ->
                                        item.phoneRowId != null && item.newNumber != null &&
                                                ContactCleanup.reformat(context, item.phoneRowId, item.newNumber)
                                    }
                                    val deleted = toApply.count { item ->
                                        item.phoneRowId == null && ContactEditor.delete(context, item.lookupKey)
                                    }
                                    formatted + deleted
                                }
                                busy = false
                                Toast.makeText(context, "Cleaned up $done entries", Toast.LENGTH_SHORT).show()
                                onDone()
                            }
                        }
                    ) { Text(if (busy) "Working…" else "Accept") }
                }
            }
        }
    }
}