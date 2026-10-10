package com.example.shieldcall

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object CallbackReminder {
    private const val DELAY = 60L * 60 * 1000
    private const val FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT

    fun actionIntent(c: Context, action: String, key: String, name: String?): PendingIntent =
        PendingIntent.getBroadcast(
            c,
            (action + key).hashCode(),
            Intent(c, CallbackReminderReceiver::class.java)
                .setAction(action)
                .putExtra("key", key)
                .putExtra("name", name),
            FLAGS
        )

    fun schedule(c: Context, key: String, name: String?) {
        c.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            System.currentTimeMillis() + DELAY,
            actionIntent(c, CallbackReminderReceiver.REMIND, key, name)
        )
    }

    fun remind(c: Context, key: String, name: String?) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("callback", "Call back reminders", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            c, key.hashCode(),
            Intent(c, MainActivity::class.java)
                .putExtra("dial", "+$key")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            FLAGS
        )
        val n = NotificationCompat.Builder(c, "callback")
            .setSmallIcon(android.R.drawable.sym_action_call)
            .setContentTitle("Call back reminder")
            .setContentText("${name?.takeIf { it.isNotBlank() } ?: "+$key"}: time to call back")
            .setAutoCancel(true)
            .setContentIntent(open)
            .addAction(android.R.drawable.sym_action_call, "Call now", open)
            .build()
        nm.notify("cb$key", 7, n)
    }
}

class CallbackReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == EXPIRED) {
            PlanAlerts.notifyExpired(context)
            return
        }
        val key = intent.getStringExtra("key") ?: return
        val name = intent.getStringExtra("name")
        when (intent.action) {
            SCHEDULE -> CallbackReminder.schedule(context, key, name)
            REMIND -> CallbackReminder.remind(context, key, name)
        }
    }

    companion object {
        const val SCHEDULE = "shieldcall.callback.SCHEDULE"
        const val REMIND = "shieldcall.callback.REMIND"
        const val EXPIRED = "shieldcall.plan.EXPIRED"
    }
}