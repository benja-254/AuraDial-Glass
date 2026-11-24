package com.auradial.glassdialer

import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.VideoProfile
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*

class IncomingCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }

        setContent {
            var activeCall by remember { mutableStateOf(CustomInCallService.currentCall) }

            DisposableEffect(Unit) {
                val listener: (Call?) -> Unit = { updated ->
                    activeCall = updated
                    if (updated == null) {
                        finish()
                    }
                }
                CustomInCallService.onCallStateChanged = listener
                onDispose {
                    if (CustomInCallService.onCallStateChanged == listener) {
                        CustomInCallService.onCallStateChanged = null
                    }
                }
            }

            val call = activeCall
            if (call == null) {
                LaunchedEffect(Unit) { finish() }
                return@setContent
            }

            val number = call.details?.handle?.schemeSpecificPart ?: "Unknown Number"
            val contactName = remember(number) {
                ContactsHelper.resolveContactName(this@IncomingCallActivity, number)
            }
            val displayName = contactName
                ?: call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
                ?: number

            val isRinging = call.state == Call.STATE_RINGING

            CallScreen(
                callerName = displayName,
                callerNumber = if (displayName == number) "" else number,
                isRinging = isRinging,
                onAnswer = { call.answer(VideoProfile.STATE_AUDIO_ONLY) },
                onEndCall = {
                    call.disconnect()
                    finish()
                }
            )
        }
    }
}
