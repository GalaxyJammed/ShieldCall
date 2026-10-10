package com.example.shieldcall

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.glance.appwidget.GlanceAppWidgetManager
import android.os.Handler
import android.os.Looper

class FavWidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.load(this)
        val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            ShieldTheme(Prefs.dark) {
                androidx.compose.material3.Surface(Modifier.fillMaxSize()) {
                    FavPicker(
                        initial = FavWidget.keys(this@FavWidgetConfigActivity, widgetId),
                        onSave = { keys ->
                            FavWidget.save(this@FavWidgetConfigActivity, widgetId, keys)
                            lifecycleScope.launch {
                                withContext(Dispatchers.IO) {
                                    FavContactsWidgetProvider.render(
                                        applicationContext,
                                        AppWidgetManager.getInstance(applicationContext),
                                        widgetId
                                    )
                                }
                                setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
                                finish()
                            }
                        },
                        onCancel = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun FavPicker(initial: List<String>, onSave: (List<String>) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val contacts by produceState<List<ContactItem>?>(null) {
        value = withContext(Dispatchers.IO) { loadContacts(context) }
    }
    var chosen by remember { mutableStateOf(initial) }
    var query by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Favorite contacts", style = MaterialTheme.typography.headlineSmall)
        Text(
            "${chosen.size} of 6 chosen. Shown on the widget in the order you pick them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search contacts") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        val list = contacts
        if (list == null) {
            CircularProgressIndicator()
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(list.filter { it.name.contains(query, true) || it.raw.contains(query) }, key = { it.key }) { c ->
                    val on = c.key in chosen
                    val full = !on && chosen.size >= 6
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !full) { chosen = if (on) chosen - c.key else chosen + c.key }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CallAvatar(photoUri = c.photo, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c.name.ifBlank { c.raw }, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                            Text(
                                c.raw,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Checkbox(checked = on, onCheckedChange = null, enabled = !full)
                    }
                    HorizontalDivider()
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onCancel) { Text("Cancel") }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = !saving,
                onClick = {
                    saving = true
                    onSave(chosen)
                }
            ) { Text("Save") }
        }
    }
}