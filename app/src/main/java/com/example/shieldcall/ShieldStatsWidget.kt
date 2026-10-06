package com.example.shieldcall

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider as ColorProviderFn
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private data class StatsData(
    val spam: Int,
    val scam: Int,
    val safe: Int,
    val votes: Int,
    val reviews: Int,
    val lookups: Int
)

class ShieldStatsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = withContext(Dispatchers.IO) {
            val dao = SpamDb.get(context).dao()
            StatsData(
                dao.identifiedCount("spam"),
                dao.identifiedCount("scam"),
                dao.identifiedCount("safe"),
                dao.identifiedTotal(),
                Contribution.reviews(context),
                LookupStats.total(context)
            )
        }
        provideContent { StatsContent(data) }
    }
}

class ShieldStatsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShieldStatsWidget()
}

@Composable
private fun StatsContent(d: StatsData) {
    val context = LocalContext.current

    val bg = ColorProviderFn(day = Color(0xFFF8F9FA), night = Color(0xFF1A1C1E))
    val textPrimary = ColorProviderFn(day = Color(0xFF1A1C1E), night = Color(0xFFE2E2E6))
    val textSecondary = ColorProviderFn(day = Color(0xFF44474E), night = Color(0xFFC4C6D0))
    val cardBg = ColorProviderFn(day = Color(0xFFEFF4FA), night = Color(0xFF232830))
    val accent = ColorProviderFn(day = Color(0xFF0061A4), night = Color(0xFF9ECAFF))
    val amber = ColorProviderFn(day = Color(0xFFC77800), night = Color(0xFFFFB74D))
    val red = ColorProviderFn(day = Color(0xFFC62828), night = Color(0xFFEF9A9A))
    val green = ColorProviderFn(day = Color(0xFF2E7D32), night = Color(0xFFA5D6A7))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(20.dp)
            .padding(12.dp)
            .clickable(widgetOpen(context, WidgetActions.STATS)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_logo),
                contentDescription = "ShieldCall logo",
                modifier = GlanceModifier.size(28.dp)
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = "ShieldCall",
                style = TextStyle(color = textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp),
                maxLines = 1
            )
            Spacer(GlanceModifier.width(6.dp))
            Text(
                text = "Stats",
                style = TextStyle(color = textSecondary, fontSize = 13.sp),
                maxLines = 1
            )
        }

        Spacer(GlanceModifier.height(6.dp))

        Text(
            text = "Identified calls",
            modifier = GlanceModifier.padding(start = 2.dp),
            style = TextStyle(color = textSecondary, fontSize = 11.sp),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(3.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            StatCell(d.spam, "Spam", amber, cardBg, textSecondary)
            Spacer(GlanceModifier.width(6.dp))
            StatCell(d.scam, "Scam", red, cardBg, textSecondary)
            Spacer(GlanceModifier.width(6.dp))
            StatCell(d.safe, "Safe", green, cardBg, textSecondary)
        }

        Spacer(GlanceModifier.height(8.dp))

        Text(
            text = "Your activity",
            modifier = GlanceModifier.padding(start = 2.dp),
            style = TextStyle(color = textSecondary, fontSize = 11.sp),
            maxLines = 1
        )
        Spacer(GlanceModifier.height(3.dp))
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            StatCell(d.votes, "Votes", accent, cardBg, textSecondary)
            Spacer(GlanceModifier.width(6.dp))
            StatCell(d.reviews, "Reviews", accent, cardBg, textSecondary)
            Spacer(GlanceModifier.width(6.dp))
            StatCell(d.lookups, "Lookups", accent, cardBg, textSecondary)
        }
    }
}

@Composable
private fun RowScope.StatCell(
    value: Int,
    label: String,
    valueColor: ColorProvider,
    cardBg: ColorProvider,
    labelColor: ColorProvider
) {
    Column(
        modifier = GlanceModifier
            .defaultWeight()
            .background(cardBg)
            .cornerRadius(12.dp)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "$value",
            style = TextStyle(color = valueColor, fontWeight = FontWeight.Bold, fontSize = 18.sp),
            maxLines = 1
        )
        Text(
            text = label,
            style = TextStyle(color = labelColor, fontWeight = FontWeight.Medium, fontSize = 11.sp),
            maxLines = 1
        )
    }
}
