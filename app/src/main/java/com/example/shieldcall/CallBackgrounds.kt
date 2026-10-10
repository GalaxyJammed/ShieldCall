package com.example.shieldcall

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object CallBackgrounds {
    private fun file(c: Context, key: String) = File(c.filesDir, "call_bg_$key.jpg")

    fun has(c: Context, key: String): Boolean = file(c, key).exists()

    fun remove(c: Context, key: String) {
        file(c, key).delete()
    }

    fun save(c: Context, key: String, uri: Uri): Boolean {
        val target = file(c, key)
        return try {
            c.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
            if (target.length() in 1..20_000_000L) {
                true
            } else {
                target.delete()
                false
            }
        } catch (e: Exception) {
            target.delete()
            false
        }
    }

    fun load(c: Context, key: String): ImageBitmap? {
        val f = file(c, key)
        if (!f.exists()) return null
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(f.path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 1600) sample *= 2
            BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
        } catch (e: Exception) {
            null
        }
    }
}


@Composable
fun CallBackdrop(overrideNumber: String? = null, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val live = overrideNumber?.takeIf { it.isNotBlank() }
        ?: CallManager.calls.firstOrNull { it.parent == null }?.details?.handle?.schemeSpecificPart.orEmpty()
    val key = remember(live) { if (live.isBlank()) null else Reports.key(live, Reports.region(context)) }
    val bg = remember(key) {
        (key?.let { CallBackgrounds.load(context, it) }) ?: CallStyle.loadBg(context)
    }
    if (bg == null) {
        content()
    } else {
        ShieldTheme(true) {
            Box(Modifier.fillMaxSize()) {
                Image(bg, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)))
                content()
            }
        }
    }
}

@Composable
fun CallBackgroundRow(key: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var has by remember(key) { mutableStateOf(CallBackgrounds.has(context, key)) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) { CallBackgrounds.save(context, key, uri) }
                has = ok
                if (!ok) Toast.makeText(context, "Couldn't use that image (max 20 MB)", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        EditSection("Call background")
        Text(
            "Shown on the call screen when this contact calls. It replaces your default background.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) { Text(if (has) "Change image" else "Choose image") }
            if (has) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = {
                    CallBackgrounds.remove(context, key)
                    has = false
                }) { Text("Remove") }
            }
        }
    }
}