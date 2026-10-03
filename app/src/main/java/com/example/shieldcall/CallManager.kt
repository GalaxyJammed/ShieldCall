package com.example.shieldcall

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

@Suppress("DEPRECATION")
object CallManager {
    val calls = mutableStateListOf<Call>()
    var tick by mutableIntStateOf(0)
        private set
    var muted by mutableStateOf(false)
        private set
    var speaker by mutableStateOf(false)
        private set
    var service: InCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            tick++
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            tick++
        }
    }

    fun add(call: Call) {
        call.registerCallback(callback)
        if (call !in calls) calls.add(call)
        tick++
    }

    fun remove(call: Call) {
        call.unregisterCallback(callback)
        calls.remove(call)
        tick++
    }

    fun audio(state: CallAudioState) {
        muted = state.isMuted
        speaker = state.route == CallAudioState.ROUTE_SPEAKER
    }

    fun toggleMute() {
        service?.setMuted(!muted)
    }

    fun toggleSpeaker() {
        service?.setAudioRoute(if (speaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }
    fun ringing(): Call? = calls.firstOrNull { it.state == Call.STATE_RINGING }
}