package com.example.shieldcall

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.ContactsContract
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneInTalk
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.telecom.Call
import android.telecom.VideoProfile


class OverlayService : Service(), LifecycleOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val stateController = SavedStateRegistryController.create(this)
    private val handler = Handler(Looper.getMainLooper())
    private var view: ComposeView? = null
    private var sound: CallSound? = null

    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = stateController.savedStateRegistry

    override fun onCreate() {
        super.onCreate()
        stateController.performRestore(null)
        registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Settings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        Prefs.load(this)
        val number = intent?.getStringExtra("number").orEmpty()
        val fake = intent?.getBooleanExtra("fake", false) == true
        sound?.stop()
        sound = if (fake) CallSound(this).also { it.start() } else null
        val incoming = intent?.getBooleanExtra("incoming", false) == true
        val contactName = intent?.getStringExtra("contactName")
        removeView()
        val isFull = Prefs.fullScreen
        val composeView = ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@OverlayService)
            setViewTreeSavedStateRegistryOwner(this@OverlayService)
            setContent {
                ShieldTheme(Prefs.dark) {
                    if (isFull) {
                        FullScreenCallCard(
                            number = number,
                            contactName = contactName,
                            onDismiss = { stopSelf() },
                            onReport = { submit(number, it) }
                        )
                    } else {
                        var declined by remember { mutableStateOf(false) }
                        if (incoming) {
                            val ringing = CallManager.ringing() != null
                            LaunchedEffect(ringing, declined) { if (!ringing && !declined) stopSelf() }
                        }
                        OverlayCard(
                            number = number,
                            contactName = contactName,
                            onDismiss = { stopSelf() },
                            onReport = { if (fake) stopSelf() else submit(number, it) },
                            onAccept = {
                                if (incoming) {
                                    CallManager.ringing()?.answer(VideoProfile.STATE_AUDIO_ONLY)
                                    startActivity(Intent(this@OverlayService, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                                }
                                stopSelf()
                            },
                            onDeclineClick = {
                                declined = true
                                if (incoming) CallManager.ringing()?.reject(false, null)
                            }
                        )
                    }
                }
            }
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            if (isFull) WindowManager.LayoutParams.MATCH_PARENT else WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    if (isFull) WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS else 0,
            if (isFull) PixelFormat.OPAQUE else PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
            if (isFull) {
                layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            getSystemService(WindowManager::class.java).addView(composeView, params)
            view = composeView
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
            registry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
            handler.removeCallbacksAndMessages(null)
            handler.postDelayed({ stopSelf() }, 30000)
        } catch (_: Exception) {
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun removeView() {
        try {
            view?.let { getSystemService(WindowManager::class.java).removeView(it) }
        } catch (_: Exception) {}
        view = null
    }

    private fun submit(number: String, type: String) {
        val tail = Reports.key(number, Reports.region(this)) ?: number
        val dao = SpamDb.get(this).dao()
        dao.insert(SpamNumber(tail, type))
        dao.deleteIdentifications(tail)
        dao.addIdentification(IdentificationEntry(number = tail, type = type, time = System.currentTimeMillis()))
        CoroutineScope(Dispatchers.IO).launch {
            try {
                Reports.report(this@OverlayService, tail, type)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        stopSelf()
    }

    override fun onDestroy() {
        sound?.stop()
        removeView()
        registry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

private fun contactPhotoUri(context: Context, number: String): String? {
    if (number.isEmpty()) return null
    val uri = Uri.withAppendedPath(
        ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
        Uri.encode(number)
    )
    context.contentResolver.query(
        uri, arrayOf(ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI), null, null, null
    )?.use { if (it.moveToFirst()) return it.getString(0) }
    return null
}

@Composable
fun CallAvatar(photoUri: String?, size: Dp = 110.dp) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, photoUri) {
        value = photoUri?.let {
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.openInputStream(Uri.parse(it))?.use { s ->
                        BitmapFactory.decodeStream(s)?.asImageBitmap()
                    }
                } catch (_: Exception) {
                    null
                }
            }
        }
    }
    val shape = Modifier.size(size).clip(CircleShape)
    val b = bitmap
    if (b != null) {
        Image(b, contentDescription = null, modifier = shape, contentScale = ContentScale.Crop)
    } else {
        Box(
            modifier = shape.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(size * 0.5f),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DeclineReasonPrompt(
    existingVote: String? = null,
    onSelect: (String?) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!existingVote.isNullOrBlank()) {
            Text(
                text = "You voted as: ${existingVote.uppercase()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (existingVote.lowercase() == "safe") Color(0xFF2E7D32) else if (existingVote.lowercase() == "spam") Color(0xFFF9A825) else Color(0xFFC62828)
            )
            Button(
                onClick = { onSelect(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Dismiss")
            }
        } else {
            Text(
                text = "Why have you rejected this call?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Button(
                onClick = { onSelect("spam") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFF9A825),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Feels like Spam")
            }

            Button(
                onClick = { onSelect("scam") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFC62828),
                    contentColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Feels like a Scam")
            }

            OutlinedButton(
                onClick = { onSelect(null) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Didn't want to answer")
            }
        }
    }
}

@Composable
fun OverlayCard(
    number: String,
    contactName: String?,
    onDismiss: () -> Unit,
    onReport: (String) -> Unit,
    onAccept: () -> Unit = onDismiss,
    onDeclineClick: () -> Unit = {}
) {
    val context = LocalContext.current
    var resolvedName by remember { mutableStateOf(contactName) }
    var location by remember { mutableStateOf<String?>(null) }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var showDeclineReason by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(number, contactName) {
        if (number.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val info = Reports.loadContactInfo(context, number)
                if (contactName.isNullOrBlank()) {
                    resolvedName = info.name
                }
                location = info.location
                photoUri = contactPhotoUri(context, number)
            }
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
    var existingVote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(number) {
        if (number.isNotBlank()) {
            withContext(Dispatchers.IO) {
                hint = CallerHint.fromNumber(context, number)
                try {
                    val region = Reports.region(context)
                    val tail = Reports.key(number, region) ?: number
                    val info = Reports.load(tail)
                    hint = CallerHint.hint(context, number, info)
                    existingVote = info.myVote ?: SpamDb.get(context).dao().entry(tail)?.takeIf { it.source == "mine" }?.type

                    val total = info.spam + info.scam + info.safe
                    if (total > 0) {
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    CallAvatar(photoUri = photoUri, size = 44.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = displayNumber,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (!location.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = location!!,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            if (resolvedName.isNullOrBlank()) hint?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
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

            if (!showDeclineReason) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onAccept,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2E7D32),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Accept")
                    }
                    Button(
                        onClick = {
                            onDeclineClick()
                            if (existingVote != null || !Reports.signedIn()) onDismiss() else showDeclineReason = true
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFC62828),
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Decline")
                    }
                }
            } else {
                DeclineReasonPrompt(
                    existingVote = existingVote,
                    onSelect = { type ->
                        if (type != null) {
                            onReport(type)
                        } else {
                            onDismiss()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun FullScreenCallCard(
    number: String,
    contactName: String?,
    onDismiss: () -> Unit,
    onReport: (String) -> Unit
) {
    val context = LocalContext.current
    var resolvedName by remember { mutableStateOf(contactName) }
    var location by remember { mutableStateOf<String?>(null) }
    var photoUri by remember { mutableStateOf<String?>(null) }
    var showDeclineReason by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(number, contactName) {
        if (number.isNotBlank()) {
            withContext(Dispatchers.IO) {
                val info = Reports.loadContactInfo(context, number)
                if (contactName.isNullOrBlank()) {
                    resolvedName = info.name
                }
                location = info.location
                photoUri = contactPhotoUri(context, number)
            }
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
    var existingVote by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(number) {
        if (number.isNotBlank()) {
            withContext(Dispatchers.IO) {
                hint = CallerHint.fromNumber(context, number)
                try {
                    val region = Reports.region(context)
                    val tail = Reports.key(number, region) ?: number
                    val info = Reports.load(tail)
                    hint = CallerHint.hint(context, number, info)
                    existingVote = info.myVote ?: SpamDb.get(context).dao().entry(tail)?.takeIf { it.source == "mine" }?.type

                    val total = info.spam + info.scam + info.safe
                    if (total > 0) {
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                CallAvatar(photoUri = photoUri, size = 120.dp)

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = displayNumber,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!location.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = location!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (resolvedName.isNullOrBlank()) hint?.let {
                            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                Surface(
                    color = (rankingColor ?: MaterialTheme.colorScheme.primary).copy(alpha = 0.15f),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text(
                        text = safetyRanking,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = rankingColor ?: MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            if (!showDeclineReason) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = { if (existingVote != null || !Reports.signedIn()) onDismiss() else showDeclineReason = true },
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFFC62828), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CallEnd,
                                contentDescription = "Decline",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Decline", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(72.dp)
                                .background(Color(0xFF2E7D32), CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhoneInTalk,
                                contentDescription = "Accept",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Accept", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                ShieldCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    DeclineReasonPrompt(
                        existingVote = existingVote,
                        onSelect = { type ->
                            if (type != null) {
                                onReport(type)
                            } else {
                                onDismiss()
                            }
                        }
                    )
                }
            }
        }
    }
}
