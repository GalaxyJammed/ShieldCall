package com.example.shieldcall

import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

object Badges {
    fun level(count: Long): Int = when {
        count >= 150 -> 3
        count >= 50 -> 2
        count >= 10 -> 1
        else -> 0
    }

    fun label(level: Int): String? = when (level) {
        3 -> "Gold contributor"
        2 -> "Silver contributor"
        1 -> "Bronze contributor"
        else -> null
    }

    fun color(level: Int): Color = when (level) {
        3 -> Color(0xFFD4A017)
        2 -> Color(0xFFA7ADB5)
        1 -> Color(0xFFB8733A)
        else -> Color.Unspecified
    }

    /** Based on the local vote history, so it costs no Firebase reads. */
    fun levelFor(context: Context): Int =
        level(SpamDb.get(context).dao().identifiedTotal().toLong())
}

@Composable
fun ContributorBadge(level: Int) {
    val label = Badges.label(level) ?: return
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Badges.color(level)
    )
}