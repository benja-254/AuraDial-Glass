package com.auradial.glassdialer

import android.graphics.BitmapFactory
import android.telecom.Call
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun CallScreen(
    callerName: String,
    callerNumber: String,
    callState: Int,
    elapsedSeconds: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    audioRouteLabel: String = "Earpiece",
    showBanner: Boolean,
    onToggleBanner: () -> Unit,
    onAnswer: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onCycleAudioRoute: () -> Unit = onToggleSpeaker,
    onHoldCall: (Boolean) -> Unit = {},
    onAddCall: () -> Unit = {},
    onViewContact: () -> Unit = {},
    onSendDtmf: (Char) -> Unit,
    onSendMessage: () -> Unit
) {
    val isRinging = callState == Call.STATE_RINGING

    if (isRinging && showBanner) {
        IncomingCallBannerOverlay(
            callerName = callerName,
            callerNumber = callerNumber,
            onAnswer = onAnswer,
            onDecline = onEndCall,
            onSendMessage = onSendMessage,
            onExpandFullScreen = onToggleBanner
        )
    } else if (isRinging) {
        FullScreenIncomingView(
            callerName = callerName,
            callerNumber = callerNumber,
            onAnswer = onAnswer,
            onDecline = onEndCall,
            onShowBanner = onToggleBanner
        )
    } else {
        ActiveCallView(
            callerName = callerName,
            callerNumber = callerNumber,
            callState = callState,
            elapsedSeconds = elapsedSeconds,
            isMuted = isMuted,
            isSpeaker = isSpeaker,
            audioRouteLabel = audioRouteLabel,
            onEndCall = onEndCall,
            onToggleMute = onToggleMute,
            onToggleSpeaker = onToggleSpeaker,
            onCycleAudioRoute = onCycleAudioRoute,
            onHoldCall = onHoldCall,
            onAddCall = onAddCall,
            onViewContact = onViewContact,
            onSendMessage = onSendMessage,
            onSendDtmf = onSendDtmf
        )
    }
}

@Composable
private fun loadWallpaperBitmap(): ImageBitmap? {
    val context = LocalContext.current
    return remember {
        runCatching {
            val options = BitmapFactory.Options().apply { inSampleSize = 4 }
            BitmapFactory.decodeResource(context.resources, R.drawable.bg_luxury, options)?.asImageBitmap()
        }.getOrNull()
    }
}

@Composable
private fun FullScreenIncomingView(
    callerName: String,
    callerNumber: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onShowBanner: () -> Unit
) {
    val wallpaper = loadWallpaperBitmap()
    var offsetY by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 190f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0C))
    ) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.42f),
                            Color.Black.copy(alpha = 0.22f),
                            Color.Black.copy(alpha = 0.62f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 56.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(1.dp, Color.White.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
                    .clickable(onClick = onShowBanner)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Incoming call  •  Banner",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = callerName,
                color = Color.White,
                fontSize = 36.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (callerNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = callerNumber,
                    color = Color.White.copy(alpha = 0.78f),
                    fontSize = 16.sp
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(top = 60.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ChevronStack(
                icon = Icons.Rounded.KeyboardArrowUp,
                tint = Color(0xFF4CAF50),
                count = 3
            )

            Spacer(modifier = Modifier.height(10.dp))

            Box(
                modifier = Modifier
                    .offset { IntOffset(0, offsetY.roundToInt()) }
                    .size(78.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color.White, Color(0xFFE2E2E2))
                        )
                    )
                    .border(2.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                    .pointerInput(Unit) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                when {
                                    offsetY <= -swipeThreshold -> onAnswer()
                                    offsetY >= swipeThreshold -> onDecline()
                                    else -> offsetY = 0f
                                }
                            },
                            onVerticalDrag = { change, dragAmount ->
                                change.consume()
                                offsetY = (offsetY + dragAmount).coerceIn(-260f, 260f)
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.call),
                    contentDescription = "Slide up to answer, down to decline",
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            ChevronStack(
                icon = Icons.Rounded.KeyboardArrowDown,
                tint = Color(0xFFFF5252),
                count = 3
            )
        }
    }
}

@Composable
private fun ChevronStack(
    icon: ImageVector,
    tint: Color,
    count: Int
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        for (i in 0 until count) {
            val alpha = when (i) {
                0 -> 0.45f
                1 -> 0.75f
                else -> 1f
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint.copy(alpha = alpha),
                modifier = Modifier
                    .size(20.dp)
                    .offset(y = (-4 * i).dp)
            )
        }
    }
}

