package com.example.shieldcall

import android.content.Context
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.google.i18n.phonenumbers.NumberParseException
import com.google.i18n.phonenumbers.PhoneNumberUtil
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object CallGuard {
    private const val WINDOW = 3L * 24 * 60 * 60 * 1000

    fun isForeign(c: Context, number: String): Boolean {
        val util = PhoneNumberUtil.getInstance()
        val region = Reports.region(c)
        return try {
            val p = util.parse(if (number.startsWith("+")) number else "+$number", region)
            util.isValidNumber(p) && p.countryCode != util.getCountryCodeForRegion(region)
        } catch (e: Exception) {
            false
        }
    }

    suspend fun warning(c: Context, number: String): String? = withContext(Dispatchers.IO) {
        val region = Reports.region(c)
        val util = PhoneNumberUtil.getInstance()
        val p = try {
            util.parse(number, region)
        } catch (e: NumberParseException) {
            return@withContext null
        }
        if (!util.isValidNumber(p)) return@withContext null
        if (!Reports.loadContactInfo(c, number).name.isNullOrBlank()) return@withContext null
        val key = "${p.countryCode}${p.nationalNumber}"
        val dao = SpamDb.get(c).dao()
        val type = util.getNumberType(p)
        val foreign = p.countryCode != util.getCountryCodeForRegion(region)
        when {
            type == PhoneNumberType.PREMIUM_RATE || type == PhoneNumberType.SHARED_COST ->
                "This looks like a premium-rate number. Calls to these numbers can cost far more than a normal call."
            dao.entry(key)?.type == "scam" ->
                "This number has been reported as a scam."
            foreign && dao.recentMissed(key, System.currentTimeMillis() - WINDOW) > 0 ->
                "This foreign number called you and hung up. That is a common \"one ring\" scam, and calling back can charge you a lot."
            else -> null
        }
    }
}

@Composable
fun rememberGuardedCall(place: (String) -> Unit): (String) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentPlace by rememberUpdatedState(place)
    var pending by remember { mutableStateOf<Pair<String, String>?>(null) }

    pending?.let { (number, reason) ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Be careful before calling") },
            text = { Text(reason) },
            confirmButton = {
                Button(
                    onClick = {
                        pending = null
                        currentPlace(number)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Call anyway") }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Cancel") } }
        )
    }

    val guarded: (String) -> Unit = { number ->
        scope.launch {
            val warning = CallGuard.warning(context, number)
            if (warning == null) currentPlace(number) else pending = number to warning
        }
    }
    return guarded
}