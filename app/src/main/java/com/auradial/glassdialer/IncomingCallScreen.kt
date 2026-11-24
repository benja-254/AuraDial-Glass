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
                modifier = Modifier.fill
