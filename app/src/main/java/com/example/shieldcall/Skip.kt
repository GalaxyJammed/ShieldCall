package com.example.shieldcall

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object Skip {
    fun check(number: String, onSpam: () -> Unit) {
        val digits = number.filter { it.isDigit() }
        if (!number.startsWith("+1") || digits.length != 11) return
        Thread {
            try {
                val conn = URL("https://spam.skipcalls.com/check/${digits.takeLast(10)}")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                if (JSONObject(body).optBoolean("is_spam")) onSpam()
            } catch (e: Exception) {
            }
        }.start()
    }

    suspend fun isSpam(number: String): Boolean? {
        val digits = number.filter { it.isDigit() }
        if (!number.startsWith("+1") || digits.length != 11) return null
        return withContext(Dispatchers.IO) {
            try {
                val conn = URL("https://spam.skipcalls.com/check/${digits.takeLast(10)}")
                    .openConnection() as HttpURLConnection
                conn.connectTimeout = 3000
                conn.readTimeout = 3000
                JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optBoolean("is_spam")
            } catch (e: Exception) {
                null
            }
        }
    }
}