package com.example.inknote.ui.canvas.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inknote.data.model.RulerState
import com.example.inknote.geometry.GeometryEngine
import kotlin.math.*

@Composable
fun RulerOverlay(
    rulerState: RulerState,
    onUpdate: (Float, Float, Float) -> Unit,
    onClose: () -> Unit
) {
    if (!rulerState.isVisible) return

    val density = LocalDensity.current
    var centerPos by remember(rulerState.centerX, rulerState.centerY) {
        mutableStateOf(Offset(rulerState.centerX, rulerState.centerY))
    }
    var currentAngle by remember(rulerState.angleDegrees) {
        mutableFloatStateOf(rulerState.angleDegrees)
    }

    val rulerWidthDp = 420.dp
    val rulerHeightDp = 76.dp
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (centerPos.x - with(density) { (rulerWidthDp / 2).toPx() }).toInt(),
                    (centerPos.y - with(density) { (rulerHeightDp / 2).toPx() }).toInt()
                )
            }
            .rotate(currentAngle)
            .width(rulerWidthDp)
            .height(rulerHeightDp)
            .shadow(12.dp, RoundedCornerShape(12.dp))
            .background(Color(0xF0F8FAFC), RoundedCornerShape(12.dp))
            .border(1.5.dp, Color(0xFF4F46E5), RoundedCornerShape(12.dp))
            .pointerInput(centerPos, currentAngle) {
                // Two-finger rotate & pan anywhere on the ruler
                detectTransformGestures { _, pan, _, rotation ->
                    centerPos += pan
                    val raw = (currentAngle + rotation + 360f) % 360f
                    currentAngle = GeometryEngine.snapAngleMagnetically(raw)
                    onUpdate(centerPos.x, centerPos.y, currentAngle)
                }
            }
    ) {
        // Millimeter & Centimeter Measurement Scale
        Canvas(modifier = Modifier.fillMaxSize()) {
            val totalTicks = 200 // 20 cm in millimeters (10 ticks per cm)
            val startMargin = 32.dp.toPx()
            val endMargin = size.width - 32.dp.toPx()
            val tickStep = (endMargin - startMargin) / totalTicks.toFloat()

            val cmStyle = TextStyle(
                color = Color(0xFF1E293B),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            val mmStyle = TextStyle(
                color = Color(0xFF64748B),
                fontSize = 8.sp,
                fontWeight = FontWeight.Normal
            )

            // Top edge ticks
            for (i in 0..totalTicks) {
                val x = startMargin + i * tickStep
                val isMajorCm = i % 10 == 0
                val isHalfCm = i % 5 == 0

                val tickLength = when {
                    isMajorCm -> 16.dp.toPx()
                    isHalfCm -> 10.dp.toPx()
                    else -> 6.dp.toPx()
                }

                drawLine(
                    color = if (isMajorCm) Color(0xFF1E293B) else Color(0xFF94A3B8),
                    start = Offset(x, 0f),
                    end = Offset(x, tickLength),
                    strokeWidth = if (isMajorCm) 1.5f else 1f
                )

                // Numbers every centimeter
                if (isMajorCm) {
                    val cmValue = (i / 10).toString()
                    val textLayout = textMeasurer.measure(cmValue, cmStyle)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = cmValue,
                        topLeft = Offset(x - textLayout.size.width / 2f, tickLength + 2.dp.toPx()),
                        style = cmStyle
                    )
                }

                // Bottom edge millimeter ticks
                drawLine(
                    color = Color(0xFF94A3B8),
                    start = Offset(x, size.height),
                    end = Offset(x, size.height - (if (isMajorCm) 12.dp.toPx() else 6.dp.toPx())),
                    strokeWidth = 1f
                )
            }

            // Draw "cm" scale units watermark
            val unitMeasured = textMeasurer.measure("cm", mmStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = "cm",
                topLeft = Offset(startMargin - 12.dp.toPx(), 4.dp.toPx()),
                style = mmStyle
            )
        }

        // Center readout & move handle
        Row(
            modifier = Modifier
                .align(Alignment.Center)
                .background(Color(0xFFEEF2FF), RoundedCornerShape(12.dp))
                .border(1.dp, Color(0xFFC7D2FE), RoundedCornerShape(12.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        centerPos += dragAmount
                        onUpdate(centerPos.x, centerPos.y, currentAngle)
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${round(currentAngle).toInt()}°",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = Color(0xFF4338CA)
            )
            Text(
                text = "• 20 cm Ruler",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF64748B)
            )
        }

        // Direct Touch Rotation Grip on Right Edge (1:1 hand-motion drag to rotate)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 4.dp)
                .size(44.dp)
                .background(Color(0xFF6366F1).copy(alpha = 0.15f), CircleShape)
                .border(1.dp, Color(0xFF6366F1), CircleShape)
                .pointerInput(centerPos) {
                    var dragStartAngle = 0f
                    var initialAngle = 0f
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val rad = Math.toRadians(currentAngle.toDouble())
                            val cosA = cos(rad).toFloat()
                            val sinA = sin(rad).toFloat()
                            val localOffset = startOffset - Offset(22.dp.toPx(), 22.dp.toPx())
                            val rotatedX = localOffset.x * cosA - localOffset.y * sinA
                            val rotatedY = localOffset.x * sinA + localOffset.y * cosA
                            val touchScreen = centerPos + Offset(with(density) { (rulerWidthDp / 2 - 26.dp).toPx() } * cosA, with(density) { (rulerWidthDp / 2 - 26.dp).toPx() } * sinA) + Offset(rotatedX, rotatedY)
                            dragStartAngle = Math.toDegrees(atan2((touchScreen.y - centerPos.y).toDouble(), (touchScreen.x - centerPos.x).toDouble())).toFloat()
                            initialAngle = currentAngle
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val rad = Math.toRadians(currentAngle.toDouble())
                            val cosA = cos(rad).toFloat()
                            val sinA = sin(rad).toFloat()
                            val touchScreen = centerPos + Offset(with(density) { (rulerWidthDp / 2 - 26.dp).toPx() } * cosA, with(density) { (rulerWidthDp / 2 - 26.dp).toPx() } * sinA) + change.position
                            val currentTouchAngle = Math.toDegrees(atan2((touchScreen.y - centerPos.y).toDouble(), (touchScreen.x - centerPos.x).toDouble())).toFloat()
                            val delta = currentTouchAngle - dragStartAngle
                            val raw = (initialAngle + delta + 360f) % 360f
                            currentAngle = GeometryEngine.snapAngleMagnetically(raw)
                            onUpdate(centerPos.x, centerPos.y, currentAngle)
                        }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Sync,
                contentDescription = "Rotate Ruler (Drag to Rotate)",
                tint = Color(0xFF4338CA),
                modifier = Modifier.size(20.dp)
            )
        }

        // Close Button on left edge
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp)
                .size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close Ruler",
                tint = Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
