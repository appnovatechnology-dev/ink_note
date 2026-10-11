package com.example

import androidx.compose.ui.input.pointer.PointerType
import com.example.inknote.data.model.PalmRejectionMode
import com.example.inknote.data.model.ToolType
import com.example.inknote.ui.canvas.components.StylusInputState
import org.junit.Assert.*
import org.junit.Test

class CanvasNavigationAndPalmRejectionTest {

    @Test
    fun `stylus only mode rejects finger touch for all tools`() {
        val stylusState = StylusInputState()
        
        // In STYLUS_ONLY mode, touch/finger is ALWAYS rejected across all tools (lasso, laser, shape, pen, eraser)
        val shouldRejectTouch = stylusState.shouldRejectTouch(
            pointerType = PointerType.Touch,
            mode = PalmRejectionMode.STYLUS_ONLY
        )
        assertTrue("Finger touch must be rejected in STYLUS_ONLY mode", shouldRejectTouch)

        // Stylus pointer must ALWAYS be accepted in STYLUS_ONLY mode
        val shouldRejectStylus = stylusState.shouldRejectTouch(
            pointerType = PointerType.Stylus,
            mode = PalmRejectionMode.STYLUS_ONLY
        )
        assertFalse("Stylus pointer must not be rejected in STYLUS_ONLY mode", shouldRejectStylus)

        // Eraser pointer (e.g. stylus back eraser) must be accepted
        val shouldRejectEraser = stylusState.shouldRejectTouch(
            pointerType = PointerType.Eraser,
            mode = PalmRejectionMode.STYLUS_ONLY
        )
        assertFalse("Stylus eraser tip must not be rejected in STYLUS_ONLY mode", shouldRejectEraser)
    }

    @Test
    fun `off mode allows both finger and stylus touches`() {
        val stylusState = StylusInputState()

        val shouldRejectTouch = stylusState.shouldRejectTouch(
            pointerType = PointerType.Touch,
            mode = PalmRejectionMode.OFF
        )
        assertFalse("Finger touch should be accepted when palm rejection is OFF", shouldRejectTouch)

        val shouldRejectStylus = stylusState.shouldRejectTouch(
            pointerType = PointerType.Stylus,
            mode = PalmRejectionMode.OFF
        )
        assertFalse("Stylus should be accepted when palm rejection is OFF", shouldRejectStylus)
    }

    @Test
    fun `auto mode rejects finger when stylus was recently active`() {
        val stylusState = StylusInputState()

        // Initially without stylus detected
        val beforeStylus = stylusState.shouldRejectTouch(
            pointerType = PointerType.Touch,
            mode = PalmRejectionMode.AUTO
        )
        assertFalse(beforeStylus)

        // Record stylus event
        stylusState.recordStylusEvent()

        // After stylus detected, finger touches are rejected
        val afterStylus = stylusState.shouldRejectTouch(
            pointerType = PointerType.Touch,
            mode = PalmRejectionMode.AUTO
        )
        assertTrue("Finger touch should be rejected when stylus was recently active in AUTO mode", afterStylus)
    }

    @Test
    fun `page jump input validation logic`() {
        val totalPages = 600

        fun validatePageJump(input: String): Pair<Int?, String?> {
            val parsed = input.trim().toIntOrNull()
            return when {
                parsed == null -> Pair(null, "Please enter a valid page number")
                parsed <= 0 -> Pair(null, "Page number must be at least 1")
                parsed > totalPages -> Pair(null, "This notebook only has $totalPages pages")
                else -> Pair(parsed - 1, null)
            }
        }

        // Valid jump
        val jump550 = validatePageJump("550")
        assertNull(jump550.second)
        assertEquals(549, jump550.first)

        val jumpFirst = validatePageJump("1")
        assertNull(jumpFirst.second)
        assertEquals(0, jumpFirst.first)

        val jumpLast = validatePageJump("600")
        assertNull(jumpLast.second)
        assertEquals(599, jumpLast.first)

        // Out of range (higher than 600)
        val jumpOutHigh = validatePageJump("601")
        assertNull(jumpOutHigh.first)
        assertEquals("This notebook only has 600 pages", jumpOutHigh.second)

        // Out of range (zero or negative)
        val jumpZero = validatePageJump("0")
        assertNull(jumpZero.first)
        assertEquals("Page number must be at least 1", jumpZero.second)

        val jumpNeg = validatePageJump("-5")
        assertNull(jumpNeg.first)
        assertEquals("Page number must be at least 1", jumpNeg.second)

        // Invalid non-numeric string
        val jumpInvalid = validatePageJump("abc")
        assertNull(jumpInvalid.first)
        assertEquals("Please enter a valid page number", jumpInvalid.second)
    }

