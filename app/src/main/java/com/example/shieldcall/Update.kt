package com.example.shieldcall

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

object Update {
    private const val OWNER = "galaxyjammed"
    private const val REPO = "ShieldCall"

    var latest by mutableStateOf<String?>(null)
        private set
    var url by mutableStateOf("")
        private set
    var apkUrl by mutableStateOf<String?>(null)
        private set
    var apkSize by mutableLongStateOf(0L)
        private set
    var notes by mutableStateOf("")
        private set
    private var apkDigest: String? = null

    fun current(c: Context): String = c.packageManager.getPackageInfo(c.packageName, 0).versionName.orEmpty()

    private fun parts(v: String) =
        v.trim().removePrefix("v").removePrefix("V").split(".").map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    private fun isNewer(a: String, b: String): Boolean {
        val x = parts(a)
        val y = parts(b)
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrElse(i) { 0 }
            val q = y.getOrElse(i) { 0 }
            if (p != q) return p > q
        }
        return false
    }

    suspend fun check(c: Context) {
        val json = withContext(Dispatchers.IO) {
            try {
                val conn = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "ShieldCall")
                if (conn.responseCode != 200) null
                else JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            } catch (e: Exception) {
                null
            }
        } ?: return
        val tag = json.optString("tag_name")
        if (tag.isBlank() || !isNewer(tag, current(c))) return
        var apk: String? = null
        var size = 0L
        var digest: String? = null
        val assets = json.optJSONArray("assets")
        if (assets != null) {
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                if (a.optString("name").endsWith(".apk", ignoreCase = true)) {
                    apk = a.optString("browser_download_url").takeIf { it.startsWith("https://") }
                    size = a.optLong("size")
                    digest = a.optString("digest").removePrefix("sha256:").takeIf { it.length == 64 }
                    break
                }
            }
        }
        apkDigest = digest
        apkSize = size
        notes = json.optString("body").trim().take(600)
        url = json.optString("html_url")
        apkUrl = apk
        latest = tag
    }

    suspend fun download(context: Context, onProgress: (Float) -> Unit): File? = withContext(Dispatchers.IO) {
        val source = apkUrl ?: return@withContext null
        try {
            val dir = File(context.cacheDir, "updates").apply { mkdirs() }
            dir.listFiles()?.forEach { it.delete() }
            val file = File(dir, "ShieldCall.apk")
            val conn = URL(source).openConnection() as HttpURLConnection
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("User-Agent", "ShieldCall")
            if (conn.responseCode != 200) return@withContext null
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: apkSize
            val md = MessageDigest.getInstance("SHA-256")
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buffer = ByteArray(32 * 1024)
                    var done = 0L
                    var n: Int
                    while (input.read(buffer).also { n = it } != -1) {
                        out.write(buffer, 0, n)
                        md.update(buffer, 0, n)
                        done += n
                        if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
            val expected = apkDigest
            if (expected != null) {
                val actual = md.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(expected, ignoreCase = true)) {
                    file.delete()
                    return@withContext null
                }
            }
            file
        } catch (e: Exception) {
            null
        }
    }

    fun canInstall(c: Context): Boolean = c.packageManager.canRequestPackageInstalls()

    fun openInstallSettings(c: Context) {
        c.startActivity(
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun install(c: Context, file: File) {
        val uri = FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", file)
        c.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun openReleasePage(c: Context) {
        c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@Composable
fun UpdateCard() {
    val version = Update.latest ?: return
    var show by remember { mutableStateOf(false) }
    val red = Color(0xFFC62828)
    ShieldCard(
        Modifier.fillMaxWidth().clickable { show = true },
        containerColor = red.copy(alpha = 0.15f)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = red, modifier = Modifier.padding(end = 12.dp))
            Column {
                Text("You're on an outdated version", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = red)
                Text("Version $version is available. Tap to update.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
    if (show) UpdateDialog { show = false }
}

@Composable
private fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    val hasApk = Update.apkUrl != null

    fun start() {
        if (!hasApk) {
            Update.openReleasePage(context)
            return
        }
        if (!Update.canInstall(context)) {
            message = "Allow ShieldCall to install updates in the settings that just opened, come back, and tap Update now again."
            Update.openInstallSettings(context)
            return
        }
        scope.launch {
            message = null
            progress = 0f
            downloading = true
            val file = Update.download(context) { progress = it }
            downloading = false
            if (file == null) {
                message = "The download failed. Check your connection, or get it from GitHub."
            } else {
                Update.install(context, file)
                onDismiss()
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Update to ${Update.latest}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (Update.apkSize > 0) Text("%.1f MB".format(Update.apkSize / 1_048_576.0), style = MaterialTheme.typography.bodySmall)
                if (Update.notes.isNotBlank()) Text(Update.notes, style = MaterialTheme.typography.bodyMedium)
                if (downloading) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (!downloading && message != null && hasApk) {
                    TextButton(onClick = { Update.openReleasePage(context) }, contentPadding = PaddingValues(0.dp)) { Text("Open GitHub instead") }
                }
            }
        },
        confirmButton = {
            Button(enabled = !downloading, onClick = { start() }) {
                Text(if (hasApk) (if (downloading) "Downloading…" else "Update now") else "Open GitHub")
            }
        },
        dismissButton = { TextButton(enabled = !downloading, onClick = onDismiss) { Text("Later") } }
    )
}