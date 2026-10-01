package com.example.shieldcall

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        Prefs.load(this)
        val number = intent?.getStringExtra("number").orEmpty()
        val contactName = intent?.getStringExtra("contactName")
        removeView()
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                ShieldTheme(Prefs.dark) {
                    OverlayCard(
                        number = number,
                        contactName = contactName,
                        onDismiss = { stopSelf() },
                        onReport = { type ->
                            val tail = Reports.key(number, Reports.region(this@OverlayService)) ?: number
                            val dao = SpamDb.get(this@OverlayService).dao()
                            dao.insert(SpamNumber(tail, type))
                            dao.addIdentification(IdentificationEntry(number = tail, type = type, time = System.currentTimeMillis()))
                            Reports.report(tail, type)
                            stopSelf()
                        }
                    )
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }
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
fun OverlayCard(
    number: String,
    contactName: String?,
    onDismiss: () -> Unit,
    onReport: (String) -> Unit
) {
    val context = LocalContext.current
    var resolvedName by remember { mutableStateOf(contactName) }

    LaunchedEffect(number, contactName) {
        if (contactName.isNullOrBlank() && number.isNotBlank()) {
            withContext(Dispatchers.IO) {
                resolvedName = Reports.loadContactName(context, number)
            }
        } else {
            resolvedName = contactName
        }
    }

    val displayName = if (!resolvedName.isNullOrBlank()) resolvedName!! else "Unknown Number"
    val displayNumber = if (number.isNotBlank()) {
        if (number.startsWith("+")) number else "+$number"
    } else {
        "Unknown Number"
    }

    var safetyRanking by remember { mutableStateOf("Safety Ranking: Unknown") }
    var rankingColor by remember { mutableStateOf<Color?>(null) }

    LaunchedEffect(number) {
        if (number.isNotBlank()) {
            withContext(Dispatchers.IO) {
                try {
                    val region = Reports.region(context)
                    val tail = Reports.key(number, region) ?: number
                    val info = Reports.load(tail)
                    val total = info.spam + info.scam + info.safe
                    if (total >= 5) {
                        val score = ((info.safe * 100) / total).toInt()
                        safetyRanking = "Safety Ranking: $score%"
                        rankingColor = if (score >= 60) Color(0xFF2E7D32) else if (score >= 30) Color(0xFFF9A825) else Color(0xFFC62828)
                    } else {
                        safetyRanking = "Safety Ranking: Unknown"
                        rankingColor = null
                    }
                } catch (_: Exception) {
                    safetyRanking = "Safety Ranking: Unknown"
                    rankingColor = null
                }
            }
        }
    }

    ShieldCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displayNumber,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            Text(
                text = safetyRanking,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = rankingColor ?: MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { onReport("safe") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF2E7D32),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Safe")
                }
                Button(
                    onClick = { onReport("spam") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF9A825),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Spam")
                }
                Button(
                    onClick = { onReport("scam") },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFC62828),
                        contentColor = Color.White
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Scam")
                }
            }
        }
    }
}
