package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.DeviceType
import com.example.ui.theme.NovaPrimary
import com.example.ui.theme.NovaSecondary

@Composable
fun DeviceAvatar(
    deviceType: DeviceType,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    isOnline: Boolean = true
) {
    val icon = when (deviceType) {
        DeviceType.ANDROID -> Icons.Default.PhoneAndroid
        DeviceType.WINDOWS -> Icons.Default.Computer
        DeviceType.MACOS -> Icons.Default.Laptop
        DeviceType.LINUX -> Icons.Default.Terminal
        DeviceType.BROWSER -> Icons.Default.Language
    }

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        colors = listOf(NovaPrimary, NovaSecondary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = deviceType.name,
                tint = Color.White,
                modifier = Modifier.size(size * 0.55f)
            )
        }

        if (isOnline) {
            Box(
                modifier = Modifier
                    .size(size * 0.28f)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
                    .border(2.dp, Color(0xFF0F172A), CircleShape)
                    .align(Alignment.BottomEnd)
            )
        }
    }
}
