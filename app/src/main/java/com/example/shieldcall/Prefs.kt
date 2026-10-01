package com.example.shieldcall

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Prefs {
    var dark by mutableStateOf(false)
        private set
    var lookup by mutableStateOf(true)
        private set
    var business by mutableStateOf(false)
        private set
    var foreign by mutableStateOf(false)
        private set
    var unknown by mutableStateOf(false)
        private set
    var unsaved by mutableStateOf(false)
        private set
    var action by mutableStateOf("popup")
        private set
    var fingerprint by mutableStateOf(false)
        private set
    var pinLock by mutableStateOf(false)
        private set
    var pinCode by mutableStateOf("1234")
        private set
    var recentLookups by mutableStateOf<List<String>>(emptyList())
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(c: Context) {
        dark = prefs(c).getBoolean("dark", false)
        lookup = prefs(c).getBoolean("lookup", true)
        business = flag(c, "business")
        foreign = flag(c, "foreign")
        unknown = flag(c, "unknown")
        unsaved = flag(c, "unsaved")
        action = actionOf(c)
        fingerprint = prefs(c).getBoolean("fingerprint", false)
        pinLock = prefs(c).getBoolean("pinLock", false)
        pinCode = prefs(c).getString("pinCode", "1234") ?: "1234"
        val recentsStr = prefs(c).getString("recentLookups", "") ?: ""
        recentLookups = if (recentsStr.isBlank()) emptyList() else recentsStr.split(",")
    }

    fun setDark(c: Context, v: Boolean) {
        dark = v
        prefs(c).edit().putBoolean("dark", v).apply()
    }

    fun setLookup(c: Context, v: Boolean) {
        lookup = v
        prefs(c).edit().putBoolean("lookup", v).apply()
    }

    fun setFingerprint(c: Context, v: Boolean) {
        fingerprint = v
        prefs(c).edit().putBoolean("fingerprint", v).apply()
    }

    fun setPinLock(c: Context, v: Boolean) {
        pinLock = v
        prefs(c).edit().putBoolean("pinLock", v).apply()
    }

    fun setPinCode(c: Context, v: String) {
        pinCode = v
        prefs(c).edit().putString("pinCode", v).apply()
    }

    fun setFlag(c: Context, key: String, v: Boolean) {
        when (key) {
            "business" -> business = v
            "foreign" -> foreign = v
            "unknown" -> unknown = v
            "unsaved" -> unsaved = v
        }
        prefs(c).edit().putBoolean(key, v).apply()
    }

    fun setAction(c: Context, v: String) {
        action = v
        prefs(c).edit().putString("action", v).apply()
    }

    fun lookupEnabled(c: Context) = prefs(c).getBoolean("lookup", true)

    fun flag(c: Context, key: String) = prefs(c).getBoolean(key, false)

    fun actionOf(c: Context) = prefs(c).getString("action", "popup") ?: "popup"

    fun addRecentLookup(c: Context, number: String) {
        val current = recentLookups.toMutableList()
        current.remove(number)
        current.add(0, number)
        if (current.size > 3) {
            current.removeAt(current.size - 1)
        }
        recentLookups = current
        prefs(c).edit().putString("recentLookups", current.joinToString(",")).apply()
    }
}
