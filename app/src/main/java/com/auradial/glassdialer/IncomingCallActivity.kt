package com.auradial.glassdialer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import kotlinx.coroutines.delay

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
            val initialAudio = CustomInCallService.instance?.callAudioState
            var isMuted by remember { mutableStateOf(initialAudio?.isMuted ?: false) }
            var isSpeaker by remember {
                mutableStateOf(initialAudio?.route == CallAudioState.ROUTE_SPEAKER)
            }
            var showBanner by remember { mutableStateOf(false) }
            var elapsedSeconds by remember { mutableIntStateOf(0) }

            DisposableEffect(Unit) {
                val callListener: (Call?) -> Unit = { updated ->
                    activeCall = updated
                    if (updated == null) {
                        finish()
                    }
                }
                val audioListener: (Boolean, Boolean) -> Unit = { muted, speaker ->
                    isMuted = muted
                    isSpeaker = speaker
                }

                CustomInCallService.onCallStateChanged = callListener
                CustomInCallService.onAudioStateChanged = audioListener

                onDispose {
                    if (CustomInCallService.onCallStateChanged == callListener) {
                        CustomInCallService.onCallStateChanged = null
                    }
                    if (CustomInCallService.onAudioStateChanged == audioListener) {
                        CustomInCallService.onAudioStateChanged = null
                    }
                }
            }

            val call = activeCall
            if (call == null) {
                LaunchedEffect(Unit) { finish() }
                return@setContent
            }

            LaunchedEffect(call.state) {
                if (call.state == Call.STATE_ACTIVE) {
                    showBanner = false
                    elapsedSeconds = 0
                    while (true) {
                        delay(1000L)
                        elapsedSeconds += 1
                    }
                } else {
                    elapsedSeconds = 0
                }
            }

            val number = call.details?.handle?.schemeSpecificPart ?: "Unknown"
            val contactName = remember(number) {
                ContactsHelper.resolveContactName(this@IncomingCallActivity, number)
            }
            val displayName = contactName
                ?: call.details?.callerDisplayName?.takeIf { it.isNotBlank() }
                ?: number

            CallScreen(
                callerName = displayName,
                callerNumber = if (displayName == number) "" else number,
                callState = call.state,
                elapsedSeconds = elapsedSeconds,
                isMuted = isMuted,
                isSpeaker = isSpeaker,
                showBanner = showBanner,
                onToggleBanner = { showBanner = !showBanner },
                onAnswer = { CustomInCallService.answerCurrentCall() },
                onEndCall = {
                    CustomInCallService.endCurrentCall()
                    finish()
                },
                onToggleMute = {
                    val next = !isMuted
                    isMuted = next
                    CustomInCallService.toggleMute(next)
                },
                onToggleSpeaker = {
                    val next = !isSpeaker
                    isSpeaker = next
                    CustomInCallService.toggleSpeaker(next)
                },
                onSendDtmf = { digit ->
                    CustomInCallService.sendDtmf(digit)
                },
                onSendMessage = {
                    val smsIntent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
                        putExtra("sms_body", "Can't talk right now, I'll call you back.")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { startActivity(smsIntent) }
                    CustomInCallService.endCurrentCall()
                    finish()
                }
            )
        }
    }
}
