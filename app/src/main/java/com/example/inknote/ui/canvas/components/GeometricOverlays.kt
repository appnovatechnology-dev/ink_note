package com.example.inknote.ui.canvas.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.inknote.data.model.CompassState
import com.example.inknote.data.model.ProtractorState
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
        // Ticks, numbered scale marks, and centimeter labels Canvas
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val totalCm = 20
            val totalTicks = totalCm * 2 // half-cm ticks
            val step = (size.width - 32.dp.toPx()) / totalTicks
            val startMargin = 16.dp.toPx()

            val textStyle = TextStyle(
                color = Color(0xFF334155),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            val mmStyle = TextStyle(
                color = Color(0xFF64748B),
                fontSize = 7.sp
            )

            // Draw centimeter ticks and numbers
            for (i in 0..totalTicks) {
                val x = startMargin + i * step
                val isMajorCm = i % 2 == 0
                val cmValue = i / 2

                val tickHeight = if (isMajorCm) 18.dp.toPx() else 10.dp.toPx()

                // Top edge tick
                drawLine(
                    color = if (isMajorCm) Color(0xFF1E293B) else Color(0xFF64748B),
                    start = Offset(x, 0f),
                    end = Offset(x, tickHeight),
                    strokeWidth = if (isMajorCm) 2f else 1f
                )

                // Numbered label for each major centimeter (0, 1, 2, 3, ...)
                if (isMajorCm) {
                    val label = "$cmValue"
                    val measured = textMeasurer.measure(label, textStyle)
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(x - (measured.size.width / 2f), tickHeight + 2.dp.toPx()),
                        style = textStyle
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

@Composable
fun ProtractorOverlay(
    protractorState: ProtractorState,
    onUpdate: (Float, Float, Float) -> Unit,
    onClose: () -> Unit
) {
    if (!protractorState.isVisible) return

    val density = LocalDensity.current
    var centerPos by remember(protractorState.centerX, protractorState.centerY) {
        mutableStateOf(Offset(protractorState.centerX, protractorState.centerY))
    }
    var rotationAngle by remember(protractorState.rotationDegrees) {
        mutableFloatStateOf(protractorState.rotationDegrees)
    }

    val sizeDp = 300.dp
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    (centerPos.x - with(density) { (sizeDp / 2).toPx() }).toInt(),
                    (centerPos.y - with(density) { (sizeDp / 2).toPx() }).toInt()
                )
            }
            .rotate(rotationAngle)
            .size(sizeDp)
            .pointerInput(centerPos, rotationAngle) {
                // Two-finger rotate & pan gesture
                detectTransformGestures { _, pan, _, rotation ->
                    centerPos += pan
                    val raw = (rotationAngle + rotation + 360f) % 360f
                    rotationAngle = GeometryEngine.snapAngleMagnetically(raw)
                    onUpdate(centerPos.x, centerPos.y, rotationAngle)
                }
            }
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(centerPos) {
                    var dragStartAngle = 0f
                    var initialRotation = 0f
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val rad = Math.toRadians(rotationAngle.toDouble())
                            val cosA = cos(rad).toFloat()
                            val sinA = sin(rad).toFloat()
                            val diff = startOffset - center
                            val rotX = diff.x * cosA - diff.y * sinA
                            val rotY = diff.x * sinA + diff.y * cosA
                            val screenTouch = centerPos + Offset(rotX, rotY)
                            dragStartAngle = Math.toDegrees(atan2((screenTouch.y - centerPos.y).toDouble(), (screenTouch.x - centerPos.x).toDouble())).toFloat()
                            initialRotation = rotationAngle
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val rad = Math.toRadians(rotationAngle.toDouble())
                            val cosA = cos(rad).toFloat()
                            val sinA = sin(rad).toFloat()
                            val diff = change.position - center
                            val rotX = diff.x * cosA - diff.y * sinA
                            val rotY = diff.x * sinA + diff.y * cosA
                            val screenTouch = centerPos + Offset(rotX, rotY)
                            val currentTouchAngle = Math.toDegrees(atan2((screenTouch.y - centerPos.y).toDouble(), (screenTouch.x - centerPos.x).toDouble())).toFloat()
                            val delta = currentTouchAngle - dragStartAngle
                            val raw = (initialRotation + delta + 360f) % 360f
                            rotationAngle = GeometryEngine.snapAngleMagnetically(raw)
                            onUpdate(centerPos.x, centerPos.y, rotationAngle)
                        }
                    )
                }
        ) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = size.width / 2f - 12.dp.toPx()

            // Outer semicircle background
            drawArc(
                color = Color(0xEEF8FAFC),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )

            // Inner transparent arc band
            drawArc(
                color = Color(0x226366F1),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = true,
                size = androidx.compose.ui.geometry.Size((radius * 0.7f) * 2, (radius * 0.7f) * 2),
                topLeft = Offset(center.x - radius * 0.7f, center.y - radius * 0.7f)
            )

            // Outer border
            drawArc(
                color = Color(0xFF4F46E5),
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                style = Stroke(width = 2.5.dp.toPx()),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                topLeft = Offset(center.x - radius, center.y - radius)
            )

            // Baseline
            drawLine(
                color = Color(0xFF4F46E5),
                start = Offset(center.x - radius, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = 2.5.dp.toPx()
            )

            val degreeStyle = TextStyle(
                color = Color(0xFF1E293B),
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold
            )
            val innerDegreeStyle = TextStyle(
                color = Color(0xFF4F46E5),
                fontSize = 7.5.sp,
                fontWeight = FontWeight.SemiBold
            )

            // Degree tick marks and numbers (0° to 180°)
            for (deg in 0..180 step 5) {
                val rad = Math.toRadians((180 - deg).toDouble())
                val isMajor10 = deg % 10 == 0
                val isSpecial = deg % 45 == 0 || deg == 90 || deg % 30 == 0
                val tickLen = if (isSpecial) 20.dp.toPx() else if (isMajor10) 14.dp.toPx() else 8.dp.toPx()

                val x1 = (center.x + radius * cos(rad)).toFloat()
                val y1 = (center.y - radius * sin(rad)).toFloat()
                val x2 = (center.x + (radius - tickLen) * cos(rad)).toFloat()
                val y2 = (center.y - (radius - tickLen) * sin(rad)).toFloat()

                drawLine(
                    color = if (deg == 90 || deg == 0 || deg == 180) Color(0xFFDC2626)
                    else if (isMajor10) Color(0xFF1E293B)
                    else Color(0xFF94A3B8),
                    start = Offset(x1, y1),
                    end = Offset(x2, y2),
                    strokeWidth = if (isSpecial) 2f else if (isMajor10) 1.5f else 1f
                )

                // Numbered degree label (Outer scale: 0°..180°)
                if (isMajor10 && deg % 30 == 0 || deg == 45 || deg == 135) {
                    val label = "$deg°"
                    val measured = textMeasurer.measure(label, degreeStyle)
                    val labelRadius = radius - tickLen - 8.dp.toPx()
                    val lx = (center.x + labelRadius * cos(rad)).toFloat() - (measured.size.width / 2f)
                    val ly = (center.y - labelRadius * sin(rad)).toFloat() - (measured.size.height / 2f)

                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(lx, ly),
                        style = degreeStyle
                    )
                }

                // Numbered inner scale (180°..0°)
                if (isMajor10 && (deg == 30 || deg == 60 || deg == 90 || deg == 120 || deg == 150)) {
                    val innerDeg = 180 - deg
                    val label = "$innerDeg"
                    val measured = textMeasurer.measure(label, innerDegreeStyle)
                    val innerRadius = radius * 0.72f
                    val lx = (center.x + innerRadius * cos(rad)).toFloat() - (measured.size.width / 2f)
                    val ly = (center.y - innerRadius * sin(rad)).toFloat() - (measured.size.height / 2f)

                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(lx, ly),
                        style = innerDegreeStyle
                    )
                }
            }

            // Center origin crosshair and indicator
            drawCircle(color = Color(0xFF4F46E5), radius = 5.dp.toPx(), center = center)
            drawCircle(color = Color.White, radius = 2.dp.toPx(), center = center)
        }

        // Center readout badge (drag to translate)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-32).dp)
                .background(Color(0xFF312E81), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        centerPos += dragAmount
                        onUpdate(centerPos.x, centerPos.y, rotationAngle)
                    }
                }
        ) {
            Text(
                text = "${round(rotationAngle).toInt()}° Protractor",
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        // Close action
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(32.dp)
                .background(Color(0xFFF1F5F9), CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xFF475569), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun CompassOverlay(
    compassState: CompassState,
    onUpdate: (Float, Float, Float, Float) -> Unit,
    onClose: () -> Unit
) {
    if (!compassState.isVisible) return

    val density = LocalDensity.current
    var pivot by remember(compassState.pivotX, compassState.pivotY) {
        mutableStateOf(Offset(compassState.pivotX, compassState.pivotY))
    }
    var radius by remember(compassState.radius) {
        mutableFloatStateOf(compassState.radius)
    }
    var angle by remember(compassState.currentAngleDegrees) {
        mutableFloatStateOf(compassState.currentAngleDegrees)
    }

    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(pivot, radius, angle) {
                // Two-finger pan & rotate
                detectTransformGestures { _, pan, _, rotation ->
                    pivot += pan
                    val raw = (angle + rotation + 360f) % 360f
                    angle = GeometryEngine.snapAngleMagnetically(raw)
                    onUpdate(pivot.x, pivot.y, radius, angle)
                }
            }
    ) {
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(pivot) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val diff = change.position - pivot
                        val dist = hypot(diff.x.toDouble(), diff.y.toDouble()).toFloat()
                        if (dist > 25f) {
                            radius = dist.coerceIn(40f, 450f)
                            angle = (Math.toDegrees(atan2(diff.y.toDouble(), diff.x.toDouble())).toFloat() + 360f) % 360f
                            onUpdate(pivot.x, pivot.y, radius, angle)
                        }
                    }
                }
        ) {
            val angleRad = Math.toRadians(angle.toDouble())
            val pencilX = (pivot.x + radius * cos(angleRad)).toFloat()
            val pencilY = (pivot.y + radius * sin(angleRad)).toFloat()

            // Center pivot crosshair
            drawCircle(color = Color(0xFFDC2626), radius = 7.dp.toPx(), center = pivot)
            drawCircle(color = Color.White, radius = 3.dp.toPx(), center = pivot)

            // Compass beam line
            drawLine(
                color = Color(0xFF4F46E5),
                start = pivot,
                end = Offset(pencilX, pencilY),
                strokeWidth = 3.5.dp.toPx()
            )

            // Dashed guide circle preview
            drawCircle(
                color = Color(0x444F46E5),
                radius = radius,
                center = pivot,
                style = Stroke(
                    width = 1.8.dp.toPx(),
                    pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )
            )

            // Sweep angle arc from 0 to angle
            drawArc(
                color = Color(0x336366F1),
                startAngle = 0f,
                sweepAngle = angle,
                useCenter = true,
                size = androidx.compose.ui.geometry.Size(radius * 0.6f, radius * 0.6f),
                topLeft = Offset(pivot.x - radius * 0.3f, pivot.y - radius * 0.3f)
            )

            // Numbered Radius & Angle on canvas
            val radiusCm = round((radius / 30f) * 10f) / 10f
            val radiusLabel = "r = ${radiusCm} cm (${round(angle).toInt()}°)"
            val midX = (pivot.x + pencilX) / 2f
            val midY = (pivot.y + pencilY) / 2f
            val labelStyle = TextStyle(
                color = Color(0xFF312E81),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
            val measured = textMeasurer.measure(radiusLabel, labelStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = radiusLabel,
                topLeft = Offset(midX - measured.size.width / 2f, midY - measured.size.height - 4.dp.toPx()),
                style = labelStyle
            )

            // Pencil tip
            drawCircle(color = Color(0xFF10B981), radius = 8.dp.toPx(), center = Offset(pencilX, pencilY))
            drawCircle(color = Color.White, radius = 3.5.dp.toPx(), center = Offset(pencilX, pencilY))
        }

        // Pivot drag handle (1 finger drag moves pivot)
        Box(
            modifier = Modifier
                .offset {
                    IntOffset((pivot.x - 24.dp.toPx()).toInt(), (pivot.y - 24.dp.toPx()).toInt())
                }
                .size(48.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        pivot += dragAmount
                        onUpdate(pivot.x, pivot.y, radius, angle)
                    }
                }
        )

        // Numbered Radius & Angle Control Card
        val radiusCm = round((radius / 30f) * 10f) / 10f
        Card(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (pivot.x - 110.dp.toPx()).toInt().coerceAtLeast(16),
                        (pivot.y - radius - 80.dp.toPx()).toInt().coerceAtLeast(16)
                    )
                }
                .shadow(8.dp, RoundedCornerShape(14.dp)),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
            shape = RoundedCornerShape(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Compass: r = $radiusCm cm (${round(angle).toInt()}°)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        color = Color(0xFF312E81)
                    )
                    IconButton(onClick = onClose, modifier = Modifier.size(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                    }
                }
                // Numbered Radius slider
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("1 cm", fontSize = 10.sp, color = Color.Gray)
                    Slider(
                        value = radius,
                        onValueChange = {
                            radius = it
                            onUpdate(pivot.x, pivot.y, radius, angle)
                        },
                        valueRange = 40f..350f,
                        modifier = Modifier.width(150.dp).height(24.dp)
                    )
                    Text("12 cm", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }
    }
}

