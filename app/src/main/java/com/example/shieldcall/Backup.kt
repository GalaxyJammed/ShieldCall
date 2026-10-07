package com.example.shieldcall

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class BackupData(val blocked: List<BlockedNumber>, val favorites: Set<String>, val settings: JSONObject?)

object Backup {
    private val actions = setOf("hangup", "block", "popup", "silence")
    private val types = setOf("number", "name", "country", "prefix")
    private val flags = listOf("business", "foreign", "unknown", "unsaved", "spamScam")
    private const val LIMIT = 5000

    fun export(c: Context, uri: Uri): Boolean = try {
        val blocked = JSONArray()
        SpamDb.get(c).dao().allBlocked().forEach {
            blocked.put(JSONObject().put("number", it.number).put("name", it.name).put("type", it.type))
        }
        val settings = JSONObject()
            .put("dark", Prefs.dark)
            .put("lookup", Prefs.lookup)
            .put("fullScreen", Prefs.fullScreen)
            .put("anonymousReviews", Prefs.anonymousReviews)
            .put("business", Prefs.business)
            .put("foreign", Prefs.foreign)
            .put("unknown", Prefs.unknown)
            .put("unsaved", Prefs.unsaved)
            .put("spamScam", Prefs.spamScam)
            .put("action", Prefs.action)
        val root = JSONObject()
            .put("app", "ShieldCall")
            .put("version", 1)
            .put("blocked", blocked)
            .put("favorites", JSONArray(Favorites.keys.toList()))
            .put("settings", settings)
        c.contentResolver.openOutputStream(uri, "wt")?.use { it.write(root.toString(2).toByteArray()) } != null
    } catch (e: Exception) {
        false
    }

    fun read(c: Context, uri: Uri): BackupData? {
        return try {
            val text = c.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return null
            val root = JSONObject(text)
            if (root.optString("app") != "ShieldCall") return null
            val blocked = ArrayList<BlockedNumber>()
            val arr = root.optJSONArray("blocked") ?: JSONArray()
            for (i in 0 until minOf(arr.length(), LIMIT)) {
                val o = arr.optJSONObject(i) ?: continue
                val number = o.optString("number")
                val type = o.optString("type", "number")
                if (number.isBlank() || number.length > 40 || type !in types) continue
                blocked.add(BlockedNumber(number = number, name = o.optString("name").take(100), type = type))
            }
            val favs = HashSet<String>()
            val fa = root.optJSONArray("favorites") ?: JSONArray()
            for (i in 0 until minOf(fa.length(), LIMIT)) {
                val k = fa.optString(i)
                if (k.length in 5..20 && k.all { it.isDigit() }) favs.add(k)
            }
            BackupData(blocked, favs, root.optJSONObject("settings"))
        } catch (e: Exception) {
            null
        }
    }

    fun applySettings(c: Context, s: JSONObject) {
        if (s.has("dark")) Prefs.setDark(c, s.optBoolean("dark"))
        if (s.has("lookup")) Prefs.setLookup(c, s.optBoolean("lookup"))
        if (s.has("fullScreen")) Prefs.setFullScreen(c, s.optBoolean("fullScreen"))
        if (s.has("anonymousReviews")) Prefs.setAnonymousReviews(c, s.optBoolean("anonymousReviews"))
        flags.forEach { if (s.has(it)) Prefs.setFlag(c, it, s.optBoolean(it)) }
        s.optString("action").takeIf { it in actions }?.let { Prefs.setAction(c, it) }
    }
}

@Composable
fun BackupSettings() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) { Backup.export(context, uri) }
            Toast.makeText(context, if (ok) "Backup saved" else "Couldn't save the backup", Toast.LENGTH_SHORT).show()
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val data = withContext(Dispatchers.IO) { Backup.read(context, uri) }
            if (data == null) {
                Toast.makeText(context, "That isn't a valid ShieldCall backup", Toast.LENGTH_SHORT).show()
            } else {
                withContext(Dispatchers.IO) {
                    val dao = SpamDb.get(context).dao()
                    data.blocked.forEach { dao.block(it) }
                }
                Favorites.merge(context, data.favorites)
                data.settings?.let { Backup.applySettings(context, it) }
                Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
            }
        }
    }

    ShieldCard(Modifier.fillMaxWidth()) {
        Column {
            BackupRow(
                Icons.Default.FileUpload,
                "Export backup",
                "Save your blocklist, favorites and settings to a file"
            ) { exporter.launch("shieldcall-backup.json") }
            HorizontalDivider()
            BackupRow(
                Icons.Default.FileDownload,
                "Import backup",
                "Restore from a ShieldCall backup file"
            ) { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
        }
    }
}

@Composable
private fun BackupRow(icon: ImageVector, title: String, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodySmall)
        }
    }
}