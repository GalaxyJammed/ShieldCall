package com.example.shieldcall

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.telephony.TelephonyManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.text.DateFormat
import java.util.TimeZone
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat

object CallPlan {
    private fun p(c: Context) = c.getSharedPreferences("plan", Context.MODE_PRIVATE)

    fun expires(c: Context): Long = p(c).getLong("expires", 0L)

    fun setExpires(c: Context, millis: Long) {
        p(c).edit().putLong("expires", millis).apply()
    }

    fun isExpired(c: Context): Boolean = expires(c) > 0L && System.currentTimeMillis() > expires(c)

    fun carrier(c: Context): String = p(c).getString("carrier", "") ?: ""
    fun balanceUrl(c: Context): String = p(c).getString("url", "") ?: ""
    fun saveLink(c: Context, carrier: String, url: String) {
        p(c).edit().putString("carrier", carrier).putString("url", url).apply()
    }

    fun isSet(c: Context): Boolean = p(c).getLong("since", 0L) > 0L
    fun threshold(c: Context): Int = p(c).getInt("threshold", 5)
    fun setThreshold(c: Context, v: Int) {
        p(c).edit().putInt("threshold", v).apply()
    }
    fun countIncoming(c: Context): Boolean = p(c).getBoolean("incoming", false)
    fun setCountIncoming(c: Context, v: Boolean) {
        p(c).edit().putBoolean("incoming", v).apply()
    }
    fun speak(c: Context): Boolean = p(c).getBoolean("speak", true)
    fun setSpeak(c: Context, v: Boolean) {
        p(c).edit().putBoolean("speak", v).apply()
    }

    fun setRemaining(c: Context, minutes: Int) {
        p(c).edit().putInt("base", minutes).putLong("since", System.currentTimeMillis()).apply()
    }

    fun clear(c: Context) {
        p(c).edit().remove("base").remove("since").remove("expires").apply()
    }

    fun remaining(c: Context): Int? {
        if (isExpired(c)) return 0
        val since = p(c).getLong("since", 0L)
        if (since == 0L) return null
        val used = SpamDb.get(c).dao().minutesSince(since, if (countIncoming(c)) 1 else 0)
        return (p(c).getInt("base", 0) - used).coerceAtLeast(0)
    }
}

object PlanSpeaker {
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    fun say(c: Context, text: String) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(c.applicationContext) { status ->
            val tts = engine ?: return@TextToSpeech
            if (status != TextToSpeech.SUCCESS) {
                tts.shutdown()
                return@TextToSpeech
            }
            if (tts.setLanguage(Locale.getDefault()) < TextToSpeech.LANG_AVAILABLE) {
                tts.setLanguage(Locale.ENGLISH)
            }
            tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {}
                override fun onDone(utteranceId: String?) {
                    tts.shutdown()
                }
                override fun onError(utteranceId: String?) {
                    tts.shutdown()
                }
            })
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "plan")
        }
    }
}

object PlanAlerts {
    private fun unit(n: Int) = if (n == 1) "1 minute" else "$n minutes"

    fun beforeCall(c: Context, uri: Uri) {
        if (uri.scheme != "tel" || !CallPlan.isSet(c) || !CallPlan.speak(c)) return
        val number = uri.schemeSpecificPart.orEmpty()
        val emergency = try {
            c.getSystemService(TelephonyManager::class.java).isEmergencyNumber(number)
        } catch (e: Exception) {
            false
        }
        if (emergency) return
        val left = CallPlan.remaining(c) ?: return
        if (left > CallPlan.threshold(c)) return
        PlanSpeaker.say(c, "Reminder, you only have ${unit(left)} of call remaining")
    }

    fun checkLow(c: Context) {
        if (!CallPlan.isSet(c)) return
        if (CallPlan.isExpired(c)) {
            notifyExpired(c)
        }
    }

