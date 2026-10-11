package com.example

import androidx.compose.ui.graphics.Color
import com.example.inknote.data.model.ShapeType
import com.example.inknote.data.model.StrokePoint
import com.example.inknote.geometry.GeometryEngine
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class ShapeRecognitionTest {

    private fun generateLinePoints(startX: Float, startY: Float, endX: Float, endY: Float, steps: Int): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        for (i in 0..steps) {
            val t = i.toFloat() / steps
            val x = startX + t * (endX - startX)
            val y = startY + t * (endY - startY)
            points.add(StrokePoint(x, y))
        }
        return points
    }

    private fun generateTrianglePoints(
        p1: Pair<Float, Float>,
        p2: Pair<Float, Float>,
        p3: Pair<Float, Float>,
        pointsPerSide: Int,
        noiseScale: Float = 0f
    ): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        fun addSegment(from: Pair<Float, Float>, to: Pair<Float, Float>) {
            for (i in 0 until pointsPerSide) {
                val t = i.toFloat() / pointsPerSide
                val nx = (Math.random().toFloat() - 0.5f) * noiseScale
                val ny = (Math.random().toFloat() - 0.5f) * noiseScale
                points.add(StrokePoint(from.first + t * (to.first - from.first) + nx, from.second + t * (to.second - from.second) + ny))
            }
        }
        addSegment(p1, p2)
        addSegment(p2, p3)
        addSegment(p3, p1)
        points.add(StrokePoint(p1.first, p1.second))
        return points
    }

    private fun generateCirclePoints(cx: Float, cy: Float, radius: Float, count: Int, noiseScale: Float = 0f): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        for (i in 0..count) {
            val theta = (i.toFloat() / count) * 2 * PI
            val r = radius + (Math.random().toFloat() - 0.5f) * noiseScale
            val x = cx + r * cos(theta).toFloat()
            val y = cy + r * sin(theta).toFloat()
            points.add(StrokePoint(x, y))
        }
        return points
    }

    private fun generateEllipsePoints(cx: Float, cy: Float, rx: Float, ry: Float, count: Int): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        for (i in 0..count) {
            val theta = (i.toFloat() / count) * 2 * PI
            val x = cx + rx * cos(theta).toFloat()
            val y = cy + ry * sin(theta).toFloat()
            points.add(StrokePoint(x, y))
        }
        return points
    }

    private fun generateRectanglePoints(x: Float, y: Float, w: Float, h: Float, pointsPerSide: Int): List<StrokePoint> {
        val points = mutableListOf<StrokePoint>()
        val p1 = Pair(x, y)
        val p2 = Pair(x + w, y)
        val p3 = Pair(x + w, y + h)
        val p4 = Pair(x, y + h)

        fun addSegment(from: Pair<Float, Float>, to: Pair<Float, Float>) {
            for (i in 0 until pointsPerSide) {
                val t = i.toFloat() / pointsPerSide
                points.add(StrokePoint(from.first + t * (to.first - from.first), from.second + t * (to.second - from.second)))
            }
        }
        addSegment(p1, p2)
        addSegment(p2, p3)
        addSegment(p3, p4)
        addSegment(p4, p1)
        points.add(StrokePoint(p1.first, p1.second))
        return points
    }

    @Test
    fun testTriangleRecognition_varietyOfSizesAndSpeeds() {
        // 1. Equilateral Triangle drawn at normal speed
        val t1 = generateTrianglePoints(Pair(100f, 50f), Pair(50f, 150f), Pair(150f, 150f), pointsPerSide = 25)
        val r1 = GeometryEngine.recognizeShape(t1, Color.Black, 3f)
        assertNotNull("Should recognize triangle", r1)
        assertEquals("Shape must be TRIANGLE, not CIRCLE or RECTANGLE", ShapeType.TRIANGLE, r1!!.shapeType)

        // 2. Small Triangle drawn quickly (sparse points, pointsPerSide = 8)
        val t2 = generateTrianglePoints(Pair(60f, 20f), Pair(30f, 70f), Pair(90f, 70f), pointsPerSide = 8)
        val r2 = GeometryEngine.recognizeShape(t2, Color.Black, 3f)
        assertNotNull("Should recognize small fast triangle", r2)
        assertEquals("Shape must be TRIANGLE", ShapeType.TRIANGLE, r2!!.shapeType)

        // 3. Large Triangle drawn slowly (dense points, pointsPerSide = 60)
        val t3 = generateTrianglePoints(Pair(300f, 50f), Pair(100f, 400f), Pair(500f, 400f), pointsPerSide = 60)
        val r3 = GeometryEngine.recognizeShape(t3, Color.Black, 3f)
        assertNotNull("Should recognize large slow triangle", r3)
        assertEquals("Shape must be TRIANGLE", ShapeType.TRIANGLE, r3!!.shapeType)

        // 4. Right-angled triangle
        val t4 = generateTrianglePoints(Pair(100f, 100f), Pair(100f, 250f), Pair(300f, 250f), pointsPerSide = 20)
        val r4 = GeometryEngine.recognizeShape(t4, Color.Black, 3f)
        assertNotNull("Should recognize right-angled triangle", r4)
        assertEquals("Shape must be TRIANGLE", ShapeType.TRIANGLE, r4!!.shapeType)

        // 5. Inverted / tilted triangle
        val t5 = generateTrianglePoints(Pair(50f, 100f), Pair(150f, 100f), Pair(100f, 220f), pointsPerSide = 20)
        val r5 = GeometryEngine.recognizeShape(t5, Color.Black, 3f)
        assertNotNull("Should recognize inverted triangle", r5)
        assertEquals("Shape must be TRIANGLE", ShapeType.TRIANGLE, r5!!.shapeType)
    }

    @Test
    fun testCircleRecognition_notConfusedWithTriangle() {
        val circlePoints = generateCirclePoints(cx = 150f, cy = 150f, radius = 60f, count = 48)
        val result = GeometryEngine.recognizeShape(circlePoints, Color.Black, 3f)
        assertNotNull("Should recognize circle", result)
        assertEquals("Shape must be CIRCLE, not TRIANGLE", ShapeType.CIRCLE, result!!.shapeType)
    }

    @Test
    fun testSquareAndRectangleRecognition() {
        // Square
        val squarePoints = generateRectanglePoints(x = 100f, y = 100f, w = 120f, h = 120f, pointsPerSide = 20)
        val squareResult = GeometryEngine.recognizeShape(squarePoints, Color.Black, 3f)
        assertNotNull("Should recognize square", squareResult)
        assertEquals("Shape must be SQUARE", ShapeType.SQUARE, squareResult!!.shapeType)

        // Rectangle (wide)
        val rectPoints = generateRectanglePoints(x = 50f, y = 50f, w = 240f, h = 90f, pointsPerSide = 20)
        val rectResult = GeometryEngine.recognizeShape(rectPoints, Color.Black, 3f)
        assertNotNull("Should recognize rectangle", rectResult)
        assertEquals("Shape must be RECTANGLE", ShapeType.RECTANGLE, rectResult!!.shapeType)
    }

    @Test
    fun testLineRecognition() {
        val linePoints = generateLinePoints(startX = 50f, startY = 80f, endX = 350f, endY = 82f, steps = 30)
        val lineResult = GeometryEngine.recognizeShape(linePoints, Color.Black, 3f)
        assertNotNull("Should recognize line", lineResult)
        assertEquals("Shape must be LINE", ShapeType.LINE, lineResult!!.shapeType)
    }

    @Test
    fun testEllipseRecognition() {
        val ellipsePoints = generateEllipsePoints(cx = 200f, cy = 200f, rx = 120f, ry = 50f, count = 50)
        val ellipseResult = GeometryEngine.recognizeShape(ellipsePoints, Color.Black, 3f)
        assertNotNull("Should recognize ellipse", ellipseResult)
        assertEquals("Shape must be ELLIPSE", ShapeType.ELLIPSE, ellipseResult!!.shapeType)
    }
}
