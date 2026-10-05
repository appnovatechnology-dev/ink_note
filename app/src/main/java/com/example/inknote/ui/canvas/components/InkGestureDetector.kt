package com.example.inknote.ui.canvas.components

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import kotlin.math.hypot

import com.example.inknote.data.model.PalmRejectionMode

/**
 * State holding hardware pointer detection information and managing palm rejection.
 */
class StylusInputState(
    var isStylusActive: Boolean = false,
    var lastStylusTimestamp: Long = 0L,
    private val palmRejectionTimeoutMs: Long = 3000L
) {
    fun recordStylusEvent() {
        isStylusActive = true
        lastStylusTimestamp = System.currentTimeMillis()
    }

    fun isStylusRecentlyActive(): Boolean {
        return isStylusActive && (System.currentTimeMillis() - lastStylusTimestamp < palmRejectionTimeoutMs)
    }

    fun isStylusConnectedOrActive(context: Context? = null): Boolean {
        if (isStylusActive) return true
        if (context != null) {
            try {
                val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
                val deviceIds = inputManager?.inputDeviceIds ?: return false
                for (id in deviceIds) {
                    val device = inputManager.getInputDevice(id) ?: continue
                    val sources = device.sources
                    if ((sources and InputDevice.SOURCE_STYLUS) == InputDevice.SOURCE_STYLUS ||
                        (sources and InputDevice.SOURCE_BLUETOOTH_STYLUS) == InputDevice.SOURCE_BLUETOOTH_STYLUS) {
                        isStylusActive = true
                        return true
                    }
                }
            } catch (_: Exception) {}
        }
        return false
    }

    /**
     * Determines whether an incoming pointer event should be ignored for drawing ink.
     * When rejected, the event is left unconsumed so fingers can freely scroll and pinch-zoom.
     */
    fun shouldRejectTouch(
        pointerType: PointerType,
        mode: PalmRejectionMode = PalmRejectionMode.STYLUS_ONLY,
        allowTouch: Boolean = false,
        hasActiveStylus: Boolean = isStylusActive
    ): Boolean {
        // Stylus and Eraser pointers are always accepted for drawing
        if (pointerType == PointerType.Stylus || pointerType == PointerType.Eraser) return false

        // If tool explicitly allows touch
        if (allowTouch) return false

        // OFF mode: Both fingers and stylus can produce ink
        if (mode == PalmRejectionMode.OFF) return false

        // STYLUS_ONLY mode:
        // When in Stylus Only mode, finger and palm touches NEVER perform tool actions
        // (drawing, lasso selection/move, shape drawing, laser pointing, erasing).
        // This leaves fingers dedicated for scrolling, pinch-zoom, and UI interaction without accidental edits.
        if (mode == PalmRejectionMode.STYLUS_ONLY) {
            return true
        }

        // AUTO mode: Reject finger/palm touch if stylus is active or was recently detected
        return isStylusRecentlyActive() || hasActiveStylus
    }

    fun reset() {
        isStylusActive = false
        lastStylusTimestamp = 0L
    }
}

/**
 * Custom PointerInputScope gesture detector that explicitly distinguishes between 'stylus' and 'touch'
 * pointer types.
 *
 * Fully supports resting a palm flat on the screen while writing sentences with a stylus,
 * touching with a second finger mid-stroke without interrupting the stroke, and rapidly lifting
 * and replacing the stylus with zero stray marks.
 */
