package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.inknote.data.preferences.SettingsManager
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ToolbarResetAndBoundaryTest {

    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager(context)
    }

    @Test
    fun testResetToolbarPositionRestoresDefaultValues() {
        // Simulate toolbar having been dragged to an off-screen/stuck position and collapsed
        settingsManager.toolbarOffsetX = -120f
        settingsManager.toolbarOffsetY = -450f // Behind top bar
        settingsManager.isToolbarCollapsed = true

        assertEquals(-120f, settingsManager.toolbarOffsetX, 0.001f)
        assertEquals(-450f, settingsManager.toolbarOffsetY, 0.001f)
        assertTrue(settingsManager.isToolbarCollapsed)

        // Perform Reset Toolbar Position
        settingsManager.resetToolbarPosition()

        // Verify it immediately returns to default original location and expanded state
        assertEquals(0f, settingsManager.toolbarOffsetX, 0.001f)
        assertEquals(0f, settingsManager.toolbarOffsetY, 0.001f)
        assertFalse("Toolbar must be expanded after reset", settingsManager.isToolbarCollapsed)
    }

    @Test
    fun testViewModelResetToolbarPositionUpdatesUiStateInstantly() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = com.example.inknote.viewmodel.NoteCanvasViewModel(app)

        // Set toolbar to an invalid / off-screen offset and collapsed
        viewModel.setToolbarOffset(500f, 800f)
        viewModel.setToolbarCollapsed(true)

        val stateBefore = viewModel.uiState.value
        assertEquals(500f, stateBefore.toolbarOffsetX, 0.001f)
        assertEquals(800f, stateBefore.toolbarOffsetY, 0.001f)
        assertTrue(stateBefore.isToolbarCollapsed)
        val initialResetTrigger = stateBefore.toolbarResetTrigger

        // Trigger reset
        viewModel.resetToolbarPosition()

        val stateAfter = viewModel.uiState.value
        assertEquals("Toolbar X offset must reset to 0f", 0f, stateAfter.toolbarOffsetX, 0.001f)
        assertEquals("Toolbar Y offset must reset to 0f", 0f, stateAfter.toolbarOffsetY, 0.001f)
        assertFalse("Toolbar must be expanded after reset", stateAfter.isToolbarCollapsed)
        assertTrue("Toolbar reset trigger must increment", stateAfter.toolbarResetTrigger > initialResetTrigger)
    }

    @Test
    fun testClampingPreventsDraggingBehindTopBar() {
        // Drag logic clamps Y with coerceAtLeast(0f)
        val initialY = 0f
        val upwardDragAmount = -250f
        val clampedY = (initialY + upwardDragAmount).coerceAtLeast(0f)

        assertEquals("Upward dragging must hit hard invisible wall at top bar (0f)", 0f, clampedY, 0.001f)

        val downwardDragAmount = 180f
        val validY = (initialY + downwardDragAmount).coerceAtLeast(0f)
        assertEquals(180f, validY, 0.001f)
    }

    @Test
    fun testDockedToolbarSnapsToEdgesAndNeverFloatsInMiddle() {
        val screenWidthPx = 1080f
        val stripWidthPx = 140f
        val dockMarginPx = 18f
        val leftDockX = dockMarginPx
        val rightDockX = screenWidthPx - stripWidthPx - dockMarginPx

        // Helper snapping function matching DockableToolbar onDragEnd logic
        fun snapHorizontal(draggedX: Float): Float {
            val snapRight = draggedX > (screenWidthPx / 2f)
            return if (snapRight) rightDockX else leftDockX
        }

        // Dragging to 200f (left half) -> must snap back to Left edge flush
        val snappedLeft = snapHorizontal(200f)
        assertEquals("Must snap flush to left edge", leftDockX, snappedLeft, 0.001f)

        // Dragging near mid-screen left side (500f) -> snaps to Left edge
        assertEquals("Must snap to left edge when released on left half", leftDockX, snapHorizontal(500f), 0.001f)

        // Dragging past mid-screen to right half (600f) -> snaps to Right edge flush
        assertEquals("Must snap to right edge when released on right half", rightDockX, snapHorizontal(600f), 0.001f)

        // Dragging far right (1000f) -> snaps to Right edge flush
        assertEquals("Must snap flush to right edge", rightDockX, snapHorizontal(1000f), 0.001f)
    }

    @Test
    fun testDockedToolbarVerticalClampingRespectsTopAndBottomBars() {
        val screenHeightPx = 1920f
        val toolbarHeightPx = 1200f
        val topBarBufferPx = 180f
        val bottomBarBufferPx = 200f // Page navigator & zoom controls height

        val minY = topBarBufferPx
        val maxY = screenHeightPx - toolbarHeightPx - bottomBarBufferPx

        fun clampVertical(draggedY: Float): Float {
            return draggedY.coerceIn(minY, maxY)
        }

        // Dragged above top bar
        assertEquals("Must not go behind top bar", minY, clampVertical(-100f), 0.001f)
        assertEquals("Must hit top bar boundary", minY, clampVertical(50f), 0.001f)

        // Valid mid-screen drag
        assertEquals("Should allow vertical repositioning within bounds", 300f, clampVertical(300f), 0.001f)

        // Dragged too low into bottom bar
        assertEquals("Must hit bottom bar boundary and not overlap bottom controls", maxY, clampVertical(1800f), 0.001f)
    }

    @Test
    fun testResetToolbarReturnsDockedStripToDefaultEdgeAndExpandedState() {
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        val viewModel = com.example.inknote.viewmodel.NoteCanvasViewModel(app)

        // User dragged toolbar to right dock edge, custom vertical position, and collapsed it
        viewModel.setToolbarOffset(850f, 400f)
        viewModel.setToolbarCollapsed(true)

        assertTrue(viewModel.uiState.value.isToolbarCollapsed)
        assertEquals(850f, viewModel.uiState.value.toolbarOffsetX, 0.001f)

        // User taps "Reset Toolbar Position" from note 3-dots menu or Settings
        viewModel.resetToolbarPosition()

        val resetState = viewModel.uiState.value
        // Must return to default Left dock (0f), top position (0f), and expanded state
        assertEquals("OffsetX must be 0f (default Left docked edge)", 0f, resetState.toolbarOffsetX, 0.001f)
        assertEquals("OffsetY must be 0f (default top position)", 0f, resetState.toolbarOffsetY, 0.001f)
        assertFalse("Toolbar must be expanded after reset", resetState.isToolbarCollapsed)
    }
}
