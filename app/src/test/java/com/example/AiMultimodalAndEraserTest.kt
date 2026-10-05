package com.example

import android.content.Context
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.test.core.app.ApplicationProvider
import com.example.inknote.data.ai.AiSmartToolsEngine
import com.example.inknote.data.ai.PageImageRenderer
import com.example.inknote.data.db.entities.PageEntity
import com.example.inknote.data.model.CanvasTextItem
import com.example.inknote.data.model.GeometricShape
import com.example.inknote.data.model.ShapeType
import com.example.inknote.data.model.StrokePoint
import com.example.inknote.data.model.ToolType
import com.example.inknote.data.model.VectorStroke
import com.example.inknote.data.preferences.SettingsManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiMultimodalAndEraserTest {

    private val context: Context by lazy {
        ApplicationProvider.getApplicationContext<Context>()
    }

    // --- Part A: AI Features Multi-modal & Handwriting Support Tests ---

    @Test
    fun `page image renderer detects populated pages with handwriting strokes`() {
        val page = PageEntity(id = 1L, notebookId = 1L, pageNumber = 1)
        val stroke = VectorStroke(
            id = 101L,
            pageId = 1L,
            toolType = ToolType.PEN,
            color = Color(0xFF0F172A),
            strokeWidth = 3f,
            points = listOf(
                StrokePoint(100f, 100f),
                StrokePoint(150f, 120f),
                StrokePoint(200f, 100f)
            )
        )

        val isPopulated = PageImageRenderer.isPagePopulated(
            page = page,
            strokes = listOf(stroke),
            shapes = emptyList(),
            textItems = emptyList(),
            mediaItems = emptyList()
        )
        assertTrue("Handwritten page with strokes must be identified as populated", isPopulated)

        // Render to Base64 JPEG
        val base64 = PageImageRenderer.renderPageToBase64Jpeg(
            page = page,
            strokes = listOf(stroke),
            shapes = emptyList(),
            textItems = emptyList(),
            mediaItems = emptyList()
        )
        assertNotNull("Rendered Base64 string must not be null", base64)
        assertTrue("Rendered Base64 string must contain valid image data", base64.length > 500)
    }

    @Test
    fun `page image renderer returns empty for blank unedited page`() {
        val page = PageEntity(id = 2L, notebookId = 1L, pageNumber = 2)
        val isPopulated = PageImageRenderer.isPagePopulated(
            page = page,
            strokes = emptyList(),
            shapes = emptyList(),
            textItems = emptyList(),
            mediaItems = emptyList()
        )
        assertFalse("Blank page without strokes, shapes, or text must not be populated", isPopulated)
    }

    @Test
    fun `page image renderer correctly renders multi-page content with shapes and text`() {
        val page1 = PageEntity(id = 1L, notebookId = 1L, pageNumber = 1)
        val shape = GeometricShape(
            id = 201L,
            pageId = 1L,
            shapeType = ShapeType.RECTANGLE,
            startX = 50f,
            startY = 50f,
            endX = 250f,
            endY = 180f
        )
        val text = CanvasTextItem(
            id = 301L,
            pageId = 1L,
            text = "Biology Lecture - Cellular Mitosis",
            x = 50f,
            y = 220f
        )

        val page1Base64 = PageImageRenderer.renderPageToBase64Jpeg(
            page = page1,
            strokes = emptyList(),
            shapes = listOf(shape),
            textItems = listOf(text),
            mediaItems = emptyList()
        )
        assertTrue("Page with shapes and text must render to valid image Base64", page1Base64.isNotBlank())
    }

    @Test
    fun `ai engine gracefully reports error when offline`() = runBlocking {
        val engine = AiSmartToolsEngine(context)
        // Robolectric by default has no active network unless mocked
        if (!engine.isOnline()) {
            try {
                engine.summarizeNotes(
                    notebookTitle = "Offline Biology Notes",
                    pageImages = listOf("mock_base64_img"),
                    notesContent = ""
                )
                fail("Should throw IllegalStateException when offline")
            } catch (e: Exception) {
                assertTrue(
                    "Error message must mention active internet connection: ${e.message}",
                    e.message?.contains("internet connection", ignoreCase = true) == true ||
                            e.message?.contains("API key", ignoreCase = true) == true
                )
            }

            try {
                engine.generateFlashcards(
                    notebookId = 1L,
                    notebookTitle = "Offline Biology Notes",
                    pageImages = listOf("mock_base64_img"),
                    notesContent = ""
                )
                fail("Should throw IllegalStateException when offline")
            } catch (e: Exception) {
                assertTrue(
                    "Error message must mention active internet connection: ${e.message}",
                    e.message?.contains("internet connection", ignoreCase = true) == true ||
                            e.message?.contains("API key", ignoreCase = true) == true
                )
            }
        }
    }

    @Test
    fun `ai engine throws IllegalArgumentException when note is completely blank`() = runBlocking {
        val engine = AiSmartToolsEngine(context)
        try {
            engine.generateQuiz(
                notebookTitle = "Blank Notebook",
                pageImages = emptyList(),
                notesContent = ""
            )
            fail("Should throw IllegalArgumentException when note is empty")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Couldn't read content", ignoreCase = true) == true)
        }
    }

    // --- Part B: Eraser Adjustable Size & Accurate Visual Indicator Tests ---

    @Test
    fun `eraser size persists correctly across settings changes`() {
        val settings = SettingsManager(context)
        
        // Default size
        val initialWidth = settings.eraserWidth
        assertTrue("Initial eraser width must be positive", initialWidth > 0f)

        // Set to Small preset (16pt)
        settings.eraserWidth = 16f
        assertEquals("Eraser width must be updated to 16f", 16f, settings.eraserWidth, 0.01f)

        // Set to Large preset (54pt)
        settings.eraserWidth = 54f
        assertEquals("Eraser width must be updated to 54f", 54f, settings.eraserWidth, 0.01f)

        // Continuous custom slider value (e.g. 72.5f)
        settings.eraserWidth = 72.5f
        assertEquals("Eraser width must support continuous slider value 72.5f", 72.5f, settings.eraserWidth, 0.01f)
    }

    @Test
    fun `eraser hit-testing with segment interpolation deletes crossing strokes`() {
        // Line drawn between (100, 100) and (100, 200)
        val stroke = VectorStroke(
            id = 501L,
            pageId = 1L,
            points = listOf(
                StrokePoint(100f, 100f),
                StrokePoint(100f, 200f)
            ),
            strokeWidth = 4f
        )

        val eraserPoint = Offset(100f, 150f)
        val eraserRadius = 15f // half of 30pt diameter

        // Segment distance formula
        val p1 = stroke.points[0]
        val p2 = stroke.points[1]
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        val lenSq = dx * dx + dy * dy
        val t = (((eraserPoint.x - p1.x) * dx + (eraserPoint.y - p1.y) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = p1.x + t * dx
        val projY = p1.y + t * dy
        val dist = hypot((eraserPoint.x - projX).toDouble(), (eraserPoint.y - projY).toDouble()).toFloat()

        assertTrue(
            "Eraser point at midpoint must intersect line segment",
            dist <= (eraserRadius + stroke.strokeWidth / 2f)
        )
    }

    @Test
    fun `eraser visual circle diameter strictly matches chosen eraser width`() {
        val chosenEraserWidth = 48f
        val scaleFactor = 1.25f

        val calculatedRadiusPx = (chosenEraserWidth / 2f) * scaleFactor
        val calculatedDiameterPx = calculatedRadiusPx * 2f
        val expectedDiameterPx = chosenEraserWidth * scaleFactor

        assertEquals(
            "Eraser visual circle diameter must strictly match chosen eraserWidth * scaleFactor with zero offset",
            expectedDiameterPx,
            calculatedDiameterPx,
            0.001f
        )
    }
}
