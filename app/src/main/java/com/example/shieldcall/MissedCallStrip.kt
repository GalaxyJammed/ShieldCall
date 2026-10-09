package com.example.shieldcall

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.Calendar

@Composable
fun MissedCallStrip(onSeeAll: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { SpamDb.get(context).dao() }
    val since = remember { startOfToday() }
    val countFlow = remember(since) { dao.missedCountSince(since) }
    val count by countFlow.collectAsState(initial = 0)
    if (count == 0) return

    ShieldCard(Modifier.fillMaxWidth().clickable(onClick = onSeeAll)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Missed Calls $count today",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "See missed calls")
        }
    }
}

private fun startOfToday(): Long {
    val c = Calendar.getInstance()
    c.set(Calendar.HOUR_OF_DAY, 0)
    c.set(Calendar.MINUTE, 0)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    return c.timeInMillis
}