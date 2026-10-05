package com.example.shieldcall

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity

@Composable
fun MainTabs(tab: Int, onTab: (Int) -> Unit, onNumber: (String) -> Unit, onSettings: () -> Unit) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = tab,
            transitionSpec = {
                val dir = if (targetState > initialState) 1 else -1
                slideInHorizontally(tween(300)) { it * dir } togetherWith
                        slideOutHorizontally(tween(300)) { -it * dir }
            },
            modifier = Modifier.fillMaxSize(),
            label = "tabs"
        ) { t ->
            when (t) {
                0 -> LookupWithDialer(onNumber, onSettings)
                1 -> ContactsScreen(onNumber)
                2 -> StatsScreen()
                else -> PreferencesScreen()
            }
        }
        if (!UiState.hideBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .onSizeChanged { UiState.barHeight = with(density) { it.height.toDp() } }
            ) {
                BottomBar(tab, onTab)
            }
        }
    }
}

@Composable
fun ScreenTitle(text: String) {
    ShieldCard(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
            Text(text, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    val items = listOf(
        "Lookup" to Icons.Default.Search,
        "Contacts" to Icons.Default.Contacts,
        "Stats" to Icons.Default.BarChart,
        "Security" to Icons.Default.Tune
    )
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, if (Prefs.dark) Color.White.copy(alpha = 0.35f) else Color.Black.copy(alpha = 0.35f)),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                items.forEachIndexed { i, (label, icon) ->
                    BarItem(label, icon, i == selected, Modifier.weight(1f)) { onSelect(i) }
                }
            }
        }
    }
}

@Composable
private fun BarItem(label: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scale by animateFloatAsState(
        if (selected) 1.2f else 1f,
        spring(dampingRatio = Spring.DampingRatioLowBouncy),
        label = "scale"
    )
    val pill by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.2f) else Color.Transparent,
        label = "pill"
    )
    val tint by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tint"
    )
    val padding by animateDpAsState(if (selected) 12.dp else 4.dp, label = "padding")
    Column(
        modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(Modifier.background(pill, RoundedCornerShape(50)).padding(horizontal = padding, vertical = 6.dp)) {
            Icon(imageVector = icon, contentDescription = label, tint = tint, modifier = Modifier.scale(scale))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            color = tint
        )
    }
}