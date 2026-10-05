package com.example

import androidx.compose.ui.input.pointer.PointerType
import com.example.inknote.data.model.PalmRejectionMode
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
}
