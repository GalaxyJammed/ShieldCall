package com.example.shieldcall

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

fun BlockedNumber.kind() = when (type) {
    "country" -> "countries"
    "name" -> "names"
    "prefix" -> "prefixes"
    else -> "numbers"
}

@Composable
fun BlockLink(title: String, count: Int?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (count != null) "$title ($count)" else title,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f)
        )
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
    }
}

@Composable
fun BlockListPage(kind: String, dao: SpamDao, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val blocked by dao.blockedFlow().collectAsState(emptyList())
    val calls by dao.blockedCallsFlow().collectAsState(emptyList())
    val blockedKeys = remember(blocked) { blocked.map { it.number }.toSet() }
    val title = when (kind) {
        "numbers" -> "Blocked Numbers"
        "countries" -> "Blocked Countries"
        "names" -> "Blocked Names"
        "prefixes" -> "Blocked Prefixes"
        else -> "Blocked Calls"
    }

    val scroll = rememberScrollState()
    Column(Modifier.fillMaxSize().scrollbar(scroll).verticalScroll(scroll).padding(horizontal = 24.dp)) {
        ShieldCard(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp)) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(28.dp))
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        if (kind == "log") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Calls ShieldCall rejected for you.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                if (calls.isNotEmpty()) TextButton(onClick = { dao.clearBlockedCalls() }) { Text("Clear") }
            }
            Spacer(Modifier.height(8.dp))
            ShieldCard(Modifier.fillMaxWidth()) {
                if (calls.isEmpty()) Text("No blocked calls yet.", Modifier.padding(16.dp))
                calls.forEachIndexed { i, c ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.name.ifBlank { "+${c.number}" }, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "+${c.number} · ${DateUtils.getRelativeTimeSpanString(c.time)}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (c.number !in blockedKeys) {
                                Text(
                                    "Blocked by a rule",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        if (c.number in blockedKeys) TextButton(onClick = { dao.unblock(c.number) }) { Text("Unblock") }
                    }
                }
            }
        } else {
            val items = blocked.filter { it.kind() == kind }
            ShieldCard(Modifier.fillMaxWidth()) {
                if (items.isEmpty()) Text("Nothing blocked here.", Modifier.padding(16.dp))
                items.forEachIndexed { i, b ->
                    if (i > 0) HorizontalDivider()
                    Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(b.name.ifBlank { if (kind == "numbers") "+${b.number}" else b.number }, style = MaterialTheme.typography.titleMedium)
                            if (kind == "numbers" && b.name.isNotBlank() && b.name != "+${b.number}") {
                                Text("+${b.number}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        TextButton(onClick = { dao.unblock(b.number) }) { Text("Unblock") }
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}