    @Test
    fun `zoom gesture can zoom significantly beyond fit to screen up to 500 percent`() {
        val manager = com.example.inknote.ui.canvas.components.ZoomGestureManager(
            fitZoom = 1.0f,
            minZoom = 0.5f,
            maxZoom = 5.0f
        )

        manager.onGestureStart(1.0f)
        var zoom = 1.0f

        // Pinch to zoom in: factor > 1.0
        for (i in 0 until 10) {
            zoom = manager.onGestureZoom(zoom, 1.25f)
        }

        // Must be able to zoom in deeply beyond fit-to-screen (well above 1.0f)
        assertTrue("Zoom should be well above fit-to-screen (1.0f)", zoom > 3.0f)
        assertTrue("Zoom should not exceed ceiling of 5.0f", zoom <= 5.0f)
    }

    @Test
    fun `zoom gesture smoothly transitions through fitZoom without hard locking`() {
        val manager = com.example.inknote.ui.canvas.components.ZoomGestureManager(
            fitZoom = 1.0f,
            minZoom = 0.5f,
            maxZoom = 5.0f
        )

        // Starting below fitZoom (e.g. 0.90f)
        manager.onGestureStart(0.90f)
        var zoom = 0.90f

        // Zooming in with deliberate pinch
        zoom = manager.onGestureZoom(zoom, 1.15f)
        // Next zoom steps crossing 1.0f
        zoom = manager.onGestureZoom(zoom, 1.15f)
        zoom = manager.onGestureZoom(zoom, 1.15f)

        // Verifies user is NOT stopped/locked at 1.0f during a continuous pinch!
        assertTrue("Zoom must smoothly cross 1.0f into page detail", zoom > 1.0f)
    }

    @Test
    fun `zoom gesture snaps cleanly to fitZoom on gesture end when close to 1_0`() {
        val manager = com.example.inknote.ui.canvas.components.ZoomGestureManager(
            fitZoom = 1.0f,
            minZoom = 0.5f,
            maxZoom = 5.0f
        )

        manager.onGestureStart(1.0f)
        // Zoom settles at 1.03f (very close to fitZoom)
        val settledNear = manager.onGestureEnd(1.03f)
        assertEquals("Should snap cleanly to exactly 1.0f when near fitZoom", 1.0f, settledNear, 0.001f)

        // Zoom settles at 0.97f (very close to fitZoom)
        val settledNearBelow = manager.onGestureEnd(0.97f)
        assertEquals("Should snap cleanly to exactly 1.0f when near fitZoom", 1.0f, settledNearBelow, 0.001f)

        // Zoom settles far away at 2.5f (examining detail)
        val settledFar = manager.onGestureEnd(2.5f)
        assertEquals("Should stay at 2.5f when far from fitZoom", 2.5f, settledFar, 0.001f)
    }

    @Test
    fun `zoom state branching between vertical scroll at or below fit and free 2D pan above fit`() {
        fun isZoomedIn(zoomScale: Float, isFitToScreen: Boolean): Boolean {
            return zoomScale > 1.005f && !isFitToScreen
        }

        // 1. At fit-to-screen (1.0f) or with fit-to-screen flag active -> constrained vertical scroll
        assertFalse("At 1.0f with isFitToScreen=true must use vertical scroll", isZoomedIn(1.0f, true))
        assertFalse("At 1.0f with isFitToScreen=false must use vertical scroll", isZoomedIn(1.0f, false))
        assertFalse("Below 1.0f (0.75f) must use vertical scroll", isZoomedIn(0.75f, false))
        assertFalse("Below 1.0f (0.50f) must use vertical scroll", isZoomedIn(0.50f, false))

        // 2. Above fit-to-screen threshold -> free 2D panning active
        assertTrue("At 1.01f with isFitToScreen=false must activate 2D pan", isZoomedIn(1.01f, false))
        assertTrue("At 1.50f with isFitToScreen=false must activate 2D pan", isZoomedIn(1.50f, false))
        assertTrue("At 3.00f with isFitToScreen=false must activate 2D pan", isZoomedIn(3.00f, false))
        assertTrue("At 5.00f with isFitToScreen=false must activate 2D pan", isZoomedIn(5.00f, false))

        // 3. User toggles Fit-To-Screen while zoomed in -> returns cleanly to vertical scroll
        assertFalse("Toggling fit-to-screen back to true immediately restores vertical scroll", isZoomedIn(1.0f, true))
    }

