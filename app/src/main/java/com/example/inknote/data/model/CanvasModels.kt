package com.example.inknote.data.model

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

enum class ToolType {
    PEN,
    HIGHLIGHTER,
    ERASER,
    LASSO,
    LASER,
    TEXT,
    SHAPE,
    RULER,
    PROTRACTOR,
    COMPASS,
    MEDIA,
    HAND
}

enum class PalmRejectionMode {
    STYLUS_ONLY,
    AUTO,
    OFF
}

enum class TemplateType {
    BLANK,
    LINED_NARROW,
    LINED_WIDE,
    GRID_SMALL,
    GRID_LARGE,
    DOTTED,
    CORNELL,
    MUSIC
}

enum class ShapeType {
    LINE,
    ARROW,
    RECTANGLE,
    SQUARE,
    CIRCLE,
    ELLIPSE,
    TRIANGLE,
    POLYGON,
    STAR
}

data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1.0f,
    val timestamp: Long = System.currentTimeMillis()
)

data class VectorStroke(
    val id: Long = 0,
    val pageId: Long = 0,
    val toolType: ToolType = ToolType.PEN,
    val color: Color = Color(0xFF1E293B),
    val strokeWidth: Float = 3.5f,
    val alpha: Float = 1.0f,
    val points: List<StrokePoint> = emptyList(),
    val audioTimestampMs: Long? = null,
    val isGeometricSnap: Boolean = false,
    val isSelected: Boolean = false
) {
    fun toSerializedPoints(): String {
        return points.joinToString(";") { "${it.x},${it.y},${it.pressure},${it.timestamp}" }
    }

    companion object {
        fun parseSerializedPoints(data: String): List<StrokePoint> {
            if (data.isBlank()) return emptyList()
            return data.split(";").mapNotNull { part ->
                val tokens = part.split(",")
                if (tokens.size >= 2) {
                    val x = tokens[0].toFloatOrNull() ?: return@mapNotNull null
                    val y = tokens[1].toFloatOrNull() ?: return@mapNotNull null
                    val pressure = tokens.getOrNull(2)?.toFloatOrNull() ?: 1.0f
                    val timestamp = tokens.getOrNull(3)?.toLongOrNull() ?: 0L
                    StrokePoint(x, y, pressure, timestamp)
                } else null
            }
        }
    }
}

data class GeometricShape(
    val id: Long = 0,
    val pageId: Long = 0,
    val shapeType: ShapeType = ShapeType.RECTANGLE,
    val startX: Float = 0f,
    val startY: Float = 0f,
    val endX: Float = 100f,
    val endY: Float = 100f,
    val strokeColor: Color = Color(0xFF1E293B),
    val fillColor: Color = Color.Transparent,
    val strokeWidth: Float = 3.5f,
    val rotationDegrees: Float = 0f,
    val isFilled: Boolean = false,
    val sideCount: Int = 5,
    val audioTimestampMs: Long? = null,
    val isSelected: Boolean = false
) {
    val width: Float get() = kotlin.math.abs(endX - startX)
    val height: Float get() = kotlin.math.abs(endY - startY)
    val left: Float get() = kotlin.math.min(startX, endX)
    val top: Float get() = kotlin.math.min(startY, endY)
    val centerX: Float get() = (startX + endX) / 2f
    val centerY: Float get() = (startY + endY) / 2f
}

data class CanvasTextItem(
    val id: Long = 0,
    val pageId: Long = 0,
    val text: String = "",
    val x: Float = 50f,
    val y: Float = 50f,
    val width: Float = 220f,
    val height: Float = 80f,
    val fontSize: Float = 16f,
    val fontColor: Color = Color(0xFF1E293B),
    val isBold: Boolean = false,
    val isItalic: Boolean = false,
    val backgroundColor: Color = Color.Transparent,
    val audioTimestampMs: Long? = null,
    val isEditing: Boolean = false,
    val isSelected: Boolean = false,
    val isMathFormula: Boolean = false,
    val linkNotebookId: Long? = null,
    val linkNotebookTitle: String? = null
)

data class CanvasMediaItem(
    val id: Long = 0,
    val pageId: Long = 0,
    val localUri: String = "",
    val x: Float = 50f,
    val y: Float = 50f,
    val width: Float = 260f,
    val height: Float = 200f,
    val rotationDegrees: Float = 0f,
    val audioTimestampMs: Long? = null,
    val isSelected: Boolean = false
)

data class RulerState(
    val isVisible: Boolean = false,
    val centerX: Float = 400f,
    val centerY: Float = 600f,
    val length: Float = 450f,
    val width: Float = 70f,
    val angleDegrees: Float = 0f
)

data class ProtractorState(
    val isVisible: Boolean = false,
    val centerX: Float = 400f,
    val centerY: Float = 600f,
    val radius: Float = 180f,
    val rotationDegrees: Float = 0f,
    val currentSweepAngle: Float = 0f
)

data class CompassState(
    val isVisible: Boolean = false,
    val pivotX: Float = 400f,
    val pivotY: Float = 600f,
    val radius: Float = 120f,
    val currentAngleDegrees: Float = 0f,
    val isDrawingArc: Boolean = false
)
