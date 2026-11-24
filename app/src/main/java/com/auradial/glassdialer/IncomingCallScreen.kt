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
import androidx.compose.material3.Icon
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
    showBanner: Boolean,
    onToggleBanner: () -> Unit,
    onAnswer: () -> Unit,
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
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
            onEndCall = onEndCall,
            onToggleMute = onToggleMute,
            onToggleSpeaker = onToggleSpeaker,
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
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.25f),
                            Color.Black.copy(alpha = 0.65f)
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
                Icon(
                    imageVector = Icons.Rounded.Call,
                    contentDescription = "Slide up to answer, down to decline",
                    tint = Color(0xFF111111),
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
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = "Answer",
                        tint = Color.White,
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
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "Decline",
                        tint = Color.White,
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
    onEndCall: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleSpeaker: () -> Unit,
    onSendDtmf: (Char) -> Unit
) {
    val wallpaper = loadWallpaperBitmap()
    var isRecording by remember { mutableStateOf(false) }
    var assistantActive by remember { mutableStateOf(false) }
    var showDialPad by remember { mutableStateOf(false) }
    var dtmfInput by remember { mutableStateOf("") }

    val formattedTimer = remember(elapsedSeconds) {
        val mins = elapsedSeconds / 60
        val secs = elapsedSeconds % 60
        String.format(Locale.US, "%02d:%02d", mins, secs)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A1220))
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
                            Color(0xFF0A1526).copy(alpha = 0.72f),
                            Color(0xFF090D16).copy(alpha = 0.88f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.14f))
                        .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isRecording) Color(0xFFFF5252) else Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formattedTimer,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                Text(
                    text = callerName,
                    color = Color.White,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
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

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = when {
                        assistantActive -> "AI Assistant active"
                        callState == Call.STATE_DIALING -> "Calling..."
                        else -> formattedTimer
                    },
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            if (showDialPad) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (dtmfInput.isNotEmpty()) {
                        Text(
                            text = dtmfInput,
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    val padRows = listOf(
                        listOf('1', '2', '3'),
                        listOf('4', '5', '6'),
                        listOf('7', '8', '9'),
                        listOf('*', '0', '#')
                    )
                    padRows.forEach { row ->
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            row.forEach { ch ->
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.14f))
                                        .clickable {
                                            dtmfInput += ch
                                            onSendDtmf(ch)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = ch.toString(),
                                        color = Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    GlassCallButton(
                        icon = if (isMuted) Icons.Rounded.MicOff else Icons.Rounded.Mic,
                        label = "Mute",
                        active = isMuted,
                        onClick = onToggleMute,
                        modifier = Modifier.weight(1f)
                    )
                    GlassCallButton(
                        icon = Icons.Rounded.GraphicEq,
                        label = if (isRecording) "Recording" else "Record",
                        active = isRecording,
                        onClick = { isRecording = !isRecording },
                        modifier = Modifier.weight(1f)
                    )
                    GlassCallButton(
                        icon = Icons.Rounded.AutoAwesome,
                        label = "AI Call\nAssistant",
                        active = assistantActive,
                        onClick = { assistantActive = !assistantActive },
                        modifier = Modifier.weight(1f)
                    )
                    GlassCallButton(
                        icon = Icons.Rounded.MoreVert,
                        label = "More",
                        active = false,
                        onClick = { showDialPad = true },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassCallButton(
                    icon = if (isSpeaker) Icons.Rounded.VolumeUp else Icons.Rounded.VolumeDown,
                    label = "Speaker",
                    active = isSpeaker,
                    onClick = onToggleSpeaker
                )

                Box(
                    modifier = Modifier
                        .width(128.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(Color(0xFFE53935))
                        .clickable(onClick = onEndCall),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallEnd,
                        contentDescription = "End call",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }

                GlassCallButton(
                    icon = Icons.Rounded.Dialpad,
                    label = "Dial pad",
                    active = showDialPad,
                    onClick = { showDialPad = !showDialPad }
                )
            }
        }
    }
}

@Composable
private fun GlassCallButton(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(
                    if (active) Color.White.copy(alpha = 0.34f)
                    else Color.White.copy(alpha = 0.14f)
                )
                .border(
                    width = 1.dp,
                    color = if (active) Color.White.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.22f),
                    shape = CircleShape
                )
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.88f),
            fontSize = 12.sp,
            lineHeight = 14.sp,
            maxLines = 2
        )
    }
}
