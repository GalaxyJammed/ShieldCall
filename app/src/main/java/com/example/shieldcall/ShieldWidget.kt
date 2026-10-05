package com.example.shieldcall

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as ColorProviderFn
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WidgetActions {
    const val LOOKUP = "shieldcall.widget.LOOKUP"
    const val DIAL = "shieldcall.widget.DIAL"
}

class ShieldWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val week = withContext(Dispatchers.IO) {
            SpamDb.get(context).dao().hangupsSince(System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000)
        }
        provideContent { WidgetContent(week) }
    }
}

class ShieldWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShieldWidget()
}

private fun open(context: Context, action: String): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setAction(action)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
)

@Composable
private fun WidgetContent(blocked: Int) {
    val context = LocalContext.current

    val bg = ColorProviderFn(day = Color(0xFFF8F9FA), night = Color(0xFF1A1C1E))
    val textPrimary = ColorProviderFn(day = Color(0xFF1A1C1E), night = Color(0xFFE2E2E6))
    val textSecondary = ColorProviderFn(day = Color(0xFF44474E), night = Color(0xFFC4C6D0))
    val cardBg = ColorProviderFn(day = Color(0xFFEFF4FA), night = Color(0xFF232830))
    val accent = ColorProviderFn(day = Color(0xFF0061A4), night = Color(0xFF9ECAFF))

    val btnPrimaryBg = ColorProviderFn(day = Color(0xFF0061A4), night = Color(0xFF9ECAFF))
    val btnPrimaryText = ColorProviderFn(day = Color(0xFFFFFFFF), night = Color(0xFF003258))

    val btnSecondaryBg = ColorProviderFn(day = Color(0xFFD1E4FF), night = Color(0xFF00497D))
    val btnSecondaryText = ColorProviderFn(day = Color(0xFF001D36), night = Color(0xFFD1E4FF))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(20.dp)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // App Header
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ShieldCall",
                style = TextStyle(
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                ),
                maxLines = 1
            )
        }

        Spacer(GlanceModifier.height(6.dp))

        // Blocked Stats Card
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .background(cardBg)
                .cornerRadius(12.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$blocked",
                    style = TextStyle(
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    ),
                    maxLines = 1
                )
                Spacer(GlanceModifier.width(4.dp))
                Text(
                    text = "calls blocked",
                    style = TextStyle(
                        color = textSecondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    maxLines = 1
                )
            }
        }

        Spacer(GlanceModifier.height(8.dp))

        // Action Buttons Row
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetButton(
                label = "Lookup",
                background = btnPrimaryBg,
                textColor = btnPrimaryText,
                action = open(context, WidgetActions.LOOKUP)
            )
            Spacer(GlanceModifier.width(6.dp))
            WidgetButton(
                label = "Dial",
                background = btnSecondaryBg,
                textColor = btnSecondaryText,
                action = open(context, WidgetActions.DIAL)
            )
        }
    }
}

@Composable
private fun RowScope.WidgetButton(
    label: String,
    background: ColorProvider,
    textColor: ColorProvider,
    action: Action
) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .background(background)
            .cornerRadius(12.dp)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = TextStyle(
                color = textColor,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            ),
            maxLines = 1
        )
    }
}
