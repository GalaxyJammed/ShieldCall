package com.example.shieldcall

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable

object Update {
    private const val OWNER = "galaxyjammed"
    private const val REPO = "ShieldCall"

    var latest by mutableStateOf<String?>(null)
        private set
    var url by mutableStateOf("")
        private set

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
        val result = withContext(Dispatchers.IO) {
            try {
                val conn = URL("https://api.github.com/repos/$OWNER/$REPO/releases/latest").openConnection() as HttpURLConnection
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.setRequestProperty("User-Agent", "ShieldCall")
                if (conn.responseCode != 200) return@withContext null
                val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val tag = json.optString("tag_name")
                val page = json.optString("html_url")
                if (tag.isNotBlank() && isNewer(tag, current(c))) tag to page else null
            } catch (e: Exception) {
                null
            }
        }
        if (result != null) {
            url = result.second
            latest = result.first
        }
    }
}

@Composable
fun UpdateCard() {
    val version = Update.latest ?: return
    val context = LocalContext.current
    val red = Color(0xFFC62828)
    ShieldCard(
        Modifier.fillMaxWidth().clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(Update.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        },
        containerColor = red.copy(alpha = 0.15f)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = red, modifier = Modifier.padding(end = 12.dp))
            Column {
                Text("You're on an outdated version", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = red)
                Text("Version $version is available. Tap to download it.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}