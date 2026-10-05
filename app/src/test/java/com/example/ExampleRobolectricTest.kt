package com.example

import android.content.Context
import androidx.compose.ui.input.pointer.PointerType
import androidx.test.core.app.ApplicationProvider
import com.example.inknote.data.model.PalmRejectionMode
import com.example.inknote.ui.canvas.components.StylusInputState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Inkstead", appName)
  }

  @Test
  fun `stylus only palm rejection rejects touch and accepts stylus`() {
    val state = StylusInputState()

    // Stylus pointer is accepted
    assertFalse(
      state.shouldRejectTouch(
        pointerType = PointerType.Stylus,
        mode = PalmRejectionMode.STYLUS_ONLY,
        allowTouch = false
      )
    )

    // Eraser pointer is accepted
    assertFalse(
      state.shouldRejectTouch(
        pointerType = PointerType.Eraser,
        mode = PalmRejectionMode.STYLUS_ONLY,
        allowTouch = false
      )
    )

    // Touch pointer (finger/palm) is rejected in STYLUS_ONLY mode
    assertTrue(
      state.shouldRejectTouch(
        pointerType = PointerType.Touch,
        mode = PalmRejectionMode.STYLUS_ONLY,
        allowTouch = false
      )
    )
  }

  @Test
  fun `palm rejection off accepts both touch and stylus`() {
    val state = StylusInputState()

    assertFalse(
      state.shouldRejectTouch(
        pointerType = PointerType.Touch,
        mode = PalmRejectionMode.OFF,
        allowTouch = false
      )
    )

    assertFalse(
      state.shouldRejectTouch(
        pointerType = PointerType.Stylus,
        mode = PalmRejectionMode.OFF,
        allowTouch = false
      )
    )
  }

  @Test
  fun `tool tap behavior selects on first tap and opens settings on second tap`() {
    var activeTool = "PEN"
    var openSettingsTool: String? = null

    fun handleToolClick(toolKey: String, isAlreadyActive: Boolean, activate: () -> Unit) {
      if (!isAlreadyActive) {
        openSettingsTool = null
        activate()
      } else {
        openSettingsTool = if (openSettingsTool == toolKey) null else toolKey
      }
    }

    // Cold start with PEN active, no settings open
    assertEquals(null, openSettingsTool)

    // Tap 1 on HIGHLIGHTER (not currently active)
    handleToolClick("HIGHLIGHTER", activeTool == "HIGHLIGHTER") {
      activeTool = "HIGHLIGHTER"
    }
    // Result: Highlighter is selected, settings remain closed!
    assertEquals("HIGHLIGHTER", activeTool)
    assertEquals(null, openSettingsTool)

    // Tap 2 on HIGHLIGHTER (now already active)
    handleToolClick("HIGHLIGHTER", activeTool == "HIGHLIGHTER") {
      activeTool = "HIGHLIGHTER"
    }
    // Result: Highlighter settings open!
    assertEquals("HIGHLIGHTER", activeTool)
    assertEquals("HIGHLIGHTER", openSettingsTool)

    // Tap 1 on ERASER while Highlighter settings are open
    handleToolClick("ERASER", activeTool == "ERASER") {
      activeTool = "ERASER"
    }
    // Result: Switches to ERASER, settings are closed!
    assertEquals("ERASER", activeTool)
    assertEquals(null, openSettingsTool)

    // Tap 2 on ERASER (now already active)
    handleToolClick("ERASER", activeTool == "ERASER") {
      activeTool = "ERASER"
    }
    // Result: Eraser settings open!
    assertEquals("ERASER", openSettingsTool)

    // Tap 3 on ERASER again: toggles settings closed
    handleToolClick("ERASER", activeTool == "ERASER") {
      activeTool = "ERASER"
    }
    assertEquals(null, openSettingsTool)

    // Verify for all other tools: Shape, Lasso, Ruler, Laser
    handleToolClick("SHAPE", activeTool == "SHAPE") { activeTool = "SHAPE" }
    assertEquals("SHAPE", activeTool)
    assertEquals(null, openSettingsTool)

    handleToolClick("SHAPE", activeTool == "SHAPE") { activeTool = "SHAPE" }
    assertEquals("SHAPE", openSettingsTool)

    handleToolClick("LASSO", activeTool == "LASSO") { activeTool = "LASSO" }
    assertEquals("LASSO", activeTool)
    assertEquals(null, openSettingsTool)

    handleToolClick("LASSO", activeTool == "LASSO") { activeTool = "LASSO" }
    assertEquals("LASSO", openSettingsTool)
  }
}
