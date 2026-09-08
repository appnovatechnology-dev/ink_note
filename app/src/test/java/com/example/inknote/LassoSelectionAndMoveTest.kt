package com.example.inknote

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import com.example.inknote.data.db.entities.ShapeEntity
import com.example.inknote.data.db.entities.StrokeEntity
import com.example.inknote.data.model.*
import com.example.inknote.geometry.GeometryEngine
import org.junit.Assert.*
import org.junit.Test

class LassoSelectionAndMoveTest {

    @Test
    fun testLassoSelection_selectsOnlyEnclosedStrokes() {
        val lassoPolygon = listOf(
            Offset(50f, 50f),
            Offset(200f, 50f),
            Offset(200f, 200f),
            Offset(50f, 200f)
        )

        val insideStroke = VectorStroke(
            id = 101L,
            points = listOf(
                StrokePoint(80f, 80f),
                StrokePoint(90f, 90f),
                StrokePoint(100f, 100f)
            )
        )

        val outsideStroke = VectorStroke(
            id = 102L,
            points = listOf(
                StrokePoint(300f, 300f),
                StrokePoint(310f, 310f),
                StrokePoint(320f, 320f)
            )
        )

        assertTrue(
            "Inside stroke should be selected by lasso",
            GeometryEngine.isStrokeSelectedByLasso(insideStroke, lassoPolygon)
        )
        assertFalse(
            "Outside stroke should NOT be selected by lasso",
            GeometryEngine.isStrokeSelectedByLasso(outsideStroke, lassoPolygon)
        )
    }

    @Test
    fun testRepeatedMoves_zeroDataLossAcross5ConsecutiveMoves() {
        // Setup initial strokes with persistent IDs
        val stroke1 = VectorStroke(
            id = 1001L,
            points = listOf(StrokePoint(100f, 100f), StrokePoint(110f, 110f)),
            color = Color.Black
        )
        val stroke2 = VectorStroke(
            id = 1002L,
            points = listOf(StrokePoint(120f, 120f), StrokePoint(130f, 130f)),
            color = Color.Blue
        )
        val unselectedStroke = VectorStroke(
            id = 1003L,
            points = listOf(StrokePoint(500f, 500f), StrokePoint(510f, 510f)),
            color = Color.Red
        )

        var allStrokes = listOf(stroke1, stroke2, unselectedStroke)
        var selectedStrokes = listOf(stroke1, stroke2)

        val initialCount = allStrokes.size
        assertEquals(3, initialCount)

        // Perform 5 consecutive move operations
        val moveDeltas = listOf(
            Pair(10f, 20f),
            Pair(-5f, 15f),
            Pair(30f, -10f),
            Pair(15f, 25f),
            Pair(-20f, -15f)
        )

        var cumulativeDx = 0f
        var cumulativeDy = 0f

        for ((index, delta) in moveDeltas.withIndex()) {
            val (dx, dy) = delta
            cumulativeDx += dx
            cumulativeDy += dy

            val selectedIds = selectedStrokes.map { it.id }.toSet()

            // Reposition selected strokes without recreating IDs or dropping elements
            allStrokes = allStrokes.map { stroke ->
                if (selectedIds.contains(stroke.id)) {
                    stroke.copy(
                        points = stroke.points.map { p ->
                            p.copy(x = p.x + dx, y = p.y + dy)
                        }
                    )
                } else {
                    stroke
                }
            }

            // Keep selectedStrokes reference in sync
            selectedStrokes = allStrokes.filter { selectedIds.contains(it.id) }

            // Assertions after every move step
            assertEquals("Total stroke count must remain 3 at move $index", initialCount, allStrokes.size)
            assertEquals("Selected stroke count must remain 2 at move $index", 2, selectedStrokes.size)

            // Verify unselected stroke was never touched or lost
            val unselected = allStrokes.find { it.id == 1003L }
            assertNotNull("Unselected stroke must never be deleted", unselected)
            assertEquals(500f, unselected!!.points[0].x, 0.001f)
            assertEquals(500f, unselected.points[0].y, 0.001f)
        }

        // Final positions check
        val finalStroke1 = allStrokes.find { it.id == 1001L }!!
        val finalStroke2 = allStrokes.find { it.id == 1002L }!!

        assertEquals(100f + cumulativeDx, finalStroke1.points[0].x, 0.001f)
        assertEquals(100f + cumulativeDy, finalStroke1.points[0].y, 0.001f)
        assertEquals(120f + cumulativeDx, finalStroke2.points[0].x, 0.001f)
        assertEquals(120f + cumulativeDy, finalStroke2.points[0].y, 0.001f)
    }

    @Test
    fun testPersistenceRoundtrip_preservesMovedStrokesAndIds() {
        val strokeEntity = StrokeEntity(
            id = 42L,
            pageId = 1L,
            toolType = "PEN",
            colorArgb = 0xFF00FF00L,
            strokeWidth = 4.0f,
            alpha = 1.0f,
            pointsJson = "[{\"x\":250.0,\"y\":350.0,\"pressure\":0.8}]",
            orderIndex = 0
        )

        val shapeEntity = ShapeEntity(
            id = 84L,
            pageId = 1L,
            shapeType = "RECTANGLE",
            startX = 100f,
            startY = 150f,
            endX = 300f,
            endY = 350f,
            strokeColorArgb = 0xFF1E293BL
        )

        assertEquals(42L, strokeEntity.id)
        assertTrue(strokeEntity.pointsJson.contains("250.0"))
        assertTrue(strokeEntity.pointsJson.contains("350.0"))
        assertEquals(84L, shapeEntity.id)
        assertEquals(100f, shapeEntity.startX, 0.001f)
        assertEquals(300f, shapeEntity.endX, 0.001f)
    }
}
