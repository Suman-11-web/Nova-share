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
import com.example.ui.theme.*
import com.example.util.RealDeviceStorage

@Composable
fun StorageBar(
    storage: RealDeviceStorage?,
    modifier: Modifier = Modifier
) {
    val total = storage?.totalBytes ?: (64L * 1024 * 1024 * 1024)
    val used = storage?.usedBytes ?: (20L * 1024 * 1024 * 1024)
    val images = storage?.imagesBytes ?: 0L
    val videos = storage?.videosBytes ?: 0L
    val audio = storage?.audioBytes ?: 0L
    val docs = storage?.docsBytes ?: 0L
    val other = storage?.otherBytes ?: (used - images - videos - audio - docs).coerceAtLeast(0L)

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
                    text = "${NetworkUtils.formatFileSize(used)} used of ${NetworkUtils.formatFileSize(total)}",
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
                val imgWeight = (images.toFloat() / total.toFloat()).coerceAtLeast(0.02f)
                val vidWeight = (videos.toFloat() / total.toFloat()).coerceAtLeast(0.02f)
                val audWeight = (audio.toFloat() / total.toFloat()).coerceAtLeast(0.02f)
                val docWeight = (docs.toFloat() / total.toFloat()).coerceAtLeast(0.02f)
                val othWeight = (other.toFloat() / total.toFloat()).coerceAtLeast(0.05f)

                Box(
                    modifier = Modifier
                        .weight(imgWeight)
                        .fillMaxHeight()
                        .background(NovaPrimary)
                )
                Box(
                    modifier = Modifier
                        .weight(vidWeight)
                        .fillMaxHeight()
                        .background(NovaSecondary)
                )
                Box(
                    modifier = Modifier
                        .weight(audWeight)
                        .fillMaxHeight()
                        .background(NovaTertiary)
                )
                Box(
                    modifier = Modifier
                        .weight(docWeight)
                        .fillMaxHeight()
                        .background(NovaSuccess)
                )
                Box(
                    modifier = Modifier
                        .weight(othWeight)
                        .fillMaxHeight()
                        .background(NovaWarning)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Storage Category Legend with actual sizes
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                StorageLegendItem(label = "Images", size = images, color = NovaPrimary)
                StorageLegendItem(label = "Videos", size = videos, color = NovaSecondary)
                StorageLegendItem(label = "Audio", size = audio, color = NovaTertiary)
                StorageLegendItem(label = "Docs", size = docs, color = NovaSuccess)
                StorageLegendItem(label = "Other", size = other, color = NovaWarning)
            }
        }
    }
}

@Composable
private fun StorageLegendItem(label: String, size: Long, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Column {
            Text(text = label, color = NovaTextSecondary, fontSize = 11.sp)
            if (size > 0) {
                Text(text = NetworkUtils.formatFileSize(size), color = NovaTextMuted, fontSize = 9.sp)
            }
        }
    }
}
