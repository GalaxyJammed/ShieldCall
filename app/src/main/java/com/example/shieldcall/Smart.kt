package com.example.shieldcall

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.Looper

object CallPrefs {
    private fun p(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)
    fun flash(c: Context) = p(c).getBoolean("flashCall", false)
    fun setFlash(c: Context, v: Boolean) = p(c).edit().putBoolean("flashCall", v).apply()
    fun faceDown(c: Context) = p(c).getBoolean("faceDownMute", false)
    fun setFaceDown(c: Context, v: Boolean) = p(c).edit().putBoolean("faceDownMute", v).apply()
}

/** Blinks the torch while a call rings. Needs no camera permission. */
object FlashAlert {
    private val handler = Handler(Looper.getMainLooper())
    private var on = false
    private var running = false
    private var camId: String? = null

    fun start(c: Context) {
        if (!CallPrefs.flash(c) || running) return
        val cm = c.getSystemService(CameraManager::class.java)
        camId = try {
            cm.cameraIdList.firstOrNull { id ->
                cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        } catch (e: Exception) {
            null
        }
        val id = camId ?: return
        running = true
        val tick = object : Runnable {
            override fun run() {
                if (!running) return
                on = !on
                try {
                    cm.setTorchMode(id, on)
                } catch (e: Exception) {
                    running = false
                    return
                }
                handler.postDelayed(this, 500)
            }
        }
        handler.post(tick)
    }

    fun stop(c: Context) {
        if (!running) return
        running = false
        handler.removeCallbacksAndMessages(null)
        val cm = c.getSystemService(CameraManager::class.java)
        camId?.let { try { cm.setTorchMode(it, false) } catch (e: Exception) { } }
        on = false
    }
}

/** Mutes the call and mic while the phone lies face down (gravity on the -Z axis). */
object FaceDown : SensorEventListener {
    var testSink: ((Boolean) -> Unit)? = null

    private fun setMute(m: Boolean) {
        val s = testSink
        if (s != null) s(m) else setMute(m)
    }
    private var manager: SensorManager? = null
    private var registered = false
    private var mutedByUs = false
    private var downSince = 0L

    fun start(c: Context) {
        if (!CallPrefs.faceDown(c) || registered) return
        val sm = c.getSystemService(SensorManager::class.java)
        val acc = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        manager = sm
        registered = true
        sm.registerListener(this, acc, SensorManager.SENSOR_DELAY_NORMAL)
    }

    fun stop() {
        if (!registered) return
        registered = false
        manager?.unregisterListener(this)
        if (mutedByUs) {
            setMute(false)
            mutedByUs = false
        }
        downSince = 0L
    }

    override fun onSensorChanged(event: SensorEvent) {
        val z = event.values[2]
        val now = System.currentTimeMillis()
        if (z < -7.5f) {
            if (downSince == 0L) downSince = now
            if (!mutedByUs && now - downSince > 1000) {
                setMute(true)
                mutedByUs = true
            }
        } else {
            downSince = 0L
            if (mutedByUs) {
                setMute(false)
                mutedByUs = false
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}