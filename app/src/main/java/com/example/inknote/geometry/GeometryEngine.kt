package com.example.inknote.geometry

import androidx.compose.ui.graphics.Color
import com.example.inknote.data.model.GeometricShape
import com.example.inknote.data.model.ShapeType
import com.example.inknote.data.model.StrokePoint
import kotlin.math.*

object GeometryEngine {

    /**
     * Attempts to recognize a freehand stroke as a geometric shape:
     * Line, Circle, Ellipse, Rectangle, Square, Triangle.
     */
    fun recognizeShape(
        points: List<StrokePoint>,
        strokeColor: Color,
        strokeWidth: Float
    ): GeometricShape? {
        if (points.size < 6) return null

        val start = points.first()
        val end = points.last()
        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY

        // Too small to be a meaningful shape
        if (width < 20f && height < 20f) return null

        val totalLength = calculatePathLength(points)
        val endDistance = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()).toFloat()

        // 1. Check if it's a straight line
        val isClosed = endDistance < (totalLength * 0.22f) || endDistance < 35f
        if (!isClosed) {
            val maxDeviation = calculateMaxLineDeviation(points, start, end)
            if (maxDeviation < (totalLength * 0.14f) && totalLength > 30f) {
                // Snap to common angle if close
                val snappedEnd = snapAngle(start.x, start.y, end.x, end.y)
                return GeometricShape(
                    shapeType = ShapeType.LINE,
                    startX = start.x,
                    startY = start.y,
                    endX = snappedEnd.first,
                    endY = snappedEnd.second,
                    strokeColor = strokeColor,
                    strokeWidth = strokeWidth
                )
            }
            return null
        }

        // Closed shape analysis
        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val aspectRatio = if (height > 0f) width / height else 1f

        // Check circularity
        val avgRadius = (width + height) / 4f
        var radiusVariance = 0.0
        points.forEach { p ->
            val dist = hypot((p.x - centerX).toDouble(), (p.y - centerY).toDouble())
            radiusVariance += abs(dist - avgRadius)
        }
        val avgVariance = radiusVariance / points.size
        val varianceRatio = avgVariance / avgRadius

        if (varianceRatio < 0.22) {
            // Close to circle or ellipse
            return if (aspectRatio in 0.82f..1.22f) {
                val radius = (width + height) / 4f
                GeometricShape(
                    shapeType = ShapeType.CIRCLE,
                    startX = centerX - radius,
                    startY = centerY - radius,
                    endX = centerX + radius,
                    endY = centerY + radius,
                    strokeColor = strokeColor,
                    strokeWidth = strokeWidth
                )
            } else {
                GeometricShape(
                    shapeType = ShapeType.ELLIPSE,
                    startX = minX,
                    startY = minY,
                    endX = maxX,
                    endY = maxY,
                    strokeColor = strokeColor,
                    strokeWidth = strokeWidth
                )
            }
        }

        // Check corners for polygons (Rectangle vs Triangle)
        val corners = detectCorners(points)
        if (corners.size == 3) {
            return GeometricShape(
                shapeType = ShapeType.TRIANGLE,
                startX = minX,
                startY = minY,
                endX = maxX,
                endY = maxY,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth
            )
        } else if (corners.size in 4..5) {
            return if (aspectRatio in 0.85f..1.15f) {
                val size = max(width, height)
                GeometricShape(
                    shapeType = ShapeType.SQUARE,
                    startX = centerX - size / 2f,
                    startY = centerY - size / 2f,
                    endX = centerX + size / 2f,
                    endY = centerY + size / 2f,
                    strokeColor = strokeColor,
                    strokeWidth = strokeWidth
                )
            } else {
                GeometricShape(
                    shapeType = ShapeType.RECTANGLE,
                    startX = minX,
                    startY = minY,
                    endX = maxX,
                    endY = maxY,
                    strokeColor = strokeColor,
                    strokeWidth = strokeWidth
                )
            }
        }

        // Fallback: If reasonably rectangular bounding box coverage
        val boxArea = width * height
        if (boxArea > 0) {
            if (aspectRatio in 0.85f..1.18f) {
                val size = max(width, height)
                return GeometricShape(
                    shapeType = ShapeType.SQUARE,
                    startX = centerX - size / 2f,
                    startY = centerY - size / 2f,
                    endX = centerX + size / 2f,
                    endY = centerY + size / 2f,
                    strokeColor = strokeColor,
                    strokeWidth = strokeWidth
                )
            } else {
                return GeometricShape(
                    shapeType = ShapeType.RECTANGLE,
                    startX = minX,
                    startY = minY,
                    endX = maxX,
                    endY = maxY,
                    strokeColor = strokeColor,
                    strokeWidth = strokeWidth
                )
            }
        }

