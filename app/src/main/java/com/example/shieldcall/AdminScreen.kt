package com.example.shieldcall

import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import kotlinx.coroutines.launch

private fun typeColor(type: String) = when (type) {
    "safe" -> Color(0xFF2E7D32)
    "spam" -> Color(0xFFF9A825)
    else -> Color(0xFFC62828)
}

@Composable
fun AdminScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var items by remember { mutableStateOf<List<Flagged>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var pending by remember { mutableStateOf<Pair<Flagged, String>?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        try {
            items = Reports.loadFlagged()
            error = null
        } catch (e: Exception) {
            Log.e("Shield", e.toString())
            error = e.message
        }
    }

    fun run(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
                reload++
            } catch (e: Exception) {
                Log.e("Shield", e.toString())
                Toast.makeText(context, "Action failed", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).scrollbar(listState),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            ShieldCard(Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
                    IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(28.dp))
                    }
                    Text(
                        "Moderation",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
        item {
            Text(
                "Reviews reported by users, most reported first. Reviews with 3 or more reports are already hidden from everyone else.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        error?.let {
            item { Text("Couldn't load reports: $it", color = MaterialTheme.colorScheme.error) }
        }
        val list = items
        if (list == null) {
            if (error == null) item { CircularProgressIndicator() }
        } else {
            if (list.isEmpty()) item { Text("No reported reviews.") }
            items(list, key = { "${it.tail}/${it.rid}" }) { f ->
                ShieldCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            f.type.uppercase() + " · reported ${f.flags}×",
                            color = typeColor(f.type),
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text("+${f.tail} · ${f.author}", style = MaterialTheme.typography.bodySmall)
                        if (f.text.isNotEmpty()) Text(f.text)
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { run { Reports.adminDismiss(f.tail, f.rid) } }) { Text("Dismiss") }
                            Button(
                                onClick = { pending = f to "delete" },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                            ) { Text("Delete") }
                            TextButton(
                                onClick = { pending = f to "ban" },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("Ban author") }
                        }
                    }
                }
            }
            item { DisputesSection() }
            item {
                ShieldCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Crashlytics test", style = MaterialTheme.typography.titleMedium)
                            Text("Crashes the app on purpose to confirm reports arrive", style = MaterialTheme.typography.bodySmall)
                        }
                        OutlinedButton(onClick = { throw RuntimeException("ShieldCall test crash") }) { Text("Crash") }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    pending?.let { (f, action) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(if (action == "ban") "Ban this author?" else "Delete this review?") },
            text = {
                Text(
                    if (action == "ban") "Their review is deleted and they can no longer vote or review."
                    else "The review is deleted permanently."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pending = null
                        run { if (action == "ban") Reports.adminBan(f.tail, f.rid) else Reports.adminDelete(f.tail, f.rid) }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(if (action == "ban") "Ban" else "Delete") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }
}