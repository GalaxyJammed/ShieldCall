package com.example.shieldcall

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import java.util.Calendar

private val AmberColor = Color(0xFFF9A825)
private val RedColor = Color(0xFFC62828)
private val GreenColor = Color(0xFF2E7D32)

enum class GraphViewMode(val label: String) {
    DAILY("Daily View"),
    WEEKLY("Weekly View"),
    MONTHLY("Monthly View")
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
fun StatsScreen() {
    val context = LocalContext.current
    val dao = remember { SpamDb.get(context).dao() }

    val identifications by dao.identificationsFlow().collectAsState(initial = emptyList())
    val hangups by dao.hangupsFlow().collectAsState(initial = emptyList())

    val spamCount = remember(identifications) { identifications.count { it.type.lowercase() == "spam" } }
    val scamCount = remember(identifications) { identifications.count { it.type.lowercase() == "scam" } }
    val safeCount = remember(identifications) { identifications.count { it.type.lowercase() == "safe" } }
    val totalIdentified = remember(identifications) { identifications.size }

    val totalSecondsSaved = remember(hangups) { hangups.sumOf { it.secondsSaved.toLong() } }
    val formattedTimeSaved = remember(totalSecondsSaved) { formatTimeSaved(totalSecondsSaved) }
    val hangupCount = remember(hangups) { hangups.size }

    var viewMode by remember { mutableStateOf(GraphViewMode.DAILY) }
    var userAnalytics by remember { mutableStateOf(UserAnalytics()) }
    var selectedDetailType by remember { mutableStateOf<String?>(null) } // "views" or "searches"

    LaunchedEffect(Unit) {
        userAnalytics = Reports.loadUserAnalytics()
    }

    fun shareStats() {
        val shareText = "🛡️ My ShieldCall Stats:\n" +
                "• $spamCount Spam calls identified\n" +
                "• $scamCount Scam calls identified\n" +
                "• $safeCount Safe callers identified\n" +
                "• $formattedTimeSaved saved from instant hangups!\n\n" +
                "Protect your phone from spam calls with ShieldCall: https://play.google.com/store/apps/details?id=${context.packageName}"

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        context.startActivity(Intent.createChooser(intent, "Share your stats"))
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
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

        Section("Identified Calls")
        ShieldCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "$totalIdentified Calls Categorized",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatBox("Spam", spamCount, AmberColor, Icons.Default.ReportProblem, Modifier.weight(1f))
                    StatBox("Scam", scamCount, RedColor, Icons.Default.Gavel, Modifier.weight(1f))
                    StatBox("Safe", safeCount, GreenColor, Icons.Default.CheckCircle, Modifier.weight(1f))
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Section("Instant Hang up Savings")
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
                        text = "Saved from Instant Hang up ($hangupCount calls avoided)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Section("Profile & Search Analytics")
        ShieldCard(Modifier.fillMaxWidth()) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedDetailType = "views" }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Who viewed my profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${userAnalytics.profileViews} profile views recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { selectedDetailType = "views" }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View Countries Detail",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                HorizontalDivider()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { selectedDetailType = "searches" }
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.15f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Who searched for me", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text("${userAnalytics.profileSearches} lookups recorded", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { selectedDetailType = "searches" }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View Countries Detail",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        Section("Identification Activity")
        ShieldCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                // Top controls with arrows for switching graph view modes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = {
                        val modes = GraphViewMode.entries
                        val prevIndex = (viewMode.ordinal - 1 + modes.size) % modes.size
                        viewMode = modes[prevIndex]
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Previous Mode"
                        )
                    }

                    Text(
                        text = viewMode.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    IconButton(onClick = {
                        val modes = GraphViewMode.entries
                        val nextIndex = (viewMode.ordinal + 1) % modes.size
                        viewMode = modes[nextIndex]
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Next Mode"
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    GraphViewMode.entries.forEach { mode ->
                        val selected = mode == viewMode
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .background(
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp)
                                )
                                .clickable { viewMode = mode }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = when (mode) {
                                    GraphViewMode.DAILY -> "Daily"
                                    GraphViewMode.WEEKLY -> "Weekly"
                                    GraphViewMode.MONTHLY -> "Monthly"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                val barDataList = remember(identifications, viewMode) {
                    calculateBarData(identifications, viewMode)
                }

                val peakInfo = remember(barDataList, viewMode) {
                    val maxBar = barDataList.maxByOrNull { it.total }
                    if (maxBar != null && maxBar.total > 0) {
                        val typeInfo = when {
                            maxBar.spam >= maxBar.scam && maxBar.spam >= maxBar.safe -> "${maxBar.spam} Spam"
                            maxBar.scam >= maxBar.spam && maxBar.scam >= maxBar.safe -> "${maxBar.scam} Scam"
                            else -> "${maxBar.safe} Safe"
                        }
                        "Peak identification: ${maxBar.label} (${maxBar.total} total, $typeInfo)"
                    } else {
                        "No identifications recorded in this view"
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
                    ActivityGraphCanvas(barDataList = calculateBarData(identifications, targetMode))
                }

                Spacer(Modifier.height(16.dp))

                // Chart Legend
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

        Spacer(Modifier.height(24.dp))
    }

    if (selectedDetailType != null) {
        val isViews = selectedDetailType == "views"
        val title = if (isViews) "Profile Views by Country" else "Searches by Country"
        val countryMap = if (isViews) userAnalytics.viewCountries else userAnalytics.searchCountries

        AlertDialog(
            onDismissRequest = { selectedDetailType = null },
            title = {
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            },
            text = {
                if (countryMap.isEmpty()) {
                    Text("No country breakdown data recorded yet.")
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        countryMap.entries.sortedByDescending { it.value }.forEach { (country, count) ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = country,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "$count ${if (isViews) "Views" else "Searches"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedDetailType = null }) {
                    Text("Close")
                }
            }
        )
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
            modifier = Modifier.padding(12.dp),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant
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

            // Draw grid lines
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

                    // Safe segment (bottom)
                    if (safeH > 0f) {
                        currentY -= safeH
                        drawRoundRect(
                            color = GreenColor,
                            topLeft = Offset(left, currentY),
                            size = Size(barWidth, safeH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }

                    // Scam segment (middle)
                    if (scamH > 0f) {
                        currentY -= scamH
                        drawRoundRect(
                            color = RedColor,
                            topLeft = Offset(left, currentY),
                            size = Size(barWidth, scamH),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }

                    // Spam segment (top)
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
                    // Empty bar placeholder line
                    drawRoundRect(
                        color = Color.Gray.copy(alpha = 0.2f),
                        topLeft = Offset(left, chartHeight - 4.dp.toPx()),
                        size = Size(barWidth, 4.dp.toPx()),
                        cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                    )
                }

                // Draw X-axis text label centered directly at centerX
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

private fun calculateBarData(
    identifications: List<IdentificationEntry>,
    mode: GraphViewMode
): List<BarData> {
    return when (mode) {
        GraphViewMode.DAILY -> {
            val labels = listOf("12A", "3A", "6A", "9A", "12P", "3P", "6P", "9P")
            val spamCounts = IntArray(8)
            val scamCounts = IntArray(8)
            val safeCounts = IntArray(8)

            val cal = Calendar.getInstance()
            identifications.forEach { entry ->
                cal.timeInMillis = entry.time
                val hour = cal.get(Calendar.HOUR_OF_DAY)
                val slot = (hour / 3).coerceIn(0, 7)
                when (entry.type.lowercase()) {
                    "spam" -> spamCounts[slot]++
                    "scam" -> scamCounts[slot]++
                    "safe" -> safeCounts[slot]++
                }
            }

            labels.mapIndexed { i, label ->
                BarData(label, spamCounts[i], scamCounts[i], safeCounts[i])
            }
        }

        GraphViewMode.WEEKLY -> {
            val labels = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
            val spamCounts = IntArray(7)
            val scamCounts = IntArray(7)
            val safeCounts = IntArray(7)

            val cal = Calendar.getInstance()
            identifications.forEach { entry ->
                cal.timeInMillis = entry.time
                val day = cal.get(Calendar.DAY_OF_WEEK)
                val index = when (day) {
                    Calendar.MONDAY -> 0
                    Calendar.TUESDAY -> 1
                    Calendar.WEDNESDAY -> 2
                    Calendar.THURSDAY -> 3
                    Calendar.FRIDAY -> 4
                    Calendar.SATURDAY -> 5
                    Calendar.SUNDAY -> 6
                    else -> 0
                }
                when (entry.type.lowercase()) {
                    "spam" -> spamCounts[index]++
                    "scam" -> scamCounts[index]++
                    "safe" -> safeCounts[index]++
                }
            }

            labels.mapIndexed { i, label ->
                BarData(label, spamCounts[i], scamCounts[i], safeCounts[i])
            }
        }

        GraphViewMode.MONTHLY -> {
            val labels = listOf("Wk 1", "Wk 2", "Wk 3", "Wk 4")
            val spamCounts = IntArray(4)
            val scamCounts = IntArray(4)
            val safeCounts = IntArray(4)

            val cal = Calendar.getInstance()
            identifications.forEach { entry ->
                cal.timeInMillis = entry.time
                val week = cal.get(Calendar.WEEK_OF_MONTH) - 1
                val index = week.coerceIn(0, 3)
                when (entry.type.lowercase()) {
                    "spam" -> spamCounts[index]++
                    "scam" -> scamCounts[index]++
                    "safe" -> safeCounts[index]++
                }
            }

            labels.mapIndexed { i, label ->
                BarData(label, spamCounts[i], scamCounts[i], safeCounts[i])
            }
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