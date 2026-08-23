package com.example.inknote.ui.canvas.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import com.example.inknote.data.model.StrokePoint
import kotlin.math.hypot

object InkStrokeSmoother {

    /**
     * Filters jitter from raw pointer input points using an adaptive exponential moving average filter.
     */
    fun smoothPoints(rawPoints: List<StrokePoint>): List<StrokePoint> {
        if (rawPoints.isEmpty()) return emptyList()
        if (rawPoints.size == 1) {
            // Synthesize tiny extension for single tap dots so they render cleanly
            val pt = rawPoints[0]
            return listOf(pt, pt.copy(x = pt.x + 0.1f, y = pt.y + 0.1f))
        }
        if (rawPoints.size == 2) return rawPoints

        val smoothed = ArrayList<StrokePoint>(rawPoints.size)
        smoothed.add(rawPoints.first())

        var prevX = rawPoints[0].x
        var prevY = rawPoints[0].y
        var prevPressure = rawPoints[0].pressure

        // Weight factor for exponential moving average
        val alpha = 0.65f
        val pressureAlpha = 0.5f

        for (i in 1 until rawPoints.size) {
            val curr = rawPoints[i]
            val dist = hypot((curr.x - prevX).toDouble(), (curr.y - prevY).toDouble())

            // Dynamic smoothing: if distance is small, apply higher smoothing to eliminate jitter;
            // if fast stroke, follow points more closely to prevent lag.
            val currentAlpha = if (dist < 4.0) 0.5f else if (dist > 25.0) 0.85f else alpha

            val smoothX = prevX + currentAlpha * (curr.x - prevX)
            val smoothY = prevY + currentAlpha * (curr.y - prevY)
            val smoothP = prevPressure + pressureAlpha * (curr.pressure - prevPressure)

            smoothed.add(
                StrokePoint(
                    x = smoothX,
                    y = smoothY,
                    pressure = smoothP.coerceIn(0.2f, 2.0f),
                    timestamp = curr.timestamp
                )
            )

            prevX = smoothX
            prevY = smoothY
            prevPressure = smoothP
        }

        return smoothed
    }

    /**
     * Generates a high-precision, midpoint quadratic Bezier path for buttery smooth pen rendering.
     */
    fun buildSmoothPath(points: List<StrokePoint>, scaleFactor: Float): Path {
        val path = Path()
        if (points.isEmpty()) return path
        if (points.size == 1) {
            val p = points[0]
            path.moveTo(p.x * scaleFactor, p.y * scaleFactor)
            path.lineTo(p.x * scaleFactor + 0.2f, p.y * scaleFactor + 0.2f)
            return path
        }

        val first = points[0]
        path.moveTo(first.x * scaleFactor, first.y * scaleFactor)

        if (points.size == 2) {
            val second = points[1]
            path.lineTo(second.x * scaleFactor, second.y * scaleFactor)
            return path
        }

        // Quadratic Bezier curves between successive midpoints
        for (i in 1 until points.size - 1) {
            val p0 = points[i]
            val p1 = points[i + 1]

            val midX = (p0.x + p1.x) / 2f * scaleFactor
            val midY = (p0.y + p1.y) / 2f * scaleFactor

            path.quadraticTo(
                p0.x * scaleFactor,
                p0.y * scaleFactor,
                midX,
                midY
            )
        }

        // Final segment to last point
        val last = points.last()
        path.lineTo(last.x * scaleFactor, last.y * scaleFactor)

        return path
    }
}
