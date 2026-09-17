package com.example.inknote.ui.canvas.components

import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import com.example.inknote.data.db.entities.PageEntity
import com.example.inknote.data.model.*
import com.example.inknote.geometry.GeometryEngine
import com.example.inknote.viewmodel.EraserMode
import java.io.File
import kotlin.math.*

data class LaserPoint(val offset: Offset, val timestamp: Long)

@Composable
fun InkDrawingCanvas(
    page: PageEntity?,
    strokes: List<VectorStroke>,
    shapes: List<GeometricShape>,
    textItems: List<CanvasTextItem>,
    mediaItems: List<CanvasMediaItem>,
    activeTool: ToolType,
    penColor: Color,
    penWidth: Float,
    highlighterColor: Color,
    highlighterWidth: Float,
    eraserMode: EraserMode,
    eraserWidth: Float,
    isMagicInkEnabled: Boolean,
    isGridSnapEnabled: Boolean,
    laserColor: Color = Color(0xFFEF4444),
    isLaserTailEnabled: Boolean = true,
    rulerState: RulerState,
    protractorState: ProtractorState,
    compassState: CompassState,
    activeShapeType: ShapeType,
    shapeStrokeColor: Color,
    shapeFillColor: Color,
    shapeStrokeWidth: Float,
    shapeIsFilled: Boolean,
    shapeSideCount: Int,
    playbackCurrentMs: Long,
    isPlayingAudio: Boolean,
    zoomScale: Float = 1.0f,
    isFitToScreen: Boolean = false,
    palmRejectionMode: PalmRejectionMode = PalmRejectionMode.STYLUS_ONLY,
    sharedStylusInputState: StylusInputState? = null,
    hasLassoSelection: Boolean = false,
    lassoSelectionBounds: Rect? = null,
    lassoSelectedStrokes: List<VectorStroke> = emptyList(),
    lassoSelectedShapes: List<GeometricShape> = emptyList(),
    lassoSelectedTextItems: List<CanvasTextItem> = emptyList(),
    lassoSelectedMediaItems: List<CanvasMediaItem> = emptyList(),
    onLassoComplete: ((List<Offset>) -> Unit)? = null,
    onMoveLasso: ((Float, Float) -> Unit)? = null,
    onDuplicateLasso: (() -> Unit)? = null,
    onDeleteLasso: (() -> Unit)? = null,
    onPinchZoom: ((Float) -> Unit)? = null,
    onAddStroke: (VectorStroke) -> Unit,
    onAddShape: (GeometricShape) -> Unit,
    onEraseAt: (Offset, Float) -> Unit,
    onAddTextItem: (Float, Float) -> Unit,
    onUpdateTextItem: (CanvasTextItem) -> Unit,
    onDeleteTextItem: (CanvasTextItem) -> Unit,
    onUpdateMediaItem: (CanvasMediaItem) -> Unit,
    onDeleteMediaItem: (CanvasMediaItem) -> Unit,
    onJumpAudioToTimestamp: (Long?) -> Unit,
    modifier: Modifier = Modifier
) {
    var liveStrokePoints by remember { mutableStateOf<List<StrokePoint>>(emptyList()) }
    var liveLassoPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var liveLaserPoints by remember { mutableStateOf<List<LaserPoint>>(emptyList()) }
    var shapeDragStart by remember { mutableStateOf<Offset?>(null) }
    var shapeDragCurrent by remember { mutableStateOf<Offset?>(null) }
    var isDraggingLassoSelection by remember { mutableStateOf(false) }
    var lassoDragStartPoint by remember { mutableStateOf<Offset?>(null) }
    var liveLassoDragDelta by remember { mutableStateOf(Offset.Zero) }
    var activeRulerEdge by remember { mutableStateOf(GeometryEngine.RulerEdge.NONE) }
    val stylusInputState = sharedStylusInputState ?: remember { StylusInputState() }

    LaunchedEffect(activeTool, penColor, highlighterColor, activeShapeType, shapeStrokeColor, shapeFillColor) {
        liveStrokePoints = emptyList()
        liveLassoPoints = emptyList()
        liveLaserPoints = emptyList()
        shapeDragStart = null
        shapeDragCurrent = null
        isDraggingLassoSelection = false
        lassoDragStartPoint = null
        liveLassoDragDelta = Offset.Zero
        activeRulerEdge = GeometryEngine.RulerEdge.NONE
    }

    // Page canvas dimensions (standard A4 ratio: 1000 x 1414)
    val pageWidth = 1000f
    val pageHeight = 1414f

    val bgImageBitmap = remember(page?.customBgPath) {
        val path = page?.customBgPath
        if (path != null && File(path).exists()) {
            try {
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = android.graphics.Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }
    }

    // Dynamic Sizing based on Zoom & Fit to Screen
    val pageModifier = if (isFitToScreen) {
        Modifier
            .fillMaxWidth()
            .aspectRatio(1000f / 1414f)
    } else {
        Modifier
            .width((440 * zoomScale).coerceIn(240f, 1800f).dp)
            .aspectRatio(1000f / 1414f)
    }

    val currentActiveTool by rememberUpdatedState(activeTool)
    val currentPenColor by rememberUpdatedState(penColor)
    val currentPenWidth by rememberUpdatedState(penWidth)
    val currentHighlighterColor by rememberUpdatedState(highlighterColor)
    val currentHighlighterWidth by rememberUpdatedState(highlighterWidth)
    val currentActiveShapeType by rememberUpdatedState(activeShapeType)
    val currentShapeStrokeColor by rememberUpdatedState(shapeStrokeColor)
    val currentShapeFillColor by rememberUpdatedState(shapeFillColor)
    val currentShapeStrokeWidth by rememberUpdatedState(shapeStrokeWidth)
    val currentShapeIsFilled by rememberUpdatedState(shapeIsFilled)
    val currentShapeSideCount by rememberUpdatedState(shapeSideCount)
    val currentRulerState by rememberUpdatedState(rulerState)
    val currentProtractorState by rememberUpdatedState(protractorState)
    val currentIsGridSnapEnabled by rememberUpdatedState(isGridSnapEnabled)
    val currentLaserColor by rememberUpdatedState(laserColor)
    val currentIsLaserTailEnabled by rememberUpdatedState(isLaserTailEnabled)
    val currentZoomScale by rememberUpdatedState(zoomScale)
    val currentOnPinchZoom by rememberUpdatedState(onPinchZoom)
    val currentOnAddStroke by rememberUpdatedState(onAddStroke)
    val currentOnAddShape by rememberUpdatedState(onAddShape)
    val currentOnEraseAt by rememberUpdatedState(onEraseAt)
    val currentOnAddTextItem by rememberUpdatedState(onAddTextItem)
    val currentOnJumpAudio by rememberUpdatedState(onJumpAudioToTimestamp)
    val currentOnLassoComplete by rememberUpdatedState(onLassoComplete)
    val currentOnMoveLasso by rememberUpdatedState(onMoveLasso)
    val currentEraserWidth by rememberUpdatedState(eraserWidth)
    val currentIsPlayingAudio by rememberUpdatedState(isPlayingAudio)
    val currentStrokes by rememberUpdatedState(strokes)
    val currentShapes by rememberUpdatedState(shapes)
    val currentTextItems by rememberUpdatedState(textItems)
    val currentMediaItems by rememberUpdatedState(mediaItems)
    val currentLassoBounds by rememberUpdatedState(lassoSelectionBounds)
    val currentHasLassoSelection by rememberUpdatedState(hasLassoSelection)
    val selectedStrokeIds = remember(lassoSelectedStrokes) { lassoSelectedStrokes.map { it.id }.toSet() }
    val currentPalmRejectionMode by rememberUpdatedState(palmRejectionMode)

    LaunchedEffect(liveLaserPoints) {
        if (liveLaserPoints.isNotEmpty()) {
            delay(16)
            val now = System.currentTimeMillis()
            val maxAge = if (currentIsLaserTailEnabled) 750L else 220L
            val filtered = liveLaserPoints.filter { now - it.timestamp < maxAge }
            if (filtered.size != liveLaserPoints.size) {
                liveLaserPoints = filtered
            }
        }
    }

    Box(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .then(pageModifier)
            .shadow(8.dp, RoundedCornerShape(4.dp))
            .background(Color.White, RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFFCBD5E1), RoundedCornerShape(4.dp))
    ) {
        // 1. Vector Drawing Canvas with Decoupled Transform & Inking Listeners
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(page?.id) {
                    detectInkGesturesWithPalmRejection(
                        inputState = stylusInputState,
                        getPalmRejectionMode = { currentPalmRejectionMode },
                        getAllowTouch = {
                            currentActiveTool == ToolType.LASSO ||
                            currentActiveTool == ToolType.TEXT ||
                            currentActiveTool == ToolType.SHAPE ||
                            currentActiveTool == ToolType.LASER
                        },
                        onGestureStart = { isStylus, pointerType, startPos, startPressure ->
                            val scaleX = pageWidth / size.width
                            val scaleY = pageHeight / size.height
                            var rawX = startPos.x * scaleX
                            var rawY = startPos.y * scaleY

                            // Check if ruler edge is touched for straight-edge constraint
                            if (currentRulerState.isVisible && (currentActiveTool == ToolType.PEN || currentActiveTool == ToolType.HIGHLIGHTER)) {
                                activeRulerEdge = GeometryEngine.identifyRulerEdge(
                                    rawX, rawY,
                                    currentRulerState.centerX, currentRulerState.centerY,
                                    currentRulerState.angleDegrees,
                                    rulerLength = 650f,
                                    rulerThickness = 110f,
                                    snapThreshold = 80f
                                )
                                if (activeRulerEdge != GeometryEngine.RulerEdge.NONE) {
                                    val constrained = GeometryEngine.constrainToRulerEdge(
                                        rawX, rawY,
                                        activeRulerEdge,
                                        currentRulerState.centerX, currentRulerState.centerY,
                                        currentRulerState.angleDegrees,
                                        rulerLength = 650f,
                                        rulerThickness = 110f
                                    )
                                    rawX = constrained.first
                                    rawY = constrained.second
                                }
                            } else {
                                activeRulerEdge = GeometryEngine.RulerEdge.NONE
                            }

                            if (activeRulerEdge == GeometryEngine.RulerEdge.NONE && currentIsGridSnapEnabled) {
                                val snapped = GeometryEngine.snapToGrid(rawX, rawY)
                                rawX = snapped.first
                                rawY = snapped.second
                            }

                            when (currentActiveTool) {
                                ToolType.PEN, ToolType.HIGHLIGHTER -> {
                                    liveStrokePoints = listOf(StrokePoint(rawX, rawY, startPressure, System.currentTimeMillis()))
                                }
                                ToolType.LASER -> {
                                    liveLaserPoints = listOf(LaserPoint(Offset(rawX, rawY), System.currentTimeMillis()))
                                }
                                ToolType.LASSO -> {
                                    val bounds = currentLassoBounds
                                    if (currentHasLassoSelection && bounds != null && bounds.inflate(40f).contains(Offset(rawX, rawY))) {
                                        isDraggingLassoSelection = true
                                        lassoDragStartPoint = Offset(rawX, rawY)
                                        liveLassoDragDelta = Offset.Zero
                                    } else {
                                        isDraggingLassoSelection = false
                                        lassoDragStartPoint = null
                                        liveLassoDragDelta = Offset.Zero
                                        liveLassoPoints = listOf(Offset(rawX, rawY))
                                    }
                                }
                                ToolType.ERASER -> {
                                    currentOnEraseAt(Offset(rawX, rawY), currentEraserWidth)
                                }
                                ToolType.SHAPE -> {
                                    shapeDragStart = Offset(rawX, rawY)
                                    shapeDragCurrent = Offset(rawX, rawY)
                                }
                                else -> {}
                            }
                        },
                        onGestureMove = { isStylus, currentPos, pressure ->
                            val scaleX = pageWidth / size.width
                            val scaleY = pageHeight / size.height
                            var dragX = currentPos.x * scaleX
                            var dragY = currentPos.y * scaleY

                            if (activeRulerEdge != GeometryEngine.RulerEdge.NONE) {
                                val constrained = GeometryEngine.constrainToRulerEdge(
                                    dragX, dragY,
                                    activeRulerEdge,
                                    currentRulerState.centerX, currentRulerState.centerY,
                                    currentRulerState.angleDegrees,
                                    rulerLength = 650f,
                                    rulerThickness = 110f
                                )
                                dragX = constrained.first
                                dragY = constrained.second
                            } else if (currentRulerState.isVisible && (currentActiveTool == ToolType.PEN || currentActiveTool == ToolType.HIGHLIGHTER)) {
                                val snapped = GeometryEngine.snapToRuler(
                                    dragX, dragY,
                                    currentRulerState.centerX, currentRulerState.centerY,
                                    currentRulerState.angleDegrees,
                                    rulerLength = 650f,
                                    rulerThickness = 110f,
                                    snapThreshold = 65f
                                )
                                if (snapped != null) {
                                    dragX = snapped.first
                                    dragY = snapped.second
                                }
                            }

                            when (currentActiveTool) {
                                ToolType.PEN, ToolType.HIGHLIGHTER -> {
                                    val p = if (pressure > 0f) pressure else 1.0f
                                    liveStrokePoints = liveStrokePoints + StrokePoint(dragX, dragY, p, System.currentTimeMillis())
                                }
                                ToolType.LASER -> {
                                    liveLaserPoints = liveLaserPoints + LaserPoint(Offset(dragX, dragY), System.currentTimeMillis())
                                }
                                ToolType.LASSO -> {
                                    if (isDraggingLassoSelection && lassoDragStartPoint != null) {
                                        val dx = dragX - lassoDragStartPoint!!.x
                                        val dy = dragY - lassoDragStartPoint!!.y
                                        liveLassoDragDelta = Offset(dx, dy)
                                    } else {
                                        liveLassoPoints = liveLassoPoints + Offset(dragX, dragY)
                                    }
                                }
                                ToolType.ERASER -> {
                                    currentOnEraseAt(Offset(dragX, dragY), currentEraserWidth)
                                }
                                ToolType.SHAPE -> {
                                    if (currentIsGridSnapEnabled) {
                                        val snapped = GeometryEngine.snapToGrid(dragX, dragY)
                                        dragX = snapped.first
                                        dragY = snapped.second
                                    }
                                    shapeDragCurrent = Offset(dragX, dragY)
                                }
                                else -> {}
                            }
                        },
                        onGestureEnd = { isStylus, wasDrag, lastPos ->
                            activeRulerEdge = GeometryEngine.RulerEdge.NONE
                            val scaleX = pageWidth / size.width
                            val scaleY = pageHeight / size.height
                            val rawX = lastPos.x * scaleX
                            val rawY = lastPos.y * scaleY

                            if (!wasDrag) {
                                if (currentActiveTool == ToolType.TEXT) {
                                    currentOnAddTextItem(rawX, rawY)
                                } else if (currentIsPlayingAudio || currentActiveTool == ToolType.PEN || currentActiveTool == ToolType.HIGHLIGHTER) {
                                    val tappedStroke = currentStrokes.findLast { stroke ->
                                        stroke.audioTimestampMs != null && stroke.points.any { p ->
                                            hypot((p.x - rawX).toDouble(), (p.y - rawY).toDouble()) < 30.0
                                        }
                                    }
                                    if (tappedStroke?.audioTimestampMs != null) {
                                        currentOnJumpAudio(tappedStroke.audioTimestampMs)
                                    }
                                }
                            }

                            when (currentActiveTool) {
                                ToolType.PEN -> {
                                    val smoothed = InkStrokeSmoother.smoothPoints(liveStrokePoints)
                                    if (smoothed.isNotEmpty()) {
                                        currentOnAddStroke(
                                            VectorStroke(
                                                toolType = ToolType.PEN,
                                                color = currentPenColor,
                                                strokeWidth = currentPenWidth,
                                                alpha = 1.0f,
                                                points = smoothed
                                            )
                                        )
                                    }
                                    liveStrokePoints = emptyList()
                                }
                                ToolType.HIGHLIGHTER -> {
                                    val smoothed = InkStrokeSmoother.smoothPoints(liveStrokePoints)
                                    if (smoothed.isNotEmpty()) {
                                        currentOnAddStroke(
                                            VectorStroke(
                                                toolType = ToolType.HIGHLIGHTER,
                                                color = currentHighlighterColor,
                                                strokeWidth = currentHighlighterWidth,
                                                alpha = 0.35f,
                                                points = smoothed
                                            )
                                        )
                                    }
                                    liveStrokePoints = emptyList()
                                }
                                ToolType.LASSO -> {
                                    if (isDraggingLassoSelection) {
                                        val dx = liveLassoDragDelta.x
                                        val dy = liveLassoDragDelta.y
                                        if (abs(dx) > 1.5f || abs(dy) > 1.5f) {
                                            currentOnMoveLasso?.invoke(dx, dy)
                                        }
                                        isDraggingLassoSelection = false
                                        lassoDragStartPoint = null
                                        liveLassoDragDelta = Offset.Zero
                                    } else {
                                        if (liveLassoPoints.size > 2) {
                                            currentOnLassoComplete?.invoke(liveLassoPoints)
                                        }
                                        liveLassoPoints = emptyList()
                                    }
                                }
                                ToolType.SHAPE -> {
                                    val start = shapeDragStart
                                    val end = shapeDragCurrent
                                    if (start != null && end != null && hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()) > 10.0) {
                                        currentOnAddShape(
                                            GeometricShape(
                                                shapeType = currentActiveShapeType,
                                                startX = start.x,
                                                startY = start.y,
                                                endX = end.x,
                                                endY = end.y,
                                                strokeColor = currentShapeStrokeColor,
                                                fillColor = currentShapeFillColor,
                                                strokeWidth = currentShapeStrokeWidth,
                                                isFilled = currentShapeIsFilled,
                                                sideCount = currentShapeSideCount
                                            )
                                        )
                                    }
                                    shapeDragStart = null
                                    shapeDragCurrent = null
                                }
                                else -> {}
                            }
                        },
                        onPinchZoom = { zFactor ->
                            currentOnPinchZoom?.invoke(zFactor)
                        },
                        onPinchStart = {
                            liveStrokePoints = emptyList()
                            shapeDragStart = null
                            shapeDragCurrent = null
                        }
                    )
                }
        ) {
            val scaleFactor = size.width / pageWidth

            // 1. Background Pattern
            if (bgImageBitmap != null) {
                drawImage(
                    image = bgImageBitmap,
                    dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())
                )
            } else {
                drawBackgroundTemplate(page?.templateType ?: "BLANK", size.width, size.height, scaleFactor)
            }

            // 2. Shapes Layer (live shifted if selected and dragging)
            shapes.forEach { shape ->
                val isSelected = lassoSelectedShapes.contains(shape)
                val renderedShape = if (isSelected && liveLassoDragDelta != Offset.Zero) {
                    shape.copy(
                        startX = shape.startX + liveLassoDragDelta.x,
                        startY = shape.startY + liveLassoDragDelta.y,
                        endX = shape.endX + liveLassoDragDelta.x,
                        endY = shape.endY + liveLassoDragDelta.y
                    )
                } else {
                    shape
                }
                drawGeometricShape(renderedShape, scaleFactor)
            }

            // 3. Vector Strokes Layer (live shifted if selected and dragging)
            strokes.forEach { stroke ->
                val isHighlightedByAudio = isPlayingAudio && stroke.audioTimestampMs != null &&
                        abs(playbackCurrentMs - stroke.audioTimestampMs) < 1500

                val isSelected = selectedStrokeIds.contains(stroke.id) || lassoSelectedStrokes.contains(stroke)
                val renderedStroke = if (isSelected && liveLassoDragDelta != Offset.Zero) {
                    stroke.copy(
                        points = stroke.points.map { it.copy(x = it.x + liveLassoDragDelta.x, y = it.y + liveLassoDragDelta.y) }
                    )
                } else {
                    stroke
                }
                drawVectorStroke(renderedStroke, scaleFactor, isHighlightedByAudio)
            }

            // 4. Live Stroke in progress
            if (liveStrokePoints.isNotEmpty()) {
                val liveStroke = VectorStroke(
                    toolType = activeTool,
                    color = if (activeTool == ToolType.HIGHLIGHTER) highlighterColor else penColor,
                    strokeWidth = if (activeTool == ToolType.HIGHLIGHTER) highlighterWidth else penWidth,
                    alpha = if (activeTool == ToolType.HIGHLIGHTER) 0.35f else 1.0f,
                    points = liveStrokePoints
                )
                drawVectorStroke(liveStroke, scaleFactor, false)
            }

            // 5. Live Lasso Path & Selection Box
            if (liveLassoPoints.size >= 2) {
                val lassoPath = Path().apply {
                    moveTo(liveLassoPoints[0].x * scaleFactor, liveLassoPoints[0].y * scaleFactor)
                    for (i in 1 until liveLassoPoints.size) {
                        lineTo(liveLassoPoints[i].x * scaleFactor, liveLassoPoints[i].y * scaleFactor)
                    }
                }
                drawPath(
                    path = lassoPath,
                    color = Color(0xFF6366F1),
                    style = Stroke(width = 2f * scaleFactor, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f))
                )
            }

            if (hasLassoSelection && lassoSelectionBounds != null) {
                val b = if (liveLassoDragDelta != Offset.Zero) lassoSelectionBounds.translate(liveLassoDragDelta) else lassoSelectionBounds
                val rect = Rect(b.left * scaleFactor, b.top * scaleFactor, b.right * scaleFactor, b.bottom * scaleFactor)
                drawRect(
                    color = Color(0x226366F1),
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Fill
                )
                drawRect(
                    color = Color(0xFF6366F1),
                    topLeft = Offset(rect.left, rect.top),
                    size = Size(rect.width, rect.height),
                    style = Stroke(width = 1.5f * scaleFactor, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f))
                )
            }

            // 6. Live Shape Preview
            val sStart = shapeDragStart
            val sEnd = shapeDragCurrent
            if (sStart != null && sEnd != null) {
                val previewShape = GeometricShape(
                    shapeType = activeShapeType,
                    startX = sStart.x,
                    startY = sStart.y,
                    endX = sEnd.x,
                    endY = sEnd.y,
                    strokeColor = shapeStrokeColor,
                    fillColor = shapeFillColor,
                    strokeWidth = shapeStrokeWidth,
                    isFilled = shapeIsFilled,
                    sideCount = shapeSideCount
                )
                drawGeometricShape(previewShape, scaleFactor)
            }

            // 7. Transient Laser Pointer Layer
            if (liveLaserPoints.isNotEmpty()) {
                val now = System.currentTimeMillis()
                val maxAge = if (currentIsLaserTailEnabled) 750L else 220L

                if (currentIsLaserTailEnabled && liveLaserPoints.size >= 2) {
                    for (i in 0 until liveLaserPoints.size - 1) {
                        val p1 = liveLaserPoints[i]
                        val p2 = liveLaserPoints[i + 1]
                        val age = now - p2.timestamp
                        if (age < maxAge) {
                            val progress = 1f - (age.toFloat() / maxAge).coerceIn(0f, 1f)
                            val alpha = progress * 0.85f
                            val strokeW = (6.5f * scaleFactor * progress).coerceAtLeast(1.5f)
                            drawLine(
                                color = currentLaserColor.copy(alpha = alpha),
                                start = Offset(p1.offset.x * scaleFactor, p1.offset.y * scaleFactor),
                                end = Offset(p2.offset.x * scaleFactor, p2.offset.y * scaleFactor),
                                strokeWidth = strokeW,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                val head = liveLaserPoints.lastOrNull()
                if (head != null) {
                    val headPos = Offset(head.offset.x * scaleFactor, head.offset.y * scaleFactor)
                    drawCircle(
                        color = currentLaserColor.copy(alpha = 0.28f),
                        radius = 16f * scaleFactor,
                        center = headPos
                    )
                    drawCircle(
                        color = currentLaserColor.copy(alpha = 0.70f),
                        radius = 8.5f * scaleFactor,
                        center = headPos
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 3.5f * scaleFactor,
                        center = headPos
                    )
                }
            }
        }

        // 2. Floating Lasso Quick Action Pill
        if (hasLassoSelection && lassoSelectionBounds != null) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val scaleX = maxWidth.value / pageWidth
                val scaleY = maxHeight.value / pageHeight
                val b = if (liveLassoDragDelta != Offset.Zero) lassoSelectionBounds.translate(liveLassoDragDelta) else lassoSelectionBounds
                val pillX = (b.left * scaleX).coerceIn(10f, maxWidth.value - 240f).dp
                val pillY = ((b.top * scaleY) - 52f).coerceAtLeast(10f).dp

                Surface(
                    modifier = Modifier
                        .offset(x = pillX, y = pillY)
                        .shadow(6.dp, RoundedCornerShape(20.dp)),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            Icons.Default.OpenWith,
                            contentDescription = "Move selection",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Drag to move",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        IconButton(
                            onClick = { onDuplicateLasso?.invoke() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                        }
                        IconButton(
                            onClick = { onDeleteLasso?.invoke() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }

        // 3. Text Box Layer
        textItems.forEach { textItem ->
            val isSelected = lassoSelectedTextItems.any { it.id == textItem.id }
            val renderedItem = if (isSelected && liveLassoDragDelta != Offset.Zero) {
                textItem.copy(x = textItem.x + liveLassoDragDelta.x, y = textItem.y + liveLassoDragDelta.y)
            } else {
                textItem
            }
            CanvasTextBox(
                textItem = renderedItem,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                isPlayingAudio = isPlayingAudio,
                playbackCurrentMs = playbackCurrentMs,
                onUpdate = onUpdateTextItem,
                onDelete = { onDeleteTextItem(textItem) },
                onJumpAudio = { onJumpAudioToTimestamp(textItem.audioTimestampMs) }
            )
        }

        // 4. Media Items Layer
        mediaItems.forEach { mediaItem ->
            val isSelected = lassoSelectedMediaItems.any { it.id == mediaItem.id }
            val renderedMedia = if (isSelected && liveLassoDragDelta != Offset.Zero) {
                mediaItem.copy(x = mediaItem.x + liveLassoDragDelta.x, y = mediaItem.y + liveLassoDragDelta.y)
            } else {
                mediaItem
            }
            CanvasMediaBox(
                mediaItem = renderedMedia,
                pageWidth = pageWidth,
                pageHeight = pageHeight,
                onUpdate = onUpdateMediaItem,
                onDelete = { onDeleteMediaItem(mediaItem) }
            )
        }
    }
}

private fun DrawScope.drawBackgroundTemplate(templateType: String, width: Float, height: Float, scaleFactor: Float) {
    val linePaintColor = Color(0x3364748B)

    when (templateType) {
        "LINED_NARROW", "LINED_WIDE" -> {
            val spacing = (if (templateType == "LINED_NARROW") 24f else 36f) * scaleFactor
            var y = spacing * 2
            while (y < height) {
                drawLine(color = linePaintColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1f)
                y += spacing
            }
            drawLine(color = Color(0x44EF4444), start = Offset(50f * scaleFactor, 0f), end = Offset(50f * scaleFactor, height), strokeWidth = 1.2f)
        }
        "GRID_SMALL", "GRID_LARGE" -> {
            val spacing = (if (templateType == "GRID_SMALL") 20f else 32f) * scaleFactor
            var x = 0f
            while (x < width) {
                drawLine(color = linePaintColor, start = Offset(x, 0f), end = Offset(x, height), strokeWidth = 1f)
                x += spacing
            }
            var y = 0f
            while (y < height) {
                drawLine(color = linePaintColor, start = Offset(0f, y), end = Offset(width, y), strokeWidth = 1f)
                y += spacing
            }
        }
        "DOTTED" -> {
            val spacing = 28f * scaleFactor
            var x = spacing
            while (x < width) {
                var y = spacing
                while (y < height) {
                    drawCircle(color = Color(0x6664748B), radius = 1.4f * scaleFactor, center = Offset(x, y))
                    y += spacing
                }
                x += spacing
            }
        }
        "CORNELL" -> {
            drawLine(color = linePaintColor, start = Offset(0f, 100f * scaleFactor), end = Offset(width, 100f * scaleFactor), strokeWidth = 1.5f)
            drawLine(color = linePaintColor, start = Offset(180f * scaleFactor, 100f * scaleFactor), end = Offset(180f * scaleFactor, height - 120f * scaleFactor), strokeWidth = 1.5f)
            drawLine(color = linePaintColor, start = Offset(0f, height - 120f * scaleFactor), end = Offset(width, height - 120f * scaleFactor), strokeWidth = 1.5f)
            var y = 130f * scaleFactor
            while (y < height - 120f * scaleFactor) {
                drawLine(color = linePaintColor, start = Offset(180f * scaleFactor, y), end = Offset(width, y), strokeWidth = 1f)
                y += 32f * scaleFactor
            }
        }
        "MUSIC" -> {
            val staveSpacing = 7f * scaleFactor
            var startY = 80f * scaleFactor
            while (startY < height - 80f * scaleFactor) {
                for (i in 0..4) {
                    drawLine(color = Color(0x55334155), start = Offset(20f * scaleFactor, startY + i * staveSpacing), end = Offset(width - 20f * scaleFactor, startY + i * staveSpacing), strokeWidth = 1f)
                }
                startY += 60f * scaleFactor
            }
        }
    }
}

private fun DrawScope.drawVectorStroke(stroke: VectorStroke, scaleFactor: Float, isAudioActive: Boolean) {
    val points = stroke.points
    if (points.isEmpty()) return

    val strokeColor = if (isAudioActive) Color(0xFF6366F1) else stroke.color
    val strokeWidthPx = (if (stroke.toolType == ToolType.HIGHLIGHTER) stroke.strokeWidth * 2.5f else stroke.strokeWidth) * scaleFactor
    val strokeAlpha = if (isAudioActive) 1.0f else stroke.alpha

    if (points.size == 1) {
        val pt = points[0]
        drawCircle(
            color = strokeColor.copy(alpha = strokeAlpha),
            radius = strokeWidthPx / 2f,
            center = Offset(pt.x * scaleFactor, pt.y * scaleFactor)
        )
        return
    }

    val path = InkStrokeSmoother.buildSmoothPath(points, scaleFactor)
    drawPath(
        path = path,
        color = strokeColor.copy(alpha = strokeAlpha),
        style = Stroke(
            width = strokeWidthPx,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round
        )
    )
}

private fun DrawScope.drawGeometricShape(shape: GeometricShape, scaleFactor: Float) {
    val sx = shape.startX * scaleFactor
    val sy = shape.startY * scaleFactor
    val ex = shape.endX * scaleFactor
    val ey = shape.endY * scaleFactor
    val strokeWidthPx = shape.strokeWidth * scaleFactor

    val left = min(sx, ex)
    val top = min(sy, ey)
    val width = abs(ex - sx)
    val height = abs(ey - sy)
    val cx = (sx + ex) / 2f
    val cy = (sy + ey) / 2f

    when (shape.shapeType) {
        ShapeType.LINE -> {
            drawLine(color = shape.strokeColor, start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
        }
        ShapeType.ARROW -> {
            drawLine(color = shape.strokeColor, start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = strokeWidthPx, cap = StrokeCap.Round)
            val angle = atan2((ey - sy).toDouble(), (ex - sx).toDouble())
            val arrowLen = 18.0 * scaleFactor
            val arrowAngle = Math.toRadians(25.0)
            val x1 = ex - arrowLen * cos(angle - arrowAngle)
            val y1 = ey - arrowLen * sin(angle - arrowAngle)
            val x2 = ex - arrowLen * cos(angle + arrowAngle)
            val y2 = ey - arrowLen * sin(angle + arrowAngle)
            val arrowPath = Path().apply {
                moveTo(ex, ey)
                lineTo(x1.toFloat(), y1.toFloat())
                lineTo(x2.toFloat(), y2.toFloat())
                close()
            }
            drawPath(path = arrowPath, color = shape.strokeColor, style = Fill)
        }
        ShapeType.RECTANGLE, ShapeType.SQUARE -> {
            if (shape.isFilled) {
                drawRect(color = shape.fillColor, topLeft = Offset(left, top), size = Size(width, height), style = Fill)
            }
            drawRect(color = shape.strokeColor, topLeft = Offset(left, top), size = Size(width, height), style = Stroke(width = strokeWidthPx))
        }
        ShapeType.CIRCLE, ShapeType.ELLIPSE -> {
            if (shape.isFilled) {
                drawOval(color = shape.fillColor, topLeft = Offset(left, top), size = Size(width, height), style = Fill)
            }
            drawOval(color = shape.strokeColor, topLeft = Offset(left, top), size = Size(width, height), style = Stroke(width = strokeWidthPx))
        }
        ShapeType.TRIANGLE -> {
            val triPath = Path().apply {
                moveTo(cx, top)
                lineTo(left, top + height)
                lineTo(left + width, top + height)
                close()
            }
            if (shape.isFilled) drawPath(path = triPath, color = shape.fillColor, style = Fill)
            drawPath(path = triPath, color = shape.strokeColor, style = Stroke(width = strokeWidthPx, join = StrokeJoin.Round))
        }
        ShapeType.POLYGON, ShapeType.STAR -> {
            val polyPath = Path()
            val r = width / 2f
            val sides = shape.sideCount.coerceAtLeast(3)
            for (i in 0 until sides) {
                val theta = (i * 2 * Math.PI / sides) - Math.PI / 2
                val px = (cx + r * cos(theta)).toFloat()
                val py = (cy + r * sin(theta)).toFloat()
                if (i == 0) polyPath.moveTo(px, py) else polyPath.lineTo(px, py)
            }
            polyPath.close()
            if (shape.isFilled) drawPath(path = polyPath, color = shape.fillColor, style = Fill)
            drawPath(path = polyPath, color = shape.strokeColor, style = Stroke(width = strokeWidthPx, join = StrokeJoin.Round))
        }
    }
}

@Composable
fun CanvasTextBox(
    textItem: CanvasTextItem,
    pageWidth: Float,
    pageHeight: Float,
    isPlayingAudio: Boolean,
    playbackCurrentMs: Long,
    onUpdate: (CanvasTextItem) -> Unit,
    onDelete: () -> Unit,
    onJumpAudio: () -> Unit
) {
    var posX by remember(textItem.x) { mutableFloatStateOf(textItem.x) }
    var posY by remember(textItem.y) { mutableFloatStateOf(textItem.y) }
    var isEditing by remember { mutableStateOf(false) }
    var textContent by remember(textItem.text) { mutableStateOf(textItem.text) }

    val isAudioLinked = textItem.audioTimestampMs != null
    val isAudioActive = isPlayingAudio && isAudioLinked && abs(playbackCurrentMs - (textItem.audioTimestampMs ?: 0L)) < 1500

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scaleX = maxWidth.value / pageWidth
        val scaleY = maxHeight.value / pageHeight
        val dpX = (posX * scaleX).dp
        val dpY = (posY * scaleY).dp
        val dpW = (textItem.width * scaleX).dp

        Box(
            modifier = Modifier
                .offset(x = dpX, y = dpY)
                .widthIn(min = 120.dp, max = dpW.coerceAtLeast(180.dp))
                .background(
                    color = if (isAudioActive) Color(0x336366F1) else textItem.backgroundColor,
                    shape = RoundedCornerShape(6.dp)
                )
                .border(
                    width = if (isEditing) 1.5.dp else if (isAudioActive) 2.dp else 0.5.dp,
                    color = if (isEditing) Color(0xFF6366F1) else if (isAudioActive) Color(0xFF4F46E5) else Color(0x44CBD5E1),
                    shape = RoundedCornerShape(6.dp)
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        posX += dragAmount.x / scaleX
                        posY += dragAmount.y / scaleY
                        onUpdate(textItem.copy(x = posX, y = posY))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = { isEditing = true },
                        onTap = {
                            if (isAudioLinked) onJumpAudio() else isEditing = true
                        }
                    )
                }
                .padding(6.dp)
        ) {
            Column {
                if (textItem.isMathFormula) {
                    Surface(
                        color = Color(0xFF4338CA),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            "TYPESET MATH",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                if (isEditing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(
                                onClick = { onUpdate(textItem.copy(isBold = !textItem.isBold)) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.FormatBold, contentDescription = "Bold", modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { onUpdate(textItem.copy(isItalic = !textItem.isItalic)) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.FormatItalic, contentDescription = "Italic", modifier = Modifier.size(16.dp))
                            }
                        }
                        Row {
                            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color(0xFFDC2626), modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = {
                                    isEditing = false
                                    onUpdate(textItem.copy(text = textContent))
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Check, contentDescription = "Done", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                            }
                        }
                    }

                    BasicTextField(
                        value = textContent,
                        onValueChange = {
                            textContent = it
                            onUpdate(textItem.copy(text = it))
                        },
                        textStyle = TextStyle(
                            fontSize = (textItem.fontSize * scaleX).sp,
                            color = textItem.fontColor,
                            fontWeight = if (textItem.isBold) FontWeight.Bold else FontWeight.Normal,
                            fontStyle = if (textItem.isItalic) FontStyle.Italic else FontStyle.Normal,
                            fontFamily = if (textItem.isMathFormula) FontFamily.Monospace else FontFamily.Default
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = textItem.text,
                        fontSize = (textItem.fontSize * scaleX).sp,
                        color = textItem.fontColor,
                        fontWeight = if (textItem.isBold) FontWeight.Bold else FontWeight.Normal,
                        fontStyle = if (textItem.isItalic) FontStyle.Italic else FontStyle.Normal,
                        fontFamily = if (textItem.isMathFormula) FontFamily.Monospace else FontFamily.Default
                    )
                }

                if (isAudioLinked) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice synced", tint = Color(0xFF4F46E5), modifier = Modifier.size(12.dp))
                        Text("Voice Marker", fontSize = 9.sp, color = Color(0xFF4F46E5), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
fun CanvasMediaBox(
    mediaItem: CanvasMediaItem,
    pageWidth: Float,
    pageHeight: Float,
    onUpdate: (CanvasMediaItem) -> Unit,
    onDelete: () -> Unit
) {
    var posX by remember(mediaItem.x) { mutableFloatStateOf(mediaItem.x) }
    var posY by remember(mediaItem.y) { mutableFloatStateOf(mediaItem.y) }
    var width by remember(mediaItem.width) { mutableFloatStateOf(mediaItem.width) }
    var height by remember(mediaItem.height) { mutableFloatStateOf(mediaItem.height) }
    var isSelected by remember { mutableStateOf(false) }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val scaleX = maxWidth.value / pageWidth
        val scaleY = maxHeight.value / pageHeight
        val dpX = (posX * scaleX).dp
        val dpY = (posY * scaleY).dp
        val dpW = (width * scaleX).dp
        val dpH = (height * scaleY).dp

        Box(
            modifier = Modifier
                .offset(x = dpX, y = dpY)
                .size(width = dpW, height = dpH)
                .border(
                    width = if (isSelected) 2.dp else 0.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent,
                    shape = RoundedCornerShape(4.dp)
                )
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        posX += dragAmount.x / scaleX
                        posY += dragAmount.y / scaleY
                        onUpdate(mediaItem.copy(x = posX, y = posY))
                    }
                }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { isSelected = !isSelected })
                }
        ) {
            AsyncImage(
                model = File(mediaItem.localUri),
                contentDescription = "Canvas Image",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(4.dp))
            )

            if (isSelected) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(24.dp)
                        .background(Color(0xCCDC2626), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}
