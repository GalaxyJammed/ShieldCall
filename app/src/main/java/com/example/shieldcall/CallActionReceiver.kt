package com.example.shieldcall

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call

@Suppress("DEPRECATION")
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            END -> CallManager.calls.firstOrNull {
                it.parent == null &&
                        it.state != Call.STATE_RINGING &&
                        it.state != Call.STATE_DISCONNECTING &&
                        it.state != Call.STATE_DISCONNECTED
            }?.disconnect()
            MUTE -> CallManager.toggleMute()
            SPEAKER -> CallManager.toggleSpeaker()
        }
    }

    companion object {
        const val END = "shieldcall.call.END"
        const val MUTE = "shieldcall.call.MUTE"
        const val SPEAKER = "shieldcall.call.SPEAKER"
    }
}