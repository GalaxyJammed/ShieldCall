package com.example.shieldcall

import android.content.Context
import com.google.i18n.phonenumbers.PhoneNumberUtil
import org.json.JSONObject
import java.util.Locale

object LookupStats {
    private fun prefs(c: Context) = c.getSharedPreferences("stats", Context.MODE_PRIVATE)

    fun record(c: Context, key: String) {
        val country = try {
            val p = PhoneNumberUtil.getInstance().parse("+$key", null)
            val region = PhoneNumberUtil.getInstance().getRegionCodeForNumber(p)
            Locale.Builder().setRegion(region).build().displayCountry.ifBlank { "Other" }
        } catch (e: Exception) {
            "Other"
        }
        val p = prefs(c)
        val obj = JSONObject(p.getString("countries", "{}") ?: "{}")
        obj.put(country, obj.optInt(country, 0) + 1)
        p.edit()
            .putInt("total", p.getInt("total", 0) + 1)
            .putString("countries", obj.toString())
            .apply()
    }

    fun total(c: Context): Int = prefs(c).getInt("total", 0)

    fun countries(c: Context): Map<String, Int> {
        val obj = JSONObject(prefs(c).getString("countries", "{}") ?: "{}")
        return obj.keys().asSequence().associateWith { obj.optInt(it, 0) }
    }
}

object Contribution {
    private fun prefs(c: Context) = c.getSharedPreferences("stats", Context.MODE_PRIVATE)

    fun reviews(c: Context): Int = prefs(c).getInt("reviews", 0)

    fun addReviews(c: Context, delta: Int) {
        prefs(c).edit().putInt("reviews", (reviews(c) + delta).coerceAtLeast(0)).apply()
    }
}