package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.NovaPrimary
import com.example.ui.theme.NovaSecondary
import kotlin.math.abs

@Composable
fun QRCodeCanvas(
    qrContent: String,
    modifier: Modifier = Modifier,
    size: Dp = 210.dp,
    foregroundColor: Color = Color.White,
    backgroundColor: Color = Color(0xFF0F172A)
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .border(2.dp, NovaPrimary.copy(alpha = 0.6f), RoundedCornerShape(24.dp))
            .padding(18.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasWidth = this.size.width
            val canvasHeight = this.size.height
            val canvasSize = minOf(canvasWidth, canvasHeight)
            val gridCount = 21 // Standard QR Version 1 grid size
            val cellSize = canvasSize / gridCount

            // Hash the string to deterministically generate modules for display
            val hash = abs(qrContent.hashCode())

            // Render Modules
            for (row in 0 until gridCount) {
                for (col in 0 until gridCount) {
                    // Skip Corner Finder Patterns (7x7 zones in 3 corners)
                    val isTopLeft = row < 7 && col < 7
                    val isTopRight = row < 7 && col >= gridCount - 7
                    val isBottomLeft = row >= gridCount - 7 && col < 7

                    if (!isTopLeft && !isTopRight && !isBottomLeft) {
                        val bitIndex = (row * gridCount + col) % 31
                        val isFilled = ((hash shr bitIndex) and 1) == 1 || ((row * 2 + col * 3) % 5 == 0)
                        if (isFilled) {
                            drawRoundRect(
                                color = foregroundColor,
                                topLeft = Offset(col * cellSize + cellSize * 0.05f, row * cellSize + cellSize * 0.05f),
                                size = Size(cellSize * 0.9f, cellSize * 0.9f),
                                cornerRadius = CornerRadius(cellSize * 0.25f)
                            )
                        }
                    }
                }
            }

            // Draw Standard Modern QR Finder Patterns (Top-Left, Top-Right, Bottom-Left)
            fun drawFinderPattern(startCol: Int, startRow: Int) {
                val startX = startCol * cellSize
                val startY = startRow * cellSize
                val patternSize = 7 * cellSize

                // Outer Frame
                drawRoundRect(
                    color = NovaPrimary,
                    topLeft = Offset(startX, startY),
                    size = Size(patternSize, patternSize),
                    cornerRadius = CornerRadius(cellSize * 1.2f)
                )
                // Inner Gap
                drawRoundRect(
                    color = backgroundColor,
                    topLeft = Offset(startX + cellSize, startY + cellSize),
                    size = Size(patternSize - 2 * cellSize, patternSize - 2 * cellSize),
                    cornerRadius = CornerRadius(cellSize * 0.8f)
                )
                // Core Square
                drawRoundRect(
                    color = NovaSecondary,
                    topLeft = Offset(startX + 2 * cellSize, startY + 2 * cellSize),
                    size = Size(patternSize - 4 * cellSize, patternSize - 4 * cellSize),
                    cornerRadius = CornerRadius(cellSize * 0.5f)
                )
            }

            drawFinderPattern(0, 0)
            drawFinderPattern(gridCount - 7, 0)
            drawFinderPattern(0, gridCount - 7)
        }
    }
}
