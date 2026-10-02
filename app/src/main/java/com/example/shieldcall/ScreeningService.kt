package com.example.shieldcall

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import java.util.Locale

class ScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val number = details.handle?.schemeSpecificPart.orEmpty()
        if (number.isEmpty()) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val region = Reports.region(this)
        val key = Reports.key(number, region)
        val name = contactName(number)
        val dao = SpamDb.get(this).dao()
        val blockedList = dao.blockedList()
        val blockedNumbers = blockedList.filter { it.type == "number" }.map { it.number }
        val blockedNames = blockedList.filter { it.type == "name" }.map { it.number.lowercase() }
        val blockedCountries = blockedList.filter { it.type == "country" }.map { it.number.lowercase() }

        val callRegion = try {
            PhoneNumberUtil.getInstance().parse(number, region).let { PhoneNumberUtil.getInstance().getRegionCodeForNumber(it) }
        } catch (e: Exception) {
            region
        }
        val countryName = try {
            Locale.Builder().setRegion(callRegion).build().displayCountry.lowercase()
        } catch (e: Exception) {
            ""
        }

        val isNumberBlocked = key != null && (key in blockedNumbers || dao.isBlocked(key) > 0)
        val isNameBlocked = name != null && blockedNames.any { name.lowercase().contains(it) }
        val isCountryBlocked = countryName in blockedCountries || callRegion.lowercase() in blockedCountries
        val listed = isNumberBlocked || isNameBlocked || isCountryBlocked

        val hidden = number.isEmpty() || details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED
        val action = Prefs.actionOf(this)
        val spamScamType = key?.let { dao.find(it)?.lowercase() }
        val isSpamScam = spamScamType == "spam" || spamScamType == "scam"
        val matched = name == null && (
                (Prefs.flag(this, "spamScam") && isSpamScam) ||
                        Prefs.flag(this, "unsaved") ||
                        (Prefs.flag(this, "unknown") && hidden) ||
                        (Prefs.flag(this, "foreign") && isForeign(number, region)) ||
                        (Prefs.flag(this, "business") && isBusiness(number, region))
                )

        if (listed || (matched && action != "popup")) {
            if (!listed && action == "block" && key != null) dao.block(BlockedNumber(key, ""))
            logCall(dao, key, number, name, "Blocked")
            dao.addHangup(HangupEntry(number = key ?: number, time = System.currentTimeMillis(), secondsSaved = 15))
            respondToCall(
                details,
                CallResponse.Builder().setDisallowCall(true).setRejectCall(true).setSkipNotification(true).build()
            )
            return
        }

        if (Settings.canDrawOverlays(this) && (Prefs.lookupEnabled(this) || matched)) {
            showOverlay(number, name)
            if (name == null && key != null) {
                Reports.lookup(key) {
                    dao.insert(SpamNumber(key, it))
                }
                Skip.check("+$key") {
                    dao.insert(SpamNumber(key, "spam"))
                }
            }
        }
        logCall(dao, key, number, name, "Incoming")
        respondToCall(details, CallResponse.Builder().build())
    }

    private fun isForeign(number: String, region: String): Boolean {
        val util = PhoneNumberUtil.getInstance()
        return try {
            util.parse(number, region).countryCode != util.getCountryCodeForRegion(region)
        } catch (e: Exception) {
            false
        }
    }

    private fun isBusiness(number: String, region: String): Boolean {
        val util = PhoneNumberUtil.getInstance()
        return try {
            util.getNumberType(util.parse(number, region)) in setOf(
                PhoneNumberType.TOLL_FREE,
                PhoneNumberType.SHARED_COST,
                PhoneNumberType.UAN,
                PhoneNumberType.PREMIUM_RATE,
                PhoneNumberType.VOIP
            )
        } catch (e: Exception) {
            false
        }
    }

    private fun showOverlay(number: String, contactName: String?) {
        startService(
            Intent(this, OverlayService::class.java)
                .putExtra("number", number)
                .putExtra("contactName", contactName)
        )
    }

    private fun contactName(number: String): String? {
        if (number.isEmpty()) return null
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(number)
        )
        contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
        )?.use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun logCall(dao: SpamDao, key: String?, number: String, name: String?, status: String) {
        if (key == null) return
        dao.addCall(CallEntry(number = key, raw = number, name = name.orEmpty(), status = status, time = System.currentTimeMillis()))
        dao.trimCalls()
    }
}