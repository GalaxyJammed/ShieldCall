package com.example.shieldcall

import android.Manifest
import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private val InGreen = Color(0xFF2E7D32)
private val OutRed = Color(0xFFC62828)
private val Amber = Color(0xFFF9A825)

private data class CallGroup(
    val ids: List<Long>,
    val key: String,
    val name: String,
    val status: String,
    val time: Long,
    val duration: Int,
    val count: Int
)

private fun group(calls: List<CallEntry>): List<CallGroup> {
    val out = ArrayList<CallGroup>()
    for (c in calls) {
        val last = out.lastOrNull()
        if (last != null && last.key == c.number && last.status == c.status) {
            out[out.size - 1] = last.copy(ids = last.ids + c.id, count = last.count + 1)
        } else {
            out.add(CallGroup(listOf(c.id), c.number, c.name, c.status, c.time, c.duration, 1))
        }
    }
    return out
}

@Composable
fun RecentCalls(modifier: Modifier, onLookup: (String) -> Unit) {
    val context = LocalContext.current
    val dao = remember { SpamDb.get(context).dao() }
    val calls by dao.allCallsFlow().collectAsState(emptyList())
    val blocked by dao.blockedFlow().collectAsState(emptyList())
    val blockedKeys = remember(blocked) { blocked.map { it.number }.toSet() }
    var filter by rememberSaveable { mutableStateOf("All") }
    var confirmClear by remember { mutableStateOf(false) }
    var pendingCall by remember { mutableStateOf<String?>(null) }
    val guarded = rememberGuardedCall { SimChoice.placeNumber(context, it) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) pendingCall?.let { guarded(it) }
        pendingCall = null
    }

    fun call(key: String) {
        val number = "+$key"
        if (context.checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            guarded(number)
        } else {
            pendingCall = number
            launcher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    val groups = remember(calls, filter) { group(calls.filter { filter == "All" || it.status == filter }) }
    val listState = rememberLazyListState()

    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("All", "Incoming", "Outgoing", "Missed", "Blocked").forEach { f ->
                FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Swipe right to call, left to delete",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f).padding(start = 4.dp)
            )
            if (calls.isNotEmpty()) TextButton(onClick = { confirmClear = true }) { Text("Clear") }
        }
        if (groups.isEmpty()) {
            Text(if (filter == "All") "No calls yet." else "No ${filter.lowercase()} calls.", Modifier.padding(top = 8.dp))
        } else {
            LazyColumn(
                Modifier.scrollbar(listState, bottomInset = UiState.bottomInset),
                state = listState,
                contentPadding = PaddingValues(bottom = UiState.bottomInset)
            ) {
                items(groups, key = { it.ids.first() }) { g ->
                    val isBlocked = g.key in blockedKeys
                    CallRow(
                        g = g,
                        blocked = isBlocked,
                        onOpen = { onLookup(g.key) },
                        onCall = { call(g.key) },
                        onDelete = { dao.deleteCalls(g.ids) },
                        onBlock = { if (isBlocked) dao.unblock(g.key) else dao.block(BlockedNumber(g.key, g.name)) }
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear call history?") },
            text = { Text("This removes every call from ShieldCall's history. It can't be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        dao.clearCalls()
                        confirmClear = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CallRow(
    g: CallGroup,
    blocked: Boolean,
    onOpen: () -> Unit,
    onCall: () -> Unit,
    onDelete: () -> Unit,
    onBlock: () -> Unit
) {
    val callNow by rememberUpdatedState(onCall)
    val deleteNow by rememberUpdatedState(onDelete)
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> {
                    callNow()
                    false
                }
                SwipeToDismissBoxValue.EndToStart -> {
                    deleteNow()
                    true
                }
                else -> false
            }
        }
    )
    val (icon, tint) = callStatusStyle(g.status, MaterialTheme.colorScheme.onSurfaceVariant)
    val title = g.name.ifBlank { "+${g.key}" } + if (g.count > 1) " ×${g.count}" else ""
    val subtitle = buildString {
        append(g.status)
        if (g.duration > 0 && g.count == 1) append(" · %d:%02d".format(g.duration / 60, g.duration % 60))
        append(" · ")
        append(DateUtils.getRelativeTimeSpanString(g.time))
    }

    SwipeToDismissBox(
        state = state,
        backgroundContent = {
            val direction = state.dismissDirection
            Box(
                Modifier.fillMaxSize()
                    .background(
                        when (direction) {
                            SwipeToDismissBoxValue.StartToEnd -> InGreen
                            SwipeToDismissBoxValue.EndToStart -> OutRed
                            else -> Color.Transparent
                        }
                    )
                    .padding(horizontal = 20.dp),
                contentAlignment = if (direction == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                when (direction) {
                    SwipeToDismissBoxValue.StartToEnd -> Icon(Icons.Default.Call, contentDescription = "Call", tint = Color.White)
                    SwipeToDismissBoxValue.EndToStart -> Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.White)
                    else -> {}
                }
            }
        }
    ) {
        Row(
            Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.background)
                .clickable(onClick = onOpen)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CallAvatar(photoUri = null, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (g.status == "Missed") OutRed else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            IconButton(onClick = onBlock) {
                Icon(
                    Icons.Default.Block,
                    contentDescription = "Block",
                    tint = if (blocked) OutRed else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

internal fun callStatusStyle(status: String, neutral: Color): Pair<ImageVector, Color> = when (status) {
    "Outgoing" -> Icons.AutoMirrored.Filled.CallMade to OutRed
    "Missed" -> Icons.AutoMirrored.Filled.CallMissed to OutRed
    "Declined" -> Icons.AutoMirrored.Filled.CallReceived to Amber
    "Blocked" -> Icons.Default.Block to neutral
    else -> Icons.AutoMirrored.Filled.CallReceived to InGreen
}