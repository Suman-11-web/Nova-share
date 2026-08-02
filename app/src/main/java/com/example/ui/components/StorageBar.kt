package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.NetworkUtils
import com.example.ui.theme.NovaDarkOutline
import com.example.ui.theme.NovaDarkSurface
import com.example.ui.theme.NovaDarkSurfaceVariant
import com.example.ui.theme.NovaPrimary
import com.example.ui.theme.NovaSecondary
import com.example.ui.theme.NovaSuccess
import com.example.ui.theme.NovaTertiary
import com.example.ui.theme.NovaTextMuted
import com.example.ui.theme.NovaTextPrimary
import com.example.ui.theme.NovaTextSecondary
import com.example.ui.theme.NovaWarning

@Composable
fun StorageBar(
    usedBytes: Long = 48L * 1024 * 1024 * 1024,
    totalBytes: Long = 128L * 1024 * 1024 * 1024,
    imagesBytes: Long = 12L * 1024 * 1024 * 1024,
    videosBytes: Long = 20L * 1024 * 1024 * 1024,
    audioBytes: Long = 4L * 1024 * 1024 * 1024,
    docsBytes: Long = 3L * 1024 * 1024 * 1024,
    modifier: Modifier = Modifier
) {
    val usedRatio = (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = NovaDarkSurface),
        border = androidx.compose.foundation.BorderStroke(1.dp, NovaDarkOutline.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Device Storage",
                    color = NovaTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "${NetworkUtils.formatFileSize(usedBytes)} used of ${NetworkUtils.formatFileSize(totalBytes)}",
                    color = NovaTextSecondary,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Multi-segment Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(NovaDarkSurfaceVariant)
            ) {
                Box(
                    modifier = Modifier
                        .weight((imagesBytes.toFloat() / totalBytes.toFloat()).coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(NovaPrimary)
                )
                Box(
                    modifier = Modifier
                        .weight((videosBytes.toFloat() / totalBytes.toFloat()).coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(NovaSecondary)
                )
                Box(
                    modifier = Modifier
                        .weight((audioBytes.toFloat() / totalBytes.toFloat()).coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(NovaTertiary)
                )
                Box(
                    modifier = Modifier
                        .weight((docsBytes.toFloat() / totalBytes.toFloat()).coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(NovaSuccess)
                )
                val otherBytes = (usedBytes - imagesBytes - videosBytes - audioBytes - docsBytes).coerceAtLeast(0)
                Box(
                    modifier = Modifier
                        .weight((otherBytes.toFloat() / totalBytes.toFloat()).coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(NovaWarning)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Storage Category Legend
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                StorageLegendItem(label = "Images", color = NovaPrimary)
                StorageLegendItem(label = "Videos", color = NovaSecondary)
                StorageLegendItem(label = "Audio", color = NovaTertiary)
                StorageLegendItem(label = "Docs", color = NovaSuccess)
                StorageLegendItem(label = "Other", color = NovaWarning)
            }
        }
    }
}

@Composable
private fun StorageLegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, color = NovaTextSecondary, fontSize = 11.sp)
    }
}
