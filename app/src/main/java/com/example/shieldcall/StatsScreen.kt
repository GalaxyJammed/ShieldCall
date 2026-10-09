package com.example.shieldcall

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import com.google.i18n.phonenumbers.PhoneNumberUtil
import android.text.format.DateUtils
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight


private val AmberColor = Color(0xFFF9A825)
private val RedColor = Color(0xFFC62828)
private val GreenColor = Color(0xFF2E7D32)
private const val DAY = 24L * 60 * 60 * 1000

enum class GraphViewMode(val label: String) {
    DAILY("Today"),
    WEEKLY("7 Days"),
    MONTHLY("4 Weeks")
}

data class BarData(
    val label: String,
    val spam: Int,
    val scam: Int,
    val safe: Int
) {
    val total get() = spam + scam + safe
}

@Composable
fun StatsScreen(onNumber: (String) -> Unit = {}) {
    val context = LocalContext.current
    val dao = remember { SpamDb.get(context).dao() }

    val identifications by dao.identificationsFlow().collectAsState(initial = emptyList())
    val hangups by dao.hangupsFlow().collectAsState(initial = emptyList())

    val spamCount = remember(identifications) { identifications.count { it.type.lowercase() == "spam" } }
    val scamCount = remember(identifications) { identifications.count { it.type.lowercase() == "scam" } }
    val safeCount = remember(identifications) { identifications.count { it.type.lowercase() == "safe" } }
    val totalIdentified = identifications.size

    val totalSecondsSaved = remember(hangups) { hangups.sumOf { it.secondsSaved.toLong() } }
    val formattedTimeSaved = remember(totalSecondsSaved) { formatTimeSaved(totalSecondsSaved) }
    val hangupCount = hangups.size

    val now = remember { System.currentTimeMillis() }
    val monthHangups = remember(hangups) { hangups.filter { it.time >= now - 30 * DAY } }
    val byReason = remember(monthHangups) {
        monthHangups.groupingBy { it.reason.ifBlank { "Other" } }.eachCount().entries.sortedByDescending { it.value }
    }
    val insights = remember(hangups) { blockInsights(hangups) }
    val reviewsWritten = remember { Contribution.reviews(context) }
    val lookups = remember { LookupStats.total(context) }
    val lookupCountries = remember { LookupStats.countries(context) }

    var viewMode by remember { mutableStateOf(GraphViewMode.DAILY) }
    var showCountries by remember { mutableStateOf(false) }

    val allBars = remember(identifications) {
        GraphViewMode.entries.associateWith { calculateBarData(identifications, it) }
    }
    val barDataList = allBars[viewMode].orEmpty()

    var openNumber by remember { mutableStateOf<String?>(null) }

    fun shareStats() {
        val shareText = "🛡️ My ShieldCall Stats:\n" +
                "• $spamCount Spam calls identified\n" +
                "• $scamCount Scam calls identified\n" +
                "• $safeCount Safe callers identified\n" +
                "• ${monthHangups.size} unwanted calls blocked this month\n\n" +
                "Protect your phone from spam calls with ShieldCall: https://github.com/galaxyjammed/ShieldCall/releases"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Share your stats"))
    }

    val scroll = rememberScrollState()

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .scrollbar(scroll, UiState.bottomInset)
            .verticalScroll(scroll)
    ) {
        ShieldCard(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 8.dp)
            ) {
                Text(
                    "Stats",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.Center)
                )
                IconButton(
                    onClick = { shareStats() },
                    modifier = Modifier.align(Alignment.CenterEnd)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share Stats",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        if (totalIdentified == 0 && hangupCount == 0 && lookups == 0) {
            ShieldCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Nothing here yet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Your stats appear as you vote on numbers, check numbers and block unwanted calls.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        Section("Identified Calls")
        ShieldCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "$totalIdentified Calls Categorized",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("Spam", spamCount, AmberColor, Icons.Default.ReportProblem, Modifier.weight(1f).clickable { ListRequest.type = "spam" })
                    StatBox("Scam", scamCount, RedColor, Icons.Default.Gavel, Modifier.weight(1f).clickable { ListRequest.type = "scam" })
                    StatBox("Safe", safeCount, GreenColor, Icons.Default.CheckCircle, Modifier.weight(1f).clickable { ListRequest.type = "safe" })
                }
                if (totalIdentified > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap on a card to view the numbers",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

    Section("Blocked Call Insights")
    ShieldCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (hangupCount == 0) {
                Text("No blocked calls yet.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                insights.busiest?.let {
                    Column {
                        Text("Busiest time", style = MaterialTheme.typography.titleSmall)
                        Text("Most blocked calls arrive around $it", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (insights.countries.isNotEmpty()) {
                    Column {
                        Text("Where they come from", style = MaterialTheme.typography.titleSmall)
                        insights.countries.forEach { (label, n) ->
                            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                                Text(label, Modifier.weight(1f))
                                Text("$n", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (insights.repeat.isNotEmpty()) {
                    Column {
                        Text("Repeat callers (tap for details)", style = MaterialTheme.typography.titleSmall)
                        insights.repeat.forEach { (key, n) ->
                            Row(Modifier.fillMaxWidth().clickable { onNumber(key) }.padding(vertical = 6.dp)) {
                                Text("+$key", Modifier.weight(1f))
                                Text("$n calls", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                if (byReason.isNotEmpty()) {
                    Text(
                        "By reason: " + byReason.joinToString("  ·  ") { "${it.key} ${it.value}" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

        Spacer(Modifier.height(20.dp))

        Section("Estimated Time Saved")
        ShieldCard(Modifier.fillMaxWidth()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CallEnd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = formattedTimeSaved,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "About 15 seconds per blocked call ($hangupCount calls avoided)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Section("Your Activity")
        ShieldCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatBox("Votes", totalIdentified, MaterialTheme.colorScheme.primary, Icons.Default.HowToVote, Modifier.weight(1f))
                    StatBox("Reviews", reviewsWritten, MaterialTheme.colorScheme.primary, Icons.Default.EditNote, Modifier.weight(1f))
                    StatBox(
                        "Lookups",
                        lookups,
                        MaterialTheme.colorScheme.primary,
                        Icons.Default.Search,
                        Modifier.weight(1f).clickable(enabled = lookups > 0) { showCountries = true }
                    )
                }
                if (lookups > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Tap Lookups to see which countries you checked",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Section("Identification Activity")
        ShieldCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                ViewModeSelector(
                    selectedMode = viewMode,
                    onModeSelected = { viewMode = it }
                )

                Spacer(Modifier.height(12.dp))

                val peakInfo = remember(barDataList) {
                    val maxBar = barDataList.maxByOrNull { it.total }
                    if (maxBar != null && maxBar.total > 0) {
                        val typeInfo = when {
                            maxBar.spam >= maxBar.scam && maxBar.spam >= maxBar.safe -> "${maxBar.spam} Spam"
                            maxBar.scam >= maxBar.spam && maxBar.scam >= maxBar.safe -> "${maxBar.scam} Scam"
                            else -> "${maxBar.safe} Safe"
                        }
                        "Busiest: ${maxBar.label} (${maxBar.total} total, $typeInfo)"
                    } else {
                        "No identifications in this period"
                    }
                }

                Text(
                    text = peakInfo,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )

                Spacer(Modifier.height(16.dp))

                AnimatedContent(
                    targetState = viewMode,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "graph_anim"
                ) { targetMode ->
                    ActivityGraphCanvas(barDataList = allBars[targetMode].orEmpty())
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LegendItem("Spam", AmberColor)
                    LegendItem("Scam", RedColor)
                    LegendItem("Safe", GreenColor)
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }

    if (showCountries) {
        AlertDialog(
            onDismissRequest = { showCountries = false },
            title = { Text("Numbers checked by country", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    lookupCountries.entries.sortedByDescending { it.value }.forEach { (country, count) ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(country, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
                            Text(
                                "$count",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCountries = false }) { Text("Close") } }
        )
    }
}

@Composable
private fun ViewModeSelector(
    selectedMode: GraphViewMode,
    onModeSelected: (GraphViewMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GraphViewMode.entries.forEach { mode ->
                val selected = mode == selectedMode
                val backgroundColor = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.Transparent
                }
                val contentColor = if (selected) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(CircleShape)
                        .background(backgroundColor)
                        .clickable { onModeSelected(mode) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun StatBox(
    title: String,
    count: Int,
    accentColor: Color,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.12f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape)
        ) { }
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun ActivityGraphCanvas(barDataList: List<BarData>) {
    val maxTotal = remember(barDataList) {
        (barDataList.maxOfOrNull { it.total } ?: 1).coerceAtLeast(1)
    }

    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val labelHeight = 28.dp.toPx()
            val chartHeight = height - labelHeight

            val barCount = barDataList.size
            if (barCount == 0) return@Canvas

            val spacing = width / barCount

            val gridColor = Color.Gray.copy(alpha = 0.2f)
            for (i in 1..3) {
                val y = chartHeight * (1f - i / 3f)
                drawLine(
                    color = gridColor,
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            barDataList.forEachIndexed { index, bar ->
                val centerX = index * spacing + spacing / 2f
                val barWidth = (spacing * 0.45f).coerceIn(12.dp.toPx(), 32.dp.toPx())
                val left = centerX - barWidth / 2f

                if (bar.total > 0) {
                    val spamH = (bar.spam.toFloat() / maxTotal) * chartHeight
                    val scamH = (bar.scam.toFloat() / maxTotal) * chartHeight
                    val safeH = (bar.safe.toFloat() / maxTotal) * chartHeight

                    var currentY = chartHeight

                    if (safeH > 0f) {
                        currentY -= safeH
                        drawRoundRect(
                            color = GreenColor,
                            topLeft = Offset(left, currentY),
                            size = Size(barWidth, safeH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }

                    if (scamH > 0f) {
                        currentY -= scamH
                        drawRoundRect(
                            color = RedColor,
                            topLeft = Offset(left, currentY),
                            size = Size(barWidth, scamH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }

                    if (spamH > 0f) {
                        currentY -= spamH
                        drawRoundRect(
                            color = AmberColor,
                            topLeft = Offset(left, currentY),
                            size = Size(barWidth, spamH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }
                } else {
                    drawRoundRect(
                        color = Color.Gray.copy(alpha = 0.2f),
                        topLeft = Offset(left, chartHeight - 4.dp.toPx()),
                        size = Size(barWidth, 4.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                val textLayoutResult = textMeasurer.measure(bar.label, labelStyle)
                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(
                        x = centerX - textLayoutResult.size.width / 2f,
                        y = chartHeight + 6.dp.toPx()
                    )
                )
            }
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

private class Buckets(n: Int) {
    val spam = IntArray(n)
    val scam = IntArray(n)
    val safe = IntArray(n)

    fun add(index: Int, type: String) {
        when (type.lowercase()) {
            "spam" -> spam[index]++
            "scam" -> scam[index]++
            "safe" -> safe[index]++
        }
    }

    fun toBars(labels: List<String>) = labels.mapIndexed { i, l -> BarData(l, spam[i], scam[i], safe[i]) }
}

private fun calculateBarData(
    identifications: List<IdentificationEntry>,
    mode: GraphViewMode
): List<BarData> {
    val today = startOfToday()
    val daysAgo = { time: Long -> ((today + DAY - 1 - time) / DAY).toInt() }
    return when (mode) {
        GraphViewMode.DAILY -> {
            val b = Buckets(8)
            val cal = Calendar.getInstance()
            identifications.forEach {
                if (it.time >= today) {
                    cal.timeInMillis = it.time
                    b.add((cal.get(Calendar.HOUR_OF_DAY) / 3).coerceIn(0, 7), it.type)
                }
            }
            b.toBars(listOf("12A", "3A", "6A", "9A", "12P", "3P", "6P", "9P"))
        }

        GraphViewMode.WEEKLY -> {
            val b = Buckets(7)
            identifications.forEach {
                val ago = daysAgo(it.time)
                if (ago in 0..6) b.add(6 - ago, it.type)
            }
            val fmt = SimpleDateFormat("EEE", Locale.getDefault())
            b.toBars((0..6).map { i -> fmt.format(Date(today + DAY / 2 - (6 - i) * DAY)) })
        }

        GraphViewMode.MONTHLY -> {
            val b = Buckets(4)
            identifications.forEach {
                val ago = daysAgo(it.time)
                if (ago in 0..27) b.add(3 - ago / 7, it.type)
            }
            b.toBars(listOf("3w ago", "2w ago", "Last wk", "This wk"))
        }
    }
}

private fun formatTimeSaved(totalSeconds: Long): String {
    if (totalSeconds <= 0) return "0s"
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return buildString {
        if (hours > 0) append("${hours}h ")
        if (minutes > 0 || hours > 0) append("${minutes}m ")
        append("${seconds}s")
    }.trim()
}

@Preview(showBackground = true)
@Composable
fun StatsScreenPreview() {
    ShieldTheme(dark = false) {
        StatsScreen(onNumber = {})
    }
}

private data class BlockInsights(
    val busiest: String?,
    val countries: List<Pair<String, Int>>,
    val repeat: List<Pair<String, Int>>
)

private fun blockInsights(hangups: List<HangupEntry>): BlockInsights {
    val numbers = hangups.map { it.number }.filter { it.length >= 5 && it.all { c -> c.isDigit() } }
    val byHour = IntArray(24)
    val cal = Calendar.getInstance()
    hangups.forEach {
        cal.timeInMillis = it.time
        byHour[cal.get(Calendar.HOUR_OF_DAY)]++
    }
    val peak = byHour.indices.maxByOrNull { byHour[it] }?.takeIf { byHour[it] > 0 }
    val busiest = peak?.let { "%02d:00–%02d:00".format(it, (it + 1) % 24) }
    val util = PhoneNumberUtil.getInstance()
    val topCountries = numbers
        .mapNotNull { n ->
            try {
                util.getRegionCodeForNumber(util.parse("+$n", null))
            } catch (e: Exception) {
                null
            }
        }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.take(3)
        .map { (region, n) ->
            val c = countries.firstOrNull { it.region == region }
            (if (c != null) "${c.flag} ${c.name}" else region) to n
        }
    val repeat = numbers.groupingBy { it }.eachCount().filter { it.value >= 2 }
        .entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }
    return BlockInsights(busiest, topCountries, repeat)
}