    fun scheduleExpiry(c: Context) {
        val at = CallPlan.expires(c)
        if (at <= System.currentTimeMillis()) return
        val pi = PendingIntent.getBroadcast(
            c, 1001,
            Intent(c, CallbackReminderReceiver::class.java).setAction(CallbackReminderReceiver.EXPIRED),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
    }

    fun notifyExpired(c: Context) {
        val at = CallPlan.expires(c)
        if (at == 0L) return
        val prefs = c.getSharedPreferences("plan", Context.MODE_PRIVATE)
        if (prefs.getLong("expired_notified", 0L) == at) return
        prefs.edit().putLong("expired_notified", at).apply()
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("plan", "Call minutes", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            c, 1002,
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, "plan")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("Call minutes expired")
            .setContentText("Your minutes have expired. Renew your plan or check your carrier balance.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify("plan_expired", 8, n)
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun CallMinutesSection() {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var linkOpen by remember { mutableStateOf(false) }
    var setOpen by remember { mutableStateOf(false) }
    val remaining by produceState<Int?>(null, version) {
        value = withContext(Dispatchers.IO) { CallPlan.remaining(context) }
    }
    var threshold by remember(version) { mutableFloatStateOf(CallPlan.threshold(context).toFloat()) }
    var incoming by remember(version) { mutableStateOf(CallPlan.countIncoming(context)) }
    var speak by remember(version) { mutableStateOf(CallPlan.speak(context)) }

    ShieldCard(Modifier.fillMaxWidth()) {
        Column {
            Row(
                Modifier.fillMaxWidth().clickable {
                    val url = CallPlan.balanceUrl(context)
                    if (url.startsWith("https://")) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        linkOpen = true
                    }
                }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AccountBalanceWallet, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Carrier balance", style = MaterialTheme.typography.titleMedium)
                    Text(
                        CallPlan.carrier(context).ifBlank { "Tap to add your carrier's balance page" },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                if (CallPlan.balanceUrl(context).isNotBlank()) {
                    TextButton(onClick = { linkOpen = true }) { Text("Edit") }
                }
            }
            HorizontalDivider()
            Row(
                Modifier.fillMaxWidth().clickable { setOpen = true }.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.padding(end = 16.dp))
                Column(Modifier.weight(1f)) {
                    Text("Minutes left", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when {
                            remaining == null -> "Not set. Tap to enter what your plan shows now"
                            CallPlan.isExpired(context) -> "Expired"
                            CallPlan.expires(context) > 0L ->
                                "About $remaining min · expires ${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(CallPlan.expires(context)))}"
                            else -> "About $remaining min. Tap to reset from your balance"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            HorizontalDivider()
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "Warn me at ${threshold.toInt()} min or less",
                    style = MaterialTheme.typography.titleMedium
                )
                Slider(
                    value = threshold,
                    onValueChange = { threshold = it },
                    onValueChangeFinished = { CallPlan.setThreshold(context, threshold.toInt()) },
                    valueRange = 1f..10f,
                    steps = 8
                )
            }
            HorizontalDivider()
            SettingRow(
                Icons.Default.Schedule,
                "Speak warnings",
                "Reads low-minute warnings aloud, only to you",
                speak
            ) {
                speak = it
                CallPlan.setSpeak(context, it)
            }
            HorizontalDivider()
            SettingRow(
                Icons.Default.Schedule,
                "Count incoming calls",
                "Also subtract calls you receive from the estimate",
                incoming
            ) {
                incoming = it
                CallPlan.setCountIncoming(context, it)
            }
            Text(
                "The estimate only includes calls made or received through ShieldCall. Calls from other phone apps aren't counted, and carriers round each call differently.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }

    if (linkOpen) {
        var carrierName by remember { mutableStateOf(CallPlan.carrier(context)) }
        var url by remember { mutableStateOf(CallPlan.balanceUrl(context)) }
        AlertDialog(
            onDismissRequest = { linkOpen = false },
            title = { Text("Carrier balance page") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = carrierName,
                        onValueChange = { carrierName = it.take(40) },
                        label = { Text("Carrier name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it.take(300) },
                        label = { Text("Balance page (https://...)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = url.startsWith("https://"),
                    onClick = {
                        CallPlan.saveLink(context, carrierName.trim(), url.trim())
                        linkOpen = false
                        version++
                    }
                ) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { linkOpen = false }) { Text("Cancel") } }
        )
    }

    if (setOpen) {
        var minutes by remember { mutableStateOf("") }
        var expiry by remember { mutableLongStateOf(CallPlan.expires(context)) }
        var pickingDate by remember { mutableStateOf(false) }
        val dateState = rememberDatePickerState(initialSelectedDateMillis = expiry.takeIf { it > 0L })
        val shownExpiry = if (expiry > 0L) DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expiry)) else "No expiry"

        AlertDialog(
            onDismissRequest = { setOpen = false },
            title = { Text("Minutes left") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Check your balance page, then enter the minutes it shows. Counting starts from now.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit).take(5) },
                        label = { Text("Minutes") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(onClick = { pickingDate = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Expires: $shownExpiry")
                    }
                    if (expiry > 0L) {
                        TextButton(onClick = { expiry = 0L }) { Text("Remove expiry") }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = minutes.isNotBlank(),
                    onClick = {
                        CallPlan.setRemaining(context, minutes.toInt())
                        CallPlan.setExpires(context, expiry)
                        PlanAlerts.scheduleExpiry(context)
                        setOpen = false
                        version++
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (CallPlan.isSet(context)) {
                        TextButton(onClick = {
                            CallPlan.clear(context)
                            setOpen = false
                            version++
                        }) { Text("Clear") }
                    }
                    TextButton(onClick = { setOpen = false }) { Text("Cancel") }
                }
            }
        )

        if (pickingDate) {
            DatePickerDialog(
                onDismissRequest = { pickingDate = false },
                confirmButton = {
                    TextButton(onClick = {
                        pickingDate = false
                        val utc = dateState.selectedDateMillis ?: return@TextButton
                        expiry = endOfDayLocal(utc)
                    }) { Text("OK") }
                },
                dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel") } }
            ) {
                DatePicker(state = dateState)
            }
        }
    }
}

private fun endOfDayLocal(utcMillis: Long): Long {
    val ymd = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        .apply { timeZone = TimeZone.getTimeZone("UTC") }
        .format(Date(utcMillis))
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse("$ymd 23:59:59")?.time ?: utcMillis
}