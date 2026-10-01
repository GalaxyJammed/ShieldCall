package com.example.shieldcall

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.telecom.Call
import android.telecom.CallScreeningService
import android.telecom.TelecomManager
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType

class ScreeningService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        if (details.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(details, CallResponse.Builder().build())
            return
        }
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val region = Reports.region(this)
        val key = Reports.key(number, region)
        val name = contactName(number)
        val dao = SpamDb.get(this).dao()
        val hidden = number.isEmpty() || details.handlePresentation != TelecomManager.PRESENTATION_ALLOWED
        val action = Prefs.actionOf(this)
        val matched = name == null && (
                Prefs.flag(this, "unsaved") ||
                        (Prefs.flag(this, "unknown") && hidden) ||
                        (Prefs.flag(this, "foreign") && isForeign(number, region)) ||
                        (Prefs.flag(this, "business") && isBusiness(number, region))
                )
        val listed = key != null && dao.isBlocked(key) > 0

        if (listed || (matched && action != "popup")) {
            if (!listed && action == "block" && key != null) dao.block(BlockedNumber(key, ""))
            logCall(dao, key, number, name, "Blocked")
            respondToCall(
                details,
                CallResponse.Builder().setDisallowCall(true).setRejectCall(true).setSkipNotification(true).build()
            )
            return
        }

        if (Prefs.lookupEnabled(this) || matched) {
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