@Composable
private fun IncomingCallBannerOverlay(
    callerName: String,
    callerNumber: String,
    onAnswer: () -> Unit,
    onDecline: () -> Unit,
    onSendMessage: () -> Unit,
    onExpandFullScreen: () -> Unit
) {
    val wallpaper = loadWallpaperBitmap()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.35f))
    ) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 14.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.88f),
                            Color(0xFFEAECEF).copy(alpha = 0.82f)
                        )
                    )
                )
                .border(1.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(28.dp))
                .clickable(onClick = onExpandFullScreen)
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF1E2229)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = callerName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Incoming call",
                        color = Color(0xFF4A4E57),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = callerName,
                        color = Color(0xFF111318),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (callerNumber.isNotBlank()) {
                        Text(
                            text = "Mobile $callerNumber",
                            color = Color(0xFF3A3E46),
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E7D32))
                        .clickable(onClick = onAnswer),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.answer),
                        contentDescription = "Answer",
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onSendMessage)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Send message",
                        color = Color(0xFF1B1D21),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = "Send quick message",
                        tint = Color(0xFF3A3E46),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFD32F2F))
                        .clickable(onClick = onDecline),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.decline),
                        contentDescription = "Decline",
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActiveCallView(
    callerName: String,
    callerNumber: String,
    callState: Int,
    elapsedSeconds: Int,
    isMuted: Boolean,
    isSpeaker: Boolean,
    audioRouteLabel: String,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onCycleAudioRoute: () -> Unit,
    onHoldCall: (Boolean) -> Unit,
    onAddCall: () -> Unit,
    onViewContact: () -> Unit,
    onSendMessage: () -> Unit,
    onSendDtmf: (Char) -> Unit
) {
    val wallpaper = loadWallpaperBitmap()
    var isRecording by remember { mutableStateOf(false) }
    var isOnHold by remember { mutableStateOf(callState == Call.STATE_HOLDING) }
    var showDialPad by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var dtmfInput by remember { mutableStateOf("") }

    val formattedTimer = remember(elapsedSeconds) {
        val mins = elapsedSeconds / 60
        val secs = elapsedSeconds % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0D12))
    ) {
        if (wallpaper != null) {
            Image(
                bitmap = wallpaper,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color(0xFF0B0E14).copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.16f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "HD",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = when {
                            isOnHold || callState == Call.STATE_HOLDING -> "On hold  $formattedTimer"
                            callState == Call.STATE_DIALING -> "Calling..."
                            else -> formattedTimer
                        },
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    IconButton(onClick = { showMoreMenu = true }) {
                        Icon(
                            imageVector = Icons.Rounded.MoreVert,
                            contentDescription = "More call options",
                            tint = Color.White.copy(alpha = 0.9f)
                        )
                    }

                    DropdownMenu(
                        expanded = showMoreMenu,
                        onDismissRequest = { showMoreMenu = false },
                        containerColor = Color(0xFF1F2124),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isRecording) "Stop recording" else "Record call",
                                    color = Color.White
                                )
                            },
                            onClick = {
                                isRecording = !isRecording
                                showMoreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = if (isOnHold) "Resume call" else "Hold call",
                                    color = Color.White
                                )
                            },
                            onClick = {
                                val nextHold = !isOnHold
                                isOnHold = nextHold
                                onHoldCall(nextHold)
                                showMoreMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("View contact", color = Color.White) },
                            onClick = {
                                showMoreMenu = false
                                onViewContact()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Send message", color = Color.White) },
                            onClick = {
                                showMoreMenu = false
                                onSendMessage()
                            }
                        )
                    }
                }
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (callerNumber.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = callerNumber,
                        color = Color.White.copy(alpha = 0.72f),
                        fontSize = 15.sp
                    )
                }
                if (isRecording) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "● Recording",
                        color = Color(0xFFFF5252),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color(0xFF181A1F).copy(alpha = 0.88f))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(28.dp))
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showDialPad) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (dtmfInput.isNotEmpty()) {
                            Text(
                                text = dtmfInput,
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        val rows = listOf(
                            listOf('1', '2', '3'),
                            listOf('4', '5', '6'),
                            listOf('7', '8', '9'),
                            listOf('*', '0', '#')
                        )
                        rows.forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row.forEach { ch ->
                                    Box(
                                        modifier = Modifier
                                            .size(50.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.1f))
                                            .clickable {
                                                dtmfInput += ch
                                                onSendDtmf(ch)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = ch.toString(),
                                            color = Color.White,
                                            fontSize = 18.sp
                                        )
                                    }
                                }
                            }
                        }
                        Text(
                            text = "Hide keypad",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 12.sp,
                            modifier = Modifier
                                .padding(top = 4.dp)
                                .clickable { showDialPad = false }
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .clickable(onClick = onAddCall)
                                .padding(vertical = 4.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.add),
                                contentDescription = "Add call",
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Add call",
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        GridCallAction(
                            icon = Icons.Rounded.Videocam,
                            label = "Video call",
                            active = false,
                            onClick = onToggleSpeaker,
                            modifier = Modifier.weight(1f)
                        )
                        GridCallAction(
                            icon = Icons.Rounded.BluetoothAudio,
                            label = "$audioRouteLabel  >",
                            active = audioRouteLabel == "Bluetooth",
                            onClick = onCycleAudioRoute,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.Top
                    ) {
                        GridCallAction(
                            icon = Icons.Rounded.VolumeUp,
                            label = "Speaker",
                            active = isSpeaker,
                            onClick = onToggleSpeaker,
                            modifier = Modifier.weight(1f)
                        )
                        GridCallAction(
                            icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                            label = "Mute",
                            active = isMuted,
                            onClick = onToggleMute,
                            modifier = Modifier.weight(1f)
                        )
                        GridCallAction(
                            icon = Icons.Rounded.Dialpad,
                            label = "Keypad",
                            active = false,
                            onClick = { showDialPad = true },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFE53935))
                        .clickable(onClick = onEndCall),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.hang_up),
                        contentDescription = "End call",
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCallAction(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (active) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.92f),
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = if (active) Color(0xFF4FC3F7) else Color.White.copy(alpha = 0.85f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
