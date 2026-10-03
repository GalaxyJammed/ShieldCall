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

@Suppress("DEPRECATION")
class ShieldInCallService : InCallService() {

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
        Prefs.load(this)
        val ringing = call.state == Call.STATE_RINGING
        if (!ringing) logOutgoing(call)
        val locked = getSystemService(KeyguardManager::class.java).isKeyguardLocked
        val mini = ringing && !Prefs.fullScreen && !locked && Settings.canDrawOverlays(this)
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
        CallManager.remove(call)
        getSystemService(NotificationManager::class.java).cancel(1)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.audio(audioState)
    }

    private fun logOutgoing(call: Call) {
        val raw = call.details.handle?.schemeSpecificPart.orEmpty()
        val key = Reports.key(raw, Reports.region(this)) ?: return
        val dao = SpamDb.get(this).dao()
        dao.addCall(
            CallEntry(
                number = key,
                raw = raw,
                name = Reports.loadContactInfo(this, raw).name.orEmpty(),
                status = "Outgoing",
                time = System.currentTimeMillis()
            )
        )
        dao.trimCalls()
    }
}