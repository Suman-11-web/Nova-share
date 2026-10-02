package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.TransferDirection
import com.example.data.model.TransferSession
import com.example.data.model.TransferStatus
import com.example.network.NetworkUtils
import com.example.ui.theme.NovaDarkOutline
import com.example.ui.theme.NovaDarkSurface
import com.example.ui.theme.NovaDarkSurfaceVariant
import com.example.ui.theme.NovaError
import com.example.ui.theme.NovaOnPrimary
import com.example.ui.theme.NovaPrimary
import com.example.ui.theme.NovaSuccess
import com.example.ui.theme.NovaTextMuted
import com.example.ui.theme.NovaTextPrimary
import com.example.ui.theme.NovaTextSecondary

@Composable
fun TransferProgressCard(
    session: TransferSession,
    onPauseResumeClicked: () -> Unit = {},
    onCancelClicked: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val progress = if (session.totalBytes > 0) {
        (session.bytesTransferred.toFloat() / session.totalBytes.toFloat()).coerceIn(0f, 1f)
    } else 0f

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(300),
        label = "progress"
    )

    val directionIcon = if (session.direction == TransferDirection.SEND) {
        Icons.Default.ArrowUpward
    } else {
        Icons.Default.ArrowDownward
    }

    val directionText = if (session.direction == TransferDirection.SEND) "Sending to" else "Receiving from"

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(NovaPrimary.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = directionIcon,
                            contentDescription = directionText,
                            tint = NovaPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "$directionText ${session.deviceName}",
                            color = NovaTextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Text(
                            text = "${session.files.size} file(s) • ${NetworkUtils.formatFileSize(session.totalBytes)}",
                            color = NovaTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                // Status Badge
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = when (session.status) {
                        TransferStatus.TRANSFERRING -> NovaPrimary.copy(alpha = 0.2f)
                        TransferStatus.COMPLETED -> NovaSuccess.copy(alpha = 0.2f)
                        else -> NovaDarkSurfaceVariant
                    }
                ) {
                    Text(
                        text = session.status.name,
                        color = when (session.status) {
                            TransferStatus.TRANSFERRING -> NovaPrimary
                            TransferStatus.COMPLETED -> NovaSuccess
                            else -> NovaTextPrimary
                        },
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Progress Bar
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = NovaPrimary,
                trackColor = NovaDarkSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Details & Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${(progress * 100).toInt()}% (${NetworkUtils.formatFileSize(session.bytesTransferred)} / ${NetworkUtils.formatFileSize(session.totalBytes)})",
                        color = NovaTextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (session.status == TransferStatus.TRANSFERRING) {
                        Text(
                            text = "Speed: ${NetworkUtils.formatSpeed(session.speedBytesPerSec)} • ETA: ${NetworkUtils.formatDuration(session.etaSeconds)}",
                            color = NovaPrimary,
                            fontSize = 11.sp
                        )
                    } else if (session.status == TransferStatus.COMPLETED) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.VerifiedUser,
                                contentDescription = "Verified Integrity",
                                tint = NovaSuccess,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "End-to-End SHA-256 Verified • Zero-loss",
                                color = NovaSuccess,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Row {
                    if (session.status == TransferStatus.TRANSFERRING || session.status == TransferStatus.PAUSED) {
                        IconButton(
                            onClick = onPauseResumeClicked,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (session.status == TransferStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.Pause,
                                contentDescription = "Pause or Resume",
                                tint = NovaTextPrimary
                            )
                        }
                        IconButton(
                            onClick = onCancelClicked,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cancel Transfer",
                                tint = NovaError
                            )
                        }
                    }
                }
            }
        }
    }
}
