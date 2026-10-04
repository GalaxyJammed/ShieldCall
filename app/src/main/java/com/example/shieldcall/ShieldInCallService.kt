package com.example.shieldcall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.core.app.NotificationCompat
import android.app.KeyguardManager
import android.provider.Settings
import android.telecom.DisconnectCause
import android.os.Handler
import android.os.Looper
import android.telecom.TelecomManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Suppress("DEPRECATION")
class ShieldInCallService : InCallService() {

    private val incomingCalls = mutableSetOf<Call>()

    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onDestroy() {
        CallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        CallManager.add(call)
        callAudioState?.let { CallManager.audio(it) }
        Prefs.load(this)
        val ringing = call.state == Call.STATE_RINGING
        if (ringing) incomingCalls.add(call)
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val mini = ringing && CallManager.calls.size == 1 && !Prefs.fullScreen && !locked && Settings.canDrawOverlays(this)
        val intent = Intent(this, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (ringing) notifyIncoming(call, intent, !mini)
        if (mini) showMini(call) else try {
            startActivity(intent)
        } catch (e: Exception) {
        }
    }

    private fun showMini(call: Call) {
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        startService(
            Intent(this, OverlayService::class.java)
                .putExtra("number", number)
                .putExtra("contactName", Reports.loadContactInfo(this, number).name)
                .putExtra("incoming", true)
        )
    }

    private fun notifyIncoming(call: Call, intent: Intent, fullScreen: Boolean) {
        val nm = getSystemService(NotificationManager::class.java)
        val channel = if (fullScreen) "calls" else "calls_mini"
        nm.createNotificationChannel(
            NotificationChannel(
                channel,
                if (fullScreen) "Calls" else "Calls (silent card)",
                if (fullScreen) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_LOW
            )
        )
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val number = call.details.handle?.schemeSpecificPart.orEmpty()
        val b = NotificationCompat.Builder(this, channel)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle("Incoming call")
            .setContentText(number.ifBlank { "Unknown number" })
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setContentIntent(pi)
        if (fullScreen) b.setPriority(NotificationCompat.PRIORITY_HIGH).setFullScreenIntent(pi, true)
        nm.notify(1, b.build())
    }

    override fun onCallRemoved(call: Call) {
        val incoming = incomingCalls.remove(call)
        CallManager.remove(call)
        getSystemService(NotificationManager::class.java).cancel(1)
        logHistory(call, incoming)
        if (call.details.disconnectCause?.code == DisconnectCause.MISSED) {
            notifyMissed(call.details.handle?.schemeSpecificPart.orEmpty())
            hideSystemMissed()
        }
    }

    private fun notifyMissed(raw: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val key = Reports.key(raw, Reports.region(this@ShieldInCallService))
            val name = Reports.loadContactInfo(this@ShieldInCallService, raw).name?.takeIf { it.isNotBlank() }
            val ranking = try {
                val i = if (key != null && Reports.signedIn()) Reports.load(key) else null
                val total = (i?.spam ?: 0) + (i?.scam ?: 0) + (i?.safe ?: 0)
                if (i != null && total > 0) "${(i.safe * 100) / total}%" else "Unknown"
            } catch (e: Exception) {
                "Unknown"
            }
            val number = if (key != null) "+$key" else raw.ifBlank { "Hidden number" }
            val tag = key ?: raw
            val callBack = PendingIntent.getActivity(
                this@ShieldInCallService,
                tag.hashCode(),
                Intent(this@ShieldInCallService, MainActivity::class.java)
                    .putExtra("dial", if (key != null) "+$key" else raw)
                    .putExtra("tag", tag)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(NotificationChannel("missed", "Missed calls", NotificationManager.IMPORTANCE_HIGH))
            val pi = PendingIntent.getActivity(
                this@ShieldInCallService, 2, Intent(this@ShieldInCallService, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val first = "$number Missed Call"
            val warning = if (name == null && key != null && CallGuard.isForeign(this@ShieldInCallService, key)) "\n⚠ Foreign number. Be careful calling back." else ""
            val second = "Safety Ranking: $ranking$warning"
            val n = NotificationCompat.Builder(this@ShieldInCallService, "missed")
                .setSmallIcon(android.R.drawable.sym_call_missed)
                .setContentTitle(name ?: "Unknown Number")
                .setContentText(first)
                .setStyle(NotificationCompat.BigTextStyle().bigText("$first\n$second"))
                .setCategory(NotificationCompat.CATEGORY_MISSED_CALL)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setWhen(System.currentTimeMillis())
                .addAction(android.R.drawable.sym_action_call, "Call back", callBack)
                .build()
            nm.notify(key ?: raw, 2, n)
        }
    }

    private fun hideSystemMissed() {
        val tm = getSystemService(TelecomManager::class.java)
        val handler = Handler(Looper.getMainLooper())
        listOf(300L, 1500L, 4000L).forEach { delay ->
            handler.postDelayed({
                try {
                    tm.cancelMissedCallsNotification()
                } catch (e: SecurityException) {
                }
            }, delay)
        }
    }

    private fun logHistory(call: Call, incoming: Boolean) {
        val details = call.details
        val raw = details.handle?.schemeSpecificPart.orEmpty()
        val key = Reports.key(raw, Reports.region(this)) ?: return
        val connected = details.connectTimeMillis
        val seconds = if (connected > 0) ((System.currentTimeMillis() - connected) / 1000).toInt() else 0
        val code = details.disconnectCause?.code
        val status = when {
            !incoming -> "Outgoing"
            code == DisconnectCause.REJECTED -> "Declined"
            code == DisconnectCause.MISSED || connected <= 0 -> "Missed"
            else -> "Incoming"
        }
        CoroutineScope(Dispatchers.IO).launch {
            val name = Reports.loadContactInfo(this@ShieldInCallService, raw).name.orEmpty()
            val dao = SpamDb.get(this@ShieldInCallService).dao()
            dao.addCall(
                CallEntry(
                    number = key,
                    raw = raw,
                    name = name,
                    status = status,
                    time = System.currentTimeMillis(),
                    duration = seconds
                )
            )
            dao.trimCalls()
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.audio(audioState)
    }
}