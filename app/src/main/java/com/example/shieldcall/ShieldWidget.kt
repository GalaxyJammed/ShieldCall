package com.example.shieldcall

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.color.ColorProvider as ColorProviderFn
import androidx.glance.unit.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object WidgetActions {
    const val LOOKUP = "shieldcall.widget.LOOKUP"
    const val DIAL = "shieldcall.widget.DIAL"
    const val RECENTS = "shieldcall.widget.RECENTS"
    const val CONTACTS = "shieldcall.widget.CONTACTS"
    const val STATS = "shieldcall.widget.STATS"
}

object Widgets {
    fun refresh(context: Context) {
        val app = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            ShieldWidget().updateAll(app)
            ShieldStatsWidget().updateAll(app)
            FavContactsWidgetProvider.refreshAll(app)
        }
    }
}

class ShieldWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { WidgetContent() }
    }
}

class ShieldWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ShieldWidget()
}

internal fun widgetOpen(context: Context, action: String): Action = actionStartActivity(
    Intent(context, MainActivity::class.java)
        .setAction(action)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
)

@Composable
private fun WidgetContent() {
    val context = LocalContext.current

    val bg = ColorProviderFn(day = Color(0xFFF8F9FA), night = Color(0xFF1A1C1E))
    val textPrimary = ColorProviderFn(day = Color(0xFF1A1C1E), night = Color(0xFFE2E2E6))

    val btnPrimaryBg = ColorProviderFn(day = Color(0xFF0061A4), night = Color(0xFF9ECAFF))
    val btnPrimaryIcon = ColorProviderFn(day = Color(0xFFFFFFFF), night = Color(0xFF003258))

    val btnSecondaryBg = ColorProviderFn(day = Color(0xFFD1E4FF), night = Color(0xFF00497D))
    val btnSecondaryIcon = ColorProviderFn(day = Color(0xFF001D36), night = Color(0xFFD1E4FF))

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(bg)
            .cornerRadius(20.dp)
            .padding(10.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(R.drawable.widget_logo),
                contentDescription = "ShieldCall logo",
                modifier = GlanceModifier.size(26.dp)
            )
            Spacer(GlanceModifier.width(6.dp))
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

        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            WidgetIconButton(R.drawable.ic_widget_search, "Lookup", btnPrimaryBg, btnPrimaryIcon, widgetOpen(context, WidgetActions.LOOKUP))
            Spacer(GlanceModifier.width(6.dp))
            WidgetIconButton(R.drawable.ic_widget_call, "Dial", btnSecondaryBg, btnSecondaryIcon, widgetOpen(context, WidgetActions.DIAL))
        }

        Spacer(GlanceModifier.height(6.dp))

        Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
            WidgetIconButton(R.drawable.ic_widget_history, "Recent calls", btnPrimaryBg, btnPrimaryIcon, widgetOpen(context, WidgetActions.RECENTS))
            Spacer(GlanceModifier.width(6.dp))
            WidgetIconButton(R.drawable.ic_widget_contacts, "Contacts", btnSecondaryBg, btnSecondaryIcon, widgetOpen(context, WidgetActions.CONTACTS))
        }
    }
}

@Composable
private fun RowScope.WidgetIconButton(
    icon: Int,
    description: String,
    background: ColorProvider,
    iconColor: ColorProvider,
    action: Action
) {
    Box(
        modifier = GlanceModifier
            .defaultWeight()
            .fillMaxHeight()
            .background(background)
            .cornerRadius(12.dp)
            .clickable(action),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = description,
            modifier = GlanceModifier.size(20.dp),
            colorFilter = ColorFilter.tint(iconColor)
        )
    }
}
