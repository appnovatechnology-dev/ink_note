package com.example.inknote.ui.canvas.components

import android.content.Context
import android.hardware.input.InputManager
import android.view.InputDevice
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import kotlin.math.abs
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
    getIsZoomedIn: () -> Boolean = { false },
    onGestureStart: (isStylus: Boolean, pointerType: PointerType, startPosition: Offset, pressure: Float) -> Unit,
    onGestureMove: (isStylus: Boolean, currentPosition: Offset, pressure: Float) -> Unit,
    onGestureEnd: (isStylus: Boolean, wasDrag: Boolean, lastPosition: Offset) -> Unit,
    onPinchZoom: (zoomFactor: Float) -> Unit,
    onPinchStart: () -> Unit = {},
    onPinchEnd: () -> Unit = {},
    onPinchPan: (panDelta: Offset) -> Unit = {},
    onDragPan: (panDelta: Offset) -> Unit = {},
    onDoubleTap: () -> Unit = {}
) {
    awaitPointerEventScope {
        var activeTrackingPointerId: PointerId? = null
        var activeTrackingIsStylus = false
        var activeTrackingLastPos = Offset.Zero
        var activeTrackingIsDrag = false

        // Deliberate pinch-to-zoom tracking with activation threshold
        var isPinching = false
        var isPinchGestureRecognized = false
        var initialPinchDistance = 0.0
        var previousPinchDistance = 0.0
        // Deliberate span change required before pinch responds (eliminates accidental triggers & resting finger jitter)
        val pinchActivationThresholdPx = 36.0

        // Double-tap tracking to return to Fit-To-Screen
        var touchTapDownTime = 0L
        var touchTapDownPos = Offset.Zero
        var touchTapMoved = false
        var lastDoubleTapTime = 0L
        var lastDoubleTapPos = Offset.Zero

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
                touchTapDownTime = 0L // Cancel any single tap during multi-touch
                val p1 = pressedTouchChanges[0]
                val p2 = pressedTouchChanges[1]
                val currDist = hypot(
                    (p1.position.x - p2.position.x).toDouble(),
                    (p1.position.y - p2.position.y).toDouble()
                )

                if (!isPinching) {
                    isPinching = true
                    isPinchGestureRecognized = false
                    initialPinchDistance = currDist
                    previousPinchDistance = currDist
                } else if (!isPinchGestureRecognized) {
                    // Require deliberate motion exceeding activation threshold before recognizing as pinch
                    val spanDelta = abs(currDist - initialPinchDistance)
                    if (spanDelta >= pinchActivationThresholdPx) {
                        isPinchGestureRecognized = true
                        previousPinchDistance = currDist
                        p1.consume()
                        p2.consume()
                        onPinchStart()
                    }
                } else {
                    p1.consume()
                    p2.consume()
                    if (previousPinchDistance > 10.0 && currDist > 10.0) {
                        val rawFactor = (currDist / previousPinchDistance).toFloat()
                        // Well-tuned scaling factor: smooth scaling tracking without disorienting jumps or vibration
                        val tunedFactor = 1.0f + (rawFactor - 1.0f) * 0.60f
                        val safeFactor = tunedFactor.coerceIn(0.85f, 1.18f)
                        if (abs(safeFactor - 1.0f) > 0.002f) {
                            onPinchZoom(safeFactor)
                        }
                    }
                    previousPinchDistance = currDist
                }
                continue
            } else if (isPinching) {
                if (isPinchGestureRecognized) {
                    onPinchEnd()
                }
                isPinching = false
                isPinchGestureRecognized = false
                initialPinchDistance = 0.0
                previousPinchDistance = 0.0
            }

            // Track tap & double-tap on canvas for fast return to Fit-To-Screen
            if (pressedTouchChanges.size == 1 && !hasStylusPressed) {
                val t = pressedTouchChanges[0]
                if (touchTapDownTime == 0L) {
                    touchTapDownTime = System.currentTimeMillis()
                    touchTapDownPos = t.position
                    touchTapMoved = false
                } else {
                    val dist = hypot((t.position.x - touchTapDownPos.x).toDouble(), (t.position.y - touchTapDownPos.y).toDouble())
                    if (dist > 18.0) {
                        touchTapMoved = true
                    }
                }
            } else if (touchTapDownTime != 0L) {
                val now = System.currentTimeMillis()
                if (!touchTapMoved && (now - touchTapDownTime) < 280L) {
                    val tapDist = hypot((touchTapDownPos.x - lastDoubleTapPos.x).toDouble(), (touchTapDownPos.y - lastDoubleTapPos.y).toDouble())
                    if (now - lastDoubleTapTime < 380L && tapDist < 50.0) {
                        onDoubleTap()
                        lastDoubleTapTime = 0L
                    } else {
                        lastDoubleTapTime = now
                        lastDoubleTapPos = touchTapDownPos
                    }
                }
                touchTapDownTime = 0L
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
                    // Rejected for drawing! (Touch pointer in STYLUS_ONLY or AUTO with active stylus)
                    // The event is left unconsumed so the parent viewport handles free-form 2D panning (when zoomed in)
                    // or native continuous vertical scrolling (at fit-to-screen) with zero coordinate oscillation/vibration.
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
    onPinchEnd: () -> Unit = {},
    onDoubleTap: () -> Unit = {}
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
        onPinchEnd = onPinchEnd,
        onDoubleTap = onDoubleTap
    )
}

