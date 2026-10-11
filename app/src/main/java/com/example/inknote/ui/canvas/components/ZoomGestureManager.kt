package com.example.inknote.ui.canvas.components

import kotlin.math.abs

/**
 * Manages pinch-to-zoom scaling with a smooth, fluid response across the full zoom range
 * (from [minZoom] up to [maxZoom]) and a soft magnetic checkpoint at "Fit to Screen" ([fitZoom]).
 *
 * Behavior:
 * 1. Users can zoom in significantly beyond fit-to-screen (up to 500% / 5.0f) to inspect fine ink details.
 * 2. When passing through fitZoom (1.0f), a gentle magnetic attraction / resistance provides tactile feedback.
 * 3. Unlike a hard lock, continuous deliberate pinching smoothly transitions across fitZoom without getting stuck.
 * 4. On gesture end, if the final scale is within a comfortable margin of fitZoom, it snaps cleanly to fitZoom (1.0f).
 */
class ZoomGestureManager(
    val fitZoom: Float = 1.0f,
    val minZoom: Float = 0.5f,
    val maxZoom: Float = 5.0f
) {
    var isGestureActive: Boolean = false
        private set

    private var gestureStartZoom: Float = fitZoom

    /**
     * Called when a deliberate pinch gesture begins (after the activation threshold has been exceeded).
     */
    fun onGestureStart(currentZoom: Float) {
        isGestureActive = true
        gestureStartZoom = currentZoom
    }

    /**
     * Computes the new zoom level given a relative zoom factor.
     * Tracks finger motion smoothly with a soft magnetic detent around [fitZoom].
     */
    fun onGestureZoom(currentZoom: Float, zoomFactor: Float): Float {
        if (!isGestureActive) return currentZoom

        val distToFit = abs(currentZoom - fitZoom)

        // Apply a gentle magnetic attraction when very close to fitZoom,
        // while allowing deliberate pinch motions to smoothly glide right through.
        val adjustedFactor = if (distToFit < 0.05f) {
            1.0f + (zoomFactor - 1.0f) * 0.75f
        } else {
            zoomFactor
        }

        val targetZoom = (currentZoom * adjustedFactor).coerceIn(minZoom, maxZoom)

        // If crossing fitZoom with very slight movement, softly gravitate to fitZoom
        return if (distToFit < 0.015f && abs(zoomFactor - 1.0f) < 0.01f) {
            fitZoom
        } else {
            targetZoom
        }
    }

    /**
     * Called when the pinch gesture ends (fingers lifted).
     * Snaps cleanly onto [fitZoom] if settled close to it, or clamps between [minZoom] and [maxZoom].
     */
    fun onGestureEnd(currentZoom: Float): Float {
        isGestureActive = false
        return if (abs(currentZoom - fitZoom) <= 0.06f) {
            fitZoom
        } else {
            currentZoom.coerceIn(minZoom, maxZoom)
        }
    }
}
