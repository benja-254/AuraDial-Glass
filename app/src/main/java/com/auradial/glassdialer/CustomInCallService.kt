package com.auradial.glassdialer

import android.content.Intent
import android.media.AudioManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

class CustomInCallService : InCallService() {

    companion object {
        var instance: CustomInCallService? = null
        var currentCall: Call? = null
        var onCallStateChanged: ((Call?) -> Unit)? = null
        var onAudioStateChanged: ((isMuted: Boolean, isSpeaker: Boolean) -> Unit)? = null

        fun answerCurrentCall() {
            currentCall?.answer(android.telecom.VideoProfile.STATE_AUDIO_ONLY)
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

        fun sendDtmf(digit: Char) {
            val call = currentCall ?: return
            call.playDtmfTone(digit)
            call.stopDtmfTone()
        }

        private fun notifyAudioState() {
            val audioState = instance?.callAudioState
            val isMuted = audioState?.isMuted ?: false
            val isSpeaker = audioState?.route == CallAudioState.ROUTE_SPEAKER
            onAudioStateChanged?.invoke(isMuted, isSpeaker)
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
        onAudioStateChanged?.invoke(isMuted, isSpeaker)
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
