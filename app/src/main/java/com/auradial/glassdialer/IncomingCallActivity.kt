package com.auradial.glassdialer

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
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
            var audioRouteLabel by remember {
                mutableStateOf(
                    CustomInCallService.routeLabelFor(
                        initialAudio?.route ?: CallAudioState.ROUTE_EARPIECE
                    )
                )
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
                val audioListener: (Boolean, Boolean, String) -> Unit = { muted, speaker, route ->
                    isMuted = muted
                    isSpeaker = speaker
                    audioRouteLabel = route
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
                audioRouteLabel = audioRouteLabel,
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
                onCycleAudioRoute = {
                    CustomInCallService.cycleAudioRoute()
                },
                onHoldCall = { hold ->
                    CustomInCallService.holdCall(hold)
                },
                onAddCall = {
                    val dialIntent = Intent(this@IncomingCallActivity, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { startActivity(dialIntent) }
                },
                onViewContact = {
                    val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                        putExtra(ContactsContract.Intents.Insert.PHONE, number)
                        if (displayName != number) {
                            putExtra(ContactsContract.Intents.Insert.NAME, displayName)
                        }
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { startActivity(intent) }
                },
                onSendDtmf = { digit: Char ->
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
