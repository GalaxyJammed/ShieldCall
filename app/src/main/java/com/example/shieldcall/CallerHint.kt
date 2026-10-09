package com.example.shieldcall

import android.content.Context
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import com.google.i18n.phonenumbers.geocoding.PhoneNumberOfflineGeocoder
import java.util.Locale

object CallerHint {
    private const val MIN_VOTES = 2

    fun fromVotes(info: Info?): String? {
        val top = info?.tags.orEmpty()
            .filter { it.key.startsWith("p_") && it.value >= MIN_VOTES }
            .maxByOrNull { it.value } ?: return null
        return Tags.label(top.key)
    }

    fun fromNumber(c: Context, number: String): String? {
        val key = Reports.key(number, Reports.region(c)) ?: return null
        val util = PhoneNumberUtil.getInstance()
        val parsed = try {
            util.parse("+$key", null)
        } catch (e: Exception) {
            return null
        }
        return when (util.getNumberType(parsed)) {
            PhoneNumberType.TOLL_FREE -> "Toll-free line, usually a company"
            PhoneNumberType.UAN -> "Company or organization line"
            PhoneNumberType.SHARED_COST -> "Service line, usually a company"
            PhoneNumberType.VOIP -> "Internet phone line, often a business"
            PhoneNumberType.FIXED_LINE -> {
                val city = PhoneNumberOfflineGeocoder.getInstance().getDescriptionForNumber(parsed, Locale.getDefault())
                if (city.isNotBlank()) "Landline in $city, possibly an office or business"
                else "Landline, possibly an office or business"
            }
            else -> null
        }
    }

    fun hint(c: Context, number: String, info: Info?): String? =
        fromVotes(info)?.let { "Likely: $it" } ?: fromNumber(c, number)
}