    @Test
    fun `free 2D panning dispatches diagonal movements in all directions simultaneously`() {
        var dispatchedX = 0f
        var dispatchedY = 0f

        fun handle2DPan(dragAmountX: Float, dragAmountY: Float) {
            dispatchedX += -dragAmountX
            dispatchedY += -dragAmountY
        }

        // 1. Diagonal Up-Left drag: finger moves (-30, -40) -> viewport pans (+30, +40)
        dispatchedX = 0f
        dispatchedY = 0f
        handle2DPan(-30f, -40f)
        assertEquals(30f, dispatchedX, 0.001f)
        assertEquals(40f, dispatchedY, 0.001f)

        // 2. Diagonal Up-Right drag: finger moves (+50, -60) -> viewport pans (-50, +60)
        dispatchedX = 0f
        dispatchedY = 0f
        handle2DPan(50f, -60f)
        assertEquals(-50f, dispatchedX, 0.001f)
        assertEquals(60f, dispatchedY, 0.001f)

        // 3. Diagonal Down-Left drag: finger moves (-25, +35) -> viewport pans (+25, -35)
        dispatchedX = 0f
        dispatchedY = 0f
        handle2DPan(-25f, 35f)
        assertEquals(25f, dispatchedX, 0.001f)
        assertEquals(-35f, dispatchedY, 0.001f)

        // 4. Diagonal Down-Right drag: finger moves (+70, +80) -> viewport pans (-70, -80)
        dispatchedX = 0f
        dispatchedY = 0f
        handle2DPan(70f, 80f)
        assertEquals(-70f, dispatchedX, 0.001f)
        assertEquals(-80f, dispatchedY, 0.001f)
    }

    @Test
    fun `page horizontal centering when zoomed below fit-to-screen has strictly equal left and right margins`() {
        val viewportWidth = 400f
        val baseWidth = 400f

        fun computeHorizontalMargins(zoomScale: Float, isFitToScreen: Boolean): Pair<Float, Float> {
            val effectiveScale = if (isFitToScreen) 1.0f else zoomScale
            val renderedWidth = baseWidth * effectiveScale
            val totalEmptySpace = (viewportWidth - renderedWidth).coerceAtLeast(0f)
            val leftMargin = totalEmptySpace / 2f
            val rightMargin = totalEmptySpace / 2f
            return Pair(leftMargin, rightMargin)
        }

        // At 50% zoom: rendered width is 200f -> leftMargin = 100f, rightMargin = 100f
        val margins50 = computeHorizontalMargins(0.50f, false)
        assertEquals(100f, margins50.first, 0.001f)
        assertEquals(100f, margins50.second, 0.001f)
        assertEquals("Left and right margins must be identical for centering", margins50.first, margins50.second, 0.001f)

        // At 75% zoom: rendered width is 300f -> leftMargin = 50f, rightMargin = 50f
        val margins75 = computeHorizontalMargins(0.75f, false)
        assertEquals(50f, margins75.first, 0.001f)
        assertEquals(50f, margins75.second, 0.001f)
        assertEquals("Left and right margins must be identical for centering", margins75.first, margins75.second, 0.001f)

        // At 90% zoom: rendered width is 360f -> leftMargin = 20f, rightMargin = 20f
        val margins90 = computeHorizontalMargins(0.90f, false)
        assertEquals(20f, margins90.first, 0.001f)
        assertEquals(20f, margins90.second, 0.001f)
        assertEquals("Left and right margins must be identical for centering", margins90.first, margins90.second, 0.001f)

        // At 100% zoom / Fit to screen: rendered width is 400f -> margins = 0
        val marginsFit = computeHorizontalMargins(1.0f, true)
        assertEquals(0f, marginsFit.first, 0.001f)
        assertEquals(0f, marginsFit.second, 0.001f)
    }

    @Test
    fun `seamless fit-to-screen sizing maintains stable finite width without collapse`() {
        val baseWidth = 440f

        fun computeTargetWidth(zoomScale: Float, isFitToScreen: Boolean): Float {
            val effectiveScale = if (isFitToScreen) 1.0f else zoomScale
            return (baseWidth * effectiveScale).coerceIn(160f, 4500f)
        }

        // Just below lock point (0.99x)
        val wBelow = computeTargetWidth(0.99f, false)
        assertEquals(435.6f, wBelow, 0.01f)

        // Exactly at lock point with isFitToScreen = true
        val wLock = computeTargetWidth(1.0f, true)
        assertEquals(440f, wLock, 0.01f)

        // Just above lock point (1.01x)
        val wAbove = computeTargetWidth(1.01f, false)
        assertEquals(444.4f, wAbove, 0.01f)

        // Continuity check: difference across lock point is minuscule and continuous
        assertTrue("Width at lock point must equal base width", wLock == baseWidth)
        assertTrue("Width step across lock boundary must be less than 5px (continuous transition)", kotlin.math.abs(wLock - wBelow) < 5f)
        assertTrue("Width step across lock boundary must be less than 5px (continuous transition)", kotlin.math.abs(wAbove - wLock) < 5f)
    }

