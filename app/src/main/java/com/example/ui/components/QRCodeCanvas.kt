package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.NovaDarkOutline
import com.example.ui.theme.NovaDarkSurface
import com.example.ui.theme.NovaPrimary
import com.example.ui.theme.NovaTextSecondary
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Generates an authentic, standard QR Code Bitmap using ZXing
 * with error correction, quiet zone, and high contrast for instantaneous camera scanning.
 */
suspend fun generateStandardQrBitmap(
    content: String,
    sizePx: Int = 512
): Bitmap = withContext(Dispatchers.Default) {
    if (content.isBlank()) {
        val emptyBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        emptyBitmap.eraseColor(AndroidColor.WHITE)
        return@withContext emptyBitmap
    }

    try {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.MARGIN to 1,
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M
        )
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val pixels = IntArray(width * height)

        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (bitMatrix.get(x, y)) {
                    AndroidColor.BLACK
                } else {
                    AndroidColor.WHITE
                }
            }
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        bitmap
    } catch (e: Exception) {
        val fallback = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        fallback.eraseColor(AndroidColor.WHITE)
        fallback
    }
}

@Composable
fun QRCodeCanvas(
    qrContent: String,
    modifier: Modifier = Modifier,
    size: Dp = 210.dp,
    foregroundColor: Color = Color.Black,
    backgroundColor: Color = Color.White
) {
    var qrBitmap by remember(qrContent) { mutableStateOf<Bitmap?>(null) }
    var isGenerating by remember(qrContent) { mutableStateOf(true) }

    LaunchedEffect(qrContent) {
        isGenerating = true
        qrBitmap = generateStandardQrBitmap(qrContent, 512)
        isGenerating = false
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .border(2.5.dp, NovaPrimary.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isGenerating || qrBitmap == null) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = NovaPrimary,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Generating QR...",
                    color = Color.DarkGray,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            qrBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = "Standard Pairing QR Code",
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
