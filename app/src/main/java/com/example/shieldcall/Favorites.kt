package com.example.shieldcall

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object Favorites {
    var keys by mutableStateOf<Set<String>>(emptySet())
        private set

    private fun prefs(c: Context) = c.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(c: Context) {
        keys = prefs(c).getStringSet("favorites", emptySet()).orEmpty().toSet()
    }

    fun toggle(c: Context, key: String) {
        keys = if (key in keys) keys - key else keys + key
        prefs(c).edit().putStringSet("favorites", keys).apply()
    }

    fun merge(c: Context, extra: Set<String>) {
        keys = keys + extra
        prefs(c).edit().putStringSet("favorites", keys).apply()
    }
}