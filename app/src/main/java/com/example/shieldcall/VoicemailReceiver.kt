package com.example.shieldcall

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import androidx.core.app.NotificationCompat

class VoicemailReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_SHOW_VOICEMAIL_NOTIFICATION) return
        val nm = context.getSystemService(NotificationManager::class.java)
        val count = intent.getIntExtra(TelephonyManager.EXTRA_NOTIFICATION_COUNT, -1)
        if (count == 0) {
            nm.cancel(3)
            return
        }
        nm.createNotificationChannel(NotificationChannel("voicemail", "Voicemail", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(
            context, 3,
            Intent(context, MainActivity::class.java)
                .putExtra("voicemail", true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val text = if (count > 0) "$count new voicemail" + if (count == 1) "" else "s" else "You have a new voicemail"
        val n = NotificationCompat.Builder(context, "voicemail")
            .setSmallIcon(android.R.drawable.stat_notify_voicemail)
            .setContentTitle("Voicemail")
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pi)
            .addAction(android.R.drawable.sym_action_call, "Listen", pi)
            .build()
        nm.notify(3, n)
    }
}