package com.example.shieldcall

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.telecom.PhoneAccount
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager

object SimChoice {
    private fun tm(c: Context) = c.getSystemService(TelecomManager::class.java)

    fun accounts(c: Context): List<PhoneAccountHandle> = try {
        tm(c).callCapablePhoneAccounts
    } catch (e: SecurityException) {
        emptyList()
    }

    fun label(c: Context, handle: PhoneAccountHandle?): String? {
        if (handle == null) return null
        return try {
            tm(c).getPhoneAccount(handle)?.label?.toString()
        } catch (e: SecurityException) {
            null
        }
    }

    fun selected(c: Context): PhoneAccountHandle? {
        val list = accounts(c)
        val saved = c.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("sim", null)
        return list.firstOrNull { it.id == saved }
            ?: try {
                tm(c).getDefaultOutgoingPhoneAccount(PhoneAccount.SCHEME_TEL)
            } catch (e: SecurityException) {
                null
            }
            ?: list.firstOrNull()
    }

    fun select(c: Context, handle: PhoneAccountHandle) {
        c.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().putString("sim", handle.id).apply()
    }

    fun placeUri(c: Context, uri: Uri) {
        PlanAlerts.beforeCall(c, uri)
        val extras = Bundle()
        if (accounts(c).size > 1) {
            selected(c)?.let { extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
        }
        tm(c).placeCall(uri, extras)
    }

    fun placeNumber(c: Context, number: String) {
        placeUri(c, Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null))
    }
}