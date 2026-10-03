package com.example.shieldcall

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.provider.ContactsContract
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.compose.ui.platform.LocalContext

private val Green = Color(0xFF2E7D32)
private val Amber = Color(0xFFF9A825)
private val Red = Color(0xFFC62828)

private fun colorOf(type: String) = when (type) {
    "safe" -> Green
    "spam" -> Amber
    else -> Red
}

@Composable
fun InfoScreen(number: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val tail = number
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signed by remember { mutableStateOf(Reports.signedIn()) }
    var info by remember { mutableStateOf<Info?>(null) }
    var reviews by remember { mutableStateOf<List<Review>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var reload by remember { mutableIntStateOf(0) }
    var text by remember { mutableStateOf("") }

    var contactInfo by remember { mutableStateOf<ContactInfo?>(null) }


    LaunchedEffect(tail) {
        withContext(Dispatchers.IO) {
            contactInfo = Reports.loadContactInfo(context, tail)
        }
    }

    LaunchedEffect(tail, reload, signed) {
        if (!signed) return@LaunchedEffect
        try {
            info = Reports.load(tail)
            if (reviews != null) reviews = Reports.loadReviews(tail)
            failed = false
        } catch (e: Exception) {
            Log.e("Shield", e.toString())
            failed = true
        }
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                reload++
            } catch (e: Exception) {
                Log.e("Shield", e.toString())
                failed = true
            }
        }
    }

    val displayNum = if (number.startsWith("+")) number else "+$number"
    val cleanNum = remember(displayNum) { displayNum.filter { it.isDigit() || it == '+' } }
    var showCallDialog by remember { mutableStateOf(false) }

    val callPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            try {
                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNum"))
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e("Shield", "Failed to call", e)
                Toast.makeText(context, "Cannot place call", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Call permission required to place calls", Toast.LENGTH_SHORT).show()
        }
    }

    if (showCallDialog) {
        AlertDialog(
            onDismissRequest = { showCallDialog = false },
            title = { Text("ShieldCall Dialer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = contactInfo?.name?.takeIf { it.isNotBlank() } ?: "Unknown Number",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = displayNum,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Press call to place a call using ShieldCall.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showCallDialog = false
                        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                            try {
                                val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$cleanNum"))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Log.e("Shield", "Failed to call", e)
                                Toast.makeText(context, "Cannot place call", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Green)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhoneInTalk,
                        contentDescription = "Call",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Call")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCallDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val i = info
    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ShieldCard(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Text(
                        "Number Details",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = contactInfo?.name?.takeIf { it.isNotBlank() } ?: "Unknown Number",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = displayNum,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.combinedClickable(
                        onClick = {
                            showCallDialog = true
                        },
                        onLongClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Phone Number", displayNum))
                            Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                        }
                    )
                )
                if (!contactInfo?.location.isNullOrBlank()) {
                    Text(
                        text = contactInfo!!.location!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        if (failed) item {
            Text("Couldn't reach the server. Check your connection.", color = MaterialTheme.colorScheme.error)
        }
        if (!signed) {
            item {
                ShieldCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Sign in with Google to see community ratings, vote and leave reviews.")
                        Button(
                            onClick = { scope.launch { if (Auth.signIn(context)) signed = true } },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Sign in with Google") }
                    }
                }
            }
        } else if (i == null) {
            if (!failed) item { CircularProgressIndicator() }
        } else {
            item { TrustCard(i) }
            item { VoteRow(i.myVote) { type -> act { Reports.report(context, tail, type) } } }
            item {
                val v = i.myVote
                if (v == null) Text("Vote above to leave a review.")
                else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it.take(300) },
                        label = { Text("Your experience") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { act { Reports.review(context, tail, v, text.trim()); text = "" } },
                        enabled = text.isNotBlank()
                    ) { Text("Post review") }
                }
            }
            val r = reviews
            if (r == null) item {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            try {
                                reviews = Reports.loadReviews(tail)
                            } catch (e: Exception) {
                                Log.e("Shield", e.toString())
                                failed = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Show reviews") }
            } else {
                item { Text("Reviews", style = MaterialTheme.typography.titleMedium) }
                if (r.isEmpty()) item { Text("No reviews yet.") }
                items(r) { ReviewCard(it) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun TrustCard(i: Info) {
    val total = i.spam + i.scam + i.safe
    ShieldCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (total == 0L) {
                Text("No reports yet", style = MaterialTheme.typography.titleMedium)
                Text("Be the first to share your experience with this number.")
            } else {
                val score = ((i.safe * 100) / total).toInt()
                Text(
                    "Trust score: $score%",
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (score >= 60) Green else if (score >= 30) Amber else Red
                )
                Text("Based on $total votes")
                Bar("Safe", i.safe, total, Green)
                Bar("Spam", i.spam, total, Amber)
                Bar("Scam", i.scam, total, Red)
            }
        }
    }
}

@Composable
private fun Bar(label: String, n: Long, total: Long, color: Color) {
    Column {
        Row {
            Text(label, Modifier.weight(1f))
            Text(n.toString())
        }
        LinearProgressIndicator(
            progress = { n.toFloat() / total },
            color = color,
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )
    }
}

@Composable
private fun VoteRow(mine: String?, onVote: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(if (mine != null) "You voted: ${mine.uppercase()}" else "How was your experience with this number?")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("safe", "spam", "scam").forEach { t ->
                Button(
                    onClick = { onVote(t) },
                    enabled = mine == null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colorOf(t),
                        disabledContainerColor = colorOf(t).copy(alpha = if (mine == t) 1f else 0.3f)
                    ),
                    modifier = Modifier.weight(1f)
                ) { Text(if (mine == t) "✓ ${t.replaceFirstChar { it.uppercase() }}" else t.replaceFirstChar { it.uppercase() }) }
            }
        }
    }
}

@Composable
private fun ReviewCard(r: Review) {
    ShieldCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                r.type.uppercase() + if (r.mine) " · You" else "",
                color = colorOf(r.type),
                style = MaterialTheme.typography.labelLarge
            )
            if (r.text.isNotEmpty()) Text(r.text)
        }
    }
}