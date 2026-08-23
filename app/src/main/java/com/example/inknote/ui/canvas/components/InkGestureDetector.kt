package com.example.inknote.ui.canvas.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.PointerType
import kotlin.math.hypot

/**
 * State holding hardware pointer detection information and managing palm rejection.
 */
class StylusInputState(
    var isStylusActive: Boolean = false,
    var lastStylusTimestamp: Long = 0L,
    private val palmRejectionTimeoutMs: Long = 1500L
) {
    fun recordStylusEvent() {
        isStylusActive = true
        lastStylusTimestamp = System.currentTimeMillis()
    }

    fun isStylusRecentlyActive(): Boolean {
        return isStylusActive && (System.currentTimeMillis() - lastStylusTimestamp < palmRejectionTimeoutMs)
    }

    /**
     * Determines whether an incoming pointer event should be ignored for reliable palm rejection.
     * When a stylus is active, ONLY stylus pointers can produce ink.
     */
    fun shouldRejectTouch(pointerType: PointerType, palmRejectionEnabled: Boolean): Boolean {
        if (!palmRejectionEnabled) return false
        // Stylus and Eraser pointers are always accepted as active drawing tools
        if (pointerType == PointerType.Stylus || pointerType == PointerType.Eraser) return false
        // Reject Touch (finger/palm) if stylus is active or was recently detected
        return isStylusRecentlyActive()
    }

    fun reset() {
        isStylusActive = false
        lastStylusTimestamp = 0L
    }
}

/**
 * Custom PointerInputScope gesture detector that explicitly distinguishes between 'stylus' and 'touch'
 * pointer types. When a stylus is detected, touch events are ignored to ensure reliable palm rejection.
 * Provides high-performance multi-touch pinch-to-zoom in and out without accidental ink strokes.
 */
suspend fun PointerInputScope.detectInkGesturesWithPalmRejection(
    inputState: StylusInputState,
    palmRejectionEnabled: Boolean,
    onGestureStart: (isStylus: Boolean, pointerType: PointerType, startPosition: Offset, pressure: Float) -> Unit,
    onGestureMove: (isStylus: Boolean, currentPosition: Offset, pressure: Float) -> Unit,
    onGestureEnd: (isStylus: Boolean, wasDrag: Boolean, lastPosition: Offset) -> Unit,
    onPinchZoom: (zoomFactor: Float) -> Unit,
    onPinchStart: () -> Unit = {},
    onPinchEnd: () -> Unit = {}
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val pointerType = down.type
        val isStylus = pointerType == PointerType.Stylus || pointerType == PointerType.Eraser

        if (isStylus) {
            inputState.recordStylusEvent()
        }

        // Palm Rejection: When stylus is active or palm rejection is enabled for touch,
        // do not consume the touch so that the parent view can scroll
        if (inputState.shouldRejectTouch(pointerType, palmRejectionEnabled)) {
            return@awaitEachGesture
        }

        var isDrag = false
        var isPinching = false
        var hadPinch = false
        var lastPosition = down.position
        val pointerId = down.id
        var previousPinchDistance = 0.0
        var gestureStarted = false

        // Do not immediately start drawing if this might be a multi-touch gesture
        val startPressure = if (down.pressure > 0f) down.pressure else 1.0f
        onGestureStart(isStylus, pointerType, down.position, startPressure)
        gestureStarted = true

        while (true) {
            val event = awaitPointerEvent()
            val pressedChanges = event.changes.filter { it.pressed }

            // Multi-finger pinch-to-zoom / transform detection (2 or more fingers)
            if (pressedChanges.size >= 2) {
                if (!isPinching) {
                    isPinching = true
                    hadPinch = true
                    previousPinchDistance = 0.0
                    onPinchStart()
                }

                val p1 = pressedChanges[0]
                val p2 = pressedChanges[1]
                val currDist = hypot((p1.position.x - p2.position.x).toDouble(), (p1.position.y - p2.position.y).toDouble())

                if (previousPinchDistance > 5.0 && currDist > 5.0) {
                    val rawFactor = (currDist / previousPinchDistance).toFloat()
                    if (rawFactor in 0.5f..2.0f) {
                        onPinchZoom(rawFactor)
                    }
                }
                previousPinchDistance = currDist

                // Allow transformation gestures to process without drawing strokes
                if (event.changes.none { it.pressed }) {
                    onPinchEnd()
                    break
                }
                continue
            } else if (isPinching) {
                // Fingers lifted after a pinch
                isPinching = false
                previousPinchDistance = 0.0
                onPinchEnd()

                if (pressedChanges.isEmpty()) {
                    break
                }
                continue
            }

            // If a pinch occurred at any point during this gesture sequence, discard ink strokes
            if (hadPinch) {
                if (pressedChanges.isEmpty()) {
                    break
                }
                continue
            }

            // If no fingers are pressed, complete gesture
            if (pressedChanges.isEmpty()) {
                if (gestureStarted && !hadPinch) {
                    onGestureEnd(isStylus, isDrag, lastPosition)
                }
                break
            }

            // Check if a stylus landed while a finger was touching
            val stylusChange = event.changes.firstOrNull { it.type == PointerType.Stylus || it.type == PointerType.Eraser }
            if (stylusChange != null) {
                inputState.recordStylusEvent()
                if (!isStylus && palmRejectionEnabled) {
                    // Cancel finger touch in favor of stylus
                    if (gestureStarted && !hadPinch) {
                        onGestureEnd(false, isDrag, lastPosition)
                    }
                    return@awaitEachGesture
                }
            }

            val change = event.changes.firstOrNull { it.id == pointerId }
            if (change == null) {
                if (isDrag && gestureStarted && !hadPinch) {
                    onGestureEnd(isStylus, isDrag, lastPosition)
                }
                break
            }

            if (change.pressed) {
                val dist = hypot((change.position.x - lastPosition.x).toDouble(), (change.position.y - lastPosition.y).toDouble())
                if (dist > 1.5) {
                    isDrag = true
                }
                change.consume()
                lastPosition = change.position
                if (isStylus) {
                    inputState.recordStylusEvent()
                }
                val pressure = if (change.pressure > 0f) change.pressure else 1.0f
                onGestureMove(isStylus, change.position, pressure)
            } else {
                change.consume()
                if (gestureStarted && !hadPinch) {
                    onGestureEnd(isStylus, isDrag, lastPosition)
                }
                break
            }
        }
    }
}