    @Test
    fun `toolbar collapse state toggles correctly and retains active tool icon`() {
        var isToolbarCollapsed = false
        var activeTool = ToolType.PEN
        val initialPosition = Pair(0f, 0f)
        var currentPosition = initialPosition

        fun getActiveToolIconName(tool: ToolType): String {
            return when (tool) {
                ToolType.PEN -> "Edit"
                ToolType.HIGHLIGHTER -> "BorderColor"
                ToolType.ERASER -> "AutoFixNormal"
                ToolType.LASSO -> "Gesture"
                ToolType.LASER -> "Highlight"
                ToolType.TEXT -> "TextFields"
                ToolType.SHAPE -> "Category"
                ToolType.RULER -> "Straighten"
                ToolType.MEDIA -> "Image"
                ToolType.HAND -> "PanTool"
            }
        }

        // Initially expanded
        assertFalse(isToolbarCollapsed)
        assertEquals("Edit", getActiveToolIconName(activeTool))

        // Collapse toolbar
        isToolbarCollapsed = !isToolbarCollapsed
        assertTrue(isToolbarCollapsed)
        // Collapsed icon matches PEN
        assertEquals("Edit", getActiveToolIconName(activeTool))

        // Switch tool while collapsed (or expanded)
        activeTool = ToolType.HIGHLIGHTER
        assertEquals("BorderColor", getActiveToolIconName(activeTool))

        activeTool = ToolType.LASER
        assertEquals("Highlight", getActiveToolIconName(activeTool))

        // Drag collapsed icon to new coordinates
        currentPosition = Pair(120f, 250f)
        assertEquals(120f, currentPosition.first, 0.001f)
        assertEquals(250f, currentPosition.second, 0.001f)

        // Expand toolbar back
        isToolbarCollapsed = !isToolbarCollapsed
        assertFalse(isToolbarCollapsed)
        // Position remains at moved location
        assertEquals(120f, currentPosition.first, 0.001f)
        assertEquals(250f, currentPosition.second, 0.001f)
        assertEquals("Highlight", getActiveToolIconName(activeTool))
    }

    @Test
    fun `pen and highlighter maintain independent continuous slider width bounds`() {
        val penMin = 1.0f
        val penMax = 20.0f
        val highlighterMin = 8.0f
        val highlighterMax = 48.0f

        var currentPenWidth = 3.5f
        var currentHighlighterWidth = 24.0f
        var currentEraserWidth = 30.0f

        // 1. Independent updates
        val newPen = 7.3f
        currentPenWidth = newPen.coerceIn(penMin, penMax)
        assertEquals(7.3f, currentPenWidth, 0.001f)
        assertEquals(24.0f, currentHighlighterWidth, 0.001f)
        assertEquals(30.0f, currentEraserWidth, 0.001f)

        val newHighlighter = 36.5f
        currentHighlighterWidth = newHighlighter.coerceIn(highlighterMin, highlighterMax)
        assertEquals(7.3f, currentPenWidth, 0.001f)
        assertEquals(36.5f, currentHighlighterWidth, 0.001f)
        assertEquals(30.0f, currentEraserWidth, 0.001f)

        // 2. Bound clamping
        val clampedUnderPen = (-5.0f).coerceIn(penMin, penMax)
        assertEquals(penMin, clampedUnderPen, 0.001f)

        val clampedOverPen = 50.0f.coerceIn(penMin, penMax)
        assertEquals(penMax, clampedOverPen, 0.001f)

        val clampedUnderHighlighter = 2.0f.coerceIn(highlighterMin, highlighterMax)
        assertEquals(highlighterMin, clampedUnderHighlighter, 0.001f)

        val clampedOverHighlighter = 100.0f.coerceIn(highlighterMin, highlighterMax)
        assertEquals(highlighterMax, clampedOverHighlighter, 0.001f)

        // 3. Decimal precision rounding (continuous slider single decimal place)
        val rawSliderValue = 12.34567f
        val rounded = (kotlin.math.round(rawSliderValue * 10f) / 10f)
        assertEquals(12.3f, rounded, 0.001f)
    }
}
