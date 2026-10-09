package com.example.shieldcall

import android.app.role.RoleManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

object DeviceInfo {
    fun summary(c: Context): String {
        val pkg = c.packageManager.getPackageInfo(c.packageName, 0)
        val rm = c.getSystemService(RoleManager::class.java)
        val isDefault = rm?.isRoleHeld(RoleManager.ROLE_DIALER) == true
        return buildString {
            append("App: ShieldCall ${pkg.versionName} (${pkg.longVersionCode})\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
            append("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            append("Default phone app: ${if (isDefault) "yes" else "no"}\n")
            append("Region: ${Reports.region(c)} · Language: ${Locale.getDefault().toLanguageTag()}")
        }
    }
}

private fun copyToCache(c: Context, uri: Uri): Uri? = try {
    val dir = File(c.cacheDir, "feedback").apply { mkdirs() }
    val file = File(dir, "screenshot.jpg")
    c.contentResolver.openInputStream(uri)?.use { input -> file.outputStream().use { input.copyTo(it) } }
    if (file.length() in 1..10_000_000) FileProvider.getUriForFile(c, "${c.packageName}.fileprovider", file) else null
} catch (e: Exception) {
    null
}

@Composable
fun ContactUsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var category by remember { mutableStateOf("Bug") }
    var message by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { image = it }
    val info = remember { DeviceInfo.summary(context) }
    val thumb by produceState<ImageBitmap?>(null, image) {
        value = image?.let { uri ->
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(uri)?.use {
                        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = 8 })?.asImageBitmap()
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }

    fun send() {
        scope.launch {
            val subject = if (category == "Bug") "ShieldCall bug report" else "ShieldCall feedback"
            val body = "Category: $category\n$info\n\n${message.trim()}"
            val attachment = image?.let { withContext(Dispatchers.IO) { copyToCache(context, it) } }
            if (image != null && attachment == null) {
                Toast.makeText(context, "Couldn't attach the image (10 MB max)", Toast.LENGTH_SHORT).show()
                return@launch
            }
            val intent = if (attachment != null) {
                Intent(Intent.ACTION_SEND).apply {
                    type = "message/rfc822"
                    putExtra(Intent.EXTRA_EMAIL, arrayOf(Links.EMAIL))
                    putExtra(Intent.EXTRA_SUBJECT, subject)
                    putExtra(Intent.EXTRA_TEXT, body)
                    putExtra(Intent.EXTRA_STREAM, attachment)
                    clipData = ClipData.newRawUri("", attachment)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                val uri = Uri.parse(
                    "mailto:${Links.EMAIL}?subject=${Uri.encode(subject)}&body=${Uri.encode(body)}"
                )
                Intent(Intent.ACTION_SENDTO, uri)
            }
            try {
                context.startActivity(if (attachment != null) Intent.createChooser(intent, "Send with") else intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No email app found", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().padding(horizontal = 24.dp).scrollbar(scroll).verticalScroll(scroll)) {
        ShieldCard(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(28.dp))
                }
                Text(
                    "Contact us",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = category == "Bug", onClick = { category = "Bug" }, label = { Text("Bug") })
            FilterChip(selected = category == "Feedback", onClick = { category = "Feedback" }, label = { Text("Feedback") })
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = message,
            onValueChange = { message = it.take(2000) },
            label = { Text(if (category == "Bug") "What went wrong?" else "Your feedback") },
            minLines = 6,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        if (image == null) {
            OutlinedButton(
                onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Attach a screenshot (optional)")
            }
        } else {
            ShieldCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    thumb?.let {
                        Image(it, contentDescription = null, modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.small), contentScale = ContentScale.Crop)
                        Spacer(Modifier.width(12.dp))
                    }
                    Text("Screenshot attached", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { image = null }) { Icon(Icons.Default.Close, contentDescription = "Remove") }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        ShieldCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Included in your email", style = MaterialTheme.typography.titleSmall)
                Text(info, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "Your email app opens with this filled in. You can review or edit everything before sending.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(enabled = message.isNotBlank(), onClick = { send() }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text("Send")
        }
        Spacer(Modifier.height(24.dp))
    }
}