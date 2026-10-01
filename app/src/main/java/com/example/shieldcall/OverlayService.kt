package com.example.shieldcall

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.TextButton

class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val stateController = SavedStateRegistryController.create(this)
    private val handler = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = stateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        stateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val number = intent?.getStringExtra("number").orEmpty()
        val label = intent?.getStringExtra("label").orEmpty()
        removeView()
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent { OverlayCard(number, label, { stopSelf() }) { type ->
                val tail = Reports.key(number, Reports.region(this@OverlayService)) ?: return@OverlayCard
                SpamDb.get(this@OverlayService).dao().insert(SpamNumber(tail, type))
                Reports.report(tail, type)
                stopSelf()
            } }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }
        getSystemService(WindowManager::class.java).addView(composeView, params)
        view = composeView
        registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ stopSelf() }, 15000)
        return START_NOT_STICKY
    }

    private fun removeView() {
        view?.let { getSystemService(WindowManager::class.java).removeView(it) }
        view = null
    }

    override fun onDestroy() {
        removeView()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

@Composable
fun OverlayCard(number: String, label: String, onDismiss: () -> Unit, onReport: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.titleLarge)
            Text(number, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { onReport("spam") }) { Text("Spam") }
                Button(onClick = { onReport("scam") }) { Text("Scam") }
                TextButton(onClick = onDismiss) { Text("Close") }
            }
        }
    }
}