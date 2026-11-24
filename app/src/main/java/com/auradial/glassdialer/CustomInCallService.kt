package com.auradial.glassdialer

import android.content.Intent
import android.telecom.Call
import android.telecom.InCallService

class CustomInCallService : InCallService() {

    companion object {
        var currentCall: Call? = null
        var onCallStateChanged: ((Call?) -> Unit)? = null
    }

    private val callCallback = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            super.onStateChanged(call, state)
            if (state == Call.STATE_DISCONNECTED) {
                if (currentCall == call) {
                    currentCall = null
                }
                onCallStateChanged?.invoke(currentCall)
            } else {
                currentCall = call
                onCallStateChanged?.invoke(call)
            }
        }
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        currentCall = call
        call.registerCallback(callCallback)
        onCallStateChanged?.invoke(call)

        val intent = Intent(this, IncomingCallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(callCallback)
        if (currentCall == call) {
            currentCall = null
        }
        onCallStateChanged?.invoke(currentCall)
    }
}
