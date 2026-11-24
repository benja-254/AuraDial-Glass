package com.auradial.glassdialer

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile

class CustomInCallService : InCallService() {

    companion object {
        var instance: CustomInCallService? = null
        var currentCall: Call? = null
        var onCallStateChanged: ((Call?) -> Unit)? = null
        var onAudioStateChanged: ((isMuted: Boolean, isSpeaker: Boolean, routeLabel: String) -> Unit)? = null

        fun answerCurrentCall() {
            currentCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
        }

        fun endCurrentCall() {
            currentCall?.disconnect()
        }

        fun toggleMute(mute: Boolean) {
            instance?.setMuted(mute)
            notifyAudioState()
        }

        fun toggleSpeaker(speaker: Boolean) {
            val route = if (speaker) {
                CallAudioState.ROUTE_SPEAKER
            } else {
                CallAudioState.ROUTE_EARPIECE
            }
            instance?.setAudioRoute(route)
            notifyAudioState()
        }

        fun cycleAudioRoute() {
            val currentRoute = instance?.callAudioState?.route ?: CallAudioState.ROUTE_EARPIECE
            val nextRoute = when (currentRoute) {
                CallAudioState.ROUTE_EARPIECE -> CallAudioState.ROUTE_SPEAKER
                CallAudioState.ROUTE_SPEAKER -> CallAudioState.ROUTE_BLUETOOTH
                else -> CallAudioState.ROUTE_EARPIECE
            }
            instance?.setAudioRoute(nextRoute)
            notifyAudioState()
        }

        fun holdCall(hold: Boolean) {
            val call = currentCall ?: return
            if (hold) call.hold() else call.unhold()
        }

        fun sendDtmf(digit: Char) {
            val call = currentCall ?: return
            call.playDtmfTone(digit)
            call.stopDtmfTone()
        }

        fun routeLabelFor(route: Int): String = when (route) {
            CallAudioState.ROUTE_SPEAKER -> "Speaker"
            CallAudioState.ROUTE_BLUETOOTH -> "Bluetooth"
            CallAudioState.ROUTE_WIRED_HEADSET -> "Headset"
            else -> "Earpiece"
        }

        private fun notifyAudioState() {
            val audioState = instance?.callAudioState
            val isMuted = audioState?.isMuted ?: false
            val route = audioState?.route ?: CallAudioState.ROUTE_EARPIECE
            val isSpeaker = route == CallAudioState.ROUTE_SPEAKER
            onAudioStateChanged?.invoke(isMuted, isSpeaker, routeLabelFor(route))
        }
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

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance == this) {
            instance = null
        }
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        val isMuted = audioState.isMuted
        val isSpeaker = audioState.route == CallAudioState.ROUTE_SPEAKER
        onAudioStateChanged?.invoke(isMuted, isSpeaker, routeLabelFor(audioState.route))
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        instance = this
        currentCall = call
        call.registerCallback(callCallback)
        onCallStateChanged?.invoke(call)
        notifyAudioState()

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
