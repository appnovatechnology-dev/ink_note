package com.example

import android.app.Application
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.inknote.data.db.AppDatabase
import com.example.inknote.data.model.GeometricShape
import com.example.inknote.data.model.ShapeType
import com.example.inknote.data.model.StrokePoint
import com.example.inknote.data.model.ToolType
import com.example.inknote.data.model.VectorStroke
import com.example.inknote.data.repository.NoteRepository
import com.example.inknote.geometry.GeometryEngine
import com.example.inknote.viewmodel.EraserMode
import com.example.inknote.viewmodel.NoteCanvasViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShapeAndEraserIndependenceTest {

    private lateinit var app: Application
    private lateinit var database: AppDatabase
    private lateinit var repository: NoteRepository
    private lateinit var viewModel: NoteCanvasViewModel

    @Before
    fun setup() = runBlocking {
        app = ApplicationProvider.getApplicationContext()
        database = AppDatabase.getInstance(app)
        repository = NoteRepository(database)
        viewModel = NoteCanvasViewModel(app)

        // Seed notebook with initial page
        val notebookId = repository.createNotebook("Shape Eraser Test Note")
        viewModel.initNotebook(notebookId)
    }

    @Test
    fun testDistanceToShapeOutline_centerIsFarFromOutline() {
        // Rectangle from (100, 100) to (300, 250) (width=200, height=150)
        val rect = GeometricShape(
            shapeType = ShapeType.RECTANGLE,
            startX = 100f,
            startY = 100f,
            endX = 300f,
            endY = 250f,
            strokeWidth = 3f
        )
        // Center of rectangle is at (200, 175)
        val center = Offset(200f, 175f)
        val distToCenter = GeometryEngine.distanceToShapeOutline(rect, center)
        // Closest edge from (200, 175) is top (y=100 -> dist 75) or bottom (y=250 -> dist 75)
        assertEquals("Distance from center to outline must be 75", 75f, distToCenter, 0.5f)

        // Point on outline (e.g. at (200, 100))
        val onEdge = Offset(200f, 100f)
        val distOnEdge = GeometryEngine.distanceToShapeOutline(rect, onEdge)
        assertEquals("Distance from edge point to outline must be 0", 0f, distOnEdge, 0.1f)

        // Circle at (200, 200) with radius 80
        val circle = GeometricShape(
            shapeType = ShapeType.CIRCLE,
            startX = 120f,
            startY = 120f,
            endX = 280f,
            endY = 280f,
            strokeWidth = 3f
        )
        val circleCenter = Offset(200f, 200f)
        val distCircleCenter = GeometryEngine.distanceToShapeOutline(circle, circleCenter)
        assertEquals("Distance from circle center to outline must be radius (80)", 80f, distCircleCenter, 0.5f)

        // Triangle from (100, 100) to (300, 300)
        val triangle = GeometricShape(
            shapeType = ShapeType.TRIANGLE,
            startX = 100f,
            startY = 100f,
            endX = 300f,
            endY = 300f,
            strokeWidth = 3f
        )
        val triCenter = Offset(200f, 200f)
        val distTriCenter = GeometryEngine.distanceToShapeOutline(triangle, triCenter)
        assertTrue("Distance from center of triangle to edges must be > 30", distTriCenter > 30f)
    }

    @Test
    fun testEraseTextInsideRectangle_keepsRectangleIntact_wholeStrokeMode() {
        val pageId = viewModel.currentPage?.id ?: return
        viewModel.setEraserMode(EraserMode.STROKE)

        // 1. Add Rectangle shape at (100, 100) to (300, 300)
        val rectShape = GeometricShape(
            id = 101L,
            pageId = pageId,
            shapeType = ShapeType.RECTANGLE,
            startX = 100f,
            startY = 100f,
            endX = 300f,
            endY = 300f,
            strokeColor = Color.Black,
            strokeWidth = 4f
        )
        viewModel.addShape(rectShape)

        // 2. Add text stroke clearly inside the rectangle (at center: 200, 200)
        val textStroke = VectorStroke(
            id = 201L,
            pageId = pageId,
            toolType = ToolType.PEN,
            color = Color.Blue,
            strokeWidth = 3f,
            points = listOf(
                StrokePoint(190f, 195f),
                StrokePoint(200f, 200f),
                StrokePoint(210f, 205f)
            )
        )
        viewModel.addStroke(textStroke)

        val initialData = viewModel.uiState.value.pagesData[pageId]!!
        assertEquals("Should have 1 shape", 1, initialData.shapes.size)
        assertEquals("Should have 1 stroke", 1, initialData.strokes.size)

        // 3. Erase text at (200, 200) with radius 15 (well inside the rectangle, far from outline edges at 100 and 300)
        viewModel.eraseAtPage(pageId, Offset(200f, 200f), radius = 15f)

        val updatedData = viewModel.uiState.value.pagesData[pageId]!!
        // The text stroke must be erased
        assertEquals("Text stroke inside shape must be erased", 0, updatedData.strokes.size)
        // The rectangle shape must remain FULLY intact!
        assertEquals("Rectangle shape must remain fully intact", 1, updatedData.shapes.size)
        assertEquals(rectShape.id, updatedData.shapes[0].id)
    }

    @Test
    fun testEraseTextInsideRectangle_keepsRectangleIntact_partialPrecisionMode() {
        val pageId = viewModel.currentPage?.id ?: return
        viewModel.setEraserMode(EraserMode.AREA) // Partial precision mode

        // 1. Add Rectangle shape
        val rectShape = GeometricShape(
            id = 102L,
            pageId = pageId,
            shapeType = ShapeType.RECTANGLE,
            startX = 100f,
            startY = 100f,
            endX = 400f,
            endY = 400f,
            strokeColor = Color.Black,
            strokeWidth = 4f
        )
        viewModel.addShape(rectShape)

        // 2. Add text stroke inside
        val textStroke = VectorStroke(
            id = 202L,
            pageId = pageId,
            toolType = ToolType.PEN,
            color = Color.Blue,
            strokeWidth = 3f,
            points = listOf(
                StrokePoint(240f, 250f),
                StrokePoint(250f, 250f),
                StrokePoint(260f, 250f)
            )
        )
        viewModel.addStroke(textStroke)

        // 3. Erase at center (250, 250) with radius 15
        viewModel.eraseAtPage(pageId, Offset(250f, 250f), radius = 15f)

        val updatedData = viewModel.uiState.value.pagesData[pageId]!!
        assertEquals("Text stroke inside shape must be erased", 0, updatedData.strokes.size)
        assertEquals("Rectangle shape must remain fully intact in partial mode", 1, updatedData.shapes.size)
    }

    @Test
    fun testEraseTextInsideCircle_keepsCircleIntact() {
        val pageId = viewModel.currentPage?.id ?: return
        viewModel.setEraserMode(EraserMode.STROKE)

        // Circle centered at (200, 200) with radius 75
        val circleShape = GeometricShape(
            id = 103L,
            pageId = pageId,
            shapeType = ShapeType.CIRCLE,
            startX = 125f,
            startY = 125f,
            endX = 275f,
            endY = 275f,
            strokeColor = Color.Black,
            strokeWidth = 4f
        )
        viewModel.addShape(circleShape)

        // Text stroke in center of circle
        val textStroke = VectorStroke(
            id = 203L,
            pageId = pageId,
            toolType = ToolType.PEN,
            color = Color.DarkGray,
            strokeWidth = 3f,
            points = listOf(
                StrokePoint(195f, 200f),
                StrokePoint(200f, 200f),
                StrokePoint(205f, 200f)
            )
        )
        viewModel.addStroke(textStroke)

        // Erase text in circle center
        viewModel.eraseAtPage(pageId, Offset(200f, 200f), radius = 18f)

        val updatedData = viewModel.uiState.value.pagesData[pageId]!!
        assertEquals("Text stroke inside circle must be erased", 0, updatedData.strokes.size)
        assertEquals("Circle shape must remain fully intact", 1, updatedData.shapes.size)
        assertEquals(circleShape.id, updatedData.shapes[0].id)
    }

    @Test
    fun testEraseTextInsideTriangle_keepsTriangleIntact() {
        val pageId = viewModel.currentPage?.id ?: return
        viewModel.setEraserMode(EraserMode.STROKE)

        // Triangle from (100, 100) to (400, 400)
        val triShape = GeometricShape(
            id = 104L,
            pageId = pageId,
            shapeType = ShapeType.TRIANGLE,
            startX = 100f,
            startY = 100f,
            endX = 400f,
            endY = 400f,
            strokeColor = Color.Black,
            strokeWidth = 4f
        )
        viewModel.addShape(triShape)

        // Text stroke at center of triangle (250, 300)
        val textStroke = VectorStroke(
            id = 204L,
            pageId = pageId,
            toolType = ToolType.PEN,
            color = Color.Red,
            strokeWidth = 3f,
            points = listOf(
                StrokePoint(245f, 300f),
                StrokePoint(250f, 300f),
                StrokePoint(255f, 300f)
            )
        )
        viewModel.addStroke(textStroke)

        // Erase text inside triangle
        viewModel.eraseAtPage(pageId, Offset(250f, 300f), radius = 15f)

        val updatedData = viewModel.uiState.value.pagesData[pageId]!!
        assertEquals("Text stroke inside triangle must be erased", 0, updatedData.strokes.size)
        assertEquals("Triangle shape must remain fully intact", 1, updatedData.shapes.size)
    }

    @Test
    fun testEraseShapeOutline_doesNotDeleteTextInside_wholeStrokeMode() {
        val pageId = viewModel.currentPage?.id ?: return
        viewModel.setEraserMode(EraserMode.STROKE)

        // Rectangle
        val rectShape = GeometricShape(
            id = 105L,
            pageId = pageId,
            shapeType = ShapeType.RECTANGLE,
            startX = 100f,
            startY = 100f,
            endX = 300f,
            endY = 300f,
            strokeColor = Color.Black,
            strokeWidth = 4f
        )
        viewModel.addShape(rectShape)

        // Text stroke in center
        val textStroke = VectorStroke(
            id = 205L,
            pageId = pageId,
            toolType = ToolType.PEN,
            color = Color.Blue,
            strokeWidth = 3f,
            points = listOf(
                StrokePoint(195f, 200f),
                StrokePoint(200f, 200f),
                StrokePoint(205f, 200f)
            )
        )
        viewModel.addStroke(textStroke)

        // Erase the top outline of the rectangle at (200, 100) with radius 10
        // (This touches the rectangle's outline, but is 100px away from the text at (200, 200))
        viewModel.eraseAtPage(pageId, Offset(200f, 100f), radius = 10f)

        val updatedData = viewModel.uiState.value.pagesData[pageId]!!
        // The rectangle shape was directly touched on its outline, so it is removed
        assertEquals("Rectangle shape should be erased when outline is touched", 0, updatedData.shapes.size)
        // The text inside was NOT touched and MUST remain fully intact!
        assertEquals("Text inside shape must NOT cascade-delete when shape outline is erased", 1, updatedData.strokes.size)
        assertEquals(textStroke.id, updatedData.strokes[0].id)
    }

    @Test
    fun testEraseShapeOutline_partialMode_doesNotDeleteTextInside() {
        val pageId = viewModel.currentPage?.id ?: return
        viewModel.setEraserMode(EraserMode.AREA) // Partial precision mode

        val rectShape = GeometricShape(
            id = 106L,
            pageId = pageId,
            shapeType = ShapeType.RECTANGLE,
            startX = 100f,
            startY = 100f,
            endX = 300f,
            endY = 300f,
            strokeColor = Color.Black,
            strokeWidth = 4f
        )
        viewModel.addShape(rectShape)

        val textStroke = VectorStroke(
            id = 206L,
            pageId = pageId,
            toolType = ToolType.PEN,
            color = Color.Blue,
            strokeWidth = 3f,
            points = listOf(
                StrokePoint(195f, 200f),
                StrokePoint(200f, 200f),
                StrokePoint(205f, 200f)
            )
        )
        viewModel.addStroke(textStroke)

        // Erase a section of the top outline at (200, 100) with radius 12
        viewModel.eraseAtPage(pageId, Offset(200f, 100f), radius = 12f)

        val updatedData = viewModel.uiState.value.pagesData[pageId]!!
        // The text inside is completely preserved
        assertTrue("Text inside must remain intact", updatedData.strokes.any { it.id == textStroke.id })
        // The shape was converted to partial trimmed outline strokes
        assertEquals("GeometricShape list should be updated", 0, updatedData.shapes.size)
        assertTrue("Remaining outline should be preserved as trimmed vector strokes", updatedData.strokes.size > 1)
    }
}
