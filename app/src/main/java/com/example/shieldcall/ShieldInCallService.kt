package com.example.shieldcall

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class ShieldInCallService : InCallService() {
    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val number = call.details?.handle?.schemeSpecificPart.orEmpty()
        startService(
            Intent(this, OverlayService::class.java)
                .putExtra("number", number)
        )
    }
}
