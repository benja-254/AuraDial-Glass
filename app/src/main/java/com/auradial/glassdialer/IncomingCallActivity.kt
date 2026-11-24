package com.auradial.glassdialer

import android.os.Bundle
import android.telecom.VideoProfile
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier

class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isBusyMode = intent.getBooleanExtra("IS_BUSY_MODE", false)
        val liveCall = CustomInCallService.currentCall

        val number = liveCall?.details?.handle?.schemeSpecificPart
            ?: intent.getStringExtra("TEST_NUMBER")
            ?: "Unknown Number"
        val name = liveCall?.details?.callerDisplayName?.takeIf { it.isNotBlank() }
            ?: intent.getStringExtra("TEST_NAME")
            ?: number

        setContent {
            Box(modifier = Modifier.fillMaxSize()) {
                IncomingCallUI(
                    callerName = name,
                    callerNumber = number,
                    isBusyBanner = isBusyMode,
                    onAnswer = {
                        liveCall?.answer(VideoProfile.STATE_AUDIO_ONLY)
                        finish()
                    },
                    onDecline = {
                        liveCall?.disconnect()
                        finish()
                    }
                )
            }
        }
    }
}
