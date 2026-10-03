package com.example.shieldcall

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

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
fun UpdateScreen(version: String, url: String) {
    val context = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ShieldCard(Modifier.fillMaxWidth()) {
            Column(
                Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text("New version available", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(
                    "Version $version is out. Download it to continue using ShieldCall.",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Download update") }
            }
        }
    }
}