suspend fun PointerInputScope.detectInkGesturesWithPalmRejection(
    inputState: StylusInputState,
    getPalmRejectionMode: () -> PalmRejectionMode,
    getAllowTouch: () -> Boolean,
    onGestureStart: (isStylus: Boolean, pointerType: PointerType, startPosition: Offset, pressure: Float) -> Unit,
    onGestureMove: (isStylus: Boolean, currentPosition: Offset, pressure: Float) -> Unit,
    onGestureEnd: (isStylus: Boolean, wasDrag: Boolean, lastPosition: Offset) -> Unit,
    onPinchZoom: (zoomFactor: Float) -> Unit,
    onPinchStart: () -> Unit = {},
    onPinchEnd: () -> Unit = {}
) {
    awaitPointerEventScope {
        var activeTrackingPointerId: PointerId? = null
        var activeTrackingIsStylus = false
        var activeTrackingLastPos = Offset.Zero
        var activeTrackingIsDrag = false

        var isPinching = false
        var previousPinchDistance = 0.0

        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Main)
            val currentMode = getPalmRejectionMode()
            val currentAllowTouch = getAllowTouch()

            // Record stylus presence on any stylus contact or hover
            for (change in event.changes) {
                if (change.type == PointerType.Stylus || change.type == PointerType.Eraser) {
                    inputState.recordStylusEvent()
                }
            }
            val hasActiveStylus = inputState.isStylusActive

            // 1. If we are currently tracking an active stroke:
            if (activeTrackingPointerId != null) {
                val trackingChange = event.changes.firstOrNull { it.id == activeTrackingPointerId }

                if (trackingChange == null || !trackingChange.pressed) {
                    // Active drawing pointer was lifted
                    trackingChange?.consume()
                    onGestureEnd(activeTrackingIsStylus, activeTrackingIsDrag, activeTrackingLastPos)
                    activeTrackingPointerId = null
                    activeTrackingIsStylus = false
                    activeTrackingIsDrag = false
                } else {
                    // Active drawing pointer moved
                    val newPos = trackingChange.position
                    val dist = hypot(
                        (newPos.x - activeTrackingLastPos.x).toDouble(),
                        (newPos.y - activeTrackingLastPos.y).toDouble()
                    )
                    if (dist > 1.5) {
                        activeTrackingIsDrag = true
                    }
                    trackingChange.consume()
                    activeTrackingLastPos = newPos
                    val pressure = if (trackingChange.pressure > 0f) trackingChange.pressure else 1.0f
                    onGestureMove(activeTrackingIsStylus, activeTrackingLastPos, pressure)
                }

                // While actively drawing with stylus, ANY simultaneous palm or finger touch
                // is ignored: zero stray marks, no aborted stroke, no accidental pinch.
                continue
            }

            // 2. Not currently drawing a stroke.
            // Check for multi-touch pinch-to-zoom (ONLY between touch fingers when NO stylus is active)
            val pressedTouchChanges = event.changes.filter { it.pressed && it.type == PointerType.Touch }
            val hasStylusPressed = event.changes.any { it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser) }

            if (pressedTouchChanges.size >= 2 && !hasStylusPressed) {
                if (!isPinching) {
                    isPinching = true
                    previousPinchDistance = 0.0
                    onPinchStart()
                }
                val p1 = pressedTouchChanges[0]
                val p2 = pressedTouchChanges[1]
                val currDist = hypot(
                    (p1.position.x - p2.position.x).toDouble(),
                    (p1.position.y - p2.position.y).toDouble()
                )
                if (previousPinchDistance > 5.0 && currDist > 5.0) {
                    val factor = (currDist / previousPinchDistance).toFloat()
                    if (factor in 0.5f..2.0f) {
                        onPinchZoom(factor)
                    }
                }
                previousPinchDistance = currDist
                continue
            } else if (isPinching) {
                isPinching = false
                previousPinchDistance = 0.0
                onPinchEnd()
            }

            // 3. Check for new stroke down:
            // Priority 1: Stylus pointer down (even if a palm is already resting flat on the screen!)
            val stylusDown = event.changes.firstOrNull {
                it.pressed && (it.type == PointerType.Stylus || it.type == PointerType.Eraser)
            }
            if (stylusDown != null) {
                inputState.recordStylusEvent()
                stylusDown.consume()
                activeTrackingPointerId = stylusDown.id
                activeTrackingIsStylus = true
                activeTrackingLastPos = stylusDown.position
                activeTrackingIsDrag = false
                val pressure = if (stylusDown.pressure > 0f) stylusDown.pressure else 1.0f
                onGestureStart(true, stylusDown.type, stylusDown.position, pressure)
                continue
            }

            // Priority 2: Non-stylus pointer (finger / mouse)
            val candidateDown = event.changes.firstOrNull { it.pressed }
            if (candidateDown != null) {
                val shouldReject = inputState.shouldRejectTouch(
                    pointerType = candidateDown.type,
                    mode = currentMode,
                    allowTouch = currentAllowTouch,
                    hasActiveStylus = hasActiveStylus
                )

                if (!shouldReject) {
                    candidateDown.consume()
                    activeTrackingPointerId = candidateDown.id
                    activeTrackingIsStylus = false
                    activeTrackingLastPos = candidateDown.position
                    activeTrackingIsDrag = false
                    val pressure = if (candidateDown.pressure > 0f) candidateDown.pressure else 1.0f
                    onGestureStart(false, candidateDown.type, candidateDown.position, pressure)
                } else {
                    // Rejected for drawing! Event is left unconsumed so LazyColumn can scroll smoothly.
                    // The pointer loop continues to run without blocking, ensuring that when the stylus
                    // touches the screen, it is immediately detected.
                }
            }
        }
    }
}

/**
 * Backward compatibility overload for existing call sites.
 */
suspend fun PointerInputScope.detectInkGesturesWithPalmRejection(
    inputState: StylusInputState,
    palmRejectionMode: PalmRejectionMode = PalmRejectionMode.STYLUS_ONLY,
    allowTouch: Boolean = false,
    onGestureStart: (isStylus: Boolean, pointerType: PointerType, startPosition: Offset, pressure: Float) -> Unit,
    onGestureMove: (isStylus: Boolean, currentPosition: Offset, pressure: Float) -> Unit,
    onGestureEnd: (isStylus: Boolean, wasDrag: Boolean, lastPosition: Offset) -> Unit,
    onPinchZoom: (zoomFactor: Float) -> Unit,
    onPinchStart: () -> Unit = {},
    onPinchEnd: () -> Unit = {}
) {
    detectInkGesturesWithPalmRejection(
        inputState = inputState,
        getPalmRejectionMode = { palmRejectionMode },
        getAllowTouch = { allowTouch },
        onGestureStart = onGestureStart,
        onGestureMove = onGestureMove,
        onGestureEnd = onGestureEnd,
        onPinchZoom = onPinchZoom,
        onPinchStart = onPinchStart,
        onPinchEnd = onPinchEnd
    )
}

