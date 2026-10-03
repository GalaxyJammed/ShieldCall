package com.example.shieldcall

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import android.media.Ringtone
import android.media.RingtoneManager

object CallStyle {
    fun bgFile(c: Context) = File(c.filesDir, "call_bg")

    private suspend fun copy(c: Context, uri: Uri, target: File, maxBytes: Long): Boolean =
        withContext(Dispatchers.IO) {
            try {
                c.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { input.copyTo(it) }
                }
                if (target.length() in 1..maxBytes) {
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

    suspend fun saveBg(c: Context, uri: Uri) = copy(c, uri, bgFile(c), 20_000_000)

    fun loadBg(c: Context): ImageBitmap? {
        val f = bgFile(c)
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

class CallSound(private val context: Context) {
    private var ringtone: Ringtone? = null

    fun start() {
        stop()
        ringtone = RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
            isLooping = true
            play()
        }
    }

    fun stop() {
        ringtone?.stop()
        ringtone = null
    }
}

@Composable
fun CallBackdrop(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val bg = remember { CallStyle.loadBg(context) }
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