        return null
    }

    fun snapToRuler(
        pointX: Float,
        pointY: Float,
        rulerCenterX: Float,
        rulerCenterY: Float,
        rulerAngleDeg: Float,
        rulerLength: Float = 650f,
        rulerThickness: Float = 110f,
        snapThreshold: Float = 65f
    ): Pair<Float, Float>? {
        val angleRad = Math.toRadians(rulerAngleDeg.toDouble())
        val dirX = cos(angleRad).toFloat()
        val dirY = sin(angleRad).toFloat()
        val normalX = -dirY
        val normalY = dirX

        val vx = pointX - rulerCenterX
        val vy = pointY - rulerCenterY

        val proj = vx * dirX + vy * dirY
        val normDist = vx * normalX + vy * normalY

        // Check if within the extended bounds along the ruler length
        val halfLen = rulerLength / 2f + 40f
        if (abs(proj) > halfLen) {
            return null
        }

        val halfThick = rulerThickness / 2f
        val distToTopEdge = abs(normDist - halfThick)
        val distToBottomEdge = abs(normDist - (-halfThick))
        val distToCenter = abs(normDist)

        return when {
            distToTopEdge <= snapThreshold && distToTopEdge <= distToBottomEdge -> {
                val sx = rulerCenterX + dirX * proj + normalX * halfThick
                val sy = rulerCenterY + dirY * proj + normalY * halfThick
                Pair(sx, sy)
            }
            distToBottomEdge <= snapThreshold -> {
                val sx = rulerCenterX + dirX * proj - normalX * halfThick
                val sy = rulerCenterY + dirY * proj - normalY * halfThick
                Pair(sx, sy)
            }
            distToCenter <= (snapThreshold * 0.75f) -> {
                val sx = rulerCenterX + dirX * proj
                val sy = rulerCenterY + dirY * proj
                Pair(sx, sy)
            }
            else -> null
        }
    }

    fun snapAngle(x1: Float, y1: Float, x2: Float, y2: Float): Pair<Float, Float> {
        val dx = x2 - x1
        val dy = y2 - y1
        val length = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (length < 1f) return Pair(x2, y2)

        val angle = Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
        val snapTargets = listOf(0.0, 30.0, 45.0, 60.0, 90.0, 120.0, 135.0, 150.0, 180.0, -30.0, -45.0, -60.0, -90.0, -120.0, -135.0, -150.0, -180.0)

        for (target in snapTargets) {
            if (abs(angle - target) < 6.0 || abs(angle - (target - 360)) < 6.0 || abs(angle - (target + 360)) < 6.0) {
                val targetRad = Math.toRadians(target)
                return Pair(
                    (x1 + length * cos(targetRad)).toFloat(),
                    (y1 + length * sin(targetRad)).toFloat()
                )
            }
        }
        return Pair(x2, y2)
    }

    fun snapToGrid(x: Float, y: Float, gridSpacing: Float = 28f): Pair<Float, Float> {
        val snapThreshold = 10f
        val nearestX = (round(x / gridSpacing) * gridSpacing)
        val nearestY = (round(y / gridSpacing) * gridSpacing)
        val sx = if (abs(x - nearestX) < snapThreshold) nearestX else x
        val sy = if (abs(y - nearestY) < snapThreshold) nearestY else y
        return Pair(sx, sy)
    }

    private fun calculatePathLength(points: List<StrokePoint>): Float {
        var len = 0f
        for (i in 1 until points.size) {
            len += hypot((points[i].x - points[i - 1].x).toDouble(), (points[i].y - points[i - 1].y).toDouble()).toFloat()
        }
        return len
    }

    private fun calculateMaxLineDeviation(points: List<StrokePoint>, start: StrokePoint, end: StrokePoint): Float {
        val dx = end.x - start.x
        val dy = end.y - start.y
        val lineLen = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (lineLen < 1f) return 0f

        var maxDev = 0f
        points.forEach { p ->
            val dev = abs((p.x - start.x) * dy - (p.y - start.y) * dx) / lineLen
            if (dev > maxDev) maxDev = dev
        }
        return maxDev
    }

    private fun detectCorners(points: List<StrokePoint>): List<StrokePoint> {
        if (points.size < 12) return emptyList()
        val corners = mutableListOf<StrokePoint>()
        val step = 4
        for (i in step until points.size - step) {
            val pPrev = points[i - step]
            val pCurr = points[i]
            val pNext = points[i + step]

            val v1x = pCurr.x - pPrev.x
            val v1y = pCurr.y - pPrev.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val dot = v1x * v2x + v1y * v2y
            val mag1 = hypot(v1x.toDouble(), v1y.toDouble())
            val mag2 = hypot(v2x.toDouble(), v2y.toDouble())

            if (mag1 > 0 && mag2 > 0) {
                val cosTheta = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
                val angleDeg = Math.toDegrees(acos(cosTheta))
                if (angleDeg > 42.0) {
                    if (corners.none { hypot((it.x - pCurr.x).toDouble(), (it.y - pCurr.y).toDouble()) < 30.0 }) {
                        corners.add(pCurr)
                    }
                }
            }
        }
        return corners
    }

    /**
     * Standard Ray-Casting algorithm for point-in-polygon containment test.
     */
    fun isPointInPolygon(px: Float, py: Float, polygon: List<androidx.compose.ui.geometry.Offset>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y

            val intersect = ((yi > py) != (yj > py)) &&
                    (px < (xj - xi) * (py - yi) / (yj - yi) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    fun computePolygonBounds(polygon: List<androidx.compose.ui.geometry.Offset>): androidx.compose.ui.geometry.Rect {
        if (polygon.isEmpty()) return androidx.compose.ui.geometry.Rect.Zero
        var minX = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var minY = Float.MAX_VALUE
        var maxY = Float.MIN_VALUE
        polygon.forEach { p ->
            minX = kotlin.math.min(minX, p.x)
            maxX = kotlin.math.max(maxX, p.x)
            minY = kotlin.math.min(minY, p.y)
            maxY = kotlin.math.max(maxY, p.y)
        }
        return androidx.compose.ui.geometry.Rect(minX, minY, maxX, maxY)
    }

    /**
     * Accurately determines whether a stroke is captured by the lasso polygon.
     * Prevents accidental selection of adjacent or surrounding strokes.
     */
    fun isStrokeSelectedByLasso(
        stroke: com.example.inknote.data.model.VectorStroke,
        polygon: List<androidx.compose.ui.geometry.Offset>,
        polyBounds: androidx.compose.ui.geometry.Rect = computePolygonBounds(polygon)
    ): Boolean {
        if (stroke.points.isEmpty()) return false

        var sMinX = Float.MAX_VALUE
        var sMaxX = Float.MIN_VALUE
        var sMinY = Float.MAX_VALUE
        var sMaxY = Float.MIN_VALUE
        stroke.points.forEach { p ->
            sMinX = min(sMinX, p.x)
            sMaxX = max(sMaxX, p.x)
            sMinY = min(sMinY, p.y)
            sMaxY = max(sMaxY, p.y)
        }
        val strokeBounds = androidx.compose.ui.geometry.Rect(sMinX, sMinY, sMaxX, sMaxY)
        if (!polyBounds.overlaps(strokeBounds)) return false

        // For dots or very short strokes (e.g. periods, i-dots)
        if (stroke.points.size <= 3) {
            return stroke.points.any { isPointInPolygon(it.x, it.y, polygon) }
        }

        // Check center of bounding box
        val cx = (sMinX + sMaxX) / 2f
        val cy = (sMinY + sMaxY) / 2f
        if (isPointInPolygon(cx, cy, polygon)) return true

        // Sample points across stroke
        val sampleCount = min(stroke.points.size, 12)
        val step = max(1, stroke.points.size / sampleCount)
        var insideCount = 0
        var totalSampled = 0
        for (i in 0 until stroke.points.size step step) {
            val pt = stroke.points[i]
            totalSampled++
            if (isPointInPolygon(pt.x, pt.y, polygon)) {
                insideCount++
            }
        }
        return totalSampled > 0 && (insideCount.toFloat() / totalSampled.toFloat()) >= 0.35f
    }

    /**
     * Determines whether a geometric shape is captured by the lasso polygon.
     */
    fun isShapeSelectedByLasso(
        shape: com.example.inknote.data.model.GeometricShape,
        polygon: List<androidx.compose.ui.geometry.Offset>,
        polyBounds: androidx.compose.ui.geometry.Rect = computePolygonBounds(polygon)
    ): Boolean {
        val sMinX = min(shape.startX, shape.endX)
        val sMaxX = max(shape.startX, shape.endX)
        val sMinY = min(shape.startY, shape.endY)
        val sMaxY = max(shape.startY, shape.endY)
        val shapeBounds = androidx.compose.ui.geometry.Rect(sMinX, sMinY, sMaxX, sMaxY)
        if (!polyBounds.overlaps(shapeBounds)) return false

        val cx = (shape.startX + shape.endX) / 2f
        val cy = (shape.startY + shape.endY) / 2f
        if (isPointInPolygon(cx, cy, polygon)) return true
        if (isPointInPolygon(shape.startX, shape.startY, polygon) && isPointInPolygon(shape.endX, shape.endY, polygon)) return true
        return false
    }

    /**
     * Determines whether a text annotation box is captured by the lasso polygon.
     */
    fun isTextSelectedByLasso(
        item: com.example.inknote.data.model.CanvasTextItem,
        polygon: List<androidx.compose.ui.geometry.Offset>,
        polyBounds: androidx.compose.ui.geometry.Rect = computePolygonBounds(polygon)
    ): Boolean {
        val textBounds = androidx.compose.ui.geometry.Rect(item.x, item.y, item.x + item.width, item.y + 60f)
        if (!polyBounds.overlaps(textBounds)) return false

        val cx = item.x + item.width / 2f
        val cy = item.y + 30f
        if (isPointInPolygon(cx, cy, polygon)) return true
        if (isPointInPolygon(item.x, item.y, polygon) && isPointInPolygon(item.x + item.width, item.y + 60f, polygon)) return true
        return false
    }
}
