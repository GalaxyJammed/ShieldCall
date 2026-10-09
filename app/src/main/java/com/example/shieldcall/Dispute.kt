package com.example.shieldcall

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

object Disputes {
    private val db get() = FirebaseFirestore.getInstance()

    /** One dispute per user per number. Stored under reports/{number}/disputes/{uid}. */
    suspend fun send(tail: String, reason: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: throw IllegalStateException("Not signed in")
        db.collection("reports").document(tail).collection("disputes").document(uid)
            .set(mapOf("reason" to reason, "time" to FieldValue.serverTimestamp()))
            .await()
    }

    /** Admin only. Returns (number, reporter uid, reason). */
    suspend fun load(): List<Triple<String, String, String>> =
        db.collectionGroup("disputes").limit(30).get().await().documents.map {
            Triple(it.reference.parent.parent?.id.orEmpty(), it.id, it.getString("reason").orEmpty())
        }

    suspend fun dismiss(tail: String, uid: String) {
        db.collection("reports").document(tail).collection("disputes").document(uid).delete().await()
    }
}

@Composable
fun DisputeButton(tail: String) {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) { Text("Report a wrong label") }
    if (open) DisputeDialog(tail) { open = false }
}

@Composable
private fun DisputeDialog(tail: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reason by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    val reasons = listOf("Wrongly marked as spam", "Wrongly marked as scam", "Wrong place type", "Other")

    AlertDialog(
        onDismissRequest = { if (!sending) onDone() },
        title = { Text("Report a wrong label") },
        text = {
            Column {
                Text("Reports go to the ShieldCall team to review. Nothing changes automatically.")
                Spacer(Modifier.height(8.dp))
                reasons.forEach { r ->
                    Row(
                        Modifier.fillMaxWidth().clickable { reason = r }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = reason == r, onClick = { reason = r })
                        Spacer(Modifier.width(8.dp))
                        Text(r)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = reason != null && !sending,
                onClick = {
                    sending = true
                    scope.launch {
                        val chosen = reason ?: return@launch
                        try {
                            Disputes.send(tail, chosen)
                            Toast.makeText(context, "Thanks, we'll review it", Toast.LENGTH_SHORT).show()
                        } catch (e: FirebaseFirestoreException) {
                            val msg = if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED)
                                "You've already reported this number"
                            else "Couldn't send. Check your connection"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Couldn't send. Check your connection", Toast.LENGTH_SHORT).show()
                        }
                        sending = false
                        onDone()
                    }
                }
            ) { Text("Send") }
        },
        dismissButton = { TextButton(enabled = !sending, onClick = onDone) { Text("Cancel") } }
    )
}

@Composable
fun DisputesSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<Triple<String, String, String>>?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(reload) {
        rows = try {
            Disputes.load()
        } catch (e: Exception) {
            emptyList()
        }
    }

    ShieldCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Wrong-label reports", style = MaterialTheme.typography.titleMedium)
            val list = rows
            when {
                list == null -> CircularProgressIndicator()
                list.isEmpty() -> Text("No reports.")
                else -> list.forEach { (tail, uid, reason) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("+$tail", style = MaterialTheme.typography.titleSmall)
                            Text(reason, style = MaterialTheme.typography.bodySmall)
                        }
                        TextButton(onClick = {
                            scope.launch {
                                try {
                                    Disputes.dismiss(tail, uid)
                                    reload++
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Couldn't dismiss", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }) { Text("Dismiss") }
                    }
                }
            }
        }
    }
}