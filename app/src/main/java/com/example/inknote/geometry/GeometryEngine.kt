package com.example.inknote.geometry

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.inknote.data.model.GeometricShape
import com.example.inknote.data.model.ShapeType
import com.example.inknote.data.model.StrokePoint
import kotlin.math.*

object GeometryEngine {

    /**
     * Highly accurate geometric shape classifier for "Magic Ink":
     * Accurately distinguishes Line, Triangle, Rectangle, Square, Circle, Ellipse, and Polygon
     * using arc-length equidistant resampling, wrap-around junction corner detection,
     * Shoelace enclosed area, isoperimetric circularity quotient (4*pi*Area/P^2),
     * radial distance variance, and edge straightness analysis.
     */
    fun recognizeShape(
        points: List<StrokePoint>,
        strokeColor: Color,
        strokeWidth: Float
    ): GeometricShape? {
        if (points.size < 5) return null

        val minX = points.minOf { it.x }
        val maxX = points.maxOf { it.x }
        val minY = points.minOf { it.y }
        val maxY = points.maxOf { it.y }
        val width = maxX - minX
        val height = maxY - minY

        // Too small to be a meaningful shape
        if (width < 18f && height < 18f) return null

        val totalLength = calculatePathLength(points)
        if (totalLength < 25f) return null

        val start = points.first()
        val end = points.last()
        val endDistance = hypot((end.x - start.x).toDouble(), (end.y - start.y).toDouble()).toFloat()
        val bboxDiag = hypot(width.toDouble(), height.toDouble()).toFloat()

        // 1. Open Stroke Check -> Straight Line
        val isClosed = endDistance < (totalLength * 0.32f) || endDistance < (bboxDiag * 0.30f) || endDistance < 42f
        if (!isClosed) {
            val maxDeviation = calculateMaxLineDeviation(points, start, end)
            if (maxDeviation < (totalLength * 0.14f) && endDistance > (totalLength * 0.82f)) {
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

        // 2. Equidistant Arc-Length Resampling (N = 72 points)
        // Completely removes speed variations, digitizer sampling rate jitter, and point density bias.
        val resampled = resamplePath(points, 72)
        if (resampled.size < 20) return null

        val centerX = (minX + maxX) / 2f
        val centerY = (minY + maxY) / 2f
        val aspectRatio = if (height > 0f) width / height else 1f
        val boundingBoxArea = width * height

        // Calculate enclosed area via Shoelace formula and perimeter
        val enclosedArea = calculateEnclosedArea(resampled)
        val perimeter = calculateClosedPerimeter(resampled)
        if (perimeter <= 0f) return null

        val areaRatio = if (boundingBoxArea > 0f) enclosedArea / boundingBoxArea else 0f

        // Isoperimetric quotient (Circularity): Q = 4 * PI * Area / Perimeter^2
        // Perfect Circle = 1.0; Square = ~0.785; Equilateral Triangle <= 0.605
        val circularity = ((4.0 * PI * enclosedArea) / (perimeter * perimeter)).toFloat().coerceIn(0f, 1.5f)

        // Radial distance variance from centroid
        val radialDistances = resampled.map { hypot((it.x - centerX).toDouble(), (it.y - centerY).toDouble()).toFloat() }
        val avgRadius = radialDistances.average().toFloat()
        val radiusVariance = if (avgRadius > 0f) {
            val varianceSum = radialDistances.sumOf { abs(it - avgRadius).toDouble() }
            (varianceSum / (radialDistances.size * avgRadius)).toFloat()
        } else 0f

        // Detect corners with wrap-around handling at closure junction
        val cornerIndices = detectResampledCorners(resampled)
        val numCorners = cornerIndices.size
        val maxTurningAngle = calculateMaxTurningAngle(resampled)

        // 3. TRIANGLE DETECTION:
        // Triangles have:
        // - 3 distinct sharp corners OR
        // - areaRatio in [0.22 .. 0.65] (a triangle fills ~50% of its bounding box)
        // - circularity <= 0.68 (mathematical bound for triangles is <= 0.605)
        // - moderate/high radius variance (points at vertices are much farther from center than edge midpoints)
        // - at least one sharp turning angle >= 45 degrees
        val isDefiniteTriangle = (numCorners == 3 && circularity <= 0.70f && areaRatio <= 0.68f) ||
                (numCorners in 2..4 && areaRatio in 0.22f..0.65f && circularity <= 0.66f && radiusVariance > 0.12f && maxTurningAngle >= 42f)

        if (isDefiniteTriangle) {
            return GeometricShape(
                shapeType = ShapeType.TRIANGLE,
                startX = minX,
                startY = minY,
                endX = maxX,
                endY = maxY,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth
            )
        }

        // 4. RECTANGLE / SQUARE DETECTION:
        // Rectangles have:
        // - At least 3 or 4 distinct corners (numCorners in 3..5)
        // - areaRatio >= 0.65 (fills most of its bounding box)
        // - circularity in [0.55 .. 0.85]
        val isDefiniteRectangle = numCorners in 3..5 &&
                ((numCorners >= 4 && areaRatio >= 0.62f) || (areaRatio >= 0.70f && circularity <= 0.85f))

        if (isDefiniteRectangle) {
            return if (aspectRatio in 0.82f..1.22f) {
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

        // 5. CIRCLE / ELLIPSE DETECTION:
        // Round shapes have:
        // - At most 2 corners (numCorners <= 2: circle has 0 or 1 at seam, ellipse has at most 2 at vertices)
        // - Area ratio consistent with oval/circle (areaRatio in 0.58f .. 0.88f, theoretical pi/4 ~ 0.785)
        // - High circularity (Q >= 0.50f for elongated ellipse, Q >= 0.68f for circle)
        val isRoundShape = numCorners <= 2 && areaRatio in 0.58f..0.88f && circularity >= 0.50f

        if (isRoundShape) {
            return if (aspectRatio in 0.80f..1.25f && radiusVariance <= 0.16f && numCorners <= 1 && circularity >= 0.68f) {
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
            } else if (aspectRatio !in 0.80f..1.25f) {
                GeometricShape(
                    shapeType = ShapeType.ELLIPSE,
                    startX = minX,
                    startY = minY,
                    endX = maxX,
                    endY = maxY,
                    strokeColor = strokeColor,
                    strokeWidth = strokeWidth
                )
            } else null
        }

        // 6. POLYGON DETECTION (e.g. Pentagon, Hexagon):
        if (numCorners in 5..8 && areaRatio in 0.55f..0.85f) {
            return GeometricShape(
                shapeType = ShapeType.POLYGON,
                startX = minX,
                startY = minY,
                endX = maxX,
                endY = maxY,
                sideCount = numCorners,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth
            )
        }

        // 7. Strict conservative fallback:
        // If it was angular with 3 corners, classify as triangle. If areaRatio is high, rectangle.
        if (areaRatio in 0.28f..0.62f && circularity < 0.65f) {
            return GeometricShape(
                shapeType = ShapeType.TRIANGLE,
                startX = minX,
                startY = minY,
                endX = maxX,
                endY = maxY,
                strokeColor = strokeColor,
                strokeWidth = strokeWidth
            )
        } else if (areaRatio >= 0.75f && maxTurningAngle >= 42f) {
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

        return null
    }

    /**
     * Resamples the points into [targetCount] equidistant points along the arc-length.
     */
    private fun resamplePath(points: List<StrokePoint>, targetCount: Int): List<StrokePoint> {
        if (points.size < 2 || targetCount < 2) return points

        val totalLength = calculatePathLength(points)
        if (totalLength <= 0f) return points

        val segmentDistances = FloatArray(points.size)
        segmentDistances[0] = 0f
        for (i in 1 until points.size) {
            val d = hypot((points[i].x - points[i - 1].x).toDouble(), (points[i].y - points[i - 1].y).toDouble()).toFloat()
            segmentDistances[i] = segmentDistances[i - 1] + d
        }

        val step = totalLength / (targetCount - 1)
        val resampled = mutableListOf<StrokePoint>()
        resampled.add(points.first())

        var currIdx = 0
        for (j in 1 until targetCount - 1) {
            val targetDist = j * step
            while (currIdx < points.size - 2 && segmentDistances[currIdx + 1] < targetDist) {
                currIdx++
            }
            val d0 = segmentDistances[currIdx]
            val d1 = segmentDistances[currIdx + 1]
            val segLen = d1 - d0
            val t = if (segLen > 0f) ((targetDist - d0) / segLen).coerceIn(0f, 1f) else 0f
            val p0 = points[currIdx]
            val p1 = points[currIdx + 1]
            val x = p0.x + t * (p1.x - p0.x)
            val y = p0.y + t * (p1.y - p0.y)
            resampled.add(StrokePoint(x, y))
        }
        resampled.add(points.last())
        return resampled
    }

    /**
     * Detects corners along an arc-length resampled closed path with circular wrap-around.
     */
    private fun detectResampledCorners(points: List<StrokePoint>): List<Int> {
        val n = points.size
        if (n < 15) return emptyList()

        val k = 5 // Window offset (corresponds to ~7% of perimeter)
        val turningAngles = FloatArray(n)

        for (i in 0 until n) {
            val prevIdx = (i - k + n) % n
            val nextIdx = (i + k) % n
            val pPrev = points[prevIdx]
            val pCurr = points[i]
            val pNext = points[nextIdx]

            val v1x = pCurr.x - pPrev.x
            val v1y = pCurr.y - pPrev.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val dot = v1x * v2x + v1y * v2y
            val mag1 = hypot(v1x.toDouble(), v1y.toDouble())
            val mag2 = hypot(v2x.toDouble(), v2y.toDouble())

            if (mag1 > 0 && mag2 > 0) {
                val cosTheta = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
                turningAngles[i] = Math.toDegrees(acos(cosTheta)).toFloat()
            } else {
                turningAngles[i] = 0f
            }
        }

        // Peak detection with non-maximum suppression
        val cornerIndices = mutableListOf<Int>()
        val minCornerAngle = 38f
        val suppressionWindow = 6

        for (i in 0 until n) {
            val angle = turningAngles[i]
            if (angle >= minCornerAngle) {
                var isMax = true
                for (w in -3..3) {
                    if (w == 0) continue
                    val neighborIdx = (i + w + n) % n
                    if (turningAngles[neighborIdx] > angle) {
                        isMax = false
                        break
                    }
                }
                if (isMax) {
                    // Check if too close to an existing corner along the circular ring
                    val isTooClose = cornerIndices.any { existingIdx ->
                        val diff = abs(existingIdx - i)
                        val circularDist = min(diff, n - diff)
                        circularDist < suppressionWindow
                    }
                    if (!isTooClose) {
                        cornerIndices.add(i)
                    }
                }
            }
        }

        return cornerIndices
    }

    private fun calculateMaxTurningAngle(points: List<StrokePoint>): Float {
        val n = points.size
        if (n < 15) return 0f
        val k = 5
        var maxAngle = 0f
        for (i in 0 until n) {
            val prevIdx = (i - k + n) % n
            val nextIdx = (i + k) % n
            val pPrev = points[prevIdx]
            val pCurr = points[i]
            val pNext = points[nextIdx]

            val v1x = pCurr.x - pPrev.x
            val v1y = pCurr.y - pPrev.y
            val v2x = pNext.x - pCurr.x
            val v2y = pNext.y - pCurr.y

            val dot = v1x * v2x + v1y * v2y
            val mag1 = hypot(v1x.toDouble(), v1y.toDouble())
            val mag2 = hypot(v2x.toDouble(), v2y.toDouble())

            if (mag1 > 0 && mag2 > 0) {
                val cosTheta = (dot / (mag1 * mag2)).coerceIn(-1.0, 1.0)
                val angle = Math.toDegrees(acos(cosTheta)).toFloat()
                if (angle > maxAngle) maxAngle = angle
            }
        }
        return maxAngle
    }

    private fun calculateClosedPerimeter(points: List<StrokePoint>): Float {
        var perimeter = 0f
        for (i in points.indices) {
            val next = (i + 1) % points.size
            perimeter += hypot((points[next].x - points[i].x).toDouble(), (points[next].y - points[i].y).toDouble()).toFloat()
        }
        return perimeter
    }

    private fun calculateEnclosedArea(points: List<StrokePoint>): Float {
        if (points.size < 3) return 0f
        var sum = 0.0
        for (i in points.indices) {
            val p1 = points[i]
            val p2 = points[(i + 1) % points.size]
            sum += (p1.x.toDouble() * p2.y.toDouble()) - (p2.x.toDouble() * p1.y.toDouble())
        }
        return (abs(sum) / 2.0).toFloat()
    }

    fun snapToRuler(
        pointX: Float,
        pointY: Float,
        rulerCenterX: Float,
        rulerCenterY: Float,
        rulerAngleDeg: Float,
        rulerLength: Float = 650f,
        rulerThickness: Float = 110f,
        snapThreshold: Float = 60f
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

        val halfLen = rulerLength / 2f + 40f
        if (abs(proj) > halfLen) {
            return null
        }

        val halfThick = rulerThickness / 2f
        val distToTopEdge = abs(normDist - halfThick)
        val distToBottomEdge = abs(normDist - (-halfThick))

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
            else -> null
        }
    }

    enum class RulerEdge { NONE, TOP, BOTTOM }

    fun identifyRulerEdge(
        pointX: Float,
        pointY: Float,
        rulerCenterX: Float,
        rulerCenterY: Float,
        rulerAngleDeg: Float,
        rulerLength: Float = 650f,
        rulerThickness: Float = 110f,
        snapThreshold: Float = 80f
    ): RulerEdge {
        val angleRad = Math.toRadians(rulerAngleDeg.toDouble())
        val dirX = cos(angleRad).toFloat()
        val dirY = sin(angleRad).toFloat()
        val normalX = -dirY
        val normalY = dirX

        val vx = pointX - rulerCenterX
        val vy = pointY - rulerCenterY

        val proj = vx * dirX + vy * dirY
        val normDist = vx * normalX + vy * normalY

        val halfLen = rulerLength / 2f + 40f
        if (abs(proj) > halfLen) return RulerEdge.NONE

        val halfThick = rulerThickness / 2f
        val distToTopEdge = abs(normDist - halfThick)
        val distToBottomEdge = abs(normDist - (-halfThick))

        return when {
            distToTopEdge <= snapThreshold && distToTopEdge <= distToBottomEdge -> RulerEdge.TOP
            distToBottomEdge <= snapThreshold -> RulerEdge.BOTTOM
            else -> RulerEdge.NONE
        }
    }

    fun constrainToRulerEdge(
        pointX: Float,
        pointY: Float,
        edge: RulerEdge,
        rulerCenterX: Float,
        rulerCenterY: Float,
        rulerAngleDeg: Float,
        rulerLength: Float = 650f,
        rulerThickness: Float = 110f
    ): Pair<Float, Float> {
        val angleRad = Math.toRadians(rulerAngleDeg.toDouble())
        val dirX = cos(angleRad).toFloat()
        val dirY = sin(angleRad).toFloat()
        val normalX = -dirY
        val normalY = dirX

        val vx = pointX - rulerCenterX
        val vy = pointY - rulerCenterY
        val proj = (vx * dirX + vy * dirY).coerceIn(-rulerLength / 2f, rulerLength / 2f)
        val halfThick = rulerThickness / 2f

        return when (edge) {
            RulerEdge.TOP -> {
                val sx = rulerCenterX + dirX * proj + normalX * halfThick
                val sy = rulerCenterY + dirY * proj + normalY * halfThick
                Pair(sx, sy)
            }
            RulerEdge.BOTTOM -> {
                val sx = rulerCenterX + dirX * proj - normalX * halfThick
                val sy = rulerCenterY + dirY * proj - normalY * halfThick
                Pair(sx, sy)
            }
            RulerEdge.NONE -> Pair(pointX, pointY)
        }
    }

    fun snapAngleMagnetically(angleDeg: Float, threshold: Float = 4f): Float {
        val snapTargets = floatArrayOf(0f, 15f, 30f, 45f, 60f, 75f, 90f, 105f, 120f, 135f, 150f, 165f, 180f, 195f, 210f, 225f, 240f, 255f, 270f, 285f, 300f, 315f, 330f, 345f, 360f)
        for (target in snapTargets) {
            if (abs(angleDeg - target) <= threshold) {
                return if (target >= 360f) 0f else target
            }
        }
        return angleDeg
    }

    private fun snapAngle(startX: Float, startY: Float, endX: Float, endY: Float): Pair<Float, Float> {
        val dx = endX - startX
        val dy = endY - startY
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len == 0f) return Pair(endX, endY)

        val angleRad = atan2(dy.toDouble(), dx.toDouble())
        var angleDeg = Math.toDegrees(angleRad).toFloat()
        if (angleDeg < 0) angleDeg += 360f

        val snapTargets = floatArrayOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f, 360f)
        for (target in snapTargets) {
            if (abs(angleDeg - target) < 10f) {
                val rad = Math.toRadians(target.toDouble())
                return Pair(
                    startX + (len * cos(rad)).toFloat(),
                    startY + (len * sin(rad)).toFloat()
                )
            }
        }
        return Pair(endX, endY)
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
     * Calculates the minimum distance from point (px, py) to the line segment from (x1, y1) to (x2, y2).
     */
    fun distToSegment(px: Float, py: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
        val dx = x2 - x1
        val dy = y2 - y1
        val lenSq = dx * dx + dy * dy
        if (lenSq == 0f) return hypot((px - x1).toDouble(), (py - y1).toDouble()).toFloat()
        val t = (((px - x1) * dx + (py - y1) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = x1 + t * dx
        val projY = y1 + t * dy
        return hypot((px - projX).toDouble(), (py - projY).toDouble()).toFloat()
    }

    /**
     * Calculates the minimum distance from [point] to the actual outline/perimeter of [shape].
     * Crucially: the interior of an unfilled shape is considered empty space, not part of the shape object.
     */
    fun distanceToShapeOutline(shape: GeometricShape, point: Offset): Float {
        val px = point.x
        val py = point.y

        return when (shape.shapeType) {
            ShapeType.LINE -> {
                distToSegment(px, py, shape.startX, shape.startY, shape.endX, shape.endY)
            }
            ShapeType.ARROW -> {
                val lineDist = distToSegment(px, py, shape.startX, shape.startY, shape.endX, shape.endY)
                val angle = atan2((shape.endY - shape.startY).toDouble(), (shape.endX - shape.startX).toDouble())
                val arrowLen = 18.0
                val arrowAngle = Math.toRadians(25.0)
                val x1 = (shape.endX - arrowLen * cos(angle - arrowAngle)).toFloat()
                val y1 = (shape.endY - arrowLen * sin(angle - arrowAngle)).toFloat()
                val x2 = (shape.endX - arrowLen * cos(angle + arrowAngle)).toFloat()
                val y2 = (shape.endY - arrowLen * sin(angle + arrowAngle)).toFloat()
                val h1 = distToSegment(px, py, shape.endX, shape.endY, x1, y1)
                val h2 = distToSegment(px, py, shape.endX, shape.endY, x2, y2)
                minOf(lineDist, h1, h2)
            }
            ShapeType.RECTANGLE, ShapeType.SQUARE -> {
                val left = min(shape.startX, shape.endX)
                val right = max(shape.startX, shape.endX)
                val top = min(shape.startY, shape.endY)
                val bottom = max(shape.startY, shape.endY)
                val dTop = distToSegment(px, py, left, top, right, top)
                val dRight = distToSegment(px, py, right, top, right, bottom)
                val dBottom = distToSegment(px, py, right, bottom, left, bottom)
                val dLeft = distToSegment(px, py, left, bottom, left, top)
                minOf(dTop, dRight, dBottom, dLeft)
            }
            ShapeType.CIRCLE -> {
                val cx = shape.centerX
                val cy = shape.centerY
                val radius = (shape.width + shape.height) / 4f
                val distFromCenter = hypot((px - cx).toDouble(), (py - cy).toDouble()).toFloat()
                abs(distFromCenter - radius)
            }
            ShapeType.ELLIPSE -> {
                val cx = shape.centerX
                val cy = shape.centerY
                val rx = shape.width / 2f
                val ry = shape.height / 2f
                if (rx <= 0f || ry <= 0f) return hypot((px - cx).toDouble(), (py - cy).toDouble()).toFloat()
                val angle = atan2((py - cy).toDouble(), (px - cx).toDouble())
                val outlineX = cx + rx * cos(angle).toFloat()
                val outlineY = cy + ry * sin(angle).toFloat()
                hypot((px - outlineX).toDouble(), (py - outlineY).toDouble()).toFloat()
            }
            ShapeType.TRIANGLE -> {
                val left = min(shape.startX, shape.endX)
                val right = max(shape.startX, shape.endX)
                val top = min(shape.startY, shape.endY)
                val bottom = max(shape.startY, shape.endY)
                val cx = (left + right) / 2f
                val d1 = distToSegment(px, py, cx, top, left, bottom)
                val d2 = distToSegment(px, py, left, bottom, right, bottom)
                val d3 = distToSegment(px, py, right, bottom, cx, top)
                minOf(d1, d2, d3)
            }
            ShapeType.POLYGON, ShapeType.STAR -> {
                val cx = shape.centerX
                val cy = shape.centerY
                val r = shape.width / 2f
                val sides = shape.sideCount.coerceAtLeast(3)
                var minDist = Float.MAX_VALUE
                for (i in 0 until sides) {
                    val theta1 = (i * 2 * Math.PI / sides) - Math.PI / 2
                    val theta2 = ((i + 1) * 2 * Math.PI / sides) - Math.PI / 2
                    val px1 = (cx + r * cos(theta1)).toFloat()
                    val py1 = (cy + r * sin(theta1)).toFloat()
                    val px2 = (cx + r * cos(theta2)).toFloat()
                    val py2 = (cy + r * sin(theta2)).toFloat()
                    val d = distToSegment(px, py, px1, py1, px2, py2)
                    if (d < minDist) minDist = d
                }
                minDist
            }
        }
    }

    /**
     * Converts a geometric shape's perimeter outline into a sequence of points
     * for vector-level partial precision trimming/erasing.
     */
    fun shapeToOutlinePoints(shape: GeometricShape): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        fun addSegment(x1: Float, y1: Float, x2: Float, y2: Float, count: Int) {
            for (i in 0 until count) {
                val t = i.toFloat() / count
                points.add(StrokePoint(x1 + t * (x2 - x1), y1 + t * (y2 - y1)))
            }
        }

        when (shape.shapeType) {
            ShapeType.LINE, ShapeType.ARROW -> {
                addSegment(shape.startX, shape.startY, shape.endX, shape.endY, 30)
                points.add(StrokePoint(shape.endX, shape.endY))
            }
            ShapeType.RECTANGLE, ShapeType.SQUARE -> {
                val left = min(shape.startX, shape.endX)
                val right = max(shape.startX, shape.endX)
                val top = min(shape.startY, shape.endY)
                val bottom = max(shape.startY, shape.endY)
                addSegment(left, top, right, top, 20)
                addSegment(right, top, right, bottom, 20)
                addSegment(right, bottom, left, bottom, 20)
                addSegment(left, bottom, left, top, 20)
                points.add(StrokePoint(left, top))
            }
            ShapeType.TRIANGLE -> {
                val left = min(shape.startX, shape.endX)
                val right = max(shape.startX, shape.endX)
                val top = min(shape.startY, shape.endY)
                val bottom = max(shape.startY, shape.endY)
                val cx = (left + right) / 2f
                addSegment(cx, top, left, bottom, 25)
                addSegment(left, bottom, right, bottom, 25)
                addSegment(right, bottom, cx, top, 25)
                points.add(StrokePoint(cx, top))
            }
            ShapeType.CIRCLE -> {
                val cx = shape.centerX
                val cy = shape.centerY
                val r = (shape.width + shape.height) / 4f
                val count = 72
                for (i in 0..count) {
                    val theta = (i.toFloat() / count) * 2 * Math.PI
                    points.add(StrokePoint((cx + r * cos(theta)).toFloat(), (cy + r * sin(theta)).toFloat()))
                }
            }
            ShapeType.ELLIPSE -> {
                val cx = shape.centerX
                val cy = shape.centerY
                val rx = shape.width / 2f
                val ry = shape.height / 2f
                val count = 72
                for (i in 0..count) {
                    val theta = (i.toFloat() / count) * 2 * Math.PI
                    points.add(StrokePoint((cx + rx * cos(theta)).toFloat(), (cy + ry * sin(theta)).toFloat()))
                }
            }
            ShapeType.POLYGON, ShapeType.STAR -> {
                val cx = shape.centerX
                val cy = shape.centerY
                val r = shape.width / 2f
                val sides = shape.sideCount.coerceAtLeast(3)
                for (i in 0 until sides) {
                    val theta1 = (i * 2 * Math.PI / sides) - Math.PI / 2
                    val theta2 = ((i + 1) * 2 * Math.PI / sides) - Math.PI / 2
                    val px1 = (cx + r * cos(theta1)).toFloat()
                    val py1 = (cy + r * sin(theta1)).toFloat()
                    val px2 = (cx + r * cos(theta2)).toFloat()
                    val py2 = (cy + r * sin(theta2)).toFloat()
                    addSegment(px1, py1, px2, py2, 15)
                }
                val firstTheta = -Math.PI / 2
                points.add(StrokePoint((cx + r * cos(firstTheta)).toFloat(), (cy + r * sin(firstTheta)).toFloat()))
            }
        }
        return points
    }
}
