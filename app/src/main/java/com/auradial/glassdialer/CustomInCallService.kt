package com.auradial.glassdialer

import android.content.Intent
import android.os.PowerManager
import android.telecom.Call
import android.telecom.InCallService

class CustomInCallService : InCallService() {

    companion object {
        var currentCall: Call? = null
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call

        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        val isScreenInteractive = powerManager.isInteractive

        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("IS_BUSY_MODE", isScreenInteractive)
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        if (currentCall == call) {
            currentCall = null
        }
    }
}
