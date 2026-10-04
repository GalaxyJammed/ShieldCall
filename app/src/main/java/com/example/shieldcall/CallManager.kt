package com.example.shieldcall

import android.bluetooth.BluetoothDevice
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
    var route by mutableIntStateOf(CallAudioState.ROUTE_EARPIECE)
         private set
    var supported by mutableIntStateOf(0)
        private set
    var btDevices by mutableStateOf<List<BluetoothDevice>>(emptyList())
        private set
    var activeBt by mutableStateOf<BluetoothDevice?>(null)
        private set
    var service: InCallService? = null

    private val callback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            tick++
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            tick++
        }

        override fun onParentChanged(call: Call, parent: Call?) {
            tick++
        }

        override fun onChildrenChanged(call: Call, children: MutableList<Call>) {
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

    fun ringing(): Call? = calls.firstOrNull { it.parent == null && it.state == Call.STATE_RINGING }

    fun audio(state: CallAudioState) {
        muted = state.isMuted
        route = state.route
        supported = state.supportedRouteMask
        btDevices = try {
            state.supportedBluetoothDevices.toList()
        } catch (e: Exception) {
            emptyList()
        }
        activeBt = try {
            state.activeBluetoothDevice
        } catch (e: Exception) {
            null
        }
        speaker = route == CallAudioState.ROUTE_SPEAKER
    }

    fun toggleMute() {
        service?.setMuted(!muted)
    }

    fun toggleSpeaker() {
        service?.setAudioRoute(if (speaker) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }

    fun chooseRoute(r: Int) {
        service?.setAudioRoute(r)
    }

    fun useBluetooth(d: BluetoothDevice) {
        service?.requestBluetoothAudio(d)
    }
}