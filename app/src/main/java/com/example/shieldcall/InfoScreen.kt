package com.example.shieldcall

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

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
    var info by remember { mutableStateOf<Info?>(null) }
    var failed by remember { mutableStateOf(value = false) }
    var reload by remember { mutableIntStateOf(0) }
    var reviews by remember { mutableStateOf<List<Review>?>(null) }
    var text by remember { mutableStateOf("") }
    val context = LocalContext.current
    var signed by remember { mutableStateOf(Reports.signedIn()) }
    var listed by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(number) {
        Reports.recordProfileView(context)
        listed = SpamDb.get(context).dao().find(number)?.uppercase()
            ?: if (Skip.isSpam("+$number") == true) "SPAM" else null
    }
    val scope = rememberCoroutineScope()

    LaunchedEffect(number, reload) {
        try {
            info = Reports.load(number)
            failed = false
            if (reviews != null) reviews = Reports.loadReviews(number)
        } catch (_: Exception) {
            failed = true
        }
    }

    fun act(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                reload++
            } catch (_: Exception) {
                failed = true
            }
        }
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
        item {
            ShieldCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    val contactName by produceState<String?>(null, number) {
                        value = withContext(Dispatchers.IO) {
                            Reports.loadContactName(context, number)
                        }
                    }
                    Text(
                        contactName ?: "Unknown Contact",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    val clipboardManager = LocalClipboardManager.current
                    Text(
                        "+$number",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:+$number"))
                                context.startActivity(intent)
                            },
                            onLongClick = {
                                clipboardManager.setText(AnnotatedString("+$number"))
                                Toast.makeText(context, "Number copied", Toast.LENGTH_SHORT).show()
                            }
                        )
                    )
                }
            }
        }
        if (failed) item {
            Text("Couldn't reach the server. Check your connection.", color = MaterialTheme.colorScheme.error)
        }
        if (i == null) {
            if (!failed) item { CircularProgressIndicator() }
        } else {
            listed?.let { l ->
                item {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Reported on public lists", style = MaterialTheme.typography.labelLarge)
                            Text(l, style = MaterialTheme.typography.titleLarge, color = colorOf(l.lowercase()))
                        }
                    }
                }
            }
            item { TrustCard(i) }
            item {
                if (!signed) Button(
                    onClick = { scope.launch { if (Auth.signIn(context)) { signed = true; reload++ } } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Sign in with Google to vote or review") }
                else VoteRow(i.myVote) { type ->
                    act {
                        Reports.report(tail, type).await()
                        withContext(Dispatchers.IO) {
                            SpamDb.get(context).dao().addIdentification(
                                IdentificationEntry(number = tail, type = type, time = System.currentTimeMillis())
                            )
                        }
                    }
                }
            }
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
                        onClick = { act { Reports.review(tail, v, text.trim()); text = "" } },
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
                    colors = ButtonDefaults.buttonColors(containerColor = colorOf(t)),
                    modifier = Modifier.weight(1f)
                ) { Text(t.replaceFirstChar { it.uppercase